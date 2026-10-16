package com.lirix.app.desktop.cast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Конфигурация параметров системного аудиозахвата (SPEC-CONNECT-01 §4.7).
 */
data class AudioFormat(
    val sampleRate: Int = 48000,
    val channels: Int = 2,
    val bitsPerSample: Int = 16,
    val frameDurationMs: Int = 10 // 10 мс или 20 мс
) {
    val samplesPerFrame: Int = (sampleRate * frameDurationMs) / 1000
    val bytesPerSample: Int = bitsPerSample / 8
    val frameSizeBytes: Int = samplesPerFrame * channels * bytesPerSample
}

/**
 * Аудиофрейм, готовый к кодированию или упаковке в QUIC-датаграмму.
 */
data class AudioChunk(
    val format: AudioFormat,
    val pcmData: ByteArray,
    val timestampSample: Long,
    val isSilent: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioChunk) return false
        return format == other.format &&
                timestampSample == other.timestampSample &&
                isSilent == other.isSilent &&
                pcmData.contentEquals(other.pcmData)
    }

    override fun hashCode(): Int {
        var result = format.hashCode()
        result = 31 * result + pcmData.contentHashCode()
        result = 31 * result + timestampSample.hashCode()
        result = 31 * result + isSilent.hashCode()
        return result
    }
}

/**
 * Абстрактный движок захвата аудио.
 */
interface AudioCaptureEngine {
    val format: AudioFormat
    val isCapturing: Boolean
    val isAvailable: Boolean

    fun startCapture(onChunkCaptured: (AudioChunk) -> Unit): Boolean
    fun stopCapture()
}

/**
 * Низколатентный кольцевой буфер для нарезки произвольного аудиопотока на фиксированные фреймы (10–20 мс).
 */
class AudioRingBuffer(
    val frameSizeBytes: Int,
    val maxCapacityBytes: Int = frameSizeBytes * 16
) {
    private val buffer = ByteArray(maxCapacityBytes)
    private var writePos = 0
    private var readPos = 0
    private var availableBytes = 0
    private val lock = Any()

    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size) {
        synchronized(lock) {
            var toWrite = length
            var srcOffset = offset

            // Если буфер переполнен, сбрасываем старые данные для минимизации задержки
            if (availableBytes + toWrite > maxCapacityBytes) {
                val overflow = (availableBytes + toWrite) - maxCapacityBytes
                readPos = (readPos + overflow) % maxCapacityBytes
                availableBytes -= overflow
            }

            while (toWrite > 0) {
                val chunk = minOf(toWrite, maxCapacityBytes - writePos)
                System.arraycopy(data, srcOffset, buffer, writePos, chunk)
                writePos = (writePos + chunk) % maxCapacityBytes
                srcOffset += chunk
                toWrite -= chunk
                availableBytes += chunk
            }
        }
    }

    fun readFrame(): ByteArray? {
        synchronized(lock) {
            if (availableBytes < frameSizeBytes) return null
            val frame = ByteArray(frameSizeBytes)
            val chunk1 = minOf(frameSizeBytes, maxCapacityBytes - readPos)
            System.arraycopy(buffer, readPos, frame, 0, chunk1)
            if (chunk1 < frameSizeBytes) {
                val chunk2 = frameSizeBytes - chunk1
                System.arraycopy(buffer, 0, frame, chunk1, chunk2)
            }
            readPos = (readPos + frameSizeBytes) % maxCapacityBytes
            availableBytes -= frameSizeBytes
            return frame
        }
    }

    fun clear() {
        synchronized(lock) {
            writePos = 0
            readPos = 0
            availableBytes = 0
        }
    }

    val available: Int get() = synchronized(lock) { availableBytes }
}

