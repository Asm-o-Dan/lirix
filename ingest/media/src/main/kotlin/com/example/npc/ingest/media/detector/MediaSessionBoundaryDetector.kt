package com.example.npc.ingest.media.detector

import com.example.npc.ingest.media.model.ActiveMediaSession

import com.example.npc.ingest.media.model.MediaMetadataSnapshot
import com.example.npc.ingest.media.model.MediaPlaybackSnapshot
import com.example.npc.ingest.media.model.MediaSessionEndReason
import com.example.npc.ingest.media.model.MediaSessionPayload
import com.example.npc.ingest.media.model.SessionBoundaryDecision
import java.time.Instant

// PlaybackStateCompat integer constants (mirror of PlaybackStateCompat.STATE_*)
private const val STATE_NONE = 0
private const val STATE_STOPPED = 1
private const val STATE_PAUSED = 2
private const val STATE_PLAYING = 3
private const val STATE_FAST_FORWARDING = 4
private const val STATE_REWINDING = 5
private const val STATE_BUFFERING = 6
private const val STATE_ERROR = 7
private const val STATE_CONNECTING = 8
private const val STATE_SKIPPING_TO_PREVIOUS = 9
private const val STATE_SKIPPING_TO_NEXT = 10
private const val STATE_SKIPPING_TO_QUEUE_ITEM = 11

/**
 * Deterministic Finite State Machine (FSM) for detecting media listening session boundaries.
 *
 * Rules (per spec §6.6):
 *  - STATE_PLAYING → open session if none exists; resume if paused.
 *  - STATE_PAUSED → do NOT close session; accumulate play time.
 *  - STATE_STOPPED / STATE_NONE → close session with calculated effectiveDurationMs.
 *  - Track metadata change during active session → atomically close previous, open new.
 *  - Heartbeat timeout (> 5 min silence during STATE_PLAYING) → force-close with HEARTBEAT_TIMEOUT.
 *  - Controller disconnected → close with CONTROLLER_DISCONNECTED.
 *
 * Thread-safety: NOT thread-safe. Caller must ensure single-threaded access
 * (e.g. via coroutines confined to a single dispatcher).
 *
 * @param minSessionThresholdMs Minimum effective duration for a "real" session (default 5000ms).
 * @param heartbeatTimeoutMs Max silence during STATE_PLAYING before force-close (default 300_000ms = 5 min).
 */
