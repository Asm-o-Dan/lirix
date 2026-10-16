package com.lirix.app.connect.cast

import com.lirix.app.connect.proto.LirixConnectMsg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Интерфейс кроссплатформенного воспроизведения аудио-потока Cast (SPEC-CONNECT-01 §4.7, TASK-CON-CAST-09).
 */
interface CastAudioSink {
    fun writePcm(data: ShortArray, offset: Int, size: Int)
    fun setVolume(volume: Float)
}

/**
 * Интерфейс декодера Opus в PCM с поддержкой Packet Loss Concealment (PLC).
 */
interface CastOpusDecoder {
    /**
     * Декодирование стандартного фрейма Opus в PCM.
     */
    fun decode(opusData: ByteArray, outPcm: ShortArray): Int

    /**
     * Генерация компенсирующего аудио-фрейма при потере пакета (PLC).
     */
    fun decodeLossConcealment(outPcm: ShortArray): Int
}

/**
 * Движок приёма и воспроизведения Live Audio Cast (SPEC-CONNECT-01 §4.7, TASK-CON-CAST-09).
 *
 * Объединяет:
 * - десериализацию QUIC-датаграмм [CastDatagramCodec];
 * - адаптивный джиттер-буфер [AdaptiveJitterBuffer] с целевой задержкой 40–80 мс;
 * - восстановление потерь (PLC) и интерполяцию звука;
 * - ежесекундный сбор метрик качества [LirixConnectMsg.CastStatus] (задержка, буфер, потери).
 */
class CastReceiverEngine(
    val jitterBuffer: AdaptiveJitterBuffer = AdaptiveJitterBuffer(),
    private val audioSink: CastAudioSink? = null,
    private val opusDecoder: CastOpusDecoder? = null,
    private val timeProvider: () -> Long = { kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds },
    private val onSendStatus: (suspend (LirixConnectMsg.CastStatus) -> Unit)? = null
) {
    companion object {
        const val STATUS_REPORT_INTERVAL_MS = 1000L
        const val SAMPLES_PER_FRAME_48K_10MS = 480
    }

    private val _status = MutableStateFlow(
        LirixConnectMsg.CastStatus(
            receiverLatencyMs = jitterBuffer.targetDelayMs,
            bufferMs = 0,
            lossPct = 0.0f
        )
    )
    val status: StateFlow<LirixConnectMsg.CastStatus> = _status.asStateFlow()

    private var volume: Float = 1.0f
    private var isRunning: Boolean = false
    private var metricsJob: Job? = null
    private val pcmBuffer = ShortArray(SAMPLES_PER_FRAME_48K_10MS * 2) // Стерео 48 кГц 10 мс

    var totalFramesProcessed: Long = 0L
        private set
    var totalPlcFramesGenerated: Long = 0L
        private set

    /**
     * Приём сырой QUIC-датаграммы из транспортного канала.
     */
    fun onDatagramReceived(
        data: ByteArray,
        arrivalMonoMs: Long = timeProvider()
    ): Boolean {
        return try {
            val datagram = CastDatagramCodec.decode(data)
            jitterBuffer.insert(datagram, arrivalMonoMs)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Обработка одного шага воспроизведения фрейма (вызывается аудио-потоком / таймером каждые 10 мс).
     *
     * @return [JitterBufferItem] если фрейм был воспроизведён или сгенерирован PLC, либо null если буферизация.
     */
    fun processNextFrame(forcePlay: Boolean = false): JitterBufferItem? {
        val item = jitterBuffer.popNextFrame(forcePlay) ?: return null
        when (item) {
            is JitterBufferItem.Frame -> {
                totalFramesProcessed++
                if (opusDecoder != null && audioSink != null) {
                    val samples = opusDecoder.decode(item.datagram.opusPayload, pcmBuffer)
                    if (samples > 0) {
                        audioSink.writePcm(pcmBuffer, 0, samples)
                    }
                }
            }
            is JitterBufferItem.LossConcealment -> {
                totalPlcFramesGenerated++
                if (opusDecoder != null && audioSink != null) {
                    val samples = opusDecoder.decodeLossConcealment(pcmBuffer)
                    if (samples > 0) {
                        audioSink.writePcm(pcmBuffer, 0, samples)
                    }
                }
            }
        }
        return item
    }

    /**
     * Формирование текущего отчёта о качестве сетевого потока CastStatus.
     */
    fun generateCastStatus(): LirixConnectMsg.CastStatus {
        val currentStatus = LirixConnectMsg.CastStatus(
            receiverLatencyMs = jitterBuffer.targetDelayMs,
            bufferMs = jitterBuffer.currentBufferMs,
            lossPct = jitterBuffer.calculateLossPercentage()
        )
        _status.value = currentStatus
        return currentStatus
    }

    /**
     * Установка громкости вывода (0.0 .. 1.0).
     */
    fun setVolume(newVolume: Float) {
        volume = newVolume.coerceIn(0.0f, 1.0f)
        audioSink?.setVolume(volume)
    }

    /**
     * Запуск периодического отчёта о качестве (раз в 1 секунду) в корутине.
     */
    fun start(scope: CoroutineScope) {
        stop()
        isRunning = true
        metricsJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            while (isActive && isRunning) {
                delay(STATUS_REPORT_INTERVAL_MS)
                val currentStatus = generateCastStatus()
                onSendStatus?.invoke(currentStatus)
            }
        }
    }

    fun stop() {
        isRunning = false
        metricsJob?.cancel()
        metricsJob = null
    }

    fun reset() {
        stop()
        jitterBuffer.reset()
        totalFramesProcessed = 0L
        totalPlcFramesGenerated = 0L
        generateCastStatus()
    }
}
