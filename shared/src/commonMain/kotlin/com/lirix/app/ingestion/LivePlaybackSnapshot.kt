package com.lirix.app.ingestion

/**
 * Contract model for real-time media playback extrapolation.
 * Spec: TASK-SYNC-01 (.sdd/tasks/TASK-SYNC-01.md)
 */
data class LivePlaybackSnapshot(
    val packageName: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val isPlaying: Boolean,
    val basePositionMs: Long,
    val lastPositionUpdateTimeMs: Long, // elapsedRealtime or monotonic
    val playbackSpeed: Float = 1.0f,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val albumArtUri: String? = null,
    val isBlacklisted: Boolean = false
) {
    fun currentPositionMs(currentElapsedRealtimeMs: Long = getSystemElapsedRealtimeMs()): Long {
        if (!isPlaying || playbackSpeed <= 0f) return basePositionMs
        val elapsed = currentElapsedRealtimeMs - lastPositionUpdateTimeMs
        val extrapolated = basePositionMs + (elapsed * playbackSpeed).toLong()
        return if (durationMs > 0L) extrapolated.coerceIn(0L, durationMs) else extrapolated.coerceAtLeast(0L)
    }
}
