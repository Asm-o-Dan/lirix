package com.example.npc.ingest.media.model

/**
 * Snapshot of media playback state extracted from [androidx.media.session.PlaybackStateCompat].
 *
 * Invariants:
 *  - [positionMs] >= 0L
 *  - [playbackSpeed] >= 0.0f
 *  - [updateTimeEpochMs] >= 0L
 */
data class MediaPlaybackSnapshot(
    /** Integer state code from PlaybackStateCompat: STATE_PLAYING, STATE_PAUSED, STATE_STOPPED, etc. */
    val state: Int,
    /** Current playback position in milliseconds. */
    val positionMs: Long,
    /** Playback speed coefficient (1.0f = normal speed). */
    val playbackSpeed: Float,
    /** System time in milliseconds (Unix epoch) when this state was last updated. */
    val updateTimeEpochMs: Long
) {
    init {
        require(positionMs >= 0L) { "positionMs must be >= 0, got $positionMs" }
        require(playbackSpeed >= 0.0f) { "playbackSpeed must be >= 0.0f, got $playbackSpeed" }
        require(updateTimeEpochMs >= 0L) { "updateTimeEpochMs must be >= 0, got $updateTimeEpochMs" }
    }

    companion object {
        /** Empty snapshot representing an unknown / null playback state. */
        val EMPTY = MediaPlaybackSnapshot(
            state = 0, // PlaybackStateCompat.STATE_NONE
            positionMs = 0L,
            playbackSpeed = 1.0f,
            updateTimeEpochMs = 0L
        )
    }
}
