package com.lirix.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.ingestion.MediaSessionCollector
import com.lirix.app.storage.AlbumArtStorage
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaSessionEnergyTest {
    private lateinit var collector: MediaSessionCollector
    private lateinit var storage: AlbumArtStorage

    @Before fun setup() {
        MediaSessionCollector.resetInstanceForTesting()
        val context = ApplicationProvider.getApplicationContext<Context>()
        collector = MediaSessionCollector.getInstance(context)
        storage = AlbumArtStorage(context)
        storage.clearAll()
    }

    @After fun cleanup() {
        MediaSessionCollector.resetInstanceForTesting()
        storage.clearAll()
    }

    private fun saveArt(bitmap: Bitmap): File {
        val metadata = MediaMetadata.Builder().putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap).build()
        val method = MediaSessionCollector::class.java.getDeclaredMethod(
            "extractAndSaveAlbumArt", String::class.java, String::class.java, String::class.java, MediaMetadata::class.java
        ).apply { isAccessible = true }
        return File(method.invoke(collector, "Song", "Artist", "Album", metadata) as String)
    }

    @Test fun unchangedArtworkDoesNotRewriteFileButMutationDoes() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val file = saveArt(bitmap)
        val sentinel = 1_600_000_000_000L
        assertTrue(file.setLastModified(sentinel))
        saveArt(bitmap)
        assertEquals(sentinel, file.lastModified())
        bitmap.eraseColor(Color.BLUE)
        saveArt(bitmap)
        assertNotEquals(sentinel, file.lastModified())
    }

    @Test fun EquivalentBitmapAndDeletedCacheFileAreHandled() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val file = saveArt(bitmap)
        val sentinel = 1_600_000_000_000L
        assertTrue(file.setLastModified(sentinel))
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        saveArt(copy)
        assertEquals(sentinel, file.lastModified())
        assertTrue(file.delete())
        assertTrue(saveArt(copy).exists())
    }

    @Test fun emptyControllerListCancelsOrphanedHeartbeat() {
        val field = MediaSessionCollector::class.java.getDeclaredField("heartbeatJob").apply { isAccessible = true }
        val heartbeat = Job()
        field.set(collector, heartbeat)
        val update = MediaSessionCollector::class.java.getDeclaredMethod("updateControllers", List::class.java)
            .apply { isAccessible = true }
        update.invoke(collector, emptyList<android.media.session.MediaController>())
        assertTrue(heartbeat.isCancelled)
        assertNull(field.get(collector))
    }
}
