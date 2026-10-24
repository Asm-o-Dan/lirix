package com.lirix.app.feature.share.animation

import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Requires a physical/emulated Android codec. Not a Robolectric file-existence assertion. */
@RunWith(AndroidJUnit4::class)
@LargeTest
class Mp4DeviceCodecTest {
    @Test fun stories1080pHas96DecodableFramesFourSecondsAndCorrectChroma() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "mp4-device-regression.mp4")
        val palette = ThemeAnimationPalette(Color.BLACK, Color.BLACK, Color.GREEN, Color.WHITE,
            Color.GRAY, Color.GRAY, Color.GREEN)
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val fixture = Properties().apply { assets.open("fixtures/media/poster.properties").use { load(it) } }
        val red = assets.open("fixtures/media/${fixture.getProperty("red")}").use { checkNotNull(BitmapFactory.decodeStream(it)) }
        val blue = assets.open("fixtures/media/${fixture.getProperty("blue")}").use { checkNotNull(BitmapFactory.decodeStream(it)) }
        assertEquals(64, red.width); assertEquals(64, blue.height)
        val expectedFrames = fixture.getProperty("frames").toInt()
        val expectedFps = fixture.getProperty("fps").toInt()
        val expectedDurationUs = fixture.getProperty("durationUs").toLong()
        val animator = object : PosterAnimator(palette) {
            override fun renderFrame(canvas: Canvas, t: Float, width: Int, height: Int) {
                canvas.drawBitmap(if (t < .5f) red else blue, null, Rect(0, 0, width, height), null)
            }
        }
        try {
            AnimatedPosterPipeline().generate(animator, AnimationSpec.STORIES, file).getOrThrow()
            val extractor = MediaExtractor()
            var decoder: MediaCodec? = null
            try {
                extractor.setDataSource(file.absolutePath)
                assertEquals(1, extractor.trackCount)
                val format = extractor.getTrackFormat(0)
                assertEquals(1080, format.getInteger(MediaFormat.KEY_WIDTH))
                assertEquals(1920, format.getInteger(MediaFormat.KEY_HEIGHT))
                assertEquals("video/avc", format.getString(MediaFormat.KEY_MIME))
                val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                assertTrue(kotlin.math.abs(durationUs - expectedDurationUs) < 100_000L)
                if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                    assertTrue("Container-reported bitrate must be positive", format.getInteger(MediaFormat.KEY_BIT_RATE) > 0)
                }
                extractor.selectTrack(0)
                var sampleCount = 0
                var payloadBytes = 0L
                var lastPts = -1L
                while (extractor.sampleTime >= 0) {
                    val pts = extractor.sampleTime
                    assertTrue(pts > lastPts)
                    assertTrue(kotlin.math.abs(pts - sampleCount * 1_000_000L / expectedFps) < 100L)
                    assertTrue("Every muxed sample must carry encoded data", extractor.sampleSize > 0)
                    payloadBytes += extractor.sampleSize
                    lastPts = pts; sampleCount++; extractor.advance()
                }
                assertEquals(expectedFrames, sampleCount)
                val payloadBitrate = payloadBytes * 8_000_000L / durationUs
                assertTrue("Video payload must have a valid positive bitrate", payloadBitrate > 0)
                assertTrue("Payload bytes cannot exceed the complete MP4 container", payloadBytes < file.length())
                android.util.Log.i("Mp4DeviceCodecTest", "frames=$sampleCount durationUs=$durationUs payloadBytes=$payloadBytes payloadBitrate=$payloadBitrate fileBytes=${file.length()}")
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                val codec = MediaCodec.createDecoderByType("video/avc").also { decoder = it }
                codec.configure(format, null, null, 0); codec.start()
                var inputEos = false
                var outputEos = false
                var decoded = 0
                var redSamples = 0
                var blueSamples = 0
                val info = MediaCodec.BufferInfo()
                val start = System.nanoTime()
                while (!outputEos) {
                    check(System.nanoTime() - start < 60_000_000_000L) { "Decoder timed out" }
                    if (!inputEos) {
                        val index = codec.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            val buffer = checkNotNull(codec.getInputBuffer(index))
                            val size = extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(index, 0, 0, 4_000_000, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEos = true
                            } else {
                                codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, 10_000)
                    if (index >= 0) {
                        try {
                            if (info.size > 0) {
                                assertTrue("Decoded PTS must preserve every input frame", kotlin.math.abs(info.presentationTimeUs - decoded * 1_000_000L / expectedFps) < 100L)
                                val image = checkNotNull(codec.getOutputImage(index)) { "No decoded YUV image" }
                                try {
                                    fun sample(planeIndex: Int): Int {
                                        val plane = image.planes[planeIndex]
                                        val scale = if (planeIndex == 0) 1 else 2
                                        val x = image.width / (2 * scale)
                                        val y = image.height / (2 * scale)
                                        return plane.buffer.get(plane.buffer.position() + y * plane.rowStride + x * plane.pixelStride).toInt() and 255
                                    }
                                    val u = sample(1); val v = sample(2)
                                    if (info.presentationTimeUs < 2_000_000) {
                                        assertTrue("Red frame has swapped/invalid chroma: U=$u V=$v", v > u + 60)
                                        redSamples++
                                    } else {
                                        assertTrue("Blue frame has swapped/invalid chroma: U=$u V=$v", u > v + 60)
                                        blueSamples++
                                    }
                                } finally { image.close() }
                                decoded++
                            }
                            outputEos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        } finally { codec.releaseOutputBuffer(index, false) }
                    }
                }
                assertEquals(expectedFrames, decoded)
                assertEquals(expectedFrames / 2, redSamples); assertEquals(expectedFrames / 2, blueSamples)
            } finally {
                runCatching { decoder?.stop() }; decoder?.release(); extractor.release()
            }
        } finally { file.delete(); red.recycle(); blue.recycle() }
    }
}
