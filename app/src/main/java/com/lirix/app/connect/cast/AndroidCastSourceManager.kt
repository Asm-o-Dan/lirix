package com.lirix.app.connect.cast

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import com.lirix.app.connect.proto.CastCodec
import com.lirix.app.connect.proto.ConnectMessageCodec
import com.lirix.app.connect.proto.Envelope
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.transport.ConnectTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Log

/**
 * AndroidCastSourceManager: Управление трансляцией системного аудиопотока Android на удалённые узлы (SPEC-CONNECT-01 §4.7, TASK-CON-CAST-11).
 *
 * Возможности:
 * 1. Инициация системного диалога MediaProjectionManager.createScreenCaptureIntent().
 * 2. Предупреждение о DRM-ограничениях [DRM_WARNING_MESSAGE].
 * 3. Активация трансляции через управляющий поток bi-stream (SetAudioCast).
 * 4. Захват аудио через [AndroidAudioCaptureEngine] и нарезка на фреймы по 10 мс (1920 байт).
 * 5. Формирование QUIC-датаграмм [CastDatagram] с кодированием через [CastDatagramCodec] и отправкой в [ConnectTransport.sendDatagram].
 * 6. Мониторинг отчётов качества [LirixConnectMsg.CastStatus] (задержка приёмника, потери пакетов).
 */
