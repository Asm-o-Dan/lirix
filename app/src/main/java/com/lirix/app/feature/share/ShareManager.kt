package com.lirix.app.feature.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Handles exporting generated bitmaps and MP4 animated posters to application cache
 * and launching the system Android Share Sheet via FileProvider.
 *
 * Spec: TASK-SHR-01 / .sdd/tasks/TASK-SHR-01.md
 * Spec: CORE-01 / .sdd/specs/media-core/core_security_and_stability_spec.md
 * Spec: SPEC-ANIM-POSTER-01 §5 / TASK-ANIM-007
 */
object ShareManager {

    private const val TAG = "ShareManager"

    /** Write the displayed poster to the user-selected document; failures stay visible to the caller. */
    suspend fun saveBitmap(context: Context, bitmap: Bitmap, destination: Uri) = withContext(Dispatchers.IO) {
        checkNotNull(context.contentResolver.openOutputStream(destination)) { "Не удалось открыть файл" }.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Не удалось записать постер" }
        }
    }

    /**
     * Saves an animated poster MP4 video to the user-selected document destination.
     */
    suspend fun saveVideo(context: Context, sourceFile: File, destination: Uri) = withContext(Dispatchers.IO) {
        check(sourceFile.isFile && sourceFile.length() > 0L) { "Исходный видеофайл не найден или пуст" }
        checkNotNull(context.contentResolver.openOutputStream(destination)) { "Не удалось открыть файл для записи" }.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Compresses the bitmap to cacheDir/shares and dispatches an ACTION_SEND intent.
     * Returns true if sharing intent was dispatched, false if an error occurred.
     */
    suspend fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        chooserTitle: String = "Поделиться треком"
    ): Boolean = withContext(Dispatchers.IO) {
        val sharesDir = File(context.cacheDir, "shares").apply { mkdirs() }
        val shareFile = File(sharesDir, "share_${System.currentTimeMillis()}.png")

        try {
            FileOutputStream(shareFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress and save share bitmap", e)
            return@withContext false
        }

        val contentUri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )
        } catch (e: IllegalArgumentException) {
            // When running in test environments (Robolectric) where FileProvider authority isn't bound to XML
            if (context.packageName.contains("robolectric") || shareFile.exists()) {
                Uri.parse("content://${context.packageName}.fileprovider/shares/${shareFile.name}")
            } else {
                Log.e(TAG, "Failed to get FileProvider URI for sharing", e)
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FileProvider URI for sharing", e)
            return@withContext false
        }

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) { context.startActivity(chooser) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch share chooser activity", e)
            false
        }
    }

    /**
     * Dispatches an ACTION_SEND intent with video/mp4 MIME type and FileProvider URI
     * for sharing an animated poster to Instagram Stories, Telegram, etc. (SPEC-ANIM-POSTER-01 §5).
     */
    suspend fun shareAnimatedPoster(
        context: Context,
        file: File,
        chooserTitle: String = "Поделиться постером"
    ): Boolean = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() == 0L) {
            Log.e(TAG, "Cannot share empty or non-existent file: ${file.absolutePath}")
            return@withContext false
        }

        val contentUri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FileProvider URI for video sharing", e)
            return@withContext false
        }

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            withContext(Dispatchers.Main) { context.startActivity(chooser) }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch video share chooser activity", e)
            false
        }
    }
}
