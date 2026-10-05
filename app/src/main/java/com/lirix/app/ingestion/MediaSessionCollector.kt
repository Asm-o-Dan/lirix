package com.lirix.app.ingestion

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.view.KeyEvent
import com.lirix.app.domain.Category
import com.lirix.app.domain.Event
import com.lirix.app.domain.EventSource
import com.lirix.app.domain.EventType
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.toEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects media playback state transitions and track metadata across active players.
 * Integrates MediaDebounceFilter to collapse rapid BUFFERING/UPDATE -> PLAYING sequences (600ms)
 * and registers active sessions with CrossSourceCorrelator to eliminate redundant notification duplicates.
 */
class MediaSessionCollector(private val context: Context) {

    init {
        appContext = context.applicationContext
        instance = this
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeControllers = ConcurrentHashMap<String, MediaController.Callback>()
    private val lastTrackMap = ConcurrentHashMap<String, String>()
    private val albumArtStorage = com.lirix.app.storage.AlbumArtStorage(context)
    private var heartbeatJob: kotlinx.coroutines.Job? = null

    private fun syncHeartbeat(isPlaying: Boolean, packageName: String) {
        heartbeatJob?.cancel()
        if (!isPlaying) {
            scope.launch {
                try {
                    val db = com.lirix.app.storage.AppDatabase.getInstance(context)
                    val engine = com.lirix.app.feature.MusicFeatureEngine(db.musicDao(), db.lyricsDao())
                    val currentPos = _livePlaybackFlow.value?.currentPositionMs()
                    engine.updateActiveSessionProgress(
                        sourcePackage = packageName,
                        currentPlaybackPositionMs = currentPos,
                        timestamp = System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Error finalizing active session")
                }
            }
            return
        }

        heartbeatJob = scope.launch {
            while (isActive) {
                delay(5000L)
                try {
                    val snapshot = _livePlaybackFlow.value
                    if (snapshot != null && snapshot.isPlaying && snapshot.packageName == packageName) {
                        val currentPos = snapshot.currentPositionMs()
                        val db = com.lirix.app.storage.AppDatabase.getInstance(context)
                        val engine = com.lirix.app.feature.MusicFeatureEngine(db.musicDao(), db.lyricsDao())
                        engine.updateActiveSessionProgress(
                            sourcePackage = packageName,
                            currentPlaybackPositionMs = currentPos,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Error updating heartbeat session progress")
                }
            }
        }
    }

    private val debounceFilter = MediaDebounceFilter(
        scope = scope,
        debounceDelayMs = 600L
    ) { packageName, title, artist, album, stateName ->
        persistMediaEvent(packageName, title, artist, album, stateName)
    }

    private var sessionManager: MediaSessionManager? = null

    fun startListening() {
        try {
            sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            Companion.sessionManager = sessionManager
            Companion.appContext = context.applicationContext
            val componentName = ComponentName(context, NotificationListener::class.java)

            sessionManager?.addOnActiveSessionsChangedListener({ controllers ->
                updateControllers(controllers)
            }, componentName)

            // Initial scan of active sessions
            val initialSessions = sessionManager?.getActiveSessions(componentName)
            updateControllers(initialSessions)
            Timber.tag(TAG).i("MediaSessionCollector started listening successfully.")
        } catch (e: SecurityException) {
            Timber.tag(TAG).w(e, "NotificationListener permission not granted yet for MediaSessionManager.")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to start MediaSessionCollector.")
        }
    }

    private fun updateControllers(controllers: List<MediaController>?) {
        if (controllers == null) return

        for (controller in controllers) {
            val pkg = controller.packageName
            activeMediaControllers[pkg] = controller
            if (activeControllers.containsKey(pkg)) continue

            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    handlePlaybackChange(controller, state)
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    handleMetadataChange(controller, metadata)
                }
            }

            controller.registerCallback(callback)
            activeControllers[pkg] = callback

            // Capture current snapshot
            handleMetadataChange(controller, controller.metadata)
        }
    }

    private fun extractAndSaveAlbumArt(title: String, artist: String, album: String, metadata: MediaMetadata?): String? {
        val trackKey = com.lirix.app.feature.MusicFeatureEngine.computeTrackKey(title, artist, album)
        val rawBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val artUriStr = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)

        if (rawBitmap != null) {
            val savedPath = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                albumArtStorage.saveBitmap(trackKey, rawBitmap)
            }
            return savedPath ?: artUriStr ?: albumArtStorage.getAlbumArtPath(trackKey)
        }
        return artUriStr ?: albumArtStorage.getAlbumArtPath(trackKey)
    }

