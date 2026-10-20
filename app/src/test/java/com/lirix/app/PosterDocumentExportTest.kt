package com.lirix.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.lirix.app.feature.share.ShareManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PosterDocumentExportTest {
    @Test
    fun savesTheDisplayedPixelsAtOriginalResolution() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(27, 48, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(31, 65, 123))
        bitmap.setPixel(13, 24, Color.rgb(238, 204, 105))
        val destination = File(context.cacheDir, "poster-document-export.png")
        try {
            ShareManager.saveBitmap(context, bitmap, Uri.fromFile(destination))
            val decoded = BitmapFactory.decodeFile(destination.absolutePath)
            assertNotNull(decoded)
            assertEquals(bitmap.width, decoded.width)
            assertEquals(bitmap.height, decoded.height)
            assertEquals(bitmap.getPixel(0, 0), decoded.getPixel(0, 0))
            assertEquals(bitmap.getPixel(13, 24), decoded.getPixel(13, 24))
        } finally {
            destination.delete()
        }
    }

    @Test
    fun inaccessibleDestinationReportsFailureInsteadOfSuccess() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val destination = Uri.parse("content://poster-export-test/unavailable")
        shadowOf(context.contentResolver).registerOutputStream(destination, object : OutputStream() {
            override fun write(value: Int) { throw IOException("Destination unavailable") }
        })
        val result = runCatching { ShareManager.saveBitmap(context, bitmap, destination) }
        assertTrue(result.isFailure)
    }
}
