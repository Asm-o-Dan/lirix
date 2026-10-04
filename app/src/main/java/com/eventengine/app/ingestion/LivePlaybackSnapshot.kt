package com.eventengine.app.ingestion

import android.content.Context

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
    val lastPositionUpdateTimeMs: Long, // SystemClock.elapsedRealtime()
    val playbackSpeed: Float = 1.0f,
    val durationMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val albumArtUri: String? = null
) {
    fun currentPositionMs(currentElapsedRealtimeMs: Long = android.os.SystemClock.elapsedRealtime()): Long {
        if (!isPlaying || playbackSpeed <= 0f) return basePositionMs
        val elapsed = currentElapsedRealtimeMs - lastPositionUpdateTimeMs
        val extrapolated = basePositionMs + (elapsed * playbackSpeed).toLong()
        return if (durationMs > 0L) extrapolated.coerceIn(0L, durationMs) else extrapolated.coerceAtLeast(0L)
    }
}

/**
 * Storage for track-specific and global timing sync offsets.
 * Preserves user calibration in range [-3000ms..+3000ms].
 * Spec: TASK-SYNC-01 (.sdd/tasks/TASK-SYNC-01.md)
 */
class SyncOffsetStore(context: Context) {
    private val prefs = context.getSharedPreferences("media_sync_offsets", Context.MODE_PRIVATE)

    fun getOffset(trackKey: String): Long {
        if (trackKey.isBlank()) return getGlobalOffset()
        val key = "offset_$trackKey"
        return if (prefs.contains(key)) {
            prefs.getLong(key, 0L)
        } else {
            getGlobalOffset()
        }
    }

    fun setOffset(trackKey: String, offsetMs: Long) {
        val clamped = offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
        if (trackKey.isNotBlank()) {
            prefs.edit().putLong("offset_$trackKey", clamped).apply()
        }
    }

    fun getGlobalOffset(): Long {
        return prefs.getLong(KEY_GLOBAL_OFFSET, 0L)
    }

    fun setGlobalOffset(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS)
        prefs.edit().putLong(KEY_GLOBAL_OFFSET, clamped).apply()
    }

    companion object {
        const val MIN_OFFSET_MS = -3000L
        const val MAX_OFFSET_MS = 3000L
        const val STEP_OFFSET_MS = 50L
        private const val KEY_GLOBAL_OFFSET = "global_offset"
    }
}
