package com.lirix.app.service

import kotlinx.coroutines.ensureActive
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.lirix.app.MainActivity
import com.lirix.app.feature.AggregatedLyricsProvider
import com.lirix.app.feature.MusicFeatureEngine
import com.lirix.app.ingestion.MediaSessionCollector
import com.lirix.app.ingestion.SyncOffsetStore
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.KaraokeLine
import com.lirix.app.ui.components.FloatingLyricsOverlayView
import com.lirix.app.ui.parseLrcLinesStrict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Foreground service hosting the Floating Lyrics & Mini-Player Overlay.
 * Integrates with WindowManager (SYSTEM_ALERT_WINDOW), MediaSessionCollector, and AppDatabase.
 * Spec: TASK-FLT-01 (.sdd/tasks/TASK-FLT-01.md)
 */
class FloatingLyricsService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var syncJob: Job? = null
    private var fetchLyricsJob: Job? = null

    private var windowManager: WindowManager? = null
    private var overlayView: FloatingLyricsOverlayView? = null

    private val _overlayState = MutableStateFlow(FloatingLyricsState())
    val overlayState: StateFlow<FloatingLyricsState> = _overlayState.asStateFlow()

    private lateinit var syncOffsetStore: SyncOffsetStore
    private var currentCachedLyrics: LyricsCacheEntity? = null
    private var cachedLrcLines: List<KaraokeLine> = emptyList()
    private var lastLoadedTrackKey: String? = null
    private val screenInteractive = MutableStateFlow(false)
    private val refreshSignal = MutableStateFlow(0)
    @Volatile private var isFetchingLyrics = false
    private var screenReceiverRegistered = false
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            screenInteractive.value = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        syncOffsetStore = SyncOffsetStore(this)

        if (!isPermissionGranted(this)) {
            Timber.w("SYSTEM_ALERT_WINDOW permission not granted. Stopping FloatingLyricsService.")
            stopSelf()
            return
        }

        try {
            MediaSessionCollector.getInstance(this).startListening()
            Timber.i("MediaSessionCollector listening started from FloatingLyricsService.onCreate")
        } catch (e: Exception) {
            Timber.w(e, "Error starting MediaSessionCollector in FloatingLyricsService")
        }

        createNotificationChannel()
        startForegroundServiceNotification()

        initOverlayView()
        ContextCompat.registerReceiver(
            this, screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        screenReceiverRegistered = true
        screenInteractive.value = (getSystemService(POWER_SERVICE) as PowerManager).isInteractive
        startPlaybackSync()
        _isRunning.value = true
        Timber.i("FloatingLyricsService started successfully")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Плавающий оверлей караоке",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Отображение караоке и аккордов поверх сторонних приложений"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceNotification() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val stopIntent = Intent(this, FloatingLyricsService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Lirix • Плавающий оверлей")
            .setContentText("Текст караоке активен поверх приложений")
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Закрыть", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error starting foreground service notification in FloatingLyricsService")
        }
    }

    private fun initOverlayView() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val density = resources.displayMetrics.density
            val displayHeight = resources.displayMetrics.heightPixels

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = (16 * density).toInt()
                y = (displayHeight * 0.22f).toInt()
            }

            overlayView = FloatingLyricsOverlayView(
                context = this,
                windowManager = windowManager!!,
                layoutParams = params,
                stateFlow = overlayState,
                onToggleExpand = { toggleExpanded() },
                onCloseClick = { stopSelf() }
            )

            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize FloatingLyricsOverlayView")
            stopSelf()
        }
    }

    private fun toggleExpanded() {
        val current = _overlayState.value
        _overlayState.value = current.copy(isExpanded = !current.isExpanded)
    }

    private fun startPlaybackSync() {
        syncJob = serviceScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val offsetChanges = callbackFlow {
                val preferences = getSharedPreferences("media_sync_offsets", MODE_PRIVATE)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
                preferences.registerOnSharedPreferenceChangeListener(listener)
                trySend(Unit)
                awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            // A calibration change must also update paused lyrics, without a polling timer.
            combine(screenInteractive, MediaSessionCollector.livePlaybackFlow, refreshSignal, offsetChanges) { interactive, snapshot, _, _ ->
                interactive to snapshot
            }.collectLatest { (interactive, snapshot) ->
                if (!interactive) return@collectLatest
                do {
                    try {
                        if (snapshot != null && snapshot.title.isNotBlank()) {
                            val trackKey = MusicFeatureEngine.computeTrackKey(
                                snapshot.title,
                                snapshot.artist.ifBlank { "Unknown Artist" },
                                snapshot.album
                            )

                            // Reload lyrics cache if track changed
                            if (trackKey != lastLoadedTrackKey) {
                                // collectLatest can cancel the Room read; commit the key only after it succeeds.
                                val loadedLyrics = db.lyricsDao().getLyrics(trackKey)
                                fetchLyricsJob?.cancel()
                                fetchLyricsJob = null
                                isFetchingLyrics = false
                                lastLoadedTrackKey = trackKey
                                currentCachedLyrics = loadedLyrics
                                cachedLrcLines = currentCachedLyrics?.syncedLyricsLrc?.let { parseLrcLinesStrict(it) } ?: emptyList()

                                // If lyrics not in Room cache, trigger asynchronous prefetch from network
                                val lacksLyrics = currentCachedLyrics == null ||
                                    (currentCachedLyrics?.syncedLyricsLrc.isNullOrBlank() && currentCachedLyrics?.plainLyrics.isNullOrBlank())

                                if (lacksLyrics) {
                                    fetchLyricsJob?.cancel()
                                    isFetchingLyrics = true
                                    fetchLyricsJob = serviceScope.launch(Dispatchers.IO) {
                                        try {
                                            com.lirix.app.feature.lyrics.AutomaticLookupCoordinator.get(db).lookup(trackKey,
                                            com.lirix.app.feature.lyrics.LookupTarget.LYRICS,
                                            com.lirix.app.feature.lyrics.LookupNetworkMonitor.usable(applicationContext))
                                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                                        if (trackKey == lastLoadedTrackKey) {
                                            currentCachedLyrics = db.lyricsDao().getLyrics(trackKey)
                                            cachedLrcLines = currentCachedLyrics?.syncedLyricsLrc?.let { parseLrcLinesStrict(it) } ?: emptyList()
                                        }
                                    } catch (e: CancellationException) {
                                            throw e
                                        } catch (e: Exception) {
                                            Timber.tag(TAG).w(e, "Error prefetching lyrics for %s", trackKey)
                                        } finally {
                                            if (lastLoadedTrackKey == trackKey) {
                                                isFetchingLyrics = false
                                                refreshSignal.update { it + 1 }
                                            }
                                        }
                                    }
                                }
                            }

                            // Calculate current extrapolated position with calibration offset
                            val offsetMs = syncOffsetStore.getOffset(trackKey)
                            val extrapolatedPos = (snapshot.currentPositionMs() + offsetMs).coerceAtLeast(0L)

                            val hasSynced = cachedLrcLines.isNotEmpty()
                            val hasPlain = !currentCachedLyrics?.plainLyrics.isNullOrBlank()
                            val isFetching = isFetchingLyrics

                            // Resolve active lyrics line
                            var activeText = ""
                            var prevText: String? = null
                            var nextText: String? = null

                            if (hasSynced) {
                                val activeIdx = cachedLrcLines.indexOfLast { it.timestampMs <= extrapolatedPos }
                                if (activeIdx in cachedLrcLines.indices) {
                                    activeText = cachedLrcLines[activeIdx].text
                                    prevText = if (activeIdx > 0) cachedLrcLines[activeIdx - 1].text else null
                                    nextText = if (activeIdx < cachedLrcLines.lastIndex) cachedLrcLines[activeIdx + 1].text else null
                                } else if (cachedLrcLines.isNotEmpty()) {
                                    nextText = cachedLrcLines.first().text
                                }
                            } else if (hasPlain) {
                                activeText = "📜 Текст без таймкодов (статичный)"
                                prevText = null
                                nextText = null
                            } else if (isFetching) {
                                activeText = "Загрузка текста..."
                                prevText = null
                                nextText = null
                            }

                            _overlayState.value = _overlayState.value.copy(
                                isPlaying = snapshot.isPlaying,
                                title = snapshot.title,
                                artist = snapshot.artist,
                                album = snapshot.album,
                                albumArtUri = snapshot.albumArtUri,
                                activeLineText = activeText,
                                previousLineText = prevText,
                                nextLineText = nextText,
                                currentChord = null,
                                hasLyrics = hasSynced || hasPlain,
                                isSynced = hasSynced
                            )
                        } else {
                            // Music idle state
                            fetchLyricsJob?.cancel()
                            isFetchingLyrics = false
                            lastLoadedTrackKey = null
                            currentCachedLyrics = null
                            cachedLrcLines = emptyList()
                            _overlayState.value = _overlayState.value.copy(
                                isPlaying = false,
                                activeLineText = "",
                                previousLineText = null,
                                nextLineText = null,
                                hasLyrics = false,
                                isSynced = false
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Error in FloatingLyricsService sync loop")
                    }

                    // Paused/idle/static lyrics update on events. Only visible timed lyrics need a clock.
                    if (snapshot?.isPlaying != true || cachedLrcLines.isEmpty()) break
                    delay(100L)
                } while (isActive)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        _isRunning.value = false
        if (screenReceiverRegistered) {
            unregisterReceiver(screenReceiver)
            screenReceiverRegistered = false
        }
        fetchLyricsJob?.cancel()
        syncJob?.cancel()
        serviceScope.cancel()

        overlayView?.let { view ->
            try {
                view.onDestroy()
                windowManager?.removeView(view)
            } catch (e: Exception) {
                Timber.w(e, "Error removing overlay view from WindowManager")
            }
        }
        overlayView = null

        super.onDestroy()
        Timber.i("FloatingLyricsService destroyed")
    }

    companion object {
        private const val TAG = "FloatingLyricsService"
        private const val CHANNEL_ID = "floating_lyrics_overlay_channel"
        private const val NOTIFICATION_ID = 50505
        const val ACTION_STOP = "com.lirix.app.service.ACTION_STOP_FLOATING_OVERLAY"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun isPermissionGranted(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        fun requestPermission(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }

        fun start(context: Context) {
            if (!isPermissionGranted(context)) {
                requestPermission(context)
                return
            }
            try {
                val intent = Intent(context, FloatingLyricsService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start FloatingLyricsService")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, FloatingLyricsService::class.java).apply {
                    action = ACTION_STOP
                }
                context.startService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop FloatingLyricsService")
            }
        }

        fun toggle(context: Context) {
            if (_isRunning.value) {
                stop(context)
            } else {
                start(context)
            }
        }
    }
}
