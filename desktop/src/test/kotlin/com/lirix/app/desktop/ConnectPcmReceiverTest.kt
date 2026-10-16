package com.lirix.app.desktop

import com.lirix.app.connect.cast.CastDatagram
import com.lirix.app.connect.cast.CastDatagramCodec
import com.lirix.app.connect.proto.CastCodec
import com.lirix.app.connect.proto.ConnectMessageCodec
import com.lirix.app.connect.proto.Envelope
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.desktop.cast.DesktopConnectServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.SourceDataLine

/**
 * Full acceptance regression tests for DesktopConnectServer LAN PCM Audio Receiver (SPEC-CON-PCM-01 v1.0, CON-PCM-QA..007).
 */
class ConnectPcmReceiverTest {

    class MockSourceDataLineHandler : InvocationHandler {
        @Volatile var isOpen = false
        @Volatile var isRunning = false
        val writtenBytes = ByteArrayOutputStream()
        @Volatile var writeException: Exception? = null
        @Volatile var openException: Exception? = null
        @Volatile var startException: Exception? = null
        @Volatile var stopException: Exception? = null
        @Volatile var flushException: Exception? = null
        @Volatile var partialWriteSize: Int? = null
        val closeCalls = java.util.concurrent.atomic.AtomicInteger()
        val writeCalls = java.util.concurrent.atomic.AtomicInteger()

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            return when (method.name) {
                "open" -> {
                    openException?.let { throw it }
                    isOpen = true
                    null
                }
                "start" -> {
                    startException?.let { throw it }
                    isRunning = true
                    null
                }
                "stop" -> {
                    stopException?.let { throw it }
                    isRunning = false
                    null
                }
                "flush" -> { flushException?.let { throw it }; null }
                "close" -> {
                    closeCalls.incrementAndGet()
                    isOpen = false
                    isRunning = false
                    null
                }
                "isOpen" -> isOpen
                "isActive", "isRunning" -> isRunning
                "isControlSupported" -> false
                "write" -> {
                    writeCalls.incrementAndGet()
                    writeException?.let { throw it }
                    val b = args?.get(0) as ByteArray
                    val off = args[1] as Int
                    val len = args[2] as Int
                    val toWrite = partialWriteSize?.let { minOf(it, len) } ?: len
                    if (toWrite > 0) synchronized(writtenBytes) { writtenBytes.write(b, off, toWrite) }
                    toWrite
                }
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.get(0)
                "toString" -> "MockSourceDataLine"
                else -> null
            }
        }

        fun createProxy(): SourceDataLine {
            return Proxy.newProxyInstance(
                SourceDataLine::class.java.classLoader,
                arrayOf(SourceDataLine::class.java),
                this
            ) as SourceDataLine
        }
    }

    private fun findFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }

    @Test
    fun testOpenAudioLineValidAndInvalidParameters() {
        val mockHandler = MockSourceDataLineHandler()
        val server = DesktopConnectServer(
            audioLineFactory = { mockHandler.createProxy() }
        )

        // Valid 48000 / 2
        val openOk = server.openAudioLine(sampleRate = 48000, channels = 2)
        assertTrue(openOk)
        assertTrue(mockHandler.isOpen)
        assertTrue(mockHandler.isRunning)

        // Invalid sampleRate 44100 -> rejected
        val openBadRate = server.openAudioLine(sampleRate = 44100, channels = 2)
        assertFalse(openBadRate)

        // Invalid channels 1 -> rejected
        val openBadChannels = server.openAudioLine(sampleRate = 48000, channels = 1)
        assertFalse(openBadChannels)

        // Line exception on open
        mockHandler.openException = RuntimeException("Sound card unavailable")
        val openError = server.openAudioLine(48000, 2)
        assertFalse(openError)
    }

    @Test
    fun testWritePcmValidAndInvalidSizes() {
        val mockHandler = MockSourceDataLineHandler()
        val server = DesktopConnectServer(
            audioLineFactory = { mockHandler.createProxy() }
        )

        // Before line is open, writePcm does nothing
        val testBytes960 = ByteArray(960) { (it % 100).toByte() }
        server.writePcm(testBytes960)
        assertEquals(0, mockHandler.writtenBytes.size())

        // Open audio line
        server.openAudioLine(48000, 2)
        // Simulate playing state
        val playingField = DesktopConnectServer::class.java.getDeclaredField("_isAudioCastPlaying")
        playingField.isAccessible = true
        (playingField.get(server) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>).value = true

        // Invalid size (1193 bytes, not divisible by 4) -> rejected without writing
        val testBytes1193 = ByteArray(1193) { 1 }
        server.writePcm(testBytes1193)
        assertEquals(0, mockHandler.writtenBytes.size())

        // Valid size (960 bytes) -> written completely
        server.writePcm(testBytes960)
        assertEquals(960, mockHandler.writtenBytes.size())
        assertTrue(testBytes960.contentEquals(mockHandler.writtenBytes.toByteArray()))

        // Partial writes handling: mock writes only 240 bytes per call
        mockHandler.writtenBytes.reset()
        mockHandler.partialWriteSize = 240
        server.writePcm(testBytes960)
        // Loop must have continued until all 960 bytes were written
        assertEquals(960, mockHandler.writtenBytes.size())
        assertTrue(testBytes960.contentEquals(mockHandler.writtenBytes.toByteArray()))

        // Output error handling: exception on write closes line and resets playing
        mockHandler.writeException = java.io.IOException("Device buffer error")
        server.writePcm(testBytes960)
        assertFalse(server.isAudioCastPlaying.value)
    }

}
