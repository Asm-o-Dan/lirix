package com.lirix.app.feature.share.animation

import android.app.Application
import android.content.ContentResolver
import android.content.ContextWrapper
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.share.ShareManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], application = Application::class)
class ShareAnimatedPosterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        val authority = "${context.packageName}.fileprovider"
        val info = context.packageManager.resolveContentProvider(authority, android.content.pm.PackageManager.GET_META_DATA)
        assertNotNull("Actual manifest must declare the sharing provider", info)
        Robolectric.buildContentProvider(FileProvider::class.java).create(info)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Actual FileProvider URI/bytes and chooser are exercised by ShareProviderDeviceTest on Android.

    @Test
    fun shareAnimatedPoster_rejectsEmptyOrNonExistentFile() = runTest {
        val nonExistent = File(tempFolder.root, "missing.mp4")
        assertFalse(ShareManager.shareAnimatedPoster(context, nonExistent))

        val emptyFile = File(tempFolder.root, "empty.mp4").apply { createNewFile() }
        assertFalse(ShareManager.shareAnimatedPoster(context, emptyFile))
    }

    @Test
    fun shareAnimatedPoster_rejectsFileOutsideConfiguredProviderRoot() = runTest {
        val outside = File(tempFolder.root, "outside.mp4").apply { writeBytes(byteArrayOf(1)) }
        assertFalse(ShareManager.shareAnimatedPoster(context, outside))
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    fun shareAnimatedPoster_missingProviderReturnsFalseWithoutChooser() = runTest {
        val file = File(File(context.cacheDir, "shares").apply { mkdirs() }, "missing-provider.mp4")
            .apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val invalidAuthority = object : ContextWrapper(context) {
            override fun getPackageName() = "com.lirix.missing.provider"
        }
        assertFalse(ShareManager.shareAnimatedPoster(invalidAuthority, file))
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test
    fun saveVideo_copiesSourceBytesToDestinationUri() = runTest {
        val sourceFile = File(tempFolder.root, "source.mp4").apply {
            writeBytes("MP4_TEST_BYTES".toByteArray())
        }
        val destFile = File(tempFolder.root, "dest.mp4")
        val destUri = Uri.fromFile(destFile)

        ShareManager.saveVideo(context, sourceFile, destUri)

        assertTrue(destFile.exists())
        assertEquals("MP4_TEST_BYTES", destFile.readText())
    }

    @Test
    fun saveVideo_destinationCannotOpenOrWriteReportsFailure() = runTest {
        val source = File(tempFolder.root, "source-failure.mp4").apply { writeBytes(byteArrayOf(9, 8, 7)) }
        val destination = Uri.parse("content://destination/failure")
        val resolver = mock(ContentResolver::class.java)
        val failingContext = mock(Context::class.java)
        `when`(failingContext.contentResolver).thenReturn(resolver)
        `when`(resolver.openOutputStream(destination)).thenReturn(null)
        assertTrue(runCatching { ShareManager.saveVideo(failingContext, source, destination) }.isFailure)
        doThrow(FileNotFoundException("open denied")).`when`(resolver).openOutputStream(destination)
        assertTrue(runCatching { ShareManager.saveVideo(failingContext, source, destination) }.isFailure)
        doAnswer {
            object : OutputStream() { override fun write(value: Int) { throw IOException("disk full") } }
        }.`when`(resolver).openOutputStream(destination)
        val writeFailure = runCatching { ShareManager.saveVideo(failingContext, source, destination) }
        assertTrue(writeFailure.exceptionOrNull() is IOException)
        assertEquals("disk full", writeFailure.exceptionOrNull()?.message)
        assertArrayEquals(byteArrayOf(9, 8, 7), source.readBytes())
    }
}
