package com.lirix.app.connect

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.connect.cast.AndroidAudioCaptureEngine
import com.lirix.app.connect.cast.AndroidAudioCaptureService
import com.lirix.app.connect.cast.AndroidCastSourceManager
import com.lirix.app.connect.cast.AudioCastForegroundService
import com.lirix.app.connect.cast.AudioChunk
import com.lirix.app.connect.cast.AudioRingBuffer
import com.lirix.app.connect.cast.CastAudioFormat
import com.lirix.app.connect.cast.CastDatagram
import com.lirix.app.connect.cast.CastDatagramCodec
import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.PairingPayload
import com.lirix.app.connect.proto.CastCodec
import com.lirix.app.connect.proto.ConnectMessageCodec
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.transport.ConnectState
import com.lirix.app.connect.transport.ConnectTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Тестовый симулятор ConnectTransport для проверки передачи датаграмм и управляющих фреймов.
 */
class FakeConnectTransport : ConnectTransport {
    private val _state = MutableStateFlow<ConnectState>(ConnectState.CONNECTED)
    override val state: StateFlow<ConnectState> = _state.asStateFlow()

    val sentControlMessages = mutableListOf<ByteArray>()
    val sentDatagrams = mutableListOf<ByteArray>()

    private val incomingControlFlow = MutableSharedFlow<ByteArray>(replay = 10)
    private val incomingDatagramFlow = MutableSharedFlow<ByteArray>(replay = 10)

    override suspend fun connect(target: PairingPayload): Result<Unit> = Result.success(Unit)
    override suspend fun connectPinned(endpointId: Bytes, knownAddrs: List<String>, relayUrl: String?): Result<Unit> = Result.success(Unit)
    override suspend fun disconnect(reason: String) {
        _state.value = ConnectState.DISCONNECTED
    }

    override suspend fun sendControlMessage(data: ByteArray) {
        sentControlMessages.add(data)
    }

    override fun incomingControlMessages(): Flow<ByteArray> = incomingControlFlow.asSharedFlow()

    override suspend fun sendDatagram(datagram: ByteArray) {
        sentDatagrams.add(datagram)
    }

    override fun incomingDatagrams(): Flow<ByteArray> = incomingDatagramFlow.asSharedFlow()

    suspend fun emitControlMessage(frame: ByteArray) {
        incomingControlFlow.emit(frame)
    }
}

/**
 * Unit-тесты для Android Audio Cast Source (SPEC-CONNECT-01 §4.7, TASK-CON-CAST-11).
 */
