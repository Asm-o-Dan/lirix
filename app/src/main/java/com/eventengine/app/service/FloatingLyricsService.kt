package com.eventengine.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.eventengine.app.MainActivity
import com.eventengine.app.feature.AggregatedLyricsProvider
import com.eventengine.app.feature.MusicFeatureEngine
import com.eventengine.app.ingestion.LivePlaybackSnapshot
import com.eventengine.app.ingestion.MediaSessionCollector
import com.eventengine.app.ingestion.SyncOffsetStore
import com.eventengine.app.storage.AppDatabase
import com.eventengine.app.storage.LyricsCacheEntity
import com.eventengine.app.storage.TrackEntity
import com.eventengine.app.ui.KaraokeLine
import com.eventengine.app.ui.components.FloatingLyricsOverlayView
import com.eventengine.app.ui.parseLrcLinesStrict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
            MediaSessionCollector(applicationContext).startListening()
            Timber.i("MediaSessionCollector listening started from FloatingLyricsService.onCreate")
        } catch (e: Exception) {
            Timber.w(e, "Error starting MediaSessionCollector in FloatingLyricsService")
        }

        createNotificationChannel()
        startForegroundServiceNotification()

        initOverlayView()
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
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

            while (isActive) {
                try {
                    val snapshot: LivePlaybackSnapshot? = MediaSessionCollector.livePlaybackFlow.value
                    if (snapshot != null && snapshot.title.isNotBlank()) {
                        val trackKey = MusicFeatureEngine.computeTrackKey(
                            snapshot.title,
                            snapshot.artist.ifBlank { "Unknown Artist" },
                            snapshot.album
                        )

                        // Reload lyrics cache if track changed
                        if (trackKey != lastLoadedTrackKey) {
                            lastLoadedTrackKey = trackKey
                            currentCachedLyrics = db.lyricsDao().getLyrics(trackKey)
                            cachedLrcLines = currentCachedLyrics?.syncedLyricsLrc?.let { parseLrcLinesStrict(it) } ?: emptyList()

                            // If lyrics not in Room cache, trigger asynchronous prefetch from network
                            val lacksLyrics = currentCachedLyrics == null ||
                                (currentCachedLyrics?.syncedLyricsLrc.isNullOrBlank() && currentCachedLyrics?.plainLyrics.isNullOrBlank())

                            if (lacksLyrics) {
                                fetchLyricsJob?.cancel()
                                fetchLyricsJob = serviceScope.launch(Dispatchers.IO) {
                                    try {
                                        val track = TrackEntity(
                                            trackKey = trackKey,
                                            title = snapshot.title,
                                            artist = snapshot.artist.ifBlank { "Unknown Artist" },
                                            album = snapshot.album,
                                            sourcePackage = snapshot.packageName,
                                            albumArtUri = snapshot.albumArtUri
                                        )
                                        val lyricsProvider = AggregatedLyricsProvider(lyricsDao = db.lyricsDao())
                                        val fetched = lyricsProvider.getLyrics(track, forceNetwork = true)

                                        if (fetched.hasLyrics && trackKey == lastLoadedTrackKey) {
                                            val plain = fetched.plainLyrics ?: fetched.lyricsText
                                            val synced = fetched.syncedLyrics
                                            val entity = LyricsCacheEntity(
                                                trackKey = trackKey,
                                                plainLyrics = plain,
                                                syncedLyricsLrc = synced,
                                                chordsAmDm = fetched.chords,
                                                userNotes = null,
                                                provider = fetched.sourceId.ifBlank { fetched.source }
                                            )
                                            db.lyricsDao().saveLyrics(entity)
                                            db.musicDao().updateLyrics(trackKey, plain, synced)

                                            currentCachedLyrics = entity
                                            cachedLrcLines = synced?.let { parseLrcLinesStrict(it) } ?: emptyList()
                                            Timber.tag(TAG).i("Successfully fetched lyrics for floating overlay: %s", trackKey)
                                        }
                                    } catch (e: Exception) {
                                        Timber.tag(TAG).w(e, "Error prefetching lyrics for %s", trackKey)
                                    }
                                }
                            }
                        }

                        // Calculate current extrapolated position with calibration offset
                        val offsetMs = syncOffsetStore.getOffset(trackKey)
                        val extrapolatedPos = (snapshot.currentPositionMs() + offsetMs).coerceAtLeast(0L)

                        val hasSynced = cachedLrcLines.isNotEmpty()
                        val hasPlain = !currentCachedLyrics?.plainLyrics.isNullOrBlank()
                        val isFetching = fetchLyricsJob?.isActive == true

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
                            currentPositionMs = extrapolatedPos,
                            durationMs = snapshot.durationMs,
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
                } catch (e: Exception) {
                    Timber.w(e, "Error in FloatingLyricsService sync loop")
                }

                // Smooth refresh rate: 100ms when playing, 500ms when paused
                val delayTime = if (_overlayState.value.isPlaying) 100L else 500L
                delay(delayTime)
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
        const val ACTION_STOP = "com.eventengine.app.service.ACTION_STOP_FLOATING_OVERLAY"

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
            val intent = Intent(context, FloatingLyricsService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingLyricsService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
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
