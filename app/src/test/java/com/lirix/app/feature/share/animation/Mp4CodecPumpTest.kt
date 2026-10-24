package com.lirix.app.feature.share.animation

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class Mp4CodecPumpTest {
    private class Fake : Mp4CodecIo {
        var pressure = 0
        var endlessPressure = false
        var omitEos = false
        var omitFrames = false
        var stopFailure = false
        var released = 0
        var stopped = false
        val queued = mutableListOf<Pair<Long, Boolean>>()
        val output = ArrayDeque<Mp4Output>()
        override fun dequeueInput(timeoutUs: Long): Int = if (endlessPressure || pressure-- > 0) -1 else 0
        override fun queueInput(index: Int, presentationTimeUs: Long, eos: Boolean) {
            queued += presentationTimeUs to eos
            if (eos && !omitEos) output += Mp4Output(eos = true)
            else if (!eos && !omitFrames) output += Mp4Output(samples = 1)
        }
        override fun drainOutput(timeoutUs: Long) = if (output.isEmpty()) null else output.removeFirst()
        override fun finishMuxer() { if (stopFailure) error("muxer stop failed"); stopped = true }
        override fun release() { released++ }
    }
    private fun pump(fake: Fake, active: () -> Unit = {}): Mp4CodecPump {
        var now = 0L
        return Mp4CodecPump(fake, 24, active, { now.also { now += 1_000_000 } }, 100_000_000)
    }
    @Test fun delayedInputQueuesEachFrameExactlyOnceWithContiguousTimestamps() {
        val fake = Fake().apply { pressure = 12 }
        val pump = pump(fake)
        var writes = 0
        repeat(3) { pump.submit { writes++ } }
        pump.finish(); pump.finish()
        assertEquals(3, writes)
        assertEquals(listOf(0L, 41666L, 83333L), fake.queued.filterNot { it.second }.map { it.first })
        assertEquals(3L, pump.submittedFrames)
        assertTrue(fake.stopped); assertEquals(1, fake.released)
    }
    @Test fun permanentPressureFailsWithoutCountingOrWritingFrame() {
        val fake = Fake().apply { endlessPressure = true }
        val pump = pump(fake)
        assertThrows(IllegalStateException::class.java) { pump.submit { fail("No input available") } }
        assertEquals(0L, pump.submittedFrames); assertEquals(1, fake.released)
    }
    @Test fun eosIsRetriedUnderPressureAndMissingOutputEosFails() {
        val fake = Fake(); val pump = pump(fake)
        pump.submit {}; fake.pressure = 5; fake.omitEos = true
        assertThrows(IllegalStateException::class.java) { pump.finish() }
        assertTrue(fake.queued.last().second); assertFalse(fake.stopped); assertEquals(1, fake.released)
    }
    @Test fun frameLossCannotBeReportedAsSuccess() {
        val fake = Fake().apply { omitFrames = true }; val pump = pump(fake)
        pump.submit {}
        assertThrows(IllegalStateException::class.java) { pump.finish() }
        assertFalse(fake.stopped); assertEquals(1, fake.released)
    }
    @Test fun finalizeFailurePropagatesAndReleasesExactlyOnce() {
        val fake = Fake().apply { stopFailure = true }; val pump = pump(fake)
        pump.submit {}
        assertEquals("muxer stop failed", assertThrows(IllegalStateException::class.java) { pump.finish() }.message)
        pump.abort(); assertEquals(1, fake.released)
    }
    @Test fun cancellationDuringBackpressurePropagatesAndReleases() {
        val fake = Fake().apply { endlessPressure = true }; var checks = 0
        val pump = pump(fake) { if (++checks > 8) throw CancellationException("cancelled") }
        assertThrows(CancellationException::class.java) { pump.submit {} }
        assertEquals(0L, pump.submittedFrames); assertEquals(1, fake.released)
    }
    @Test fun failedInputWriteDoesNotQueueOrAdvanceFrame() {
        val fake = Fake(); val pump = pump(fake)
        assertThrows(IllegalArgumentException::class.java) { pump.submit { throw IllegalArgumentException("bad image plane") } }
        assertTrue(fake.queued.isEmpty()); assertEquals(0L, pump.submittedFrames)
        assertEquals(1, fake.released)
        assertThrows(IllegalStateException::class.java) { pump.finish() }
    }
}
