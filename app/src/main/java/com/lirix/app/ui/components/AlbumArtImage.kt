package com.lirix.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lirix.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AlbumArtBitmapCache {
    private val lru = object : LruCache<String, ImageBitmap>(64) {}

    fun get(path: String): ImageBitmap? = synchronized(lru) { lru.get(path) }
    fun put(path: String, bitmap: ImageBitmap) {
        synchronized(lru) { lru.put(path, bitmap) }
    }
    fun clear() {
        synchronized(lru) { lru.evictAll() }
    }
}

@Composable
fun rememberAlbumArtBitmap(artUri: String?): ImageBitmap? {
    if (artUri.isNullOrBlank()) return null
    var bitmapState by remember(artUri) { mutableStateOf(AlbumArtBitmapCache.get(artUri)) }

    LaunchedEffect(artUri) {
        if (bitmapState == null) {
            withContext(Dispatchers.IO) {
                try {
                    val cleanPath = artUri.removePrefix("file://")
                    val file = File(cleanPath)
                    if (file.exists() && file.length() > 0L) {
                        val decoded = BitmapFactory.decodeFile(file.absolutePath)
                        val imageBmp = decoded?.asImageBitmap()
                        if (imageBmp != null) {
                            AlbumArtBitmapCache.put(artUri, imageBmp)
                            bitmapState = imageBmp
                        }
                    }
                } catch (e: Exception) {
                    // Игнорируем битые файлы
                }
            }
        }
    }

    return bitmapState
}

@Composable
fun AlbumArtThumbnail(
    artUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    borderColor: Color = AppColors.BorderSubtle,
    contentDescription: String? = null,
    fallbackIcon: ImageVector = Icons.Default.MusicNote
) {
    val bitmap = rememberAlbumArtBitmap(artUri)

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
                .border(1.dp, borderColor, shape)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(
                    Brush.radialGradient(
                        listOf(AppColors.SurfaceLevel3, AppColors.SurfaceLevel1, AppColors.AmoledBlack)
                    )
                )
                .border(1.dp, borderColor, shape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = AppColors.TextSecondary,
                modifier = Modifier.size(size * 0.45f)
            )
        }
    }
}
