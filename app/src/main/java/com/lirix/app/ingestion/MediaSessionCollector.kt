package com.lirix.app.ingestion

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
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
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects media playback state transitions and track metadata across active players.
 * Integrates MediaDebounceFilter to collapse rapid BUFFERING/UPDATE -> PLAYING sequences (600ms)
 * and registers active sessions with CrossSourceCorrelator to eliminate redundant notification duplicates.
 */
class MediaSessionCollector private constructor(private val context: Context) {

    init {
        appContext = context.applicationContext
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeControllers = ConcurrentHashMap<String, MediaController.Callback>()
    private val lastTrackMap = ConcurrentHashMap<String, String>()
    private val albumArtStorage = com.lirix.app.storage.AlbumArtStorage(context)
    private var heartbeatJob: kotlinx.coroutines.Job? = null
    private var heartbeatPackage: String? = null
    private data class SavedArtwork(val bitmap: WeakReference<Bitmap>, val generation: Int)
    private val savedArtwork = LinkedHashMap<String, SavedArtwork>()

    private data class ListeningObservation(
        val snapshot: LivePlaybackSnapshot?, val state: String,
        val wall: Long = System.currentTimeMillis(),
        val mono: Long = android.os.SystemClock.elapsedRealtime(),
        val progressOnly: Boolean = false
    )
    private val listeningObservations = kotlinx.coroutines.channels.Channel<ListeningObservation>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val accountingJob = scope.launch {
        var engine: com.lirix.app.feature.MusicFeatureEngine? = null
        var prefetchedKey: String? = null
        var lastIdentity: List<String?>? = null
        var lastStateMono = Long.MIN_VALUE
        for (observation in listeningObservations) {
            try {
                val currentEngine = engine ?: AppDatabase.getInstance(context).let { db ->
                    com.lirix.app.feature.MusicFeatureEngine(db.musicDao(), db.lyricsDao(), blacklistDao = db.blacklistDao())
                        .also { engine = it; it.startBlacklistSync(scope, db) }
                }
                val snapshot = observation.snapshot
                if (snapshot == null) {
                    if (observation.mono < lastStateMono) continue
                    currentEngine.stopListeningProgress(timestamp = observation.wall, monotonicMs = observation.mono)
                    lastIdentity = null
                    lastStateMono = observation.mono
                } else if (observation.progressOnly) {
                    currentEngine.updateActiveSessionProgress(snapshot.packageName,
                        timestamp = observation.wall, monotonicMs = observation.mono)
                } else {
                    if (observation.mono < lastStateMono) continue
                    lastStateMono = observation.mono
                    val identity = listOf(snapshot.packageName, snapshot.title, snapshot.artist,
                        snapshot.album, observation.state, snapshot.albumArtUri)
                    if (identity == lastIdentity) continue
                    val track = currentEngine.recordPlaybackSignal(snapshot.title, snapshot.artist, snapshot.album,
                        snapshot.packageName, playbackState = observation.state,
                        timestamp = observation.wall, albumArtUri = snapshot.albumArtUri, monotonicMs = observation.mono)
                    lastIdentity = identity
                    if (track != null && observation.state == "PLAYING" && prefetchedKey != track.trackKey) {
                        prefetchedKey = track.trackKey
                        currentEngine.prefetchLyricsAsync(context, scope, track)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Timber.tag(TAG).w(e, "Failed to record confirmed listening interval") }
        }
    }

    private fun enqueuePlayback(snapshot: LivePlaybackSnapshot?, state: String) {
        listeningObservations.trySend(ListeningObservation(snapshot, state))
    }

    private fun syncHeartbeat(isPlaying: Boolean, packageName: String) {
        if (isPlaying && heartbeatPackage == packageName && heartbeatJob?.isActive == true) return
        heartbeatJob?.cancel()
        heartbeatJob = null
        heartbeatPackage = if (isPlaying) packageName else null
        if (!isPlaying) return
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(5000L)
                val snapshot = _livePlaybackFlow.value
                if (snapshot == null || !snapshot.isPlaying || snapshot.packageName != packageName) return@launch
                val observation = ListeningObservation(snapshot, "PLAYING", progressOnly = true)
                if (_livePlaybackFlow.value === snapshot) listeningObservations.send(observation)
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
    private var isSessionListenerRegistered = false
    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateControllers(controllers)
    }

    @Synchronized
    fun startListening() {
        try {
            sessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
            Companion.sessionManager = sessionManager
            Companion.appContext = context.applicationContext
            val componentName = ComponentName(context, NotificationListener::class.java)

            if (!isSessionListenerRegistered) {
                sessionManager?.addOnActiveSessionsChangedListener(activeSessionsListener, componentName)
                isSessionListenerRegistered = true
            }

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
        if (controllers.isNullOrEmpty()) {
            enqueuePlayback(null, "STOPPED")
            val previous = _livePlaybackFlow.value
            if (previous != null) syncHeartbeat(false, previous.packageName)
            else {
                heartbeatJob?.cancel()
                heartbeatJob = null
            }
            // Все сессии закрыты: разрегистрируем колбэки и переходим в IDLE
            for ((pkg, callback) in activeControllers) {
                try {
                    activeMediaControllers[pkg]?.unregisterCallback(callback)
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Error unregistering callback for %s", pkg)
                }
            }
            activeControllers.clear()
            activeMediaControllers.clear()
            _livePlaybackFlow.value = null
            return
        }

        // Очистка сессий, которые пропали из системы
        val currentPkgs = controllers.map { it.packageName }.toSet()
        val removedPkgs = activeMediaControllers.keys - currentPkgs
        for (pkg in removedPkgs) {
            val cb = activeControllers.remove(pkg)
            if (cb != null) {
                try {
                    activeMediaControllers[pkg]?.unregisterCallback(cb)
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Error unregistering removed session for %s", pkg)
                }
            }
            activeMediaControllers.remove(pkg)
        }

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
        }

        // Если текущий отображаемый трек принадлежал удаленному пакету, переключаем на живой
        val currentSnapshot = _livePlaybackFlow.value
        if (currentSnapshot?.packageName in removedPkgs) {
            enqueuePlayback(null, "STOPPED")
            syncHeartbeat(false, currentSnapshot!!.packageName)
            _livePlaybackFlow.value = null
        }
        if (currentSnapshot == null || currentSnapshot.packageName in removedPkgs || !currentSnapshot.isPlaying) {
            val bestTarget = resolveTargetController(controllers)
            if (bestTarget != null) {
                handleMetadataChange(bestTarget, bestTarget.metadata)
            }
        }
    }

    @Synchronized
    private fun extractAndSaveAlbumArt(title: String, artist: String, album: String, metadata: MediaMetadata?): String? {
        val trackKey = com.lirix.app.feature.MusicFeatureEngine.computeTrackKey(title, artist, album)
        val rawBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val artUriStr = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI)

        if (rawBitmap != null && !rawBitmap.isRecycled) {
            val saved = savedArtwork[trackKey]
            val previousBitmap = saved?.bitmap?.get()
            val unchanged = previousBitmap != null && !previousBitmap.isRecycled &&
                previousBitmap.generationId == saved?.generation &&
                (previousBitmap === rawBitmap || previousBitmap.sameAs(rawBitmap))
            if (unchanged) {
                albumArtStorage.getAlbumArtPath(trackKey)?.let { return it }
            }
            val savedPath = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                albumArtStorage.saveBitmap(trackKey, rawBitmap)
            }
            if (savedPath != null) {
                savedArtwork[trackKey] = SavedArtwork(WeakReference(rawBitmap), rawBitmap.generationId)
                while (savedArtwork.size > 8) savedArtwork.remove(savedArtwork.keys.first())
            }
            return savedPath ?: artUriStr ?: albumArtStorage.getAlbumArtPath(trackKey)
        }
        return artUriStr ?: albumArtStorage.getAlbumArtPath(trackKey)
    }

    private fun handlePlaybackChange(controller: MediaController, state: PlaybackState?) {
        if (state == null) {
            val previous = _livePlaybackFlow.value?.takeIf { it.packageName == controller.packageName } ?: return
            val paused = previous.copy(isPlaying = false, basePositionMs = previous.currentPositionMs(),
                lastPositionUpdateTimeMs = android.os.SystemClock.elapsedRealtime())
            _livePlaybackFlow.value = paused
            enqueuePlayback(paused, "PAUSED")
            syncHeartbeat(false, controller.packageName)
            return
        }
        activeMediaControllers[controller.packageName] = controller

        val current = _livePlaybackFlow.value
        val isPlaying = state.state == PlaybackState.STATE_PLAYING
        if (current != null && current.isPlaying && current.packageName != controller.packageName && !isPlaying) {
            // Защита активного воспроизведения: фоновая неиграющая сессия не может сбить текущий плеер
            return
        }

        val metadata = controller.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
        val album = metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim().orEmpty()
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
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
            else -> "PAUSED"
        }
        enqueuePlayback(_livePlaybackFlow.value?.takeIf { it.packageName == controller.packageName }, stateName)
        syncHeartbeat(isPlaying, controller.packageName)
        emitMediaEvent(controller.packageName, metadata, stateName)
    }

