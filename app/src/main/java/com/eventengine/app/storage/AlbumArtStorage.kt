package com.eventengine.app.storage

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Local storage manager for album cover art files.
 * Downscales images to max 512x512 and stores them as compact WebP (85% quality).
 * Spec: TASK-ART-01 (.sdd/tasks/TASK-ART-01.md)
 */
class AlbumArtStorage(private val context: Context) {

    private val artDir: File by lazy {
        File(context.filesDir, SUBDIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    suspend fun saveBitmap(trackKey: String, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        if (trackKey.isBlank() || bitmap.isRecycled) return@withContext null

        try {
            val ratio = if (bitmap.width > MAX_DIMENSION || bitmap.height > MAX_DIMENSION) {
                min(MAX_DIMENSION.toFloat() / bitmap.width, MAX_DIMENSION.toFloat() / bitmap.height)
            } else {
                1.0f
            }

            val scaledBitmap = if (ratio < 1.0f) {
                val targetW = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                val targetH = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
            } else {
                bitmap
            }

            val targetFile = File(artDir, "$trackKey.webp")
            FileOutputStream(targetFile).use { out ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                scaledBitmap.compress(format, WEBP_QUALITY, out)
            }

            if (scaledBitmap != bitmap && !scaledBitmap.isRecycled) {
                scaledBitmap.recycle()
            }

            targetFile.absolutePath
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to save album art for track %s", trackKey)
            null
        }
    }

    fun getAlbumArtPath(trackKey: String): String? {
        if (trackKey.isBlank()) return null
        val file = File(artDir, "$trackKey.webp")
        return if (file.exists() && file.length() > 0L) file.absolutePath else null
    }

    fun deleteAlbumArt(trackKey: String): Boolean {
        if (trackKey.isBlank()) return false
        val file = File(artDir, "$trackKey.webp")
        return if (file.exists()) file.delete() else false
    }

    fun clearAll(): Boolean {
        return try {
            artDir.listFiles()?.forEach { it.delete() }
            true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear album art cache")
            false
        }
    }

    companion object {
        private const val TAG = "AlbumArtStorage"
        const val SUBDIR_NAME = "album_art"
        const val MAX_DIMENSION = 512
        const val WEBP_QUALITY = 85
    }
}
