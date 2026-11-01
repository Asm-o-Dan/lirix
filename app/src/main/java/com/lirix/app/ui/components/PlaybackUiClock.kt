package com.lirix.app.ui.components

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.lirix.app.ingestion.LivePlaybackSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.ceil

/** An immutable playback anchor; reading it never changes Compose state. */
internal data class PlaybackUiClock(
    val snapshot: LivePlaybackSnapshot? = null,
    val localPositionMs: Long = 0L,
    val localAnchorMs: Long = 0L,
    val localIsPlaying: Boolean = false,
    val syncOffsetMs: Long = 0L
) {
    val isPlaying: Boolean get() = snapshot?.let { it.isPlaying && it.playbackSpeed > 0f } ?: localIsPlaying
    val speed: Float get() = snapshot?.playbackSpeed ?: 1f

    fun rawPositionMs(nowMs: Long = SystemClock.elapsedRealtime()): Long =
        snapshot?.currentPositionMs(nowMs)
            ?: (localPositionMs + if (localIsPlaying) (nowMs - localAnchorMs).coerceAtLeast(0L) else 0L)

    fun positionMs(nowMs: Long = SystemClock.elapsedRealtime()): Long =
        karaokeLyricPosition(rawPositionMs(nowMs), syncOffsetMs)

    fun isAdvancing(nowMs: Long = SystemClock.elapsedRealtime()): Boolean =
        isPlaying && (snapshot == null || snapshot.durationMs <= 0L || rawPositionMs(nowMs) < snapshot.durationMs)
}

/** Only schedule work at a lyric boundary, accounting for playback speed. */
internal fun nextLyricUpdateDelayMs(timestamps: List<Long>, positionMs: Long, speed: Float): Long? {
    if (speed <= 0f || !speed.isFinite()) return null
    val next = timestamps.firstOrNull { it > positionMs } ?: return null
    return ceil((next.toDouble() - positionMs.toDouble()) / speed).toLong().coerceAtLeast(1L)
}

/** Owns changing state in this small UI scope instead of invalidating the player screen. */
@Composable
internal fun PlaybackClockContent(
    clock: PlaybackUiClock,
    lyricTimestamps: List<Long>? = null,
    intervalMs: Long? = null,
    content: @Composable (Long) -> Unit
) {
    fun projectedPosition(): Long {
        val position = clock.positionMs()
        return if (lyricTimestamps == null) position
        else lyricTimestamps.lastOrNull { it <= position } ?: Long.MIN_VALUE
    }

    var position by remember(clock, lyricTimestamps) { mutableLongStateOf(projectedPosition()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(clock, lyricTimestamps, intervalMs, lifecycle) {
        // A paused or absent source needs a single value, with no recurring job.
        if (!clock.isPlaying) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            position = projectedPosition()
            while (isActive && clock.isAdvancing()) {
                if (lyricTimestamps != null) {
                    val waitMs = nextLyricUpdateDelayMs(lyricTimestamps, clock.positionMs(), clock.speed)
                        ?: break
                    delay(waitMs)
                } else if (intervalMs != null) {
                    delay(intervalMs)
                } else {
                    withFrameMillis { }
                }
                position = projectedPosition()
            }
        }
    }
    content(position)
}