    private fun handleMetadataChange(controller: MediaController, metadata: MediaMetadata?) {
        if (metadata == null) return
        activeMediaControllers[controller.packageName] = controller

        val current = _livePlaybackFlow.value
        val state = controller.playbackState
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        if (current != null && current.isPlaying && current.packageName != controller.packageName && !isPlaying) {
            // Защита активного воспроизведения
            return
        }

        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)?.trim().orEmpty()
        val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim().orEmpty()
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
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
        val observedState = when (state?.state) {
            PlaybackState.STATE_PLAYING -> "PLAYING"
            PlaybackState.STATE_BUFFERING -> "BUFFERING"
            PlaybackState.STATE_STOPPED -> "STOPPED"
            else -> "PAUSED"
        }
        enqueuePlayback(_livePlaybackFlow.value?.takeIf { it.packageName == controller.packageName }, observedState)
        syncHeartbeat(isPlaying, controller.packageName)
        emitMediaEvent(controller.packageName, metadata, observedState)
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

    @Synchronized
    fun stopListening() {
        enqueuePlayback(null, "STOPPED")
        heartbeatJob?.cancel()
        heartbeatJob = null
        debounceFilter.cancelAll()
        if (isSessionListenerRegistered && sessionManager != null) {
            try {
                sessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Error removing active sessions changed listener")
            }
            isSessionListenerRegistered = false
        }
        for ((pkg, callback) in activeControllers) {
            try {
                activeMediaControllers[pkg]?.unregisterCallback(callback)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Error unregistering callback for %s", pkg)
            }
        }
        activeControllers.clear()
        activeMediaControllers.clear()
        lastTrackMap.clear()
        savedArtwork.clear()
        _livePlaybackFlow.value = null
        com.lirix.app.feature.MusicLyricsNotificationManager.cancelLyricsPrompt(context)
    }

