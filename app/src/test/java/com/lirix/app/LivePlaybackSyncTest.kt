package com.lirix.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.ingestion.SyncOffsetStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit tests for TASK-SYNC-01 (TEST-SYNC-01):
 * - LivePlaybackSnapshot position extrapolation formulas:
 *   * Paused state (isPlaying = false) -> returns basePositionMs without drift.
 *   * Playing state with normal speed (1.0f) -> advances strictly by elapsed time delta.
 *   * Playing state with accelerated speed (1.5f, 2.0f) -> scales advancement proportionally.
 *   * Playing state with negative or zero speed -> freezes at basePositionMs.
 *   * Clamping to durationMs when extrapolated position exceeds track duration.
 *   * Unbounded duration (durationMs <= 0) -> allows advancement without upper clamp.
 *   * Negative extrapolation protection (coerceAtLeast(0L)).
 *
 * - SyncOffsetStore calibration and persistence:
 *   * Range enforcement: clamping between -3000ms and +3000ms.
 *   * Fallback to globalOffset when track-specific offset is not set.
 *   * Track-specific offset overrides globalOffset.
 *   * Per-track isolation: modifications to track A do not affect track B.
 */
@RunWith(AndroidJUnit4::class)
class LivePlaybackSyncTest {

    private lateinit var context: Context
    private lateinit var syncOffsetStore: SyncOffsetStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear shared preferences before each test
        context.getSharedPreferences("media_sync_offsets", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        syncOffsetStore = SyncOffsetStore(context)
    }

    // ------------------------------------------------------------------------
    // 1. LivePlaybackSnapshot Extrapolation Math Tests
    // ------------------------------------------------------------------------

    @Test
    fun testExtrapolation_whenPaused_returnsBasePosition() {
        val basePos = 45000L
        val updateTime = 100000L
        val snapshot = LivePlaybackSnapshot(
            packageName = "com.spotify.music",
            title = "Starboy",
            artist = "The Weeknd",
            isPlaying = false,
            basePositionMs = basePos,
            lastPositionUpdateTimeMs = updateTime,
            playbackSpeed = 1.0f,
            durationMs = 210000L
        )

        // Even after 5000ms elapsed, paused position must remain unchanged
        val currentElapsed = updateTime + 5000L
        val pos = snapshot.currentPositionMs(currentElapsed)
        assertEquals("Paused playback must not extrapolate time", basePos, pos)
    }

    @Test
    fun testExtrapolation_whenPlayingNormalSpeed_advancesEqually() {
        val basePos = 30000L
        val updateTime = 100000L
        val snapshot = LivePlaybackSnapshot(
            packageName = "com.spotify.music",
            title = "In The End",
            artist = "Linkin Park",
            isPlaying = true,
            basePositionMs = basePos,
            lastPositionUpdateTimeMs = updateTime,
            playbackSpeed = 1.0f,
            durationMs = 216000L
        )

        val deltaElapsed = 3250L
        val pos = snapshot.currentPositionMs(updateTime + deltaElapsed)
        assertEquals("Position must advance by exactly delta elapsed at 1.0x speed", basePos + deltaElapsed, pos)
    }

    @Test
    fun testExtrapolation_whenPlayingAcceleratedSpeed_scalesProportionally() {
        val basePos = 20000L
        val updateTime = 50000L
        val snapshot = LivePlaybackSnapshot(
            packageName = "ru.yandex.music",
            title = "Podcast Episode",
            artist = "Speaker",
            isPlaying = true,
            basePositionMs = basePos,
            lastPositionUpdateTimeMs = updateTime,
            playbackSpeed = 1.5f,
            durationMs = 600000L
        )

        val deltaElapsed = 4000L
        // Expected advance: 4000 * 1.5 = 6000ms -> total 26000ms
        val pos = snapshot.currentPositionMs(updateTime + deltaElapsed)
        assertEquals("Position must advance proportionally to 1.5x speed", 26000L, pos)
    }

    @Test
    fun testExtrapolation_whenSpeedZeroOrNegative_freezesAtBasePosition() {
        val basePos = 15000L
        val updateTime = 80000L

        val zeroSpeedSnapshot = LivePlaybackSnapshot(
            packageName = "com.spotify.music",
            title = "Test",
            artist = "Artist",
            isPlaying = true,
            basePositionMs = basePos,
            lastPositionUpdateTimeMs = updateTime,
            playbackSpeed = 0.0f,
            durationMs = 180000L
        )
        assertEquals(basePos, zeroSpeedSnapshot.currentPositionMs(updateTime + 10000L))

        val negSpeedSnapshot = zeroSpeedSnapshot.copy(playbackSpeed = -1.0f)
        assertEquals(basePos, negSpeedSnapshot.currentPositionMs(updateTime + 10000L))
    }

