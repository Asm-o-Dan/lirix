package com.example.npc.ingest.media.observer

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.pipeline.EventProcessingOrchestrator
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.media.detector.MediaSessionBoundaryDetector
import com.example.npc.ingest.media.mapper.MediaPayloadMapper
import com.example.npc.ingest.media.model.MediaSessionPayload
import com.example.npc.ingest.media.model.SessionBoundaryDecision
import com.example.npc.ingest.media.recovery.MediaSessionRecoveryManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

private const val TAG = "MediaSessionObserver"
private const val HEARTBEAT_CHECK_INTERVAL_MS = 30_000L

/**
 * Observer of system media sessions. Subscribes to [MediaSessionManager.OnActiveSessionsChangedListener]
 * and routes playback/metadata callbacks through [MediaSessionBoundaryDetector].
 *
 * On session close decisions, persists RawEvent + Event via [StorageGateway] and updates [SourceHealth].
 *
 * @param context Application context (used for system services).
 * @param boundaryDetector FSM for session boundary detection.
 * @param recoveryManager Handles dangling session recovery on app restart.
 * @param storageGateway Persistence gateway for storing raw events and domain events.
 * @param coroutineScope Scope for background heartbeat coroutine. Caller owns lifecycle.
 * @param ioDispatcher Coroutine dispatcher for I/O (default: Dispatchers.IO).
 * @param orchestrator Optional event pipeline orchestrator to dispatch saved events.
 */