class MediaSessionBoundaryDetector(
    private val minSessionThresholdMs: Long = 5_000L,
    private val heartbeatTimeoutMs: Long = 300_000L
) {

    // Active sessions keyed by packageName
    private val activeSessions = mutableMapOf<String, ActiveMediaSession>()

    // Last known metadata per package — used when session opens before metadata arrives
    private val lastKnownMetadata = mutableMapOf<String, MediaMetadataSnapshot>()

    /**
     * Processes a playback state change event for [packageName].
     *
     * @return FSM decision: NoOp, OpenSession, or CloseSession.
     */
    fun onPlaybackStateChanged(
        packageName: String,
        state: MediaPlaybackSnapshot,
        now: Instant
    ): SessionBoundaryDecision {
        val current = activeSessions[packageName]

        return when (state.state) {
            STATE_PLAYING -> {
                if (current == null) {
                    // Open a new session
                    val meta = lastKnownMetadata[packageName] ?: MediaMetadataSnapshot.EMPTY
                    val newSession = ActiveMediaSession(
                        sessionId = "$packageName:${now.toEpochMilli()}",
                        packageName = packageName,
                        metadata = meta,
                        sessionStartedAt = now,
                        lastActivePlayStartedAt = now,
                        accumulatedPlayTimeMs = 0L,
                        lastState = STATE_PLAYING,
                        lastEventAt = now
                    )
                    activeSessions[packageName] = newSession
                    SessionBoundaryDecision.OpenSession(newSession)
                } else {
                    if (current.lastState == STATE_PLAYING) {
                        // Already playing — heartbeat only
                        activeSessions[packageName] = current.copy(lastEventAt = now)
                        SessionBoundaryDecision.NoOp
                    } else {
                        // Resuming from pause
                        val resumed = current.copy(
                            lastActivePlayStartedAt = now,
                            lastState = STATE_PLAYING,
                            lastEventAt = now
                        )
                        activeSessions[packageName] = resumed
                        SessionBoundaryDecision.NoOp
                    }
                }
            }

            STATE_PAUSED -> {
                if (current == null) {
                    // Start from paused — ignore
                    SessionBoundaryDecision.NoOp
                } else {
                    if (current.lastActivePlayStartedAt != null) {
                        val delta = maxOf(0L, now.toEpochMilli() - current.lastActivePlayStartedAt.toEpochMilli())
                        val newAccumulated = current.accumulatedPlayTimeMs + delta
                        val updated = current.copy(
                            lastActivePlayStartedAt = null,
                            accumulatedPlayTimeMs = newAccumulated,
                            lastState = STATE_PAUSED,
                            lastEventAt = now
                        )
                        activeSessions[packageName] = updated
                    }
                    // Session stays open
                    SessionBoundaryDecision.NoOp
                }
            }

            STATE_STOPPED, STATE_NONE -> {
                if (current == null) {
                    SessionBoundaryDecision.NoOp
                } else {
                    val payload = buildClosePayload(
                        session = current,
                        now = now,
                        endReason = if (state.state == STATE_STOPPED) MediaSessionEndReason.STATE_STOPPED
                                    else MediaSessionEndReason.STATE_NONE,
                        lastError = null
                    )
                    activeSessions.remove(packageName)
                    SessionBoundaryDecision.CloseSession(payload)
                }
            }

            else -> {
                // STATE_BUFFERING, STATE_CONNECTING, etc. — heartbeat only
                if (current != null) {
                    activeSessions[packageName] = current.copy(lastEventAt = now)
                }
                SessionBoundaryDecision.NoOp
            }
        }
    }

    /**
     * Processes a metadata change event for [packageName].
     *
     * If the track changed during active playback, atomically closes the previous session
     * and opens a new one for the new track.
     *
     * @return NoOp (metadata update) or SwitchTrack (track changed).
     */
    fun onMetadataChanged(
        packageName: String,
        metadata: MediaMetadataSnapshot,
        now: Instant
    ): SessionBoundaryDecision {
        // Always update metadata cache
        lastKnownMetadata[packageName] = metadata

        val current = activeSessions[packageName] ?: return SessionBoundaryDecision.NoOp

        // Check if track actually changed
        val trackChanged = current.metadata.title != metadata.title ||
                current.metadata.artist != metadata.artist

        if (!trackChanged) {
            // Same track — just update metadata in session (e.g. album art loaded)
            activeSessions[packageName] = current.copy(metadata = metadata, lastEventAt = now)
            return SessionBoundaryDecision.NoOp
        }

        // Track changed — close previous, open new
        val delta = if (current.lastActivePlayStartedAt != null) {
            maxOf(0L, now.toEpochMilli() - current.lastActivePlayStartedAt.toEpochMilli())
        } else 0L
        val totalDuration = current.accumulatedPlayTimeMs + delta

        val closedPayload = MediaSessionPayload(
            packageName = current.packageName,
            trackTitle = current.metadata.title,
            artist = current.metadata.artist,
            album = current.metadata.album,
            trackDurationMs = current.metadata.durationMs,
            sessionStartedAtEpochMs = current.sessionStartedAt.toEpochMilli(),
            sessionEndedAtEpochMs = now.toEpochMilli(),
            effectiveDurationMs = totalDuration,
            isMicroSession = totalDuration < minSessionThresholdMs,
            endReason = MediaSessionEndReason.TRACK_CHANGED,
            lastError = null
        )

        val newSession = ActiveMediaSession(
            sessionId = "$packageName:${now.toEpochMilli()}",
            packageName = packageName,
            metadata = metadata,
            sessionStartedAt = now,
            lastActivePlayStartedAt = if (current.lastState == STATE_PLAYING) now else null,
            accumulatedPlayTimeMs = 0L,
            lastState = current.lastState,
            lastEventAt = now
        )
        activeSessions[packageName] = newSession

        return SessionBoundaryDecision.SwitchTrack(
            previousSessionToClose = closedPayload,
            newSessionToOpen = newSession
        )
    }

    /**
     * Handles controller disappearing from active sessions list (player closed / killed).
     *
     * @return CloseSession with CONTROLLER_DISCONNECTED, or NoOp if no session was active.
     */
    fun onControllerDisconnected(packageName: String, now: Instant): SessionBoundaryDecision {
        val current = activeSessions.remove(packageName) ?: return SessionBoundaryDecision.NoOp

        val payload = buildClosePayload(
            session = current,
            now = now,
            endReason = MediaSessionEndReason.CONTROLLER_DISCONNECTED,
            lastError = null
        )
        return SessionBoundaryDecision.CloseSession(payload)
    }

    /**
     * Periodic heartbeat check. Sessions in STATE_PLAYING that haven't received events
     * for > [heartbeatTimeoutMs] are force-closed with HEARTBEAT_TIMEOUT error.
     *
     * NOTE: Sessions in STATE_PAUSED are NOT affected (per spec §6.6).
     *
     * @return List of CloseSession decisions for each timed-out session.
     */
    fun checkHeartbeats(now: Instant): List<SessionBoundaryDecision.CloseSession> {
        val expired = mutableListOf<SessionBoundaryDecision.CloseSession>()
        val expiredKeys = mutableListOf<String>()

        for ((pkg, session) in activeSessions) {
            if (session.lastState != STATE_PLAYING) continue

            val silenceMs = now.toEpochMilli() - session.lastEventAt.toEpochMilli()
            if (silenceMs > heartbeatTimeoutMs) {
                // Use lastEventAt as the effective session end (cap to last known active moment)
                val lastActive = session.lastEventAt
                val deltaAtTimeout = maxOf(
                    0L,
                    lastActive.toEpochMilli() - (session.lastActivePlayStartedAt?.toEpochMilli() ?: lastActive.toEpochMilli())
                )
                val totalDuration = session.accumulatedPlayTimeMs + deltaAtTimeout

                val payload = MediaSessionPayload(
                    packageName = session.packageName,
                    trackTitle = session.metadata.title,
                    artist = session.metadata.artist,
                    album = session.metadata.album,
                    trackDurationMs = session.metadata.durationMs,
                    sessionStartedAtEpochMs = session.sessionStartedAt.toEpochMilli(),
                    sessionEndedAtEpochMs = lastActive.toEpochMilli(),
                    effectiveDurationMs = totalDuration,
                    isMicroSession = totalDuration < minSessionThresholdMs,
                    endReason = MediaSessionEndReason.HEARTBEAT_TIMEOUT,
                    lastError = "heartbeat_timeout"
                )
                expired.add(SessionBoundaryDecision.CloseSession(payload))
                expiredKeys.add(pkg)
            }
        }

        expiredKeys.forEach { activeSessions.remove(it) }
        return expired
    }

    /** Returns the active session for [packageName], or null if no session is tracked. */
    fun getActiveSession(packageName: String): ActiveMediaSession? = activeSessions[packageName]

    /** Returns all currently tracked active sessions. */
    fun getAllActiveSessions(): List<ActiveMediaSession> = activeSessions.values.toList()

    // --- Private helpers ---

    private fun buildClosePayload(
        session: ActiveMediaSession,
        now: Instant,
        endReason: MediaSessionEndReason,
        lastError: String?
    ): MediaSessionPayload {
        val activeSegment = if (session.lastActivePlayStartedAt != null) {
            maxOf(0L, now.toEpochMilli() - session.lastActivePlayStartedAt.toEpochMilli())
        } else 0L
        val totalDuration = session.accumulatedPlayTimeMs + activeSegment

        return MediaSessionPayload(
            packageName = session.packageName,
            trackTitle = session.metadata.title,
            artist = session.metadata.artist,
            album = session.metadata.album,
            trackDurationMs = session.metadata.durationMs,
            sessionStartedAtEpochMs = session.sessionStartedAt.toEpochMilli(),
            sessionEndedAtEpochMs = now.toEpochMilli(),
            effectiveDurationMs = totalDuration,
            isMicroSession = totalDuration < minSessionThresholdMs,
            endReason = endReason,
            lastError = lastError
        )
    }
}
