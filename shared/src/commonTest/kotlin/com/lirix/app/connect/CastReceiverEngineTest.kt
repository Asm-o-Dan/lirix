package com.lirix.app.connect

import com.lirix.app.connect.cast.AdaptiveJitterBuffer
import com.lirix.app.connect.cast.CastAudioSink
import com.lirix.app.connect.cast.CastDatagram
import com.lirix.app.connect.cast.CastDatagramCodec
import com.lirix.app.connect.cast.CastOpusDecoder
import com.lirix.app.connect.cast.CastReceiverEngine
import com.lirix.app.connect.cast.JitterBufferItem
import com.lirix.app.connect.proto.LirixConnectMsg
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CastReceiverEngineTest {

    private fun createDatagram(
        seq: Int,
        timestamp48k: Long = seq * 480L,
        flags: Int = 0,
        payloadSize: Int = 120
    ): CastDatagram {
        return CastDatagram(
            seq = seq,
            timestamp48k = timestamp48k,
            flags = flags,
            opusPayload = ByteArray(payloadSize) { (it + seq).toByte() }
        )
    }

    // ── 1. CastDatagramCodec Tests ──

    @Test
    fun testDatagramCodecRoundTrip() {
        val original = createDatagram(
            seq = 42500,
            timestamp48k = 3_000_000L,
            flags = CastDatagram.FLAG_FEC or CastDatagram.FLAG_DTX,
            payloadSize = 240
        )

        val encoded = CastDatagramCodec.encode(original)
        assertEquals(7 + 240, encoded.size)

        val decoded = CastDatagramCodec.decode(encoded)
        assertEquals(original.seq, decoded.seq)
        assertEquals(original.timestamp48k, decoded.timestamp48k)
        assertEquals(original.flags, decoded.flags)
        assertTrue(decoded.hasFec)
        assertTrue(decoded.isDtx)
        assertEquals(original, decoded)
    }

    @Test
    fun testDatagramCodecSequenceAndTimestampBoundaries() {
        val maxU16Datagram = createDatagram(seq = 65535, timestamp48k = 4294967295L)
        val encoded = CastDatagramCodec.encode(maxU16Datagram)
        val decoded = CastDatagramCodec.decode(encoded)

        assertEquals(65535, decoded.seq)
        assertEquals(4294967295L, decoded.timestamp48k)
    }

    @Test
    fun testDatagramCodecRejectsInvalidSizes() {
        // Less than header (7 bytes)
        assertFailsWith<IllegalArgumentException> {
            CastDatagramCodec.decode(ByteArray(6))
        }

        // Exceeding MTU 1200 bytes
        assertFailsWith<IllegalArgumentException> {
            val oversized = CastDatagram(
                seq = 1,
                timestamp48k = 0L,
                opusPayload = ByteArray(1201)
            )
            CastDatagramCodec.encode(oversized)
        }
    }

    // ── 2. AdaptiveJitterBuffer Tests ──

    @Test
    fun testJitterBufferReorderingAndPlayout() {
        val buffer = AdaptiveJitterBuffer(minBufferMs = 40, maxBufferMs = 80, frameDurationMs = 10)

        // Packets arrive out of order: 1, 3, 2, 4 (target buffer 40 ms = 4 packets)
        // With stable transit (arrivalMonoMs = seq * 10L), targetDelayMs remains 40 ms
        buffer.insert(createDatagram(seq = 1, timestamp48k = 480L), arrivalMonoMs = 10L)
        buffer.insert(createDatagram(seq = 3, timestamp48k = 1440L), arrivalMonoMs = 30L)
        buffer.insert(createDatagram(seq = 2, timestamp48k = 960L), arrivalMonoMs = 20L)

        // With 3 frames (30 ms < 40 ms target delay), buffer is still pre-buffering
        assertNull(buffer.popNextFrame())

        // Insert 4th frame -> reaches 40 ms threshold -> playout starts
        buffer.insert(createDatagram(seq = 4, timestamp48k = 1920L), arrivalMonoMs = 40L)

        // Frames must be popped strictly in order 1, 2, 3, 4
        val f1 = buffer.popNextFrame()
        assertIs<JitterBufferItem.Frame>(f1)
        assertEquals(1, f1.datagram.seq)

        val f2 = buffer.popNextFrame()
        assertIs<JitterBufferItem.Frame>(f2)
        assertEquals(2, f2.datagram.seq)

        val f3 = buffer.popNextFrame()
        assertIs<JitterBufferItem.Frame>(f3)
        assertEquals(3, f3.datagram.seq)

        val f4 = buffer.popNextFrame()
        assertIs<JitterBufferItem.Frame>(f4)
        assertEquals(4, f4.datagram.seq)

        // Empty buffer
        assertNull(buffer.popNextFrame())
    }

    @Test
    fun testJitterBufferDropsDuplicatesAndLatePackets() {
        val buffer = AdaptiveJitterBuffer(minBufferMs = 40)

        assertTrue(buffer.insert(createDatagram(seq = 10, timestamp48k = 4800L), arrivalMonoMs = 100L))
        // Duplicate seq 10
        assertFalse(buffer.insert(createDatagram(seq = 10, timestamp48k = 4800L), arrivalMonoMs = 105L))
        assertEquals(1L, buffer.duplicatePackets)

        // Play frame 10 (force play)
        val popped = buffer.popNextFrame(forcePlay = true)
        assertNotNull(popped)

        // Now expectedSeq is 11. An arriving packet with seq = 9 is late
        assertFalse(buffer.insert(createDatagram(seq = 9, timestamp48k = 4320L), arrivalMonoMs = 110L))
        assertEquals(1L, buffer.droppedLatePackets)
    }

    @Test
    fun testJitterBufferGeneratesLossConcealmentOnMissingPacket() {
        val buffer = AdaptiveJitterBuffer(minBufferMs = 40)

        // Packet 10 arrived, packet 11 was lost, packet 12 arrived
        buffer.insert(createDatagram(seq = 10, timestamp48k = 4800L), arrivalMonoMs = 100L)
        buffer.insert(createDatagram(seq = 12, timestamp48k = 5760L), arrivalMonoMs = 120L)

        // Pop frame 10
        val f1 = buffer.popNextFrame(forcePlay = true)
        assertIs<JitterBufferItem.Frame>(f1)
        assertEquals(10, f1.datagram.seq)

        // Next frame (11) is missing, but packet 12 is in buffer -> triggers PLC!
        val f2 = buffer.popNextFrame(forcePlay = true)
        assertIs<JitterBufferItem.LossConcealment>(f2)
        assertEquals(11, f2.expectedSeq)
        assertEquals(1L, buffer.lostPackets)

        // Next frame is 12 -> plays normally
        val f3 = buffer.popNextFrame(forcePlay = true)
        assertIs<JitterBufferItem.Frame>(f3)
        assertEquals(12, f3.datagram.seq)

        // Loss percentage: 1 lost out of (2 received + 1 lost) = 1/3 = 33.33%
        val lossPct = buffer.calculateLossPercentage()
        assertTrue(lossPct > 33.0f && lossPct < 34.0f)
    }

    @Test
    fun testJitterBufferAdaptsDelayOnHighJitter() {
        val buffer = AdaptiveJitterBuffer(minBufferMs = 40, maxBufferMs = 80)
        assertEquals(40, buffer.targetDelayMs)

        // Simulate packets with high transit jitter
        buffer.insert(createDatagram(seq = 1, timestamp48k = 0L), arrivalMonoMs = 100L)
        buffer.insert(createDatagram(seq = 2, timestamp48k = 480L), arrivalMonoMs = 150L) // +40ms jitter
        buffer.insert(createDatagram(seq = 3, timestamp48k = 960L), arrivalMonoMs = 180L)

        // Jitter increased, buffer expanded within [40, 80]
        assertTrue(buffer.measuredJitterMs > 0.0)
        assertTrue(buffer.targetDelayMs in 40..80)
    }

    // ── 3. CastReceiverEngine Tests ──

    @Test
    fun testCastReceiverEngineDecodesAndInvokesPlc() {
        var writtenSamplesCount = 0
        var volumeSet = 0.0f
        var plcCalledCount = 0

        val fakeSink = object : CastAudioSink {
            override fun writePcm(data: ShortArray, offset: Int, size: Int) {
                writtenSamplesCount += size
            }
            override fun setVolume(volume: Float) {
                volumeSet = volume
            }
        }

        val fakeDecoder = object : CastOpusDecoder {
            override fun decode(opusData: ByteArray, outPcm: ShortArray): Int {
                return 480 // 10ms at 48kHz mono/stereo
            }
            override fun decodeLossConcealment(outPcm: ShortArray): Int {
                plcCalledCount++
                return 480
            }
        }

        val engine = CastReceiverEngine(
            audioSink = fakeSink,
            opusDecoder = fakeDecoder
        )

        engine.setVolume(0.85f)
        assertEquals(0.85f, volumeSet)

        // Feed encoded datagrams: seq 100, (101 lost), seq 102
        val d100 = CastDatagramCodec.encode(createDatagram(seq = 100, timestamp48k = 48000L))
        val d102 = CastDatagramCodec.encode(createDatagram(seq = 102, timestamp48k = 48960L))

        assertTrue(engine.onDatagramReceived(d100, arrivalMonoMs = 1000L))
        assertTrue(engine.onDatagramReceived(d102, arrivalMonoMs = 1020L))

        // Process frame 100
        val p1 = engine.processNextFrame(forcePlay = true)
        assertIs<JitterBufferItem.Frame>(p1)
        assertEquals(480, writtenSamplesCount)
        assertEquals(1L, engine.totalFramesProcessed)

        // Process frame 101 (lost -> triggers PLC)
        val p2 = engine.processNextFrame(forcePlay = true)
        assertIs<JitterBufferItem.LossConcealment>(p2)
        assertEquals(960, writtenSamplesCount)
        assertEquals(1, plcCalledCount)
        assertEquals(1L, engine.totalPlcFramesGenerated)

        // Process frame 102
        val p3 = engine.processNextFrame(forcePlay = true)
        assertIs<JitterBufferItem.Frame>(p3)
        assertEquals(1440, writtenSamplesCount)
        assertEquals(2L, engine.totalFramesProcessed)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testCastReceiverEnginePeriodicCastStatusReport() = runTest {
        val reportedStatuses = mutableListOf<LirixConnectMsg.CastStatus>()

        val engine = CastReceiverEngine(
            onSendStatus = { reportedStatuses.add(it) }
        )

        // Feed packets: 4 packets (40 ms buffer)
        for (i in 1..4) {
            val d = CastDatagramCodec.encode(createDatagram(seq = i, timestamp48k = i * 480L))
            engine.onDatagramReceived(d, arrivalMonoMs = i * 10L)
        }

        engine.start(this)

        // Advance 1 second -> status report generated
        advanceTimeBy(1000L)
        runCurrent()

        assertEquals(1, reportedStatuses.size)
        val status = reportedStatuses[0]
        assertEquals(40, status.bufferMs)
        assertEquals(0.0f, status.lossPct)
        assertTrue(status.receiverLatencyMs in 40..80)

        // Advance another second
        advanceTimeBy(1000L)
        runCurrent()
        assertEquals(2, reportedStatuses.size)

        engine.stop()
    }
}