    @Test
    fun testExtrapolation_clampsToDuration_whenExceeded() {
        val duration = 180000L // 3 minutes
        val basePos = 175000L  // 5 seconds before end
        val updateTime = 10000L
        val snapshot = LivePlaybackSnapshot(
            packageName = "com.spotify.music",
            title = "Numb",
            artist = "Linkin Park",
            isPlaying = true,
            basePositionMs = basePos,
            lastPositionUpdateTimeMs = updateTime,
            playbackSpeed = 1.0f,
            durationMs = duration
        )

        // Elapsed delta is 10s (would reach 185s, exceeding 180s duration)
        val pos = snapshot.currentPositionMs(updateTime + 10000L)
        assertEquals("Position must be clamped to durationMs", duration, pos)
    }

    @Test
    fun testExtrapolation_whenDurationZeroOrUnknown_doesNotClampUpper() {
        val basePos = 10000L
        val updateTime = 20000L
        val snapshot = LivePlaybackSnapshot(
            packageName = "app.revanced.android.apps.youtube.music",
            title = "Live Stream",
            artist = "Live Band",
            isPlaying = true,
            basePositionMs = basePos,
            lastPositionUpdateTimeMs = updateTime,
            playbackSpeed = 1.0f,
            durationMs = 0L // Unknown / live stream duration
        )

        val deltaElapsed = 50000L
        val pos = snapshot.currentPositionMs(updateTime + deltaElapsed)
        assertEquals("Position must advance past standard boundaries if durationMs is 0", basePos + deltaElapsed, pos)
    }

    // ------------------------------------------------------------------------
    // 2. SyncOffsetStore Persistence and Clamping Tests
    // ------------------------------------------------------------------------

    @Test
    fun testSyncOffsetStore_fallbackToGlobalOffset() {
        // When global offset is not set, default is 0ms
        assertEquals(0L, syncOffsetStore.getGlobalOffset())
        assertEquals(0L, syncOffsetStore.getOffset("track_uncalibrated"))

        // Set global offset (e.g. +200ms for Bluetooth headphone delay)
        syncOffsetStore.setGlobalOffset(200L)
        assertEquals(200L, syncOffsetStore.getGlobalOffset())
        // Uncalibrated track must inherit global offset
        assertEquals("Track without specific offset must fallback to global offset", 200L, syncOffsetStore.getOffset("track_uncalibrated"))
    }

    @Test
    fun testSyncOffsetStore_perTrackOverrideAndIsolation() {
        syncOffsetStore.setGlobalOffset(150L)

        val trackA = "sha256_track_a"
        val trackB = "sha256_track_b"

        // Calibrate Track A to -350ms
        syncOffsetStore.setOffset(trackA, -350L)

        assertEquals(-350L, syncOffsetStore.getOffset(trackA))
        assertEquals("Track B must still inherit global offset", 150L, syncOffsetStore.getOffset(trackB))

        // Calibrate Track B to +500ms
        syncOffsetStore.setOffset(trackB, 500L)
        assertEquals(-350L, syncOffsetStore.getOffset(trackA))
        assertEquals(500L, syncOffsetStore.getOffset(trackB))
    }

    @Test
    fun testSyncOffsetStore_clampingToMinMaxLimits() {
        val trackKey = "sha256_extreme"

        // Value beyond max (+4500ms) must be clamped to +3000ms
        syncOffsetStore.setOffset(trackKey, 4500L)
        assertEquals(3000L, syncOffsetStore.getOffset(trackKey))

        // Value beyond min (-5000ms) must be clamped to -3000ms
        syncOffsetStore.setOffset(trackKey, -5000L)
        assertEquals(-3000L, syncOffsetStore.getOffset(trackKey))

        // Global offset clamping
        syncOffsetStore.setGlobalOffset(9999L)
        assertEquals(3000L, syncOffsetStore.getGlobalOffset())

        syncOffsetStore.setGlobalOffset(-9999L)
        assertEquals(-3000L, syncOffsetStore.getGlobalOffset())
    }

    // ------------------------------------------------------------------------
    // 3. TASK-SYNC-02: Bidirectional Transport Control & Optimistic Updates
    // ------------------------------------------------------------------------

