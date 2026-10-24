package com.lirix.app.feature.share.animation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import timber.log.Timber
import java.io.File

/**
 * Orchestrator pipeline that drives frame-by-frame rendering and MP4 encoding
 * for animated share posters (SPEC-ANIM-POSTER-01 §3, §3.2, §3.3).
 *
 * Runs exclusively on [Dispatchers.Default] to avoid blocking UI or audio pipelines.
 */
class AnimatedPosterPipeline(
    private val context: Context? = null,
    private val defaultAnimator: PosterAnimator? = null,
    private val defaultSpec: AnimationSpec? = null
) {
    private var encoderFactory: (File, AnimationSpec, () -> Unit) -> PosterVideoEncoder =
        { file, spec, active -> Mp4FrameEncoder(file, spec, checkCancellation = active) }

    internal constructor(factory: (File, AnimationSpec, () -> Unit) -> PosterVideoEncoder) : this() {
        encoderFactory = factory
    }

    constructor(
        animator: PosterAnimator,
        spec: AnimationSpec,
        context: Context? = null
    ) : this(context, animator, spec)

    /**
     * Generates an MP4 video file by iterating through each frame in [spec.totalFrames],
     * drawing into a hardware/software [Canvas] using [animator.renderFrame], and
     * encoding frames via [Mp4FrameEncoder].
     *
     * @param animator The poster animator responsible for drawing elements per normalized timestamp t.
     * @param spec Dimensions, framerate and duration parameters.
     * @param outputFile Target file where the MP4 container will be saved.
     * @param onProgress Callback invoked once per frame with progress in [0.0 .. 1.0).
     * @return [Result.success] with the output file on completion, or [Result.failure] on error.
     */
    suspend fun generate(
        animator: PosterAnimator,
        spec: AnimationSpec,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.Default) {
        val totalFrames = spec.totalFrames.coerceAtLeast(1)
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        var encoder: PosterVideoEncoder? = null
        var bitmap: Bitmap? = null

        try {
            val exportContext = currentCoroutineContext()
            encoder = encoderFactory(outputFile, spec) { exportContext.ensureActive() }
            val enc = encoder

            bitmap = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            for (frameIndex in 0 until totalFrames) {
                currentCoroutineContext().ensureActive()
                yield()

                val t = if (totalFrames == 1) 1f else frameIndex.toFloat() / (totalFrames - 1)
                onProgress(frameIndex.toFloat() / totalFrames)

                bitmap.eraseColor(0)
                animator.renderFrame(canvas, t, spec.width, spec.height)
                enc.encodeFrame(bitmap)
            }

            bitmap.recycle()
            bitmap = null

            enc.finish()
            encoder = null

            currentCoroutineContext().ensureActive()
            onProgress(1f)

            Result.success(outputFile)
        } catch (e: Throwable) {
            bitmap?.recycle()
            bitmap = null

            runCatching { encoder?.abort() }
            encoder = null

            if (outputFile.exists()) {
                runCatching { outputFile.delete() }
            }

            if (e is CancellationException) {
                throw e
            }

            Timber.e(e, "AnimatedPosterPipeline failed during poster generation")
            Result.failure(e)
        }
    }

    /**
     * Overload when [animator] and [spec] were provided via constructor.
     */
    suspend fun generate(
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = generate(
        animator = defaultAnimator ?: error("PosterAnimator must be specified in constructor or call"),
        spec = defaultSpec ?: AnimationSpec(),
        outputFile = outputFile,
        onProgress = onProgress
    )

    /**
     * Overload when only [animator] is specified, using constructor/default [spec].
     */
    suspend fun generate(
        animator: PosterAnimator,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = generate(
        animator = animator,
        spec = defaultSpec ?: AnimationSpec(),
        outputFile = outputFile,
        onProgress = onProgress
    )

    /**
     * Convenience method to generate or throw exception directly.
     */
    suspend fun generateOrThrow(
        animator: PosterAnimator,
        spec: AnimationSpec,
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): File = generate(animator, spec, outputFile, onProgress).getOrThrow()

    suspend fun generateOrThrow(
        outputFile: File,
        onProgress: (Float) -> Unit = {}
    ): File = generate(outputFile, onProgress).getOrThrow()

    companion object {
        suspend fun generate(
            context: Context? = null,
            animator: PosterAnimator,
            spec: AnimationSpec,
            outputFile: File,
            onProgress: (Float) -> Unit = {}
        ): Result<File> = AnimatedPosterPipeline(context).generate(animator, spec, outputFile, onProgress)
    }
}
