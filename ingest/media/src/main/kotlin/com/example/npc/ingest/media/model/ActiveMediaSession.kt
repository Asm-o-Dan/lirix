package com.example.npc.ingest.media.model

import java.time.Instant

/**
 * Mutable in-memory operational state of an open media listening session tracked by
 * [com.example.npc.ingest.media.detector.MediaSessionBoundaryDetector].
 *
 * Invariants:
 *  - [sessionId].isNotBlank()
 *  - [packageName].isNotBlank()
 *  - [accumulatedPlayTimeMs] >= 0L
 *  - If [lastActivePlayStartedAt] != null, then lastActivePlayStartedAt >= sessionStartedAt
 */
data class ActiveMediaSession(
    /**
     * Unique session identifier. Format: "$packageName:${sessionStartedAt.toEpochMilli()}".
     */
    val sessionId: String,

    /** Android package name of the player app (e.g. "com.spotify.music"). */
    val packageName: String,

    /** Current track metadata snapshot. */
    val metadata: MediaMetadataSnapshot,

    /** Exact moment of the first STATE_PLAYING transition that opened this session. */
    val sessionStartedAt: Instant,

    /**
     * Moment of the last playback resumption (after pause or initial start).
     * Null when the player is currently paused (i.e. not actively playing).
     */
    val lastActivePlayStartedAt: Instant?,

    /**
     * Net accumulated play time in milliseconds, not counting the current play segment.
     * The current play segment time = (now - lastActivePlayStartedAt) is added on-the-fly.
     */
    val accumulatedPlayTimeMs: Long,

    /** Last recorded PlaybackStateCompat.STATE_* code. */
    val lastState: Int,

    /** Timestamp of the last received event / callback — used for heartbeat monitoring. */
    val lastEventAt: Instant
) {
    init {
        require(sessionId.isNotBlank()) { "sessionId must not be blank" }
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(accumulatedPlayTimeMs >= 0L) { "accumulatedPlayTimeMs must be >= 0, got $accumulatedPlayTimeMs" }
        require(lastActivePlayStartedAt == null || !lastActivePlayStartedAt.isBefore(sessionStartedAt)) {
            "lastActivePlayStartedAt ($lastActivePlayStartedAt) must be >= sessionStartedAt ($sessionStartedAt)"
        }
    }
}
