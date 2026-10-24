package com.lirix.app.feature.share.animation

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.share.ShareManager
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** AndroidX FileProvider assumes Android filesystem paths; test actual URI access on Android. */
@RunWith(AndroidJUnit4::class)
class ShareProviderDeviceTest {
    @Test fun actualProviderSharesExactBytesWithReadGrantAndRejectsOutsideRoot() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val launched = AtomicReference<Intent?>()
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { check(launched.compareAndSet(null, intent)) }
        }
        // Transport fixture: codec/container correctness is covered independently by Mp4DeviceCodecTest.
        val bytes = ByteArray(4096) { ((it * 31 + 7) % 256).toByte() }
        val file = File(File(app.cacheDir, "shares").apply { mkdirs() }, "provider-transport-test.mp4")
        val outside = File(app.filesDir, "provider-outside-test.mp4")
        try {
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", file)
            assertArrayEquals(bytes, checkNotNull(app.contentResolver.openInputStream(uri)).use { it.readBytes() })
            assertTrue(ShareManager.shareAnimatedPoster(context, file, "Share transport test"))
            val chooser = checkNotNull(launched.get())
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION") val send = checkNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
            assertEquals(Intent.ACTION_SEND, send.action)
            assertEquals("video/mp4", send.type)
            assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            @Suppress("DEPRECATION") val exported = checkNotNull(send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            assertEquals("content", exported.scheme)
            assertEquals(uri, exported)
            assertArrayEquals(bytes, checkNotNull(app.contentResolver.openInputStream(exported)).use { it.readBytes() })
            launched.set(null)
            outside.writeBytes(bytes)
            assertFalse(ShareManager.shareAnimatedPoster(context, outside))
            assertNull(launched.get())
        } finally { file.delete(); outside.delete() }
    }
}