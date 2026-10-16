package com.lirix.app.desktop

import com.lirix.app.connect.cast.CastDatagram
import com.lirix.app.connect.cast.CastDatagramCodec
import com.lirix.app.connect.cast.packetizePcm16Stereo
import com.lirix.app.connect.proto.CastCodec
import com.lirix.app.connect.proto.ConnectMessageCodec
import com.lirix.app.connect.proto.Envelope
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.desktop.cast.DesktopConnectServer
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.DataOutputStream
import java.net.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Exercise the real TCP/UDP receiver with an output seam, without a physical sound card. */
class ConnectPcmSocketTest {
    private class Harness : AutoCloseable {
        val line = ConnectPcmReceiverTest.MockSourceDataLineHandler()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = DesktopConnectServer(scope = scope, audioLineFactory = { line.createProxy() })
        val port = ServerSocket(0).use { it.localPort }
        val control: Socket
        private val udp = DatagramSocket()
        private var msgId = 0L
        init {
            server.start(port)
            check(server.isListening.value)
            control = Socket("127.0.0.1", port)
        }

        fun cast(enabled: Boolean = true, codec: CastCodec = CastCodec.PCM_S16LE, rate: Int = 48000) {
            val message = LirixConnectMsg.SetAudioCast(
                enabled = enabled, codec = codec, sampleRate = rate, channels = 2, bitrateKbps = 1536
            )
            val body = ConnectMessageCodec.encode(Envelope(msgId = ++msgId, sentAtMonoMs = 0, body = message))
            DataOutputStream(control.getOutputStream()).apply {
                write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size).array())
                write(body)
                flush()
            }
        }

        suspend fun send(payload: ByteArray) {
            val before = server.receivedDatagramCount.value
            val wire = CastDatagramCodec.encode(CastDatagram(0, 0, 0, payload))
            udp.send(DatagramPacket(wire, wire.size, InetAddress.getByName("127.0.0.1"), port))
            await { server.receivedDatagramCount.value > before }
        }

        fun written(): ByteArray = synchronized(line.writtenBytes) { line.writtenBytes.toByteArray() }
        override fun close() {
            try { control.close() } finally {
                try { udp.close() } finally { server.stop(); scope.cancel() }
            }
        }
    }

    companion object {
        private suspend fun await(predicate: () -> Boolean) = withTimeout(5000) {
            while (!predicate()) delay(5)
        }
    }

    @Test fun packetizerToActualSocketReceiverPreservesAllBytesThroughPartialWrites() = runBlocking {
        Harness().use { h ->
            h.line.partialWriteSize = 240
            h.cast()
            await { h.server.isAudioCastPlaying.value }
            val original = ByteArray(1920) { (it * 31).toByte() }
            for (packet in packetizePcm16Stereo(original, 65535, 4294967200)) h.send(packet.opusPayload)
            await { h.written().size == original.size }
            assertArrayEquals(original, h.written())
            assertEquals(8, h.line.writeCalls.get())
        }
    }

    @Test fun smallThenLargeAndMalformedDatagramsDoNotTruncateNextAudio() = runBlocking {
        Harness().use { h ->
            h.cast()
            await { h.server.isAudioCastPlaying.value }
            val small = byteArrayOf(1, 2, 3, 4)
            val large = ByteArray(960) { 7 }
            h.send(small)
            await { h.written().size == 4 }
            h.send(ByteArray(1193))
            h.send(large)
            await { h.written().size == 964 }
            assertArrayEquals(small + large, h.written())
        }
    }

    @Test fun udpBeforeStartAndAfterStopCannotOpenAudioButExplicitRestartWorks() = runBlocking {
        Harness().use { h ->
            h.send(ByteArray(960))
            assertFalse(h.line.isOpen)
            h.cast()
            await { h.server.isAudioCastPlaying.value }
            h.send(ByteArray(960))
            await { h.written().size == 960 }
            h.cast(enabled = false)
            await { !h.server.isAudioCastPlaying.value && !h.line.isOpen }
            h.send(ByteArray(960))
            assertEquals(960, h.written().size)
            assertFalse(h.line.isOpen)
            h.cast()
            await { h.server.isAudioCastPlaying.value }
            h.send(ByteArray(960))
            await { h.written().size == 1920 }
        }
    }

    @Test fun unsupportedCodecAndRateAreRejectedThroughRealControlSocket() = runBlocking {
        Harness().use { h ->
            h.cast()
            await { h.server.isAudioCastPlaying.value }
            h.cast(codec = CastCodec.OPUS)
            await { !h.server.isAudioCastPlaying.value && !h.line.isOpen }
            h.cast()
            await { h.server.isAudioCastPlaying.value }
            h.cast(rate = 44100)
            await { !h.server.isAudioCastPlaying.value && !h.line.isOpen }
        }
    }

    @Test fun writeFailureAndInvalidProgressStopCastWithoutSpinning() = runBlocking {
        for (progress in listOf<Int?>(null, 0, -1, 2)) Harness().use { h ->
            h.cast()
            await { h.server.isAudioCastPlaying.value }
            if (progress == null) h.line.writeException = IllegalStateException("Output removed")
            else h.line.partialWriteSize = progress
            h.send(ByteArray(960))
            await { !h.server.isAudioCastPlaying.value && !h.line.isOpen }
            assertEquals(1, h.line.writeCalls.get())
            assertEquals(1, h.line.closeCalls.get())
        }
    }

    @Test fun failedOpenAndStartReleaseLineEvenWhenStopAndFlushThrow() {
        for (failOnStart in listOf(false, true)) {
            val line = ConnectPcmReceiverTest.MockSourceDataLineHandler().apply {
                if (failOnStart) startException = IllegalStateException("Start failed")
                else openException = IllegalStateException("Open failed")
                stopException = IllegalStateException("Stop failed")
                flushException = IllegalStateException("Flush failed")
            }
            val server = DesktopConnectServer(audioLineFactory = { line.createProxy() })
            assertFalse(server.openAudioLine())
            assertEquals(1, line.closeCalls.get())
            assertFalse(server.isAudioCastPlaying.value)
        }
    }

    @Test fun cleanupCancellationStillClosesAndIsPropagated() {
        val line = ConnectPcmReceiverTest.MockSourceDataLineHandler()
        val server = DesktopConnectServer(audioLineFactory = { line.createProxy() })
        assertTrue(server.openAudioLine())
        line.stopException = CancellationException("Cancelled")
        line.flushException = IllegalStateException("Flush failed")
        assertThrows(CancellationException::class.java) { server.closeAudioLine() }
        assertEquals(1, line.closeCalls.get())
        server.closeAudioLine()
        assertEquals(1, line.closeCalls.get())
    }
}