    fun onNotificationDismissed(packageName: String) {
        val current = _livePlaybackFlow.value
        if (current != null && current.packageName == packageName && !current.isPlaying) {
            val remaining = activeMediaControllers.values.filter { it.packageName != packageName }
            val best = resolveTargetController(remaining)
            if (best != null && best.playbackState?.state == PlaybackState.STATE_PLAYING) {
                handleMetadataChange(best, best.metadata)
            } else {
                _livePlaybackFlow.value = null
            }
        }
    }

    companion object {
        private const val TAG = "MediaSessionCollector"

        @Volatile
        private var instance: MediaSessionCollector? = null

        fun getInstance(context: Context): MediaSessionCollector {
            return instance ?: synchronized(this) {
                instance ?: MediaSessionCollector(context.applicationContext).also { instance = it }
            }
        }

        @androidx.annotation.VisibleForTesting
        fun resetInstanceForTesting() {
            synchronized(this) {
                instance?.stopListening()
                instance?.listeningObservations?.close()
                instance?.accountingJob?.cancel()
                instance?.scope?.coroutineContext?.get(kotlinx.coroutines.Job)?.cancel()
                instance = null
            }
        }

        private val _livePlaybackFlow = MutableStateFlow<LivePlaybackSnapshot?>(null)
        val livePlaybackFlow: StateFlow<LivePlaybackSnapshot?> = _livePlaybackFlow.asStateFlow()

        fun markCurrentTrackBlacklisted(isBlacklisted: Boolean) {
            val current = _livePlaybackFlow.value
            if (current != null && current.isBlacklisted != isBlacklisted) {
                _livePlaybackFlow.value = current.copy(isBlacklisted = isBlacklisted)
            }
        }

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

        fun resolveTargetController(controllers: List<MediaController>): MediaController? {
            if (controllers.isEmpty()) return null

            var bestController: MediaController? = null
            var maxScore = -1

            for (controller in controllers) {
                val state = controller.playbackState
                val actions = state?.actions ?: 0L

                val isPlaying = state?.state == PlaybackState.STATE_PLAYING
                val hasPlayPauseAction = (actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)) != 0L
                val hasPositiveActions = actions > 0L
                val hasSeekToAction = (actions and PlaybackState.ACTION_SEEK_TO) != 0L
                val hasMetadata = controller.metadata != null

                val score = (if (isPlaying) 10000 else 0) +
                        (if (hasPlayPauseAction) 1000 else 0) +
                        (if (hasPositiveActions) 100 else 0) +
                        (if (hasSeekToAction) 50 else 0) +
                        (if (hasMetadata) 10 else 0)

                if (score > maxScore) {
                    maxScore = score
                    bestController = controller
                }
            }

            return bestController
        }