class MediaSessionObserver(
    private val context: Context,
    private val boundaryDetector: MediaSessionBoundaryDetector,
    private val recoveryManager: MediaSessionRecoveryManager,
    private val storageGateway: StorageGateway,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val orchestrator: EventProcessingOrchestrator? = null
) {
    private val activeControllers = mutableMapOf<String, MediaControllerHolder>()
    private val lastTrackEventIdByPackage = mutableMapOf<String, Long>()
    private val seqGenerator = AtomicLong(0L)
    private var isRunning = false

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        handleActiveSessionsChanged(controllers)
    }

    /**
     * Starts media session monitoring:
     *  1. Recover dangling sessions from previous process lifecycle.
     *  2. Register OnActiveSessionsChangedListener.
     *  3. Poll initial sessions.
     *  4. Start periodic heartbeat checker.
     *
     * Idempotent — safe to call multiple times.
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        // 1. Recover dangling sessions
        coroutineScope.launch(ioDispatcher) {
            try {
                val recovered = recoveryManager.recoverDanglingSessions(Instant.now())
                if (recovered > 0) {
                    Log.i(TAG, "Recovered $recovered dangling media session(s) from previous run")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error during dangling session recovery", e)
            }
        }

        // 2–3. Register listener and get initial sessions
        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        val listenerComponent = ComponentName(
            context,
            "com.example.npc.ingest.notification.service.PipelineNotificationListenerService"
        )

        try {
            manager?.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent)
            val initialControllers = manager?.getActiveSessions(listenerComponent)
            handleActiveSessionsChanged(initialControllers)
        } catch (e: SecurityException) {
            Log.e(TAG, "Notification listener permission not granted. Media session tracking is disabled", e)
            coroutineScope.launch(ioDispatcher) {
                try {
                    storageGateway.upsertSourceHealth(
                        SourceHealth(
                            source = SourceId.MEDIA,
                            lastEventAt = Instant.now(),
                            events24h = 0,
                            lastError = "SecurityException: notification listener permission not granted",
                            queueDepth = 0
                        )
                    )
                } catch (ex: Throwable) {
                    Log.e(TAG, "Failed to update SourceHealth after SecurityException", ex)
                }
            }
            isRunning = false
            return
        }

        // 4. Heartbeat ticker
        coroutineScope.launch(ioDispatcher) {
            while (isActive) {
                delay(HEARTBEAT_CHECK_INTERVAL_MS)
                checkHeartbeatTimeouts()
            }
        }
    }

    /**
     * Stops media session monitoring:
     *  - Unregisters OnActiveSessionsChangedListener.
     *  - Closes all active sessions with CONTROLLER_DISCONNECTED.
     *  - Unregisters all MediaControllerHolder callbacks.
     *
     * Idempotent.
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false

        val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        try {
            manager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
        } catch (_: Throwable) {}

        val now = Instant.now()
        activeControllers.values.forEach { holder ->
            holder.unregister()
            val decision = boundaryDetector.onControllerDisconnected(holder.packageName, now)
            if (decision is SessionBoundaryDecision.CloseSession) {
                coroutineScope.launch(ioDispatcher) {
                    processSessionClose(decision.completedSession)
                }
            }
        }
        activeControllers.clear()
    }

    /**
     * Synchronizes internal controller registry with the OS active sessions list.
     * Called both on listener callback and during [start].
     */
    internal fun handleActiveSessionsChanged(controllers: List<MediaController>?) {
        val now = Instant.now()
        val currentPackages = controllers?.map { it.packageName }?.toSet() ?: emptySet()

        // Close sessions for disappeared controllers
        val removed = activeControllers.keys.filter { it !in currentPackages }
        for (pkg in removed) {
            val holder = activeControllers.remove(pkg) ?: continue
            holder.unregister()
            val decision = boundaryDetector.onControllerDisconnected(pkg, now)
            if (decision is SessionBoundaryDecision.CloseSession) {
                coroutineScope.launch(ioDispatcher) {
                    processSessionClose(decision.completedSession)
                }
            }
        }

        // Register new controllers
        controllers?.forEach { nativeController ->
            val pkg = nativeController.packageName ?: return@forEach
            if (pkg in activeControllers) return@forEach

            val compatController = try {
                val token = MediaSessionCompat.Token.fromToken(nativeController.sessionToken)
                MediaControllerCompat(context, token)
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to create MediaControllerCompat for $pkg", e)
                return@forEach
            }

            val callback = object : MediaControllerCompat.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
                    val snapshot = state?.let {
                        MediaPayloadMapper.extractPlaybackState(
                            state = it.state,
                            positionMs = maxOf(0L, it.position),
                            playbackSpeed = it.playbackSpeed,
                            updateTimeEpochMs = it.lastPositionUpdateTime
                        )
                    } ?: return
                    handlePlaybackState(pkg, snapshot)
                }

                override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
                    val snapshot = metadata?.let {
                        MediaPayloadMapper.extractMetadata(
                            titleStr = it.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                                ?: it.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE),
                            artistStr = it.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
                                ?: it.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST),
                            albumStr = it.getString(MediaMetadataCompat.METADATA_KEY_ALBUM),
                            durationMs = it.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).let { d ->
                                if (d <= 0) -1L else d
                            }
                        )
                    } ?: return
                    handleMetadata(pkg, snapshot)
                }

                override fun onSessionDestroyed() {
                    handleSessionDestroyed(pkg)
                }
            }

            val holder = MediaControllerHolder(pkg, compatController, callback)
            holder.register()
            activeControllers[pkg] = holder

            // Immediately poll current state
            compatController.playbackState?.let { state ->
                val snapshot = MediaPayloadMapper.extractPlaybackState(
                    state = state.state,
                    positionMs = maxOf(0L, state.position),
                    playbackSpeed = state.playbackSpeed,
                    updateTimeEpochMs = state.lastPositionUpdateTime
                )
                handlePlaybackState(pkg, snapshot)
            }
            compatController.metadata?.let { meta ->
                val snapshot = MediaPayloadMapper.extractMetadata(
                    titleStr = meta.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                        ?: meta.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE),
                    artistStr = meta.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
                        ?: meta.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST),
                    albumStr = meta.getString(MediaMetadataCompat.METADATA_KEY_ALBUM),
                    durationMs = meta.getLong(MediaMetadataCompat.METADATA_KEY_DURATION).let { d ->
                        if (d <= 0) -1L else d
                    }
                )
                handleMetadata(pkg, snapshot)
            }
        }
    }

    // --- Internal event handlers ---

    private fun handlePlaybackState(packageName: String, state: com.example.npc.ingest.media.model.MediaPlaybackSnapshot) {
        val now = Instant.now()
        val decision = boundaryDetector.onPlaybackStateChanged(packageName, state, now)
        processFsmDecision(decision)

        // Save snapshot of active session for crash recovery
        val activeSession = boundaryDetector.getActiveSession(packageName)
        if (activeSession != null) {
            coroutineScope.launch(ioDispatcher) {
                recoveryManager.saveActiveSessionSnapshot(activeSession)
            }
        }
    }

    private fun handleMetadata(packageName: String, metadata: com.example.npc.ingest.media.model.MediaMetadataSnapshot) {
        val now = Instant.now()
        val decision = boundaryDetector.onMetadataChanged(packageName, metadata, now)
        processFsmDecision(decision)
    }

    private fun handleSessionDestroyed(packageName: String) {
        val now = Instant.now()
        activeControllers.remove(packageName)?.unregister()
        val decision = boundaryDetector.onControllerDisconnected(packageName, now)
        processFsmDecision(decision)
    }

    internal fun processFsmDecision(decision: SessionBoundaryDecision) {
        when (decision) {
            is SessionBoundaryDecision.CloseSession -> {
                coroutineScope.launch(ioDispatcher) {
                    processSessionClose(decision.completedSession)
                }
            }
            is SessionBoundaryDecision.SwitchTrack -> {
                coroutineScope.launch(ioDispatcher) {
                    processSessionClose(decision.previousSessionToClose)
                    recoveryManager.saveActiveSessionSnapshot(decision.newSessionToOpen)
                    recordTrackStarted(decision.newSessionToOpen)
                }
            }
            is SessionBoundaryDecision.OpenSession -> {
                coroutineScope.launch(ioDispatcher) {
                    recoveryManager.saveActiveSessionSnapshot(decision.session)
                    recordTrackStarted(decision.session)
                }
            }
            SessionBoundaryDecision.NoOp -> { /* nothing */ }
        }
    }

    internal suspend fun recordTrackStarted(session: com.example.npc.ingest.media.model.ActiveMediaSession) {
        val title = session.metadata.title ?: "Аудиотрек"
        val artist = session.metadata.artist ?: ""
        val text = if (artist.isNotBlank()) "$artist — $title (▶️ Сейчас играет)" else "$title (▶️ Сейчас играет)"
        val escapedTitle = title.replace("\"", "\\\"")
        val escapedArtist = artist.replace("\"", "\\\"")
        val escapedAlbum = (session.metadata.album ?: "").replace("\"", "\\\"")
        val payloadJson = """{"packageName":"${session.packageName}","trackTitle":"$escapedTitle","artist":"$escapedArtist","album":"$escapedAlbum","state":"PLAYING"}"""
        val seq = seqGenerator.incrementAndGet()
        val rawEvent = com.example.npc.core.model.RawEvent(
            id = 0L,
            seq = seq,
            source = SourceId.MEDIA,
            packageName = session.packageName,
            receivedAt = session.lastEventAt,
            payloadJson = payloadJson,
            hash = com.example.npc.core.model.normalize.EventNormalizer.computeDeduplicationKey(SourceId.MEDIA, session.packageName, payloadJson)
        )
        val rawId = storageGateway.insertRawEvent(rawEvent)
        if (rawId != -1L) {
            val domainEvent = com.example.npc.core.model.normalize.EventNormalizer.normalize(
                rawEvent = rawEvent.copy(id = rawId),
                title = title,
                text = text,
                threadKey = com.example.npc.core.model.ThreadKey("media:${session.packageName}"),
                isUpdateOf = lastTrackEventIdByPackage[session.packageName]
            )
            val savedEventId = storageGateway.insertEvent(domainEvent)
            lastTrackEventIdByPackage[session.packageName] = savedEventId
            orchestrator?.submit(savedEventId)
        }
    }

    internal suspend fun processSessionClose(payload: MediaSessionPayload) {
        try {
            val payloadJson = MediaPayloadMapper.toPayloadJson(payload)
            val seq = seqGenerator.incrementAndGet()

            // Step 1: Insert RawEvent (dedup handled by StorageGateway)
            val rawEvent = com.example.npc.core.model.RawEvent(
                id = 0L,
                seq = seq,
                source = SourceId.MEDIA,
                packageName = payload.packageName,
                receivedAt = Instant.ofEpochMilli(payload.sessionEndedAtEpochMs),
                payloadJson = payloadJson,
                hash = com.example.npc.core.model.normalize.EventNormalizer.computeDeduplicationKey(
                    SourceId.MEDIA,
                    payload.packageName,
                    payloadJson
                )
            )
            val rawId = storageGateway.insertRawEvent(rawEvent)
            if (rawId == -1L) {
                // Duplicate — skip to SourceHealth update
                storageGateway.upsertSourceHealth(buildHealth(payload))
                return
            }

            // Step 2: Create domain Event (recording even micro-sessions/skips to ensure history)
            val title = payload.trackTitle ?: "Unknown Track"
            val text = when {
                payload.isMicroSession -> if (payload.artist != null) "${payload.artist} — $title (⏭️ Пропущен)" else "$title (⏭️ Пропущен)"
                payload.artist != null -> "${payload.artist} — $title"
                else -> title
            }
            val domainEvent = com.example.npc.core.model.normalize.EventNormalizer.normalize(
                rawEvent = rawEvent.copy(id = rawId),
                title = title,
                text = text,
                threadKey = com.example.npc.core.model.ThreadKey("media:${payload.packageName}"),
                isUpdateOf = lastTrackEventIdByPackage[payload.packageName]
            )
            val savedEventId = storageGateway.insertEvent(domainEvent)
            lastTrackEventIdByPackage[payload.packageName] = savedEventId
            orchestrator?.submit(savedEventId)

            // Step 3: Clear crash-recovery snapshot
            recoveryManager.clearActiveSessionSnapshot(payload.packageName)

            // Step 4: Update SourceHealth
            storageGateway.upsertSourceHealth(buildHealth(payload))

        } catch (e: Throwable) {
            Log.e(TAG, "Error persisting media session close for ${payload.packageName}", e)
            try {
                storageGateway.upsertSourceHealth(
                    SourceHealth(
                        source = SourceId.MEDIA,
                        lastEventAt = Instant.ofEpochMilli(payload.sessionEndedAtEpochMs),
                        events24h = 0,
                        lastError = e.message ?: "unknown error",
                        queueDepth = 0
                    )
                )
            } catch (_: Throwable) {}
        }
    }

    private fun checkHeartbeatTimeouts() {
        val expired = boundaryDetector.checkHeartbeats(Instant.now())
        for (decision in expired) {
            coroutineScope.launch(ioDispatcher) {
                processSessionClose(decision.completedSession)
            }
        }
    }

    private fun buildHealth(payload: MediaSessionPayload) = SourceHealth(
        source = SourceId.MEDIA,
        lastEventAt = Instant.ofEpochMilli(payload.sessionEndedAtEpochMs),
        events24h = 0,
        lastError = payload.lastError,
        queueDepth = 0
    )
}
