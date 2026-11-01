package com.lirix.app.ui.components

import com.lirix.app.ingestion.LivePlaybackSnapshot
import org.junit.Assert.*
import org.junit.Test

class PlaybackUiClockTest {
    private fun snapshot(playing: Boolean = true, speed: Float = 1f, duration: Long = 20_000L) =
        LivePlaybackSnapshot("player", "Track", "Artist", isPlaying = playing,
            basePositionMs = 1_000L, lastPositionUpdateTimeMs = 5_000L,
            playbackSpeed = speed, durationMs = duration)

    @Test fun pausedAndMissingSourcesHaveNoAdvancingClock() {
        val paused = PlaybackUiClock(snapshot(playing = false), syncOffsetMs = -200L)
        assertFalse(paused.isPlaying)
        assertFalse(paused.isAdvancing(50_000L))
        assertEquals(800L, paused.positionMs(50_000L))
        assertEquals(paused.positionMs(6_000L), paused.positionMs(50_000L))
        assertFalse(PlaybackUiClock().isAdvancing(50_000L))
        assertEquals(0L, PlaybackUiClock().positionMs(50_000L))
    }

    @Test fun playingClockResamplesElapsedTimeAndStopsAtDuration() {
        val clock = PlaybackUiClock(snapshot(speed = 2f), syncOffsetMs = 250L)
        assertEquals(3_250L, clock.positionMs(6_000L))
        // No accumulated UI ticks: coming back from the background catches up immediately.
        assertEquals(19_250L, clock.positionMs(14_000L))
        assertTrue(clock.isAdvancing(14_000L))
        assertEquals(20_250L, clock.positionMs(50_000L))
        assertFalse(clock.isAdvancing(50_000L))
    }

    @Test fun localClockReanchorsPreciselyAfterPauseAndSeek() {
        val playing = PlaybackUiClock(localPositionMs = 4_123L, localAnchorMs = 1_000L, localIsPlaying = true)
        val paused = playing.copy(localPositionMs = playing.rawPositionMs(1_777L), localIsPlaying = false)
        assertEquals(4_900L, paused.rawPositionMs(20_000L))
        val sought = paused.copy(localPositionMs = 10_321L, localAnchorMs = 20_000L, localIsPlaying = true)
        assertEquals(10_821L, sought.rawPositionMs(20_500L))
    }

    @Test fun lyricScheduleUsesNextBoundarySpeedAndDuplicateTimestamps() {
        val timestamps = listOf(1_000L, 2_000L, 2_000L, 4_000L)
        assertEquals(500L, nextLyricUpdateDelayMs(timestamps, 0L, 2f))
        assertEquals(500L, nextLyricUpdateDelayMs(timestamps, 1_000L, 2f))
        assertEquals(4_000L, nextLyricUpdateDelayMs(timestamps, 2_000L, 0.5f))
        assertEquals(1L, nextLyricUpdateDelayMs(timestamps, 1_999L, 2f))
        assertNull(nextLyricUpdateDelayMs(timestamps, 4_000L, 1f))
        assertNull(nextLyricUpdateDelayMs(emptyList(), 0L, 1f))
        assertNull(nextLyricUpdateDelayMs(timestamps, 0L, 0f))
    }
}