@RunWith(AndroidJUnit4::class)
class AndroidCastSourceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    // ── 1. Тесты формата и буфера нарезки фреймов ──

    @Test
    fun testCastAudioFormatCalculations() {
        val format = CastAudioFormat(sampleRate = 48000, channels = 2, bitsPerSample = 16, frameDurationMs = 10)
        assertEquals(480, format.samplesPerFrame)
        assertEquals(2, format.bytesPerSample)
        assertEquals(1920, format.frameSizeBytes) // 480 * 2 * 2 = 1920 байт
    }

    @Test
    fun testAudioRingBufferSlicing() {
        val frameSize = 1920
        val ringBuffer = AudioRingBuffer(frameSizeBytes = frameSize)

        // Пишем половинный кусок (960 байт) - фрейм ещё не готов
        val halfChunk = ByteArray(960) { 1 }
        ringBuffer.write(halfChunk)
        assertEquals(960, ringBuffer.available)
        assertEquals(null, ringBuffer.readFrame())

        // Дописываем второй кусок (1200 байт), суммарно 2160 байт (1 фрейм + 240 байт остаток)
        val secondChunk = ByteArray(1200) { 2 }
        ringBuffer.write(secondChunk)
        assertEquals(2160, ringBuffer.available)

        val frame = ringBuffer.readFrame()
        assertNotNull(frame)
        assertEquals(frameSize, frame?.size)
        assertEquals(240, ringBuffer.available)

        // Проверяем непрерывность склейки
        assertEquals(1.toByte(), frame?.get(0))
        assertEquals(1.toByte(), frame?.get(959))
        assertEquals(2.toByte(), frame?.get(960))
        assertEquals(2.toByte(), frame?.get(1919))
    }

    // ── 2. Тесты движка захвата аудио и детекции тишины (VAD / DTX) ──

    @Test
    fun testAudioCaptureEngineSilenceDetection() {
        val engine = AndroidAudioCaptureEngine(allowSyntheticInput = true)

        // 1. Нулевой сигнал (тишина) -> true
        val silencePcm = ByteArray(1920) { 0 }
        assertTrue(engine.checkIsSilent(silencePcm))

        // 2. Громкий сигнал (синусоида с высокой амплитудой) -> false
        val loudPcm = ByteArray(1920)
        val buffer = ByteBuffer.wrap(loudPcm).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until (1920 / 2)) {
            buffer.putShort((20000 * kotlin.math.sin(i * 0.1)).toInt().toShort())
        }
        assertFalse(engine.checkIsSilent(loudPcm))
    }

    @Test
    fun testAudioCaptureEngineFeedingAndChunkDelivery() {
        val engine = AndroidAudioCaptureEngine(allowSyntheticInput = true)
        val receivedChunks = mutableListOf<AudioChunk>()

        val started = engine.startCapture { chunk ->
            receivedChunks.add(chunk)
        }
        assertTrue(started)
        assertTrue(engine.isCapturing)

        // Подаем 3840 байт = ровно 2 фрейма по 1920 байт
        val testData = ByteArray(3840) { 0 }
        engine.feedRawAudio(testData)

        assertEquals(2, receivedChunks.size)
        assertEquals(1920, receivedChunks[0].pcmData.size)
        assertEquals(0L, receivedChunks[0].timestampSample)
        assertTrue(receivedChunks[0].isSilent)

        assertEquals(1920, receivedChunks[1].pcmData.size)
        assertEquals(480L, receivedChunks[1].timestampSample)
        assertTrue(receivedChunks[1].isSilent)

        engine.stopCapture()
        assertFalse(engine.isCapturing)
    }

    // ── 3. Тесты менеджера трансляции AndroidCastSourceManager ──

    @Test
    fun testStartCastSendsSetAudioCastAndStreamsDatagrams() = runTest {
        val engine = AndroidAudioCaptureEngine(allowSyntheticInput = true)
        val manager = AndroidCastSourceManager(audioCaptureEngine = engine, scope = this)
        val transport = FakeConnectTransport()

        val started = manager.startCast(
            transport = transport,
            deviceName = "Windows PC",
            bitrateKbps = 192
        )
        assertTrue(started)
        assertTrue(manager.isCasting.value)
        assertEquals("Windows PC", manager.activeDeviceName.value)

        // Проверяем отправку управляющего сообщения SetAudioCast(enabled = true)
        assertTrue(transport.sentControlMessages.isNotEmpty())
        val envelope = ConnectMessageCodec.decode(transport.sentControlMessages.first())
        assertTrue(envelope.body is LirixConnectMsg.SetAudioCast)
        val setCastMsg = envelope.body as LirixConnectMsg.SetAudioCast
        assertTrue(setCastMsg.enabled)
        assertEquals(CastCodec.PCM_S16LE, setCastMsg.codec)
        assertEquals(48000, setCastMsg.sampleRate)
        assertEquals(2, setCastMsg.channels)
        assertEquals(1536, setCastMsg.bitrateKbps)

        // Подаем 1920 байт аудио (480 stereo frames)
        val audioBytes = ByteArray(1920) { (it % 250).toByte() }
        engine.feedRawAudio(audioBytes)

        // Даем корутине worker отправить датаграммы
        kotlinx.coroutines.delay(100)

        // Должно быть отправлено ровно 2 датаграммы по 960 байт (1920 байт без потерь)
        assertEquals(2, transport.sentDatagrams.size)

        val d1 = CastDatagramCodec.decode(transport.sentDatagrams[0])
        assertEquals(0, d1.seq)
        assertEquals(0L, d1.timestamp48k)
        assertEquals(0, d1.flags)
        assertEquals(960, d1.opusPayload.size)

        val d2 = CastDatagramCodec.decode(transport.sentDatagrams[1])
        assertEquals(1, d2.seq)
        assertEquals(240L, d2.timestamp48k)
        assertEquals(0, d2.flags)
        assertEquals(960, d2.opusPayload.size)

        // Проверяем полную реконструкцию байтов без потерь
        val reconstructed = d1.opusPayload + d2.opusPayload
        assertTrue(audioBytes.contentEquals(reconstructed))

        // Подаем второй фрейм 1920 байт
        engine.feedRawAudio(audioBytes)
        kotlinx.coroutines.delay(100)

        assertEquals(4, transport.sentDatagrams.size)
        val d3 = CastDatagramCodec.decode(transport.sentDatagrams[2])
        assertEquals(2, d3.seq)
        assertEquals(480L, d3.timestamp48k)
        assertEquals(960, d3.opusPayload.size)

        val d4 = CastDatagramCodec.decode(transport.sentDatagrams[3])
        assertEquals(3, d4.seq)
        assertEquals(720L, d4.timestamp48k)
        assertEquals(960, d4.opusPayload.size)

        // Остановка
        manager.stopCast()
        assertFalse(manager.isCasting.value)
        assertFalse(engine.isCapturing)
    }

    @Test
    fun testCastStatusTelemetryHandling() = runTest {
        val manager = AndroidCastSourceManager(audioCaptureEngine = AndroidAudioCaptureEngine(allowSyntheticInput = true), scope = this)
        val transport = FakeConnectTransport()

        manager.startCast(transport = transport, deviceName = "Danya Laptop")

        val statusMsg = LirixConnectMsg.CastStatus(
            receiverLatencyMs = 65,
            bufferMs = 45,
            lossPct = 2.4f
        )
        val envelope = com.lirix.app.connect.proto.Envelope(
            msgId = 100L,
            sentAtMonoMs = 5000L,
            body = statusMsg
        )
        val frameBytes = ConnectMessageCodec.encode(envelope)

        // Симулируем приход отчёта качества от приёмника
        transport.emitControlMessage(frameBytes)
        kotlinx.coroutines.delay(50)

        assertEquals(65, manager.currentLatencyMs.value)
        assertEquals(2.4f, manager.currentLossPct.value, 0.01f)

        // Проверяем прямое обновление через handleCastStatus
        manager.handleCastStatus(LirixConnectMsg.CastStatus(receiverLatencyMs = 80, bufferMs = 60, lossPct = 0.0f))
        assertEquals(80, manager.currentLatencyMs.value)
        assertEquals(0.0f, manager.currentLossPct.value, 0.01f)

        manager.stopCast()
    }

    @Test
    fun productionCaptureWithoutProjectionCannotReportSuccess() {
        val engine = AndroidAudioCaptureEngine(allowSyntheticInput = false)
        assertFalse(engine.startCapture { error("No capture callback expected") })
        assertFalse(engine.isCapturing)
    }

    @Test
    fun immediateRestartWaitsForPreviousStopControlMessage() = runTest {
        val engine = AndroidAudioCaptureEngine(allowSyntheticInput = true)
        val manager = AndroidCastSourceManager(audioCaptureEngine = engine, scope = this)
        val transport = FakeConnectTransport()
        assertTrue(manager.startCast(transport, "PC"))
        manager.stopCast()
        assertTrue(manager.startCast(transport, "PC"))
        val controls = transport.sentControlMessages.map { ConnectMessageCodec.decode(it).body }
            .filterIsInstance<LirixConnectMsg.SetAudioCast>()
        assertEquals(listOf(true, false, true), controls.map { it.enabled })
        manager.stopCastSuspend()
    }

    @Test
    fun retryWaitsForCleanupAfterFailedCaptureStart() = runTest {
        val manager = AndroidCastSourceManager(
            audioCaptureEngine = AndroidAudioCaptureEngine(allowSyntheticInput = false), scope = this
        )
        val delegate = FakeConnectTransport()
        val offStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        val releaseOff = kotlinx.coroutines.CompletableDeferred<Unit>()
        val events = mutableListOf<Boolean>()
        val transport = object : ConnectTransport by delegate {
            override suspend fun sendControlMessage(data: ByteArray) {
                val body = ConnectMessageCodec.decode(data).body as LirixConnectMsg.SetAudioCast
                if (!body.enabled && !offStarted.isCompleted) {
                    offStarted.complete(Unit)
                    releaseOff.await()
                }
                events.add(body.enabled)
            }
        }
        assertFalse(manager.startCast(transport, "PC"))
        offStarted.await()
        val retry = async { manager.startCast(transport, "PC") }
        runCurrent()
        assertEquals(listOf(true), events)
        releaseOff.complete(Unit)
        assertFalse(retry.await())
        manager.stopCastSuspend()
        assertEquals(listOf(true, false, true, false), events)
    }

    // ── 4. Тесты системных интентов, предупреждения DRM и сервиса ──

    @Test
    fun testDrmWarningMessageAndIntentCreation() {
        assertNotNull(AndroidCastSourceManager.DRM_WARNING_MESSAGE)
        assertTrue(AndroidCastSourceManager.DRM_WARNING_MESSAGE.contains("DRM"))
        assertTrue(AndroidCastSourceManager.DRM_WARNING_MESSAGE.contains("Netflix"))

        val intent = AndroidCastSourceManager.createScreenCaptureIntent(context)
        assertNotNull(intent)
    }

    @Test
    fun testForegroundServiceActionConstantsAndTypealias() {
        assertEquals("com.lirix.app.connect.cast.ACTION_START_CAST", AudioCastForegroundService.ACTION_START_CAST)
        assertEquals("com.lirix.app.connect.cast.ACTION_STOP_CAST", AudioCastForegroundService.ACTION_STOP_CAST)
        assertEquals("extra_device_name", AudioCastForegroundService.EXTRA_DEVICE_NAME)

        // Проверяем эквивалентность typealias
        val serviceClass = AndroidAudioCaptureService::class.java
        assertEquals(AudioCastForegroundService::class.java, serviceClass)
    }
}
