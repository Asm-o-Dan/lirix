package com.lirix.app.connect.cast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.lirix.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Параметры системного аудиозахвата Android Cast (SPEC-CONNECT-01 §4.7, TASK-CON-CAST-11).
 * Формат по умолчанию: 48 кГц, 16 бит PCM, 2 канала (стерео), нарезка фреймов по 10 мс (1920 байт).
 */
data class CastAudioFormat(
    val sampleRate: Int = 48000,
    val channels: Int = 2,
    val bitsPerSample: Int = 16,
    val frameDurationMs: Int = 10
) {
    val samplesPerFrame: Int = (sampleRate * frameDurationMs) / 1000 // 480 сэмплов при 10 мс
    val bytesPerSample: Int = bitsPerSample / 8 // 2 байта
    val frameSizeBytes: Int = samplesPerFrame * channels * bytesPerSample // 1920 байт
}

/**
 * Аудиофрейм захваченного системного звука.
 */
data class AudioChunk(
    val format: CastAudioFormat,
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
 * Низколатентный кольцевой буфер для нарезки непрерывного аудиопотока на фреймы фиксированного размера (1920 байт).
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

            // При переполнении буфера сбрасываем старые данные для сохранения ультранизкой задержки
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
 * Движок захвата системного аудио Android (AudioPlaybackCapture, API 29+).
 */
class AndroidAudioCaptureEngine(
    val format: CastAudioFormat = CastAudioFormat(),
    private val allowSyntheticInput: Boolean = false
) {
    companion object {
        const val SILENCE_RMS_THRESHOLD = 0.002f
    }

    private var _isCapturing = false
    val isCapturing: Boolean get() = _isCapturing

    val isAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private val ringBuffer = AudioRingBuffer(format.frameSizeBytes)
    private var chunkCallback: ((AudioChunk) -> Unit)? = null
    private var sampleTimestamp: Long = 0L
    private var workerJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Подача сырых PCM-данных (для работы AudioRecord или детерминированного тестирования).
     */
    fun feedRawAudio(pcmData: ByteArray, offset: Int = 0, length: Int = pcmData.size) {
        if (!_isCapturing) return
        ringBuffer.write(pcmData, offset, length)
        processAvailableFrames()
    }

    /**
     * Запуск захвата аудио с опциональным MediaProjection токеном.
     */
    fun startCapture(
        mediaProjection: MediaProjection? = null,
        onChunkCaptured: (AudioChunk) -> Unit
    ): Boolean {
        if (_isCapturing) return true
        sampleTimestamp = 0L
        ringBuffer.clear()
        chunkCallback = onChunkCaptured

        // Synthetic input is an explicit test seam, never a production fallback.
        if (allowSyntheticInput) {
            _isCapturing = true
            return true
        }
        if (!isAvailable || mediaProjection == null) {
            chunkCallback = null
            Log.e("LirixCast", "stage=capture-start missing MediaProjection or unsupported Android version")
            return false
        }

        var createdRecord: AudioRecord? = null
        try {
            val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val audioFormat = android.media.AudioFormat.Builder()
                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(format.sampleRate)
                .setChannelMask(android.media.AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val minBufferSize = AudioRecord.getMinBufferSize(
                format.sampleRate, android.media.AudioFormat.CHANNEL_IN_STEREO,
                android.media.AudioFormat.ENCODING_PCM_16BIT
            )
            check(minBufferSize > 0) { "Unsupported audio capture format" }
            val record = try {
                AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(captureConfig)
                    .setAudioFormat(audioFormat)
                    .setBufferSizeInBytes(maxOf(minBufferSize, format.frameSizeBytes * 4))
                    .build()
            } catch (denied: SecurityException) {
                chunkCallback = null
                Log.e("LirixCast", "stage=capture-start microphone permission denied", denied)
                return false
            }
            createdRecord = record
            check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord not initialized" }
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "AudioRecord not recording" }
            audioRecord = record
            _isCapturing = true
            workerJob = scope.launch {
                val readBuffer = ByteArray(format.frameSizeBytes)
                while (isActive && _isCapturing) {
                    val bytesRead = record.read(readBuffer, 0, readBuffer.size)
                    when {
                        bytesRead > 0 -> {
                            ringBuffer.write(readBuffer, 0, bytesRead)
                            processAvailableFrames()
                        }
                        bytesRead == 0 -> delay(2)
                        else -> {
                            Log.e("LirixCast", "stage=capture-read AudioRecord error code=" + bytesRead)
                            stopCapture()
                        }
                    }
                }
            }
            Log.i("LirixCast", "stage=capture-started sampleRate=48000 channels=2 bits=16")
            return true
        } catch (error: Exception) {
            _isCapturing = false
            audioRecord = null
            chunkCallback = null
            try { createdRecord?.stop() } catch (_: Exception) {}
            try { createdRecord?.release() } catch (_: Exception) {}
            if (error is kotlinx.coroutines.CancellationException) throw error
            Log.e("LirixCast", "stage=capture-start failed", error)
            return false
        }
    }

    /**
     * Остановка захвата аудио.
     */
    fun stopCapture() {
        _isCapturing = false
        workerJob?.cancel()
        workerJob = null

        try { audioRecord?.stop() } catch (error: Exception) {
            Log.e("LirixCast", "stage=capture-stop failed", error)
        }
        try { audioRecord?.release() } catch (error: Exception) {
            Log.e("LirixCast", "stage=capture-release failed", error)
        }
        audioRecord = null

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
     * Проверка на тишину по порогу RMS (для выставления DTX-флага в QUIC-датаграммах).
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
 * Foreground Service для захвата системного аудио и удержания процесса (Android 14+ compatible).
 */
class AudioCastForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_CAST -> {
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Удалённое устройство"
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                val notification = buildNotification(deviceName)

                // Android 14+ (API 34) требует указания FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                _isRunning.value = true

                // Создание MediaProjection и запуск трансляции
                if (resultCode != 0 && resultData != null) {
                    val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
                    val mediaProjection = mpManager?.getMediaProjection(resultCode, resultData)
                    AndroidCastSourceManager.getInstance().attachMediaProjection(mediaProjection, deviceName)

                    val endpointHex = intent.getStringExtra(EXTRA_ENDPOINT_HEX)
                    CoroutineScope(Dispatchers.IO).launch {
                        com.lirix.app.connect.AndroidConnectManager.getInstance(applicationContext).startAudioCastToDevice(
                            deviceName = deviceName,
                            endpointHex = endpointHex,
                            projection = mediaProjection
                        )
                    }
                }
            }
            ACTION_STOP_CAST -> {
                stopCastInternal()
            }
        }

        return START_STICKY
    }

    private fun stopCastInternal() {
        AndroidCastSourceManager.getInstance().stopCast()
        _isRunning.value = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopCastInternal()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Канал уведомлений системной трансляции аудиопотока Lirix Cast"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(deviceName: String): Notification {
        val stopIntent = Intent(this, AudioCastForegroundService::class.java).apply {
            action = ACTION_STOP_CAST
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Lirix Audio Cast")
            .setContentText("Трансляция звука на $deviceName")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Остановить",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val NOTIFICATION_ID = 2002
        const val CHANNEL_ID = "lirix_audio_cast"
        const val CHANNEL_NAME = "Lirix Audio Cast"

        const val ACTION_START_CAST = "com.lirix.app.connect.cast.ACTION_START_CAST"
        const val ACTION_STOP_CAST = "com.lirix.app.connect.cast.ACTION_STOP_CAST"
        const val EXTRA_DEVICE_NAME = "extra_device_name"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_ENDPOINT_HEX = "extra_endpoint_hex"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(
            context: Context,
            deviceName: String,
            resultCode: Int,
            data: Intent,
            endpointHex: String? = null
        ) {
            val intent = Intent(context, AudioCastForegroundService::class.java).apply {
                action = ACTION_START_CAST
                putExtra(EXTRA_DEVICE_NAME, deviceName)
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                if (endpointHex != null) {
                    putExtra(EXTRA_ENDPOINT_HEX, endpointHex)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AudioCastForegroundService::class.java).apply {
                action = ACTION_STOP_CAST
            }
            context.startService(intent)
        }
    }
}

/**
 * Псевдоним для согласованности с формулировками в задачах SDD.
 */
typealias AndroidAudioCaptureService = AudioCastForegroundService