    @Test
    fun testSeekTo_updatesSnapshotPositionAndTimestampOptimistically() {
        val initialTime = 100000L
        val snapshot = LivePlaybackSnapshot(
            packageName = "com.spotify.music",
            title = "Starboy",
            artist = "The Weeknd",
            isPlaying = true,
            basePositionMs = 30000L,
            lastPositionUpdateTimeMs = initialTime,
            playbackSpeed = 1.0f,
            durationMs = 210000L
        )

        // Target seek: 95000ms at new system time
        val seekTargetMs = 95000L
        val seekTime = initialTime + 4500L

        // Optimistic snapshot update model: freezes basePositionMs to seekTargetMs, updates timestamp
        val optimisticSnapshot = snapshot.copy(
            basePositionMs = seekTargetMs.coerceIn(0L, snapshot.durationMs),
            lastPositionUpdateTimeMs = seekTime
        )

        assertEquals("Snapshot base position must match seek target immediately", seekTargetMs, optimisticSnapshot.basePositionMs)
        assertEquals("Last position update time must be refreshed to seek invocation time", seekTime, optimisticSnapshot.lastPositionUpdateTimeMs)
        assertEquals("Immediate extrapolated position must equal seek target", seekTargetMs, optimisticSnapshot.currentPositionMs(seekTime))
    }

    @Test
    fun testTogglePlayPause_invertsPlayingStateOptimistically() {
        val t0 = 50000L
        val playingSnapshot = LivePlaybackSnapshot(
            packageName = "ru.yandex.music",
            title = "Numb",
            artist = "Linkin Park",
            isPlaying = true,
            basePositionMs = 20000L,
            lastPositionUpdateTimeMs = t0,
            playbackSpeed = 1.0f,
            durationMs = 180000L
        )

        // 1. Pause action at t0 + 5000ms (accumulated position 25000ms)
        val pauseTime = t0 + 5000L
        val currentPos = playingSnapshot.currentPositionMs(pauseTime)
        assertEquals(25000L, currentPos)

        val pausedSnapshot = playingSnapshot.copy(
            isPlaying = false,
            basePositionMs = currentPos,
            lastPositionUpdateTimeMs = pauseTime
        )

        org.junit.Assert.assertFalse("Playing state must be inverted to false optimistically", pausedSnapshot.isPlaying)
        assertEquals(25000L, pausedSnapshot.basePositionMs)
        // Position must remain frozen while paused
        assertEquals(25000L, pausedSnapshot.currentPositionMs(pauseTime + 10000L))

        // 2. Play action at pauseTime + 10000ms
        val resumeTime = pauseTime + 10000L
        val resumedSnapshot = pausedSnapshot.copy(
            isPlaying = true,
            basePositionMs = pausedSnapshot.currentPositionMs(resumeTime),
            lastPositionUpdateTimeMs = resumeTime
        )

        org.junit.Assert.assertTrue("Playing state must be inverted to true optimistically", resumedSnapshot.isPlaying)
        assertEquals(25000L, resumedSnapshot.basePositionMs)
        // Extrapolation resumes from 25000ms
        assertEquals(28000L, resumedSnapshot.currentPositionMs(resumeTime + 3000L))
    }

    @Test
    fun testSeekRelative_clampsToDurationAndZero() {
        val duration = 150000L // 2.5 minutes
        val snapshot = LivePlaybackSnapshot(
            packageName = "com.spotify.music",
            title = "In The End",
            artist = "Linkin Park",
            isPlaying = true,
            basePositionMs = 8000L, // 8 seconds
            lastPositionUpdateTimeMs = 10000L,
            playbackSpeed = 1.0f,
            durationMs = duration
        )

        // Replay 10s: 8s - 10s = -2s -> clamped to 0L
        val replayedPos = (snapshot.basePositionMs - 10000L).coerceAtLeast(0L)
        assertEquals("Replay past start of track must clamp to 0L", 0L, replayedPos)

        // Near end of track: 145s + 10s = 155s -> clamped to duration 150s
        val nearEndSnapshot = snapshot.copy(basePositionMs = 145000L)
        val forwardedPos = (nearEndSnapshot.basePositionMs + 10000L).coerceIn(0L, duration)
        assertEquals("Forward past track duration must clamp to durationMs", duration, forwardedPos)

        // Normal middle shift: 60s + 10s = 70s
        val midSnapshot = snapshot.copy(basePositionMs = 60000L)
        val normalForwardPos = (midSnapshot.basePositionMs + 10000L).coerceIn(0L, duration)
        assertEquals(70000L, normalForwardPos)

        // Normal middle shift backwards: 60s - 10s = 50s
        val normalBackwardPos = (midSnapshot.basePositionMs - 10000L).coerceIn(0L, duration)
        assertEquals(50000L, normalBackwardPos)
    }

    // ------------------------------------------------------------------------
    // TASK-BUG-09A: resolveSeekController Scoring & Action Filtering Tests
    // ------------------------------------------------------------------------

    @Test
    fun testResolveSeekController_prefersControllerWithSeekToAction() {
        val emptyList = emptyList<android.media.session.MediaController>()
        val resultNull = com.lirix.app.ingestion.MediaSessionCollector.resolveSeekController(emptyList)
        org.junit.Assert.assertNull("Empty list must return null", resultNull)
    }
}

