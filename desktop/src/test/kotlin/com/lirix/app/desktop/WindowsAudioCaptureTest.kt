package com.lirix.app.desktop

import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.PairingPayload
import com.lirix.app.connect.proto.CastCodec
import com.lirix.app.connect.proto.ConnectMessageCodec
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.transport.ConnectState
import com.lirix.app.connect.transport.ConnectTransport
import com.lirix.app.desktop.cast.AudioFormat
import com.lirix.app.desktop.cast.AudioRingBuffer
import com.lirix.app.desktop.cast.CastDatagram
import com.lirix.app.desktop.cast.CastDatagramCodec
import com.lirix.app.desktop.cast.DesktopCastSourceManager
import com.lirix.app.desktop.cast.WindowsWasapiAudioCapture
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WindowsAudioCaptureTest {

    private class MockConnectTransport : ConnectTransport {
        override val state: StateFlow<ConnectState> = MutableStateFlow(ConnectState.CONNECTED)
        val sentControlMessages = mutableListOf<ByteArray>()
        val sentDatagrams = mutableListOf<ByteArray>()
        val incomingControlFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
        val incomingDatagramFlow = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)

        override suspend fun connect(target: PairingPayload): Result<Unit> = Result.success(Unit)
        override suspend fun connectPinned(endpointId: Bytes, knownAddrs: List<String>, relayUrl: String?): Result<Unit> = Result.success(Unit)
        override suspend fun disconnect(reason: String) {}

        override suspend fun sendControlMessage(data: ByteArray) {
            sentControlMessages.add(data)
        }

        override fun incomingControlMessages(): Flow<ByteArray> = incomingControlFlow.asSharedFlow()

        override suspend fun sendDatagram(datagram: ByteArray) {
            sentDatagrams.add(datagram)
        }

        override fun incomingDatagrams(): Flow<ByteArray> = incomingDatagramFlow.asSharedFlow()
    }

    @Test
    fun testAudioRingBuffer_FramingAndSlicing() {
        val frameSize = 1920 // 10ms at 48kHz, 16bit, stereo
        val ring = AudioRingBuffer(frameSizeBytes = frameSize, maxCapacityBytes = frameSize * 4)

        // Пишем данные кусками меньше размера одного фрейма (по 500 байт)
        ring.write(ByteArray(500) { 1 })
        assertNull("Неполный фрейм не должен читаться", ring.readFrame())

        ring.write(ByteArray(500) { 2 })
        assertNull("1000 байт < 1920 байт", ring.readFrame())

        ring.write(ByteArray(1000) { 3 }) // Всего записано 2000 байт
        val frame1 = ring.readFrame()
        assertNotNull("При наличии 2000 байт должен читаться фрейм 1920 байт", frame1)
        assertEquals(frameSize, frame1!!.size)
        assertEquals(80, ring.available) // Остаток: 2000 - 1920 = 80 байт
    }

    @Test
    fun testCastDatagramCodec_RoundtripAndEndianness() {
        val payload = ByteArray(240) { (it % 128).toByte() }
        val datagram = CastDatagram(
            seq = 42150,
            timestamp48k = 1920000L,
            flags = (CastDatagram.FLAG_FEC.toInt() or CastDatagram.FLAG_DTX.toInt()).toByte(),
            payload = payload
        )

        val encoded = CastDatagramCodec.encode(datagram)
        assertEquals(CastDatagram.HEADER_SIZE + payload.size, encoded.size)

        // Проверяем Big-Endian порядок байтов в заголовке
        val buf = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN)
        val decodedSeq = buf.short.toInt() and 0xFFFF
        val decodedTs = buf.int.toLong() and 0xFFFFFFFFL
        val decodedFlags = buf.get()

        assertEquals(42150, decodedSeq)
        assertEquals(1920000L, decodedTs)
        assertEquals((CastDatagram.FLAG_FEC.toInt() or CastDatagram.FLAG_DTX.toInt()).toByte(), decodedFlags)

        val decoded = CastDatagramCodec.decode(encoded)
        assertEquals(datagram.seq, decoded.seq)
        assertEquals(datagram.timestamp48k, decoded.timestamp48k)
        assertEquals(datagram.flags, decoded.flags)
        assertArrayEquals(datagram.payload, decoded.payload)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCastDatagramCodec_ExceedMtuThrows() {
        val largePayload = ByteArray(1201) // 1201 + 7 > 1200 MTU
        val datagram = CastDatagram(seq = 1, timestamp48k = 0L, payload = largePayload)
        CastDatagramCodec.encode(datagram)
    }

    @Test
    fun testSilenceDetection() {
        val capture = WindowsWasapiAudioCapture(osName = "Windows 11")

        val silentBytes = ByteArray(1920) { 0 }
        assertTrue("Нулевые сэмплы должны определяться как тишина", capture.checkIsSilent(silentBytes))

        // Генерируем тестовый синусоидальный сигнал полной амплитуды
        val activeBytes = ByteArray(1920)
        val bb = ByteBuffer.wrap(activeBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 960) {
            val sample = (30000 * kotlin.math.sin(i * 0.1)).toInt().toShort()
            bb.putShort(sample)
        }
        assertFalse("Громкий сигнал не должен определяться как тишина", capture.checkIsSilent(activeBytes))
    }

    @Test
    fun testDesktopCastSourceManager_FullFlow() = runBlocking {
        val mockTransport = MockConnectTransport()
        val capture = WindowsWasapiAudioCapture(osName = "Windows 11")
        val manager = DesktopCastSourceManager(audioCaptureEngine = capture)

        assertFalse(manager.isCasting.value)

        val started = manager.startCast(mockTransport, bitrateKbps = 192)
        assertTrue(started)
        assertTrue(manager.isCasting.value)

        // Проверяем, что отправлен управляющий конверт SetAudioCast(enabled = true)
        assertEquals(1, mockTransport.sentControlMessages.size)
        val env = ConnectMessageCodec.decode(mockTransport.sentControlMessages[0])
        val setCastMsg = env.body as LirixConnectMsg.SetAudioCast
        assertTrue(setCastMsg.enabled)
        assertEquals(CastCodec.OPUS, setCastMsg.codec)
        assertEquals(192, setCastMsg.bitrateKbps)

        // Подаём 2 полных фрейма аудиоданных (2 x 1920 = 3840 байт)
        val audioData = ByteArray(3840) { 1 }
        capture.feedRawAudio(audioData)

        // Даём корутине отправить датаграммы
        kotlinx.coroutines.delay(50L)

        assertEquals(2, mockTransport.sentDatagrams.size)
        val d1 = CastDatagramCodec.decode(mockTransport.sentDatagrams[0])
        val d2 = CastDatagramCodec.decode(mockTransport.sentDatagrams[1])

        assertEquals(0, d1.seq)
        assertEquals(0L, d1.timestamp48k)
        assertEquals(1, d2.seq)
        assertEquals(480L, d2.timestamp48k) // Смещение на 480 сэмплов (10 мс при 48 кГц)

        // Проверяем реакцию на отчёт CastStatus
        manager.handleCastStatus(LirixConnectMsg.CastStatus(receiverLatencyMs = 65, bufferMs = 40, lossPct = 1.2f))
        assertEquals(65, manager.currentLatencyMs.value)
        assertEquals(1.2f, manager.currentLossPct.value, 0.001f)

        // Остановка
        manager.stopCast()
        assertFalse(manager.isCasting.value)
        assertEquals(2, mockTransport.sentControlMessages.size) // Отправлен SetAudioCast(enabled = false)
        val stopEnv = ConnectMessageCodec.decode(mockTransport.sentControlMessages[1])
        assertFalse((stopEnv.body as LirixConnectMsg.SetAudioCast).enabled)
    }
}