        fun resolveSeekController(controllers: List<MediaController>): MediaController? {
            if (controllers.isEmpty()) return null

            var bestController: MediaController? = null
            var maxScore = -1

            for (controller in controllers) {
                val state = controller.playbackState
                val actions = state?.actions ?: 0L

                val hasSeekToAction = (actions and PlaybackState.ACTION_SEEK_TO) != 0L
                val isPlaying = state?.state == PlaybackState.STATE_PLAYING
                val hasPlaybackState = state != null
                val hasMetadata = controller.metadata != null

                val score = (if (hasSeekToAction) 10000 else 0) +
                        (if (isPlaying) 1000 else 0) +
                        (if (hasPlaybackState) 100 else 0) +
                        (if (hasMetadata) 10 else 0)

                if (score > maxScore) {
                    maxScore = score
                    bestController = controller
                }
            }

            return bestController
        }

        fun resolveSkipController(controllers: List<MediaController>, isNext: Boolean): MediaController? {
            if (controllers.isEmpty()) return null

            val targetAction = if (isNext) PlaybackState.ACTION_SKIP_TO_NEXT else PlaybackState.ACTION_SKIP_TO_PREVIOUS
            var bestController: MediaController? = null
            var maxScore = -1

            for (controller in controllers) {
                val state = controller.playbackState
                val actions = state?.actions ?: 0L

                val hasSkipAction = (actions and targetAction) != 0L
                val isPlaying = state?.state == PlaybackState.STATE_PLAYING
                val hasPlaybackState = state != null
                val hasMetadata = controller.metadata != null

                val score = (if (hasSkipAction) 10000 else 0) +
                        (if (isPlaying) 1000 else 0) +
                        (if (hasPlaybackState) 100 else 0) +
                        (if (hasMetadata) 10 else 0)

                if (score > maxScore) {
                    maxScore = score
                    bestController = controller
                }
            }

            return bestController
        }

