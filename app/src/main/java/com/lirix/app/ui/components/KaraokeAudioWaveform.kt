package com.lirix.app.ui.components

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

data class KaraokeAudioWaveform(val peaks: List<Float>, val durationMs: Long, val name: String)

/** Real decoded PCM only. No recording, stream scraping or synthetic audio bars. */
suspend fun decodeKaraokeWaveform(context: Context, uri: Uri, progress: (Float) -> Unit = {}): KaraokeAudioWaveform = withContext(Dispatchers.IO) {
    require(uri.scheme == "content" || uri.scheme == "file") { "Выберите локальный аудиофайл" }
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    var started = false
    try {
        extractor.setDataSource(context, uri, null)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("В файле нет звуковой дорожки")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
        require(durationUs in 1..14_400_000_000L) { "Нужен аудиофайл с известной длительностью до четырёх часов" }
        val peakCount = (durationUs / 10_000L).coerceIn(1, 100_000).toInt()
        val peaks = FloatArray(peakCount)
        val codec = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
        decoder = codec
        codec.configure(format, null, null, 0)
        codec.start()
        started = true
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        var inputFinished = false
        var outputFinished = false
        var frames = 0L
        var lastProgressAt = SystemClock.elapsedRealtime()
        var reported = -1
        val info = MediaCodec.BufferInfo()
        while (!outputFinished) {
            currentCoroutineContext().ensureActive()
            check(SystemClock.elapsedRealtime() - lastProgressAt < 15_000) { "Декодер не отвечает. Попробуйте другой аудиофайл." }
            if (!inputFinished) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val input = requireNotNull(codec.getInputBuffer(index))
                    val size = extractor.readSampleData(input, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputFinished = true
                    } else {
                        codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                    lastProgressAt = SystemClock.elapsedRealtime()
                }
            }
            val index = codec.dequeueOutputBuffer(info, 10_000)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val output = codec.outputFormat
                channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                sampleRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                encoding = if (output.containsKey(MediaFormat.KEY_PCM_ENCODING)) output.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
            } else if (index >= 0) {
                try {
                    if (info.size > 0) {
                        val data = requireNotNull(codec.getOutputBuffer(index))
                        data.position(info.offset)
                        data.limit(info.offset + info.size)
                        frames += accumulateWaveformPcm(data.slice().order(ByteOrder.nativeOrder()), channels, sampleRate,
                            encoding, info.presentationTimeUs, durationUs, peaks)
                    }
                    outputFinished = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    lastProgressAt = SystemClock.elapsedRealtime()
                    val percent = (info.presentationTimeUs * 100 / durationUs).coerceIn(0, 100).toInt()
                    if (percent / 2 != reported / 2) { progress(percent / 100f); reported = percent }
                } finally { codec.releaseOutputBuffer(index, false) }
            }
        }
        check(frames > 0) { "Аудиофайл не содержит декодируемых отсчётов" }
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        progress(1f)
        KaraokeAudioWaveform(peaks.toList(), durationUs / 1000, name)
    } finally {
        if (started) runCatching { decoder?.stop() }
        runCatching { decoder?.release() }
        extractor.release()
    }
}

/** Frame/channel aggregation is tested separately from platform codecs. */
internal fun accumulateWaveformPcm(
    data: ByteBuffer, channels: Int, sampleRate: Int, encoding: Int,
    presentationTimeUs: Long, durationUs: Long, peaks: FloatArray
): Int {
    require(channels in 1..32 && sampleRate > 0 && durationUs > 0 && peaks.isNotEmpty())
    val bytes = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_16BIT -> 2
        AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        else -> error("Формат PCM не поддерживается")
    }
    val frameCount = data.remaining() / (channels * bytes)
    repeat(frameCount) { frame ->
        var peak = 0f
        repeat(channels) {
            val value = when (encoding) {
                AudioFormat.ENCODING_PCM_8BIT -> abs(((data.get().toInt() and 255) - 128) / 128f)
                AudioFormat.ENCODING_PCM_16BIT -> abs(data.short.toInt() / 32768f)
                AudioFormat.ENCODING_PCM_FLOAT -> abs(data.float).let { if (it.isFinite()) it else 0f }
                AudioFormat.ENCODING_PCM_32BIT -> abs(data.int.toDouble() / 2147483648.0).toFloat()
                else -> {
                    val word = (data.get().toInt() and 255) or ((data.get().toInt() and 255) shl 8) or (data.get().toInt() shl 16)
                    abs(word / 8388608f)
                }
            }
            peak = max(peak, value.coerceIn(0f, 1f))
        }
        val time = presentationTimeUs + frame.toLong() * 1_000_000 / sampleRate
        if (time in 0 until durationUs) {
            val index = (time.toDouble() / durationUs * peaks.size).toInt().coerceIn(0, peaks.lastIndex)
            peaks[index] = max(peaks[index], peak)
        }
    }
    return frameCount
}