/**
 * WindowsWasapiAudioCapture: Захват системного звука Windows в режиме Loopback (SPEC §4.7).
 *
 * Особенности:
 * 1. Формат стерео 48 кГц, 16 бит PCM.
 * 2. Низколатентная нарезка через кольцевой буфер по 10/20 мс.
 * 3. Автоматическое определение тишины (VAD/DTX) по порогу среднеквадратичной амплитуды (RMS).
 * 4. Поддержка инъекции тестового аудиопотока через [feedRawAudio] для детерминированного тестирования.
 */
class WindowsWasapiAudioCapture(
    override val format: AudioFormat = AudioFormat(),
    private val osName: String = System.getProperty("os.name") ?: ""
) : AudioCaptureEngine {

    companion object {
        const val SILENCE_RMS_THRESHOLD = 0.002f
    }

    private var _isCapturing = false
    override val isCapturing: Boolean get() = _isCapturing

    override val isAvailable: Boolean
        get() = osName.lowercase().contains("win")

    private val ringBuffer = AudioRingBuffer(format.frameSizeBytes)
    private var chunkCallback: ((AudioChunk) -> Unit)? = null
    private var sampleTimestamp: Long = 0L
    private var workerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Подача сырых PCM байтов в движок (из WASAPI Loopback либо симулятора).
     */
    fun feedRawAudio(pcmData: ByteArray, offset: Int = 0, length: Int = pcmData.size) {
        if (!_isCapturing) return
        ringBuffer.write(pcmData, offset, length)
        processAvailableFrames()
    }

    override fun startCapture(onChunkCaptured: (AudioChunk) -> Unit): Boolean {
        if (!isAvailable && osName.isNotBlank() && !osName.lowercase().contains("win")) {
            return false
        }
        if (_isCapturing) return true

        this.chunkCallback = onChunkCaptured
        _isCapturing = true
        sampleTimestamp = 0L
        ringBuffer.clear()

        // Запуск фонового тикера для регулярной обработки фреймов при наличии данных
        workerJob = scope.launch {
            while (isActive && _isCapturing) {
                processAvailableFrames()
                delay((format.frameDurationMs / 2).coerceAtLeast(1).toLong())
            }
        }
        return true
    }

    override fun stopCapture() {
        _isCapturing = false
        workerJob?.cancel()
        workerJob = null
        chunkCallback = null
        ringBuffer.clear()
    }

    private fun processAvailableFrames() {
        val callback = chunkCallback ?: return
        while (true) {
            val frameBytes = ringBuffer.readFrame() ?: break
            val isSilent = checkIsSilent(frameBytes)
            val chunk = AudioChunk(
                format = format,
                pcmData = frameBytes,
                timestampSample = sampleTimestamp,
                isSilent = isSilent
            )
            sampleTimestamp += format.samplesPerFrame
            callback(chunk)
        }
    }

    /**
     * Проверка на тишину (RMS < SILENCE_RMS_THRESHOLD) для выставления DTX флага.
     */
    fun checkIsSilent(pcm16Bytes: ByteArray): Boolean {
        if (pcm16Bytes.isEmpty()) return true
        val buffer = ByteBuffer.wrap(pcm16Bytes).order(ByteOrder.LITTLE_ENDIAN)
        var sumSquares = 0.0
        val sampleCount = pcm16Bytes.size / 2

        for (i in 0 until sampleCount) {
            val sample = buffer.short.toFloat() / 32768.0f
            sumSquares += (sample * sample)
        }

        val rms = sqrt(sumSquares / sampleCount)
        return rms < SILENCE_RMS_THRESHOLD
    }
}

/**
 * Fallback движок для не-Windows систем или сред без аудиоустройств.
 */
class DummyAudioCaptureEngine(
    override val format: AudioFormat = AudioFormat()
) : AudioCaptureEngine {
    private var _isCapturing = false
    override val isCapturing: Boolean get() = _isCapturing
    override val isAvailable: Boolean get() = false

    override fun startCapture(onChunkCaptured: (AudioChunk) -> Unit): Boolean {
        _isCapturing = true
        return true
    }

    override fun stopCapture() {
        _isCapturing = false
    }
}