        fun togglePlayPause(): Boolean {
            val snapshot = _livePlaybackFlow.value
            val targetPackage = snapshot?.packageName
            val controllers = getControllers(targetPackage)
            if (controllers.isEmpty()) {
                Timber.tag(TAG).w("togglePlayPause: no controllers found for package %s", targetPackage)
                return false
            }

            val targetController = resolveTargetController(controllers) ?: return false

            val isCurrentlyPlaying = (targetController.playbackState?.state == PlaybackState.STATE_PLAYING) ||
                    (snapshot?.isPlaying == true)
            val nextPlaying = !isCurrentlyPlaying

            val currentPos = snapshot?.currentPositionMs()
                ?: targetController.playbackState?.position
                ?: 0L

            if (snapshot != null) {
                _livePlaybackFlow.value = snapshot.copy(
                    isPlaying = nextPlaying,
                    basePositionMs = currentPos,
                    lastPositionUpdateTimeMs = android.os.SystemClock.elapsedRealtime()
                )
            }
            instance?.syncHeartbeat(nextPlaying, targetController.packageName)

            var transportSuccess = false
            try {
                if (isCurrentlyPlaying) {
                    targetController.transportControls.pause()
                } else {
                    targetController.transportControls.play()
                }
                transportSuccess = true
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "transportControls failed on %s", targetController.packageName)
            }

            return if (transportSuccess) {
                true
            } else {
                sendMediaButtonFallback(targetController, isCurrentlyPlaying)
            }
        }

        internal fun sendMediaButtonFallback(controller: MediaController, wasPlaying: Boolean): Boolean {
            return try {
                val primaryKeyCode = if (wasPlaying) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY
                val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, primaryKeyCode)
                val upEvent = KeyEvent(KeyEvent.ACTION_UP, primaryKeyCode)
                val downHandled = controller.dispatchMediaButtonEvent(downEvent)
                val upHandled = controller.dispatchMediaButtonEvent(upEvent)
                val directHandled = downHandled || upHandled

                if (!directHandled) {
                    if (wasPlaying) {
                        // ПАУЗА: разрешен ТОЛЬКО идемпотентный код STOP.
                        // Отправка KEYCODE_MEDIA_PLAY_PAUSE КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНА!
                        val stopDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP)
                        val stopUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP)
                        controller.dispatchMediaButtonEvent(stopDown) || controller.dispatchMediaButtonEvent(stopUp)
                    } else {
                        // ВОСПРОИЗВЕДЕНИЕ: переключатель допустим как резерв
                        val toggleDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                        val toggleUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                        controller.dispatchMediaButtonEvent(toggleDown) || controller.dispatchMediaButtonEvent(toggleUp)
                    }
                } else {
                    true
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "dispatchMediaButtonEvent failed completely")
                false
            }
        }

        fun skipToNext(): Boolean {
            val snapshot = _livePlaybackFlow.value
            val targetPackage = snapshot?.packageName
            val controllers = getControllers(targetPackage)
            val controller = resolveSkipController(controllers, isNext = true) ?: resolveTargetController(controllers) ?: return false

            return try {
                controller.transportControls.skipToNext()
                true
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "transportControls.skipToNext failed, using fallback")
                val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
                val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
                controller.dispatchMediaButtonEvent(down) || controller.dispatchMediaButtonEvent(up)
            }
        }

        fun skipToPrevious(): Boolean {
            val snapshot = _livePlaybackFlow.value
            val targetPackage = snapshot?.packageName
            val controllers = getControllers(targetPackage)
            val controller = resolveSkipController(controllers, isNext = false) ?: resolveTargetController(controllers) ?: return false

            return try {
                controller.transportControls.skipToPrevious()
                true
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "transportControls.skipToPrevious failed, using fallback")
                val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                controller.dispatchMediaButtonEvent(down) || controller.dispatchMediaButtonEvent(up)
            }
        }

        fun seekTo(positionMs: Long): Boolean {
            val snapshot = _livePlaybackFlow.value
            val targetPackage = snapshot?.packageName
            val controllers = getControllers(targetPackage)
            val controller = resolveSeekController(controllers) ?: resolveTargetController(controllers)

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
                controller != null
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
