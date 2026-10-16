package com.lirix.app.feature.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Loads bounded local artwork; no network requests or file I/O inside the renderer. */
object PosterArtworkLoader {
    suspend fun load(context: Context, value: String?, targetPx: Int = 720): Bitmap? = withContext(Dispatchers.IO) {
        if (value.isNullOrBlank()) return@withContext null
        val parsed = Uri.parse(value)
        val uri = if (parsed.scheme == null) Uri.fromFile(File(value)) else parsed
        if (uri.scheme != "file" && uri.scheme != "content") return@withContext null
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            if (options.outWidth <= 0 || options.outHeight <= 0) return@withContext null
            var sample = 1
            while (maxOf(options.outWidth, options.outHeight) / sample > targetPx.coerceAtLeast(1) * 2) sample *= 2
            options.inJustDecodeBounds = false
            options.inSampleSize = sample
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (_: java.io.IOException) { null }
        catch (_: SecurityException) { null }
    }
}
