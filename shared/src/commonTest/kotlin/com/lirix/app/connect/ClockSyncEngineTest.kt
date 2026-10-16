package com.lirix.app.connect

import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.time.ClockSyncEngine
import com.lirix.app.connect.time.PlaybackPositionInterpolator
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClockSyncEngineTest {

    // ── 1. NTP 4-timestamp calculation and simulated network accuracy ────────

    @Test
    fun testNtpFormulaExactSymmetricNetwork() {
        val engine = ClockSyncEngine()

        // Client clock: 1000
        // Server clock offset: +5000 (i.e. server = client + 5000)
        // One-way delay: 20 ms (RTT = 40 ms)
        // Server processing: 5 ms

        val t0 = 1000L                        // Client sends Ping
        val t1 = t0 + 5000L + 20L             // Server receives Ping (6020L)
        val t2 = t1 + 5L                      // Server sends Pong after 5ms (6025L)
        val t3 = t0 + 20L + 5L + 20L          // Client receives Pong (1045L)

        val sample = engine.addSample(t0, t1, t2, t3)

        assertEquals(40L, sample.rtt)
        assertEquals(5000L, sample.offset)
        assertEquals(5000L, engine.estimatedOffset)
        assertEquals(40L, engine.minRtt)
    }

    @Test
    fun testNtpAccuracyWithSimulatedJitterAndAsymmetry() {
        val engine = ClockSyncEngine()
        val trueServerOffset = 12500L // Server is 12500 ms ahead

        // Simulate 6 samples with varying RTTs (20 ms to 150 ms)
        val networkScenarios = listOf(
            Triple(15L, 15L, 2L),   // RTT 30 ms (clean, symmetric) -> best sample!
            Triple(40L, 50L, 5L),   // RTT 90 ms (jitter spike)
            Triple(80L, 70L, 10L),  // RTT 150 ms (bufferbloat)
            Triple(16L, 18L, 3L),   // RTT 34 ms (slight asymmetry)
            Triple(50L, 30L, 4L),   // RTT 80 ms (asymmetric)
            Triple(25L, 25L, 2L)    // RTT 50 ms
        )

        var clientClock = 20000L
        for ((upDelay, downDelay, serverProc) in networkScenarios) {
            val t0 = clientClock
            val t1 = t0 + trueServerOffset + upDelay
            val t2 = t1 + serverProc
            val t3 = t0 + upDelay + serverProc + downDelay

            engine.addSample(t0, t1, t2, t3)
            clientClock += 1000L
        }

        assertTrue(engine.isSynchronized)
        assertEquals(6, engine.sampleCount)

        // The best sample must have selected RTT 30 ms
        val best = engine.bestSample
        assertNotNull(best)
        assertEquals(30L, best.rtt)

        // Estimated offset should be within 10 ms of true offset (DoD accuracy requirement)
        val error = abs(engine.estimatedOffset - trueServerOffset)
        assertTrue(error <= 10L, "Offset error $error ms exceeds 10 ms threshold")
    }

    @Test
    fun testFilteringOutliersAndGlitchSamples() {
        val engine = ClockSyncEngine(maxValidRttMs = 1000L)

        // Sample 1: Glitch with negative RTT
        engine.addSample(t0 = 500L, t1 = 100L, t2 = 100L, t3 = 400L) // RTT = -100

        // Sample 2: Huge lag spike (> 1000 ms)
        engine.addSample(t0 = 1000L, t1 = 3000L, t2 = 3050L, t3 = 3500L) // RTT = 2450

        // Sample 3: Valid clean sample (RTT = 50 ms, offset = 1000)
        engine.addSample(t0 = 2000L, t1 = 3025L, t2 = 3030L, t3 = 2055L) // RTT = 50

        val best = engine.bestSample
        assertNotNull(best)
        assertEquals(50L, best.rtt)
        assertEquals(1000L, engine.estimatedOffset)
    }

    @Test
    fun testPingPongMessageHelpers() {
        val clientEngine = ClockSyncEngine()
        val serverEngine = ClockSyncEngine()

        val clientT0 = 10000L
        val pingMsg = clientEngine.createPing(clientT0)
        assertEquals(clientT0, pingMsg.t0)

        // Server handles Ping at server time 25000L
        val pongMsg = serverEngine.handlePingOnServer(pingMsg, receiveServerMonoMs = 25020L, sendServerMonoMs = 25025L)
        assertEquals(clientT0, pongMsg.t0)
        assertEquals(25020L, pongMsg.t1)
        assertEquals(25025L, pongMsg.t2)

        // Client receives Pong at client time 10050L
        val sample = clientEngine.handlePongOnClient(pongMsg, receiveClientMonoMs = 10050L)
        assertEquals(45L, sample.rtt) // (10050 - 10000) - (25025 - 25020) = 50 - 5 = 45
        assertTrue(clientEngine.sampleCount == 1)
    }

    // ── 2. PlaybackPositionInterpolator tests ────────────────────────────────

    @Test
    fun testInterpolationMonotonicAdvanceDuringPlay() {
        val interpolator = PlaybackPositionInterpolator()

        interpolator.setAnchor(
            positionMs = 30000L,
            positionAtMonoMs = 1000L,
            durationMs = 180000L,
            playbackRate = 1.0f,
            isPlaying = true,
            clockOffset = 0L
        )

        // Exactly at anchor time
        assertEquals(30000L, interpolator.interpolate(nowMonoMs = 1000L))

        // 5 seconds later
        assertEquals(35000L, interpolator.interpolate(nowMonoMs = 6000L))

        // 10.5 seconds later
        assertEquals(40500L, interpolator.interpolate(nowMonoMs = 11500L))
    }

    @Test
    fun testInterpolationWithDifferentPlaybackRates() {
        // 1.5x speed
        val pos15x = PlaybackPositionInterpolator.calculatePosition(
            positionMs = 10000L,
            positionAtMonoMs = 1000L,
            durationMs = 60000L,
            playbackRate = 1.5f,
            isPlaying = true,
            nowMonoMs = 3000L, // +2000 ms -> +3000 ms at 1.5x
            offset = 0L
        )
        assertEquals(13000L, pos15x)

        // 0.5x speed
        val pos05x = PlaybackPositionInterpolator.calculatePosition(
            positionMs = 10000L,
            positionAtMonoMs = 1000L,
            durationMs = 60000L,
            playbackRate = 0.5f,
            isPlaying = true,
            nowMonoMs = 3000L, // +2000 ms -> +1000 ms at 0.5x
            offset = 0L
        )
        assertEquals(11000L, pos05x)
    }

    @Test
    fun testInterpolationPinnedWhenPaused() {
        val interpolator = PlaybackPositionInterpolator()

        interpolator.setAnchor(
            positionMs = 42000L,
            positionAtMonoMs = 5000L,
            durationMs = 200000L,
            playbackRate = 1.0f,
            isPlaying = false, // PAUSED
            clockOffset = 0L
        )

        // Regardless of elapsed time, position must remain strictly 42000L
        assertEquals(42000L, interpolator.interpolate(nowMonoMs = 5000L))
        assertEquals(42000L, interpolator.interpolate(nowMonoMs = 15000L))
        assertEquals(42000L, interpolator.interpolate(nowMonoMs = 100000L))
    }

    @Test
    fun testDurationAndLowerBoundClamping() {
        val duration = 120000L // 2 minutes

        // Upper bound clamp: track plays beyond duration
        val pastEnd = PlaybackPositionInterpolator.calculatePosition(
            positionMs = 115000L,
            positionAtMonoMs = 1000L,
            durationMs = duration,
            isPlaying = true,
            nowMonoMs = 10000L // +9000 ms -> 124000L, should clamp to 120000L
        )
        assertEquals(120000L, pastEnd)

        // Lower bound clamp: now is before anchor (e.g. clock jitter)
        val beforeStart = PlaybackPositionInterpolator.calculatePosition(
            positionMs = 1000L,
            positionAtMonoMs = 5000L,
            durationMs = duration,
            isPlaying = true,
            nowMonoMs = 1000L // -4000 ms -> -3000L, should clamp to 0L
        )
        assertEquals(0L, beforeStart)
    }

    @Test
    fun testClockOffsetAppliedAccurately() {
        val interpolator = PlaybackPositionInterpolator()

        // Remote host mono clock is 5000 ms ahead of local clock (clockOffset = +5000)
        // Host recorded anchor: pos 20000ms at host clock 15000L
        // Corresponding local time is 15000 - 5000 = 10000L
        interpolator.setAnchor(
            positionMs = 20000L,
            positionAtMonoMs = 15000L,
            durationMs = 240000L,
            playbackRate = 1.0f,
            isPlaying = true,
            clockOffset = 5000L
        )

        // At local time 10000L, position should be exactly 20000L
        assertEquals(20000L, interpolator.interpolate(nowMonoMs = 10000L))

        // At local time 13000L (+3000 ms locally), position should be 23000L
        assertEquals(23000L, interpolator.interpolate(nowMonoMs = 13000L))
    }

    @Test
    fun testUpdateFromRemoteStateUpdate() {
        val interpolator = PlaybackPositionInterpolator()

        val update = LirixConnectMsg.RemoteStateUpdate(
            epoch = 3,
            trackId = "track-99",
            trackTitle = "Track Title",
            trackArtist = "Artist",
            durationMs = 180000L,
            positionMs = 45000L,
            positionAtMonoMs = 20000L,
            playbackRate = 1.0f,
            isPlaying = true,
            volume = 1.0f
        )

        interpolator.updateFromRemoteState(update, clockOffset = 2000L)

        assertNotNull(interpolator.anchor)
        assertEquals(45000L, interpolator.anchor?.positionMs)
        assertEquals(20000L, interpolator.anchor?.positionAtMonoMs)
        assertEquals(2000L, interpolator.anchor?.clockOffset)

        // Equivalent local time: 20000 - 2000 = 18000L
        assertEquals(45000L, interpolator.interpolate(nowMonoMs = 18000L))
        assertEquals(47000L, interpolator.interpolate(nowMonoMs = 20000L))
    }
}