class AndroidCastSourceManager(
    val audioCaptureEngine: AndroidAudioCaptureEngine = AndroidAudioCaptureEngine(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _currentLatencyMs = MutableStateFlow(0)
    val currentLatencyMs: StateFlow<Int> = _currentLatencyMs.asStateFlow()

    private val _currentLossPct = MutableStateFlow(0.0f)
    val currentLossPct: StateFlow<Float> = _currentLossPct.asStateFlow()

    private val _sentDatagramCount = MutableStateFlow(0L)
    val sentDatagramCount: StateFlow<Long> = _sentDatagramCount.asStateFlow()

    private val _activeDeviceName = MutableStateFlow<String?>(null)
    val activeDeviceName: StateFlow<String?> = _activeDeviceName.asStateFlow()

    private var activeTransport: ConnectTransport? = null
    private var incomingMessagesJob: Job? = null
    private var senderJob: Job? = null
    private var stopMessageJob: Job? = null
    private var outgoingChunks: Channel<AudioChunk>? = null
    var mediaProjection: MediaProjection? = null
        private set

    /**
     * Привязка MediaProjection токена от Foreground Service.
     */
    fun attachMediaProjection(projection: MediaProjection?, deviceName: String? = null) {
        this.mediaProjection = projection
        if (deviceName != null) {
            _activeDeviceName.value = deviceName
        }
    }

    /**
     * Запуск сессии трансляции Audio Cast на подключённый узел через [transport].
     * LAN PCM режим (SPEC-CON-PCM-01 v1.0, CON-PCM-002):
     * Объявляет PCM_S16LE, 48 кГц, 2 канала, битрейт 1536 кбит/с.
     * Параметр [bitrateKbps] сохранён для обратной совместимости API.
     */
    suspend fun startCast(
        transport: ConnectTransport,
        deviceName: String,
        projection: MediaProjection? = null,
        bitrateKbps: Int = 192
    ): Boolean {
        stopMessageJob?.join()
        stopMessageJob = null
        if (_isCasting.value) return true

        val targetProjection = projection ?: this.mediaProjection
        activeTransport = transport
        _activeDeviceName.value = deviceName
        mediaProjection = targetProjection
        _sentDatagramCount.value = 0L

        // Отправка управляющего сообщения на запуск стрима на стороне приёмника (PCM_S16LE, 1536 kbps)
        val setCastMsg = LirixConnectMsg.SetAudioCast(
            enabled = true,
            codec = CastCodec.PCM_S16LE,
            sampleRate = 48000,
            channels = 2,
            bitrateKbps = 1536
        )
        val envelope = Envelope(
            msgId = System.currentTimeMillis(),
            sentAtMonoMs = System.currentTimeMillis(),
            body = setCastMsg
        )
        try {
            transport.sendControlMessage(ConnectMessageCodec.encode(envelope))
        } catch (e: Exception) {
            activeTransport = null
            _activeDeviceName.value = null
            mediaProjection = null
            if (e is CancellationException) throw e
            Log.e("LirixCast", "control-send-failed", e)
            return false
        }

        // Подписка на входящие отчеты CastStatus от удаленного узла
        incomingMessagesJob = scope.launch {
            try {
                transport.incomingControlMessages().collect { frameBytes ->
                    try {
                        val env = ConnectMessageCodec.decode(frameBytes)
                        val body = env.body
                        if (body is LirixConnectMsg.CastStatus) {
                            handleCastStatus(body)
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("LirixCast", "control-decode-failed", e)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("LirixCast", "control-stream-closed", e)
            }
        }

        // Создаем bounded Channel и единственный sender worker до запуска capture (устранение гонки)
        val channel = Channel<AudioChunk>(capacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        outgoingChunks = channel

        senderJob = scope.launch {
            var currentSeq = 0
            try {
                for (chunk in channel) {
                    val initialTs = chunk.timestampSample and 0xFFFFFFFFL
                    val datagrams = try {
                        packetizePcm16Stereo(chunk.pcmData, currentSeq, initialTs)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("LirixCast", "packetize-failed", e)
                        continue
                    }

                    for (datagram in datagrams) {
                        try {
                            val encoded = CastDatagramCodec.encode(datagram)
                            transport.sendDatagram(encoded)
                            _sentDatagramCount.value += 1
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.e("LirixCast", "datagram-send-failed", e)
                        }
                        currentSeq = (datagram.seq + 1) % 65536
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("LirixCast", "sender-worker-error", e)
            }
        }

        // Запуск захвата звука (callback выполняет только неблокирующий trySend)
        var captureCancellation: CancellationException? = null
        val captureStarted = try {
            audioCaptureEngine.startCapture(targetProjection) { chunk -> channel.trySend(chunk) }
        } catch (error: CancellationException) {
            captureCancellation = error
            false
        }

        if (captureStarted) {
            _isCasting.value = true
        } else {
            Log.e("LirixCast", "capture-start-failed", null)
            incomingMessagesJob?.cancel()
            incomingMessagesJob = null
            senderJob?.cancel()
            senderJob = null
            outgoingChunks?.close()
            outgoingChunks = null

            val tr = activeTransport
            activeTransport = null
            _activeDeviceName.value = null
            mediaProjection = null

            if (tr != null) {
                stopMessageJob = scope.launch {
                    try {
                        val setCastOff = LirixConnectMsg.SetAudioCast(
                            enabled = false,
                            codec = CastCodec.PCM_S16LE,
                            sampleRate = 48000,
                            channels = 2,
                            bitrateKbps = 1536
                        )
                        val env = Envelope(
                            msgId = System.currentTimeMillis(),
                            sentAtMonoMs = System.currentTimeMillis(),
                            body = setCastOff
                        )
                        tr.sendControlMessage(ConnectMessageCodec.encode(env))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("LirixCast", "start-failure-cleanup-send-failed", e)
                    }
                }
            }
        }

        captureCancellation?.let { throw it }
        return captureStarted
    }

    /**
     * Обработка телеметрии качества CastStatus от приёмника.
     */
    fun handleCastStatus(status: LirixConnectMsg.CastStatus) {
        _currentLatencyMs.value = status.receiverLatencyMs
        _currentLossPct.value = status.lossPct
    }

    /**
     * Остановка трансляции системного аудио Cast.
     */
    fun stopCast() {
        val wasCasting = _isCasting.value
        _isCasting.value = false

        // Обязательный cleanup каналов и корутин даже если isCasting было false
        outgoingChunks?.close()
        outgoingChunks = null

        val previousSender = senderJob
        previousSender?.cancel()
        senderJob = null

        try {
            audioCaptureEngine.stopCapture()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("LirixCast", "stop-capture-failed", e)
        }

        incomingMessagesJob?.cancel()
        incomingMessagesJob = null

        val transport = activeTransport
        activeTransport = null
        _activeDeviceName.value = null
        mediaProjection = null

        if (transport != null && wasCasting) {
            stopMessageJob = scope.launch {
                try {
                    previousSender?.join()
                    val setCastOff = LirixConnectMsg.SetAudioCast(
                        enabled = false,
                        codec = CastCodec.PCM_S16LE,
                        sampleRate = 48000,
                        channels = 2,
                        bitrateKbps = 1536
                    )
                    val envelope = Envelope(
                        msgId = System.currentTimeMillis(),
                        sentAtMonoMs = System.currentTimeMillis(),
                        body = setCastOff
                    )
                    transport.sendControlMessage(ConnectMessageCodec.encode(envelope))
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("LirixCast", "stop-control-send-failed", e)
                }
            }
        }
    }

    /**
     * Принудительная синхронная/suspend остановка.
     */
    suspend fun stopCastSuspend() {
        stopCast()
        stopMessageJob?.join()
    }

    companion object {
        /**
         * Понятное системное предупреждение о DRM-ограничениях для пользователей.
         */
        const val DRM_WARNING_MESSAGE =
            "Внимание: Некоторые защищенные приложения (Netflix, Spotify, Кинопоиск и др.) могут блокировать захват звука из-за DRM-политик Android. Звук из незащищенных приложений и браузеров транслируется без ограничений."

        /**
         * Создание системного интента запроса разрешения захвата экрана/аудио через MediaProjectionManager.
         */
        fun createScreenCaptureIntent(context: Context): Intent {
            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            return mpManager.createScreenCaptureIntent()
        }

        @Volatile
        private var instance: AndroidCastSourceManager? = null

        fun getInstance(): AndroidCastSourceManager {
            return instance ?: synchronized(this) {
                instance ?: AndroidCastSourceManager().also { instance = it }
            }
        }
    }
}
