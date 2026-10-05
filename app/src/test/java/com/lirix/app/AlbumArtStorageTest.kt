package com.lirix.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.storage.AlbumArtStorage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Unit-tests for AlbumArtStorage (TEST-ART-01 / TASK-ART-01).
 * Verifies:
 * 1. Saving valid bitmap to local file in album_art/{trackKey}.webp.
 * 2. Downscaling large bitmaps (>512x512) to fit within MAX_DIMENSION (512px).
 * 3. Handling empty or blank trackKey gracefully (returns null, no crash).
 * 4. Handling recycled bitmap gracefully (returns null, no crash).
 * 5. getAlbumArtPath retrieval and file existence check.
 * 6. Deletion of single album art and clearAll().
 */
@RunWith(AndroidJUnit4::class)
class AlbumArtStorageTest {

    private lateinit var context: Context
    private lateinit var storage: AlbumArtStorage

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        storage = AlbumArtStorage(context)
        storage.clearAll()
    }

    @After
    fun tearDown() {
        storage.clearAll()
    }

    @Test
    fun testSaveBitmap_standardSize_savesSuccessfully() = runBlocking {
        val trackKey = "sha256_starboy"
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)

        val savedPath = storage.saveBitmap(trackKey, bitmap)
        assertNotNull("Path must be returned on successful save", savedPath)

        val file = File(savedPath!!)
        assertTrue("Saved file must exist on disk", file.exists())
        assertTrue("Saved file must not be empty", file.length() > 0)
        assertTrue("File path must end with .webp", savedPath.endsWith("$trackKey.webp"))

        val retrievedPath = storage.getAlbumArtPath(trackKey)
        assertEquals("getAlbumArtPath must return same path", savedPath, retrievedPath)
    }

    @Test
    fun testSaveBitmap_largeDimensions_downscalesToMax512() = runBlocking {
        val trackKey = "sha256_hires_art"
        // Create 1200x800 bitmap (aspect ratio 1.5:1)
        val largeBitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)

        val savedPath = storage.saveBitmap(trackKey, largeBitmap)
        assertNotNull(savedPath)

        // Read dimensions back without loading entire image into memory
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(savedPath, options)

        assertTrue("Width must be <= MAX_DIMENSION (512)", options.outWidth <= AlbumArtStorage.MAX_DIMENSION)
        assertTrue("Height must be <= MAX_DIMENSION (512)", options.outHeight <= AlbumArtStorage.MAX_DIMENSION)
        // With 1200x800 -> 512x341
        assertEquals("Width must be exactly 512", 512, options.outWidth)
        assertEquals("Height must be downscaled proportionally to 341", 341, options.outHeight)
    }

    @Test
    fun testSaveBitmap_emptyOrBlankTrackKey_returnsNull() = runBlocking {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val emptyResult = storage.saveBitmap("", bitmap)
        assertNull("Empty trackKey must return null", emptyResult)

        val blankResult = storage.saveBitmap("   ", bitmap)
        assertNull("Blank trackKey must return null", blankResult)
    }

    @Test
    fun testSaveBitmap_recycledBitmap_returnsNull() = runBlocking {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bitmap.recycle()

        val result = storage.saveBitmap("recycled_track", bitmap)
        assertNull("Recycled bitmap must return null", result)
    }

    @Test
    fun testGetAlbumArtPath_nonExistentTrack_returnsNull() {
        val path = storage.getAlbumArtPath("non_existent_key")
        assertNull("Non-existent track must return null", path)
    }

    @Test
    fun testDeleteAlbumArt_removesFile() = runBlocking {
        val trackKey = "sha256_delete_me"
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val savedPath = storage.saveBitmap(trackKey, bitmap)
        assertNotNull(savedPath)
        assertTrue(File(savedPath!!).exists())

        val deleted = storage.deleteAlbumArt(trackKey)
        assertTrue("deleteAlbumArt must return true", deleted)
        assertNull("getAlbumArtPath must return null after deletion", storage.getAlbumArtPath(trackKey))
        assertFalse("File must no longer exist", File(savedPath).exists())
    }

    @Test
    fun testClearAll_cleansDirectory() = runBlocking {
        val b1 = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val b2 = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)

        storage.saveBitmap("track1", b1)
        storage.saveBitmap("track2", b2)

        assertNotNull(storage.getAlbumArtPath("track1"))
        assertNotNull(storage.getAlbumArtPath("track2"))

        assertTrue("clearAll must return true", storage.clearAll())
        assertNull(storage.getAlbumArtPath("track1"))
        assertNull(storage.getAlbumArtPath("track2"))
    }
}
