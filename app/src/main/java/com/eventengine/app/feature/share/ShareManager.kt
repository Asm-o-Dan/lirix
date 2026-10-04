package com.eventengine.app.feature.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Handles exporting generated bitmaps to application cache and launching
 * the system Android Share Sheet via FileProvider.
 *
 * Spec: TASK-SHR-01 / .sdd/tasks/TASK-SHR-01.md
 */
object ShareManager {

    /**
     * Compresses the bitmap to cacheDir/shares and dispatches an ACTION_SEND intent.
     */
    suspend fun shareBitmap(
        context: Context,
        bitmap: Bitmap,
        chooserTitle: String = "Поделиться треком"
    ) = withContext(Dispatchers.IO) {
        val sharesDir = File(context.cacheDir, "shares").apply { mkdirs() }
        val shareFile = File(sharesDir, "share_${System.currentTimeMillis()}.png")

        FileOutputStream(shareFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        val contentUri: Uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                shareFile
            )
        } catch (e: Exception) {
            Uri.fromFile(shareFile)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