    private fun handlePlaybackChange(controller: MediaController, state: PlaybackState?) {
        if (state == null) return
        activeMediaControllers[controller.packageName] = controller

        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim().orEmpty()
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val isPlaying = state.state == PlaybackState.STATE_PLAYING
        val basePos = state.position
        val updateTime = if (state.lastPositionUpdateTime > 0L) state.lastPositionUpdateTime else android.os.SystemClock.elapsedRealtime()
        val speed = if (state.playbackSpeed > 0f) state.playbackSpeed else 1.0f

        if (title.isNotEmpty() || artist.isNotEmpty()) {
            val finalArtUri = extractAndSaveAlbumArt(title, artist, album, metadata)
            _livePlaybackFlow.value = LivePlaybackSnapshot(
                packageName = controller.packageName,
                title = title,
                artist = artist,
                album = album,
                isPlaying = isPlaying,
                basePositionMs = basePos,
                lastPositionUpdateTimeMs = updateTime,
                playbackSpeed = speed,
                durationMs = duration,
                albumArtUri = finalArtUri
            )
        }

        val stateName = when (state.state) {
            PlaybackState.STATE_PLAYING -> "PLAYING"
            PlaybackState.STATE_PAUSED -> "PAUSED"
            PlaybackState.STATE_STOPPED -> "STOPPED"
            PlaybackState.STATE_BUFFERING -> "BUFFERING"
            else -> "OTHER"
        }
        syncHeartbeat(isPlaying, controller.packageName)
        emitMediaEvent(controller.packageName, metadata, stateName)
    }

    private fun handleMetadataChange(controller: MediaController, metadata: MediaMetadata?) {
        if (metadata == null) return
        activeMediaControllers[controller.packageName] = controller

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim().orEmpty()
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val state = controller.playbackState
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val basePos = state?.position ?: 0L
        val updateTime = if ((state?.lastPositionUpdateTime ?: 0L) > 0L) state!!.lastPositionUpdateTime else android.os.SystemClock.elapsedRealtime()
        val speed = if ((state?.playbackSpeed ?: 0f) > 0f) state!!.playbackSpeed else 1.0f

        if (title.isNotEmpty() || artist.isNotEmpty()) {
            val finalArtUri = extractAndSaveAlbumArt(title, artist, album, metadata)
            _livePlaybackFlow.value = LivePlaybackSnapshot(
                packageName = controller.packageName,
                title = title,
                artist = artist,
                album = album,
                isPlaying = isPlaying,
                basePositionMs = basePos,
                lastPositionUpdateTimeMs = updateTime,
                playbackSpeed = speed,
                durationMs = duration,
                albumArtUri = finalArtUri
            )
        }
        syncHeartbeat(isPlaying, controller.packageName)
        emitMediaEvent(controller.packageName, metadata, "UPDATE")
    }

    private fun emitMediaEvent(packageName: String, metadata: MediaMetadata?, stateName: String) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim()
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim()

        if (title.isNullOrEmpty() && artist.isNullOrEmpty()) return

        // Inform CrossSourceCorrelator about active media session
        if (stateName == "STOPPED") {
            CrossSourceCorrelator.unregisterMediaSession(packageName)
        } else {
            CrossSourceCorrelator.registerActiveMediaSession(packageName, title, artist)
        }

