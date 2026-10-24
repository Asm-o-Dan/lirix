package com.lirix.app.feature.share.animation

import android.graphics.Bitmap
import java.io.File

/** Offline AVC encoder with deterministic timestamps and confirmed, failure-aware finalization.
 * Flexible YUV input is written using Image plane strides; NV12 is never assumed for flexible codecs.
 */
class Mp4FrameEncoder(
    val outputFile: File,
    val spec: AnimationSpec,
    private val preferSurface: Boolean = false,
    private val checkCancellation: () -> Unit = {}
) : AutoCloseable, PosterVideoEncoder {

    companion object {
        /**
         * Converts an ARGB [Bitmap] into NV12 (YUV420SemiPlanar) byte array.
         */
        internal fun convertArgbToNv12(
            bitmap: Bitmap,
            width: Int,
            height: Int,
            pixelsBuffer: IntArray,
            outYuv: ByteArray
        ) {
            val bmpW = bitmap.width
            val bmpH = bitmap.height
            if (bmpW == width && bmpH == height) {
                bitmap.getPixels(pixelsBuffer, 0, width, 0, 0, width, height)
            } else {
                val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                scaled.getPixels(pixelsBuffer, 0, width, 0, 0, width, height)
                if (scaled != bitmap) {
                    scaled.recycle()
                }
            }

            val frameSize = width * height
            var yIndex = 0
            var uvIndex = frameSize

            for (j in 0 until height) {
                val rowOffset = j * width
                val isEvenRow = (j and 1) == 0
                for (i in 0 until width) {
                    val color = pixelsBuffer[rowOffset + i]
                    val r = (color ushr 16) and 0xFF
                    val g = (color ushr 8) and 0xFF
                    val b = color and 0xFF

                    // Y = 0.299R + 0.587G + 0.114B
                    val y = (77 * r + 150 * g + 29 * b + 128) shr 8
                    outYuv[yIndex++] = y.coerceIn(0, 255).toByte()

                    if (isEvenRow && (i and 1) == 0) {
                        // NV12 format: interleaved U, V
                        val u = ((-43 * r - 85 * g + 128 * b + 128) shr 8) + 128
                        val v = ((128 * r - 107 * g - 21 * b + 128) shr 8) + 128
                        outYuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                        outYuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                    }
                }
            }
        }
    }

    val width = spec.width
    val height = spec.height
    private val backend: AndroidMp4CodecIo
    private val pump: Mp4CodecPump
    private var complete = false
    private val pixels by lazy { IntArray(width * height) }
    private val yuv by lazy { ByteArray(width * height * 3 / 2) }

    init {
        require(width >= 2 && height >= 2 && width % 2 == 0 && height % 2 == 0) { "AVC requires positive even dimensions" }
        require(spec.fps > 0 && spec.totalFrames > 0) { "Invalid video timing" }
        require(!preferSurface) { "Offline export requires deterministic ByteBuffer timestamps" }
        outputFile.parentFile?.mkdirs()
        check(!outputFile.exists() || outputFile.delete()) { "Cannot replace video output" }
        backend = try { AndroidMp4CodecIo(outputFile, width, height, spec.fps) }
        catch (failure: Throwable) { outputFile.delete(); throw failure }
        pump = Mp4CodecPump(backend, spec.fps, checkCancellation)
    }

    override fun encodeFrame(bitmap: Bitmap) {
        try {
            convertArgbToNv12(bitmap, width, height, pixels, yuv)
            pump.submit { backend.writeFrame(it, yuv) }
        } catch (failure: Throwable) {
            abortAfter(failure)
            throw failure
        }
    }

    override fun finish() {
        if (complete) return
        try {
            pump.finish()
            check(outputFile.isFile && outputFile.length() > 0L) { "Empty video output" }
            complete = true
        } catch (failure: Throwable) {
            abortAfter(failure)
            throw failure
        }
    }

    /** Failure/cancellation cleanup never queues EOS or turns a partial file into a success. */
    override fun abort() {
        try { pump.abort() } finally { if (!complete) outputFile.delete() }
    }

    private fun abortAfter(failure: Throwable) {
        try { abort() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
    }

    override fun close() = finish()
}
