package com.example.npc.ingest.media.model

/**
 * Immutable structured DTO of a completed media listening session for serialization
 * into [com.example.npc.core.model.RawEvent.payloadJson].
 *
 * Invariants:
 *  - [sessionStartedAtEpochMs] <= [sessionEndedAtEpochMs]
 *  - [effectiveDurationMs] >= 0L
 *  - [effectiveDurationMs] <= (sessionEndedAtEpochMs - sessionStartedAtEpochMs)
 *  - [isMicroSession] == (effectiveDurationMs < 5000L)
 */
data class MediaSessionPayload(
    /** Android package name of the player app. */
    val packageName: String,

    /** Track title (null if unknown / live stream). */
    val trackTitle: String?,

    /** Artist name (null if unknown). */
    val artist: String?,

    /** Album name (null if unknown). */
    val album: String?,

    /** Total track duration from metadata in milliseconds (-1L = unknown). */
    val trackDurationMs: Long,

    /** Session start time as Unix epoch milliseconds. */
    val sessionStartedAtEpochMs: Long,

    /** Session end time as Unix epoch milliseconds. */
    val sessionEndedAtEpochMs: Long,

    /** Net play time excluding pauses, in milliseconds. */
    val effectiveDurationMs: Long,

    /** True when effectiveDurationMs < 5000L (random track skip, not a real listen). */
    val isMicroSession: Boolean,

    /** Reason why this session was closed. */
    val endReason: MediaSessionEndReason,

    /** Optional error annotation (e.g. "heartbeat_timeout"), null for clean closes. */
    val lastError: String?
) {
    init {
        require(sessionStartedAtEpochMs <= sessionEndedAtEpochMs) {
            "sessionStartedAtEpochMs ($sessionStartedAtEpochMs) must be <= sessionEndedAtEpochMs ($sessionEndedAtEpochMs)"
        }
        require(effectiveDurationMs >= 0L) {
            "effectiveDurationMs must be >= 0, got $effectiveDurationMs"
        }
        require(effectiveDurationMs <= (sessionEndedAtEpochMs - sessionStartedAtEpochMs)) {
            "effectiveDurationMs ($effectiveDurationMs) cannot exceed session wall time (${sessionEndedAtEpochMs - sessionStartedAtEpochMs})"
        }
        require(isMicroSession == (effectiveDurationMs < MICRO_SESSION_THRESHOLD_MS)) {
            "isMicroSession ($isMicroSession) mismatch: effectiveDurationMs=$effectiveDurationMs, threshold=$MICRO_SESSION_THRESHOLD_MS"
        }
    }

    companion object {
        /** Sessions shorter than this are considered skips and not written to the Event table. */
        const val MICRO_SESSION_THRESHOLD_MS = 5000L
    }
}
