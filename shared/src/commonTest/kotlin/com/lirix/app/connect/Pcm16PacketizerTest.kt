package com.lirix.app.connect

import com.lirix.app.connect.cast.packetizePcm16Stereo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Regression tests for PCM16 Stereo Packetizer (SPEC-CON-PCM-01 v1.0, CON-PCM-QA).
 */
class Pcm16PacketizerTest {

    @Test
    fun testLossless1920BytesPacketization() {
        val input = ByteArray(1920) { (it % 251).toByte() }
        val datagrams = packetizePcm16Stereo(input, initialSeq = 0, initialTimestamp48k = 0L)

        assertEquals(2, datagrams.size)

        // Datagram 0
        assertEquals(0, datagrams[0].seq)
        assertEquals(0L, datagrams[0].timestamp48k)
        assertEquals(0, datagrams[0].flags)
        assertEquals(960, datagrams[0].opusPayload.size)

        // Datagram 1
        assertEquals(1, datagrams[1].seq)
        assertEquals(240L, datagrams[1].timestamp48k) // 960 bytes / 4 bytes per sample frame = 240
        assertEquals(0, datagrams[1].flags)
        assertEquals(960, datagrams[1].opusPayload.size)

        // Lossless byte-for-byte reconstruction
        val reconstructed = datagrams[0].opusPayload + datagrams[1].opusPayload
        assertTrue(input.contentEquals(reconstructed), "Reconstructed PCM bytes must be identical to input")
    }

    @Test
    fun testShort4BytesPacketization() {
        val input = byteArrayOf(10, 20, 30, 40)
        val datagrams = packetizePcm16Stereo(input, initialSeq = 42, initialTimestamp48k = 1000L)

        assertEquals(1, datagrams.size)
        assertEquals(42, datagrams[0].seq)
        assertEquals(1000L, datagrams[0].timestamp48k)
        assertEquals(0, datagrams[0].flags)
        assertEquals(4, datagrams[0].opusPayload.size)
        assertTrue(input.contentEquals(datagrams[0].opusPayload))
    }

    @Test
    fun testEmptyInputReturnsEmptyList() {
        val datagrams = packetizePcm16Stereo(ByteArray(0), initialSeq = 10, initialTimestamp48k = 200L)
        assertTrue(datagrams.isEmpty())
    }

    @Test
    fun testInvalidSizeNotDivisibleBy4Throws() {
        // 1193 bytes (former buggy truncation size)
        val ex1193 = assertFailsWith<IllegalArgumentException> {
            packetizePcm16Stereo(ByteArray(1193), initialSeq = 0, initialTimestamp48k = 0L)
        }
        assertEquals("PCM payload must contain complete stereo frames", ex1193.message)

        // 2 bytes
        val ex2 = assertFailsWith<IllegalArgumentException> {
            packetizePcm16Stereo(ByteArray(2), initialSeq = 0, initialTimestamp48k = 0L)
        }
        assertEquals("PCM payload must contain complete stereo frames", ex2.message)
    }

    @Test
    fun testSeqAndTimestampRollover() {
        val input = ByteArray(1920) { 1 }

        // Sequence rollover at 65535
        val datagramsSeq = packetizePcm16Stereo(input, initialSeq = 65535, initialTimestamp48k = 0L)
        assertEquals(2, datagramsSeq.size)
        assertEquals(65535, datagramsSeq[0].seq)
        assertEquals(0, datagramsSeq[1].seq)

        // Timestamp modulo 2^32 rollover
        // 4294967200L + 240 = 4294967440L -> 4294967440 % 2^32 = 144L
        val datagramsTs = packetizePcm16Stereo(input, initialSeq = 100, initialTimestamp48k = 4294967200L)
        assertEquals(2, datagramsTs.size)
        assertEquals(4294967200L, datagramsTs[0].timestamp48k)
        assertEquals(144L, datagramsTs[1].timestamp48k)
    }

    @Test
    fun testInvalidPreconditionsThrow() {
        assertFailsWith<IllegalArgumentException> {
            packetizePcm16Stereo(ByteArray(4), initialSeq = -1, initialTimestamp48k = 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            packetizePcm16Stereo(ByteArray(4), initialSeq = 65536, initialTimestamp48k = 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            packetizePcm16Stereo(ByteArray(4), initialSeq = 0, initialTimestamp48k = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            packetizePcm16Stereo(ByteArray(4), initialSeq = 0, initialTimestamp48k = 4294967296L)
        }
    }
}
