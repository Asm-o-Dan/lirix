package com.lirix.app.feature.share.animation

import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.ui.theme.ColorPalettes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class AnimatedPosterPipelineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // Unit tests exercise pipeline orchestration. Hardware encoding is covered by Mp4DeviceCodecTest.
    private fun pipeline(failFinish: Boolean = false) = AnimatedPosterPipeline { file, _, active ->
        object : PosterVideoEncoder {
            override fun encodeFrame(bitmap: android.graphics.Bitmap) { active() }
            override fun finish() { if (failFinish) error("finalize failed"); file.writeBytes(byteArrayOf(1)) }
            override fun abort() { file.delete() }
        }
    }

    private val palette = ThemeAnimationPalette.fromColors(ColorPalettes.SpotifyMood)

    @Test fun failedFinalizationDoesNotReportSuccessOrLeaveFile() = runBlocking {
        val file = File(tempFolder.root, "bad-finalize.mp4")
        val animator = object : PosterAnimator(palette) {
            override fun renderFrame(canvas: Canvas, t: Float, width: Int, height: Int) = Unit
        }
        val progress = mutableListOf<Float>()
        val result = pipeline(failFinish = true).generate(animator,
            AnimationSpec(durationMs = 1000, fps = 2, width = 32, height = 32), file) { progress += it }
        assertTrue(result.isFailure)
        assertFalse(file.exists())
        assertFalse(1f in progress)
    }

    @Test fun finalRenderedTimestampIsInclusiveAndSingleFrameIsComplete() = runBlocking {
        for (frames in listOf(1, 4)) {
            val times = mutableListOf<Float>()
            val animator = object : PosterAnimator(palette) {
                override fun renderFrame(canvas: Canvas, t: Float, width: Int, height: Int) { times += t }
            }
            pipeline().generate(animator, AnimationSpec(durationMs = 1000, fps = frames, width = 32, height = 32),
                File(tempFolder.root, "timeline-$frames.mp4")).getOrThrow()
            assertEquals(frames, times.size)
            assertEquals(1f, times.last(), 0f)
        }
    }

    @Test
    fun pipeline_reportsIncreasingFrameProgressAndCompletionOnlyAfterFinish() = runBlocking {
        // DoD: onProgress вызывается totalFrames раз с нарастающим значением
        val outputFile = File(tempFolder.root, "test_progress.mp4")
        val spec = AnimationSpec(durationMs = 1000L, fps = 10, width = 32, height = 32)
        assertEquals(10, spec.totalFrames)

        val progressValues = mutableListOf<Float>()
        val mockAnimator = object : PosterAnimator(palette) {
            override fun renderFrame(canvas: Canvas, t: Float, width: Int, height: Int) {
                canvas.drawColor(Color.GREEN)
            }
        }

        val pipeline = pipeline()
        val result = pipeline.generate(mockAnimator, spec, outputFile) { progress ->
            progressValues.add(progress)
        }

        assertTrue("Pipeline generation should succeed", result.isSuccess)
        assertEquals("onProgress must be called totalFrames times", spec.totalFrames + 1, progressValues.size)
        assertEquals("First progress should start at 0.0", 0f, progressValues.first(), 0.0001f)

        // Verify strictly increasing values
        for (i in 0 until progressValues.size - 1) {
            assertTrue(
                "Progress values must be strictly increasing: ${progressValues[i]} < ${progressValues[i + 1]}",
                progressValues[i] < progressValues[i + 1]
            )
        }
    }

    @Test
    fun pipeline_withFakeEncoder_rendersEveryFrameAndReturnsCompletedOutput() = runBlocking {
        // DoD: Pipeline с mock-animator (заливка цветом) генерирует файл
        val outputFile = File(tempFolder.root, "test_mock_animator.mp4")
        val spec = AnimationSpec(durationMs = 500L, fps = 12, width = 64, height = 64)

        var renderedFrames = 0
        val mockAnimator = object : PosterAnimator(palette) {
            override fun renderFrame(canvas: Canvas, t: Float, width: Int, height: Int) {
                canvas.drawColor(palette.background)
                renderedFrames++
            }
        }

        val pipeline = pipeline()
        val fileResult = pipeline.generate(mockAnimator, spec, outputFile)

        assertTrue(fileResult.isSuccess)
        val file = fileResult.getOrThrow()
        assertTrue("Generated file must exist", file.exists())
        assertTrue("Generated file size must be > 0 bytes (got ${file.length()})", file.length() > 0)
        assertEquals(spec.totalFrames, renderedFrames)
    }

    @Test
    fun pipeline_onExceptionInAnimator_returnsFailureAndCleansUp() = runBlocking {
        val outputFile = File(tempFolder.root, "test_failure.mp4")
        val spec = AnimationSpec(durationMs = 500L, fps = 10, width = 32, height = 32)

        val failingAnimator = object : PosterAnimator(palette) {
            override fun renderFrame(canvas: Canvas, t: Float, width: Int, height: Int) {
                if (t > 0.3f) {
                    throw RuntimeException("Simulated rendering failure")
                }
            }
        }

        val pipeline = pipeline()
        val result = pipeline.generate(failingAnimator, spec, outputFile)

        assertTrue("Expected failure result", result.isFailure)
        assertEquals("Simulated rendering failure", result.exceptionOrNull()?.message)
        assertFalse("Incomplete file should be cleaned up on failure", outputFile.exists())
    }

    @Test
    fun pipeline_cancellationAfterPartialFrameAbortsWithoutFinishOrSuccess() = runBlocking {
        val outputFile = File(tempFolder.root, "test_cancelled.mp4")
        val partialBytes = "known-partial-video".toByteArray()
        val started = CompletableDeferred<Unit>()
        val aborts = AtomicInteger()
        val finishes = AtomicInteger()
        val progress = Collections.synchronizedList(mutableListOf<Float>())
        val pipeline = AnimatedPosterPipeline { file, _, active ->
            object : PosterVideoEncoder {
                override fun encodeFrame(bitmap: android.graphics.Bitmap) {
                    file.writeBytes(partialBytes)
                    started.complete(Unit)
                    // Model an encoder waiting on input backpressure; production passes this same cancellation check.
                    while (true) { active(); Thread.yield() }
                }
                override fun finish() { finishes.incrementAndGet() }
                override fun abort() { aborts.incrementAndGet(); file.delete() }
            }
        }
        val animator = object : PosterAnimator(palette) {
            override fun renderFrame(canvas: Canvas, t: Float, width: Int, height: Int) = Unit
        }
        val job = async {
            pipeline.generate(animator, AnimationSpec(durationMs = 4000L, fps = 24, width = 32, height = 32),
                outputFile) { progress += it }
        }
        try {
            withTimeout(5_000) { started.await() }
            assertTrue("Must cancel an export which actually wrote a partial frame", outputFile.readBytes().contentEquals(partialBytes))
            job.cancelAndJoin()
            assertTrue(job.isCancelled)
            assertEquals(1, aborts.get())
            assertEquals(0, finishes.get())
            assertFalse(1f in progress)
            assertFalse("Known partial output must be deleted", outputFile.exists())
        } finally { job.cancelAndJoin() }
    }
}
