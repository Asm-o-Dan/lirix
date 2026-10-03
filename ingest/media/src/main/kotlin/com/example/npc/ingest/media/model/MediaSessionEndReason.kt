package com.example.npc.ingest.media.model

/**
 * Enumeration of reasons why a media listening session ended.
 *
 * Used in [MediaSessionPayload.endReason] to classify the closure cause.
 */
enum class MediaSessionEndReason {
    /** Player transitioned to STATE_STOPPED. */
    STATE_STOPPED,

    /** Session reset to inactive STATE_NONE. */
    STATE_NONE,

    /** Track changed while playing (next track / playlist auto-advance). Previous session closed atomically. */
    TRACK_CHANGED,

    /** Controller removed from active sessions list (player process closed / service unloaded). */
    CONTROLLER_DISCONNECTED,

    /** No events from player for > 5 minutes while in STATE_PLAYING. Session force-closed with lastError = "heartbeat_timeout". */
    HEARTBEAT_TIMEOUT,

    /** Session recovered after unexpected app / OS kill (HyperOS LMK, power cycle). */
    APP_RESTART
}
