package com.example.npc.ingest.media.model

/**
 * Result of the [com.example.npc.ingest.media.detector.MediaSessionBoundaryDetector] FSM computation.
 *
 * Used to communicate what storage pipeline action (if any) should be taken after each
 * playback state or metadata callback.
 */
sealed interface SessionBoundaryDecision {

    /** No action needed — session state updated in memory, nothing to persist. */
    data object NoOp : SessionBoundaryDecision

    /** A new listening session has started. The caller may persist a snapshot for crash recovery. */
    data class OpenSession(
        val session: ActiveMediaSession
    ) : SessionBoundaryDecision

    /** A session has completed. The caller must persist the payload via StorageGateway. */
    data class CloseSession(
        val completedSession: MediaSessionPayload
    ) : SessionBoundaryDecision

    /**
     * A track changed while the player was active.
     * The previous session must be closed ([previousSessionToClose]) and a new one opened ([newSessionToOpen]).
     * The caller must persist the previous session and may persist a snapshot of the new one.
     */
    data class SwitchTrack(
        val previousSessionToClose: MediaSessionPayload,
        val newSessionToOpen: ActiveMediaSession
    ) : SessionBoundaryDecision
}