        // Pass through 600ms debounce filter
        debounceFilter.onStateChange(packageName, title, artist, album, stateName)
    }

    private suspend fun persistMediaEvent(
        packageName: String,
        title: String?,
        artist: String?,
        album: String?,
        stateName: String
    ) {
        val stateAgnosticKey = "$packageName:$title:$artist"
        if (stateName != "PAUSED" && stateName != "STOPPED") {
            if (lastTrackMap[packageName] == stateAgnosticKey) {
                return // Skip duplicate identical state in non-terminal mode
            }
            lastTrackMap[packageName] = stateAgnosticKey
        } else {
            lastTrackMap[packageName] = "$stateAgnosticKey:$stateName"
        }

        val trackKey = "$packageName|$title|$artist|$stateName"

        val now = System.currentTimeMillis()
        val fingerprint = computeFingerprint(trackKey)

        val event = Event(
            timestamp = now,
            source = EventSource.MEDIA_SESSION,
            sourcePackage = packageName,
            appName = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() },
            title = title,
            text = if (artist != null) "$artist — $album" else album,
            eventType = EventType.MEDIA_PLAYBACK,
            category = Category.MUSIC,
            confidence = 1.0f,
            isOngoing = stateName == "PLAYING",
            mediaTrack = title,
            mediaArtist = artist,
            mediaPlaybackState = stateName,
            contentFingerprint = fingerprint,
            rawPayload = "track=$title;artist=$artist;state=$stateName"
        )

        Timber.tag(TAG).i("Recorded Media Event: [%s] %s - %s (%s)", packageName, artist, title, stateName)

        val db = com.lirix.app.storage.AppDatabase.getInstance(context)
        val musicEngine = com.lirix.app.feature.MusicFeatureEngine(db.musicDao(), db.lyricsDao())

        if (!title.isNullOrBlank()) {
            val cleanArtist = artist.orEmpty().ifBlank { "Unknown Artist" }
            val cleanAlbum = album.orEmpty()
            val trackKeyForArt = com.lirix.app.feature.MusicFeatureEngine.computeTrackKey(title, cleanArtist, cleanAlbum)
            val finalArtUri = albumArtStorage.getAlbumArtPath(trackKeyForArt)
                ?: _livePlaybackFlow.value?.takeIf { it.title == title }?.albumArtUri

            val recordedTrack = musicEngine.recordPlaybackSignal(
                title = title,
                artist = cleanArtist,
                album = cleanAlbum,
                sourcePackage = packageName,
                playbackState = stateName,
                timestamp = now,
                albumArtUri = finalArtUri
            )

            if (recordedTrack != null) {
                musicEngine.prefetchLyricsAsync(
                    context = context,
                    scope = scope,
                    track = recordedTrack
                )
            }
        }

        // Show status bar prompt notification for lyrics when music is active
        if (stateName == "PLAYING" || stateName == "UPDATE") {
            if (!title.isNullOrBlank()) {
                com.lirix.app.feature.MusicLyricsNotificationManager.showLyricsPrompt(
                    context = context,
                    title = title,
                    artist = artist.orEmpty(),
                    album = album.orEmpty()
                )
            }
        } else if (stateName == "STOPPED") {
            com.lirix.app.feature.MusicLyricsNotificationManager.cancelLyricsPrompt(context)
        }
    }

    private fun computeFingerprint(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun stopListening() {
        heartbeatJob?.cancel()
        debounceFilter.cancelAll()
        activeControllers.clear()
        activeMediaControllers.clear()
        lastTrackMap.clear()
        _livePlaybackFlow.value = null
        instance = null
        com.lirix.app.feature.MusicLyricsNotificationManager.cancelLyricsPrompt(context)
    }

    companion object {
        private const val TAG = "MediaSessionCollector"
        private var instance: MediaSessionCollector? = null

        private val _livePlaybackFlow = MutableStateFlow<LivePlaybackSnapshot?>(null)
        val livePlaybackFlow: StateFlow<LivePlaybackSnapshot?> = _livePlaybackFlow.asStateFlow()

        private val activeMediaControllers = ConcurrentHashMap<String, MediaController>()

        @Volatile
        internal var appContext: Context? = null
        @Volatile
        internal var sessionManager: MediaSessionManager? = null

        fun getControllers(packageName: String? = null): List<MediaController> {
            try {
                val manager = sessionManager ?: (appContext?.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager)
                val ctx = appContext
                val componentName = if (ctx != null) ComponentName(ctx, NotificationListener::class.java) else null
                val sessions = if (manager != null && componentName != null) {
                    manager.getActiveSessions(componentName)
                } else null

                if (sessions != null) {
                    for (controller in sessions) {
                        val existing = activeMediaControllers[controller.packageName]
                        // Only overwrite if existing is null or new one is PLAYING while old one is not
                        if (existing == null ||
                            (controller.playbackState?.state == PlaybackState.STATE_PLAYING && existing.playbackState?.state != PlaybackState.STATE_PLAYING) ||
                            (controller.playbackState != null && existing.playbackState == null)
                        ) {
                            activeMediaControllers[controller.packageName] = controller
                        }
                    }
                    val filtered = if (packageName != null) sessions.filter { it.packageName == packageName } else sessions
                    if (filtered.isNotEmpty()) {
                        return filtered.sortedWith(
                            compareByDescending<MediaController> { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                                .thenByDescending { it.playbackState != null }
                                .thenByDescending { it.metadata != null }
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Could not refresh active sessions from MediaSessionManager")
            }

            val cachedList = activeMediaControllers.values.filter { packageName == null || it.packageName == packageName }
            return cachedList.sortedWith(
                compareByDescending<MediaController> { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                    .thenByDescending { it.playbackState != null }
            )
        }

        fun getActiveController(packageName: String? = null): MediaController? {
            return getControllers(packageName).firstOrNull()
        }

        fun togglePlayPause(): Boolean {
            val snapshot = _livePlaybackFlow.value
            val targetPackage = snapshot?.packageName
            val controllers = getControllers(targetPackage)
            if (controllers.isEmpty()) return false

            val playingControllers = controllers.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            val isCurrentlyPlaying = playingControllers.isNotEmpty() || (snapshot?.isPlaying == true)
            val nextPlaying = !isCurrentlyPlaying
            val primaryController = playingControllers.firstOrNull() ?: controllers.first()

            val currentPos = snapshot?.currentPositionMs()
                ?: primaryController.playbackState?.position
                ?: 0L

            // 1. ОПТИМИСТИЧНОЕ ОБНОВЛЕНИЕ SNAPSHOT (мгновенный отклик UI)
            if (snapshot != null) {
                _livePlaybackFlow.value = snapshot.copy(
                    isPlaying = nextPlaying,
                    basePositionMs = currentPos,
                    lastPositionUpdateTimeMs = android.os.SystemClock.elapsedRealtime()
                )
            }
            instance?.syncHeartbeat(nextPlaying, primaryController.packageName)

            // 2. ОТПРАВКА КОМАНДЫ ВО ВСЕ СЕССИИ ДАННОГО ПРИЛОЖЕНИЯ
            // Приложения вроде Telegram/AyuGram создают несколько параллельных MediaSession,
            // поэтому для гарантированной паузы отправляем команду всем играющим сессиям пакета.
            val targetControllers = if (isCurrentlyPlaying) {
                if (playingControllers.isNotEmpty()) playingControllers else controllers
            } else {
                listOf(primaryController)
            }

            var anySuccess = false
            for (controller in targetControllers) {
                try {
                    if (isCurrentlyPlaying) {
                        controller.transportControls.pause()
                    } else {
                        controller.transportControls.play()
                    }
                    anySuccess = true
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "transportControls failed on %s", controller.packageName)
                }
                val fallbackOk = sendMediaButtonFallback(controller, isCurrentlyPlaying)
                if (fallbackOk) anySuccess = true
            }

            return anySuccess
        }

        private fun sendMediaButtonFallback(controller: MediaController, wasPlaying: Boolean): Boolean {
            return try {
                val keyCode = if (wasPlaying) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
                val downHandled = controller.dispatchMediaButtonEvent(downEvent)
                val upHandled = controller.dispatchMediaButtonEvent(upEvent)
                val directHandled = downHandled || upHandled

                // Дополнительный fallback: KEYCODE_MEDIA_PLAY_PAUSE
                if (!directHandled) {
                    val toggleDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    val toggleUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    controller.dispatchMediaButtonEvent(toggleDown) || controller.dispatchMediaButtonEvent(toggleUp)
                } else true
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "dispatchMediaButtonEvent failed completely")
                false
            }
        }

        fun seekTo(positionMs: Long): Boolean {
            val snapshot = _livePlaybackFlow.value
            val controller = getActiveController(snapshot?.packageName)

            val validDuration = snapshot?.durationMs ?: controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            val clampedPosition = if (validDuration > 0L) {
                positionMs.coerceIn(0L, validDuration)
            } else {
                positionMs.coerceAtLeast(0L)
            }

            // 1. ОПТИМИСТИЧНОЕ ОБНОВЛЕНИЕ: замораживаем позицию в snapshot на новом месте
            if (snapshot != null) {
                _livePlaybackFlow.value = snapshot.copy(
                    basePositionMs = clampedPosition,
                    lastPositionUpdateTimeMs = android.os.SystemClock.elapsedRealtime()
                )
            }

            // 2. ОТПРАВКА ВО ВНЕШНИЙ ПЛЕЕР
            return try {
                controller?.transportControls?.seekTo(clampedPosition)
                true
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to seekTo %d on controller", clampedPosition)
                false
            }
        }

        fun seekRelative(deltaMs: Long): Boolean {
            val snapshot = _livePlaybackFlow.value
            val current = snapshot?.currentPositionMs()
                ?: getActiveController()?.playbackState?.position
                ?: 0L
            return seekTo(current + deltaMs)
        }
    }
}
