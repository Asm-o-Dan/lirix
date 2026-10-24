package com.lirix.app.feature.share.animation

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File

/** Android-only adapter; retry, EOS, cancellation and frame accounting live in Mp4CodecPump. */
internal class AndroidMp4CodecIo(file: File, private val width: Int, private val height: Int, fps: Int) : Mp4CodecIo {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var started = false
    private var muxerStarted = false
    private var track = -1
    private var colorFormat = 0
    private var released = false
    private val info = MediaCodec.BufferInfo()

    init {
        try {
            val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).also { codec = it }
            val supported = c.codecInfo.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).colorFormats
            colorFormat = listOf(
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            ).firstOrNull { it in supported } ?: error("AVC codec has no supported YUV420 input format")
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL)
                setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT601_NTSC)
                // Baseline prevents frame reordering; do not force Level 3.1 (too low for 1080p).
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            }
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            c.start(); started = true
        } catch (failure: Throwable) {
            try { release() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
    }

    override fun dequeueInput(timeoutUs: Long) = checkNotNull(codec).dequeueInputBuffer(timeoutUs)

    fun writeFrame(index: Int, nv12: ByteArray) {
        val c = checkNotNull(codec)
        // Flexible YUV is a family, not a promise of NV12. Respect the codec's actual strides.
        if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
            val image = c.getInputImage(index) ?: error("Flexible YUV codec did not provide an input Image")
            try {
                check(image.planes.size == 3 && image.width >= width && image.height >= height) { "Invalid YUV input image" }
                image.planes.forEachIndexed { planeIndex, plane ->
                    val planeWidth = if (planeIndex == 0) width else width / 2
                    val planeHeight = if (planeIndex == 0) height else height / 2
                    val buffer = plane.buffer
                    val base = buffer.position()
                    for (row in 0 until planeHeight) for (col in 0 until planeWidth) {
                        val src = if (planeIndex == 0) row * width + col
                        else width * height + row * width + col * 2 + planeIndex - 1
                        val dest = base + row * plane.rowStride + col * plane.pixelStride
                        check(dest < buffer.limit()) { "YUV input plane is too small" }
                        buffer.put(dest, nv12[src])
                    }
                }
            } finally { image.close() }
        } else {
            val buffer = c.getInputBuffer(index) ?: error("Null input buffer")
            buffer.clear()
            check(buffer.remaining() >= nv12.size) { "Input buffer cannot hold a complete frame" }
            if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) buffer.put(nv12)
            else {
                val luma = width * height
                buffer.put(nv12, 0, luma)
                for (channel in 0..1) for (i in 0 until luma / 4) buffer.put(nv12[luma + i * 2 + channel])
            }
        }
    }

    override fun queueInput(index: Int, presentationTimeUs: Long, eos: Boolean) {
        checkNotNull(codec).queueInputBuffer(index, 0, if (eos) 0 else width * height * 3 / 2,
            presentationTimeUs, if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
    }

    override fun drainOutput(timeoutUs: Long): Mp4Output? {
        val c = checkNotNull(codec)
        return when (val index = c.dequeueOutputBuffer(info, timeoutUs)) {
            MediaCodec.INFO_TRY_AGAIN_LATER -> null
            MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                check(!muxerStarted) { "Codec output format changed twice" }
                track = checkNotNull(muxer).addTrack(c.outputFormat)
                checkNotNull(muxer).start(); muxerStarted = true
                Mp4Output()
            }
            MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Mp4Output()
            else -> {
                check(index >= 0) { "Unexpected codec output $index" }
                try {
                    var samples = 0
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        check(muxerStarted) { "Video sample arrived before output format" }
                        val bytes = c.getOutputBuffer(index) ?: error("Null output buffer")
                        bytes.position(info.offset); bytes.limit(info.offset + info.size)
                        checkNotNull(muxer).writeSampleData(track, bytes, info)
                        samples = 1
                    }
                    Mp4Output(samples, info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0)
                } finally { c.releaseOutputBuffer(index, false) }
            }
        }
    }

    override fun finishMuxer() {
        check(muxerStarted) { "Muxer never started" }
        checkNotNull(muxer).stop()
        muxerStarted = false
    }

    override fun release() {
        if (released) return
        released = true
        var failure: Throwable? = null
        fun attempt(block: () -> Unit) { try { block() } catch (e: Throwable) { if (failure == null) failure = e else failure?.addSuppressed(e) } }
        if (started) attempt { codec?.stop() }; started = false
        attempt { codec?.release() }; codec = null
        // abort intentionally does not stop/finalize the muxer: the partial file is deleted by its owner.
        attempt { muxer?.release() }; muxer = null
        failure?.let { throw it }
    }
}
