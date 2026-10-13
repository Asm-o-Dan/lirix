package com.lirix.app

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.share.ShareManager
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.ingestion.MediaSessionCollector
import com.lirix.app.ingestion.NotificationListener
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Verification unit tests for TASK-CORE-01 (.sdd/specs/media-core/core_security_and_stability_spec.md).
 * Covers:
 * 1. ACCESS_NETWORK_STATE permission declaration in manifest.
 * 2. ShareManager safely using FileProvider without FileUriExposedException or Uri.fromFile.
 * 3. MediaSessionCollector thread-safe singleton behavior.
 * 4. NotificationListener lifecycle independence (onDestroy does not wipe global MediaSessionCollector).
 * 5. Build script does not expose plaintext keystore passwords.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class CoreSecurityAndStabilityTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val providerInfo = android.content.pm.ProviderInfo().apply {
            authority = "${context.packageName}.fileprovider"
            grantUriPermissions = true
            metaData = android.os.Bundle().apply {
                putInt("android.support.FILE_PROVIDER_PATHS", R.xml.file_paths)
            }
        }
        Robolectric.buildContentProvider(androidx.core.content.FileProvider::class.java).create(providerInfo)
    }

    @Test
    fun test_manifest_declaresAccessNetworkState() {
        val manifestFile = File("src/main/AndroidManifest.xml").let {
            if (it.exists()) it else File("app/src/main/AndroidManifest.xml")
        }
        assertTrue("AndroidManifest.xml must exist", manifestFile.exists())
        val content = manifestFile.readText()
        assertTrue(
            "AndroidManifest.xml must declare android.permission.ACCESS_NETWORK_STATE",
            content.contains("android.permission.ACCESS_NETWORK_STATE")
        )
    }

    @Test
    fun test_mediaSessionCollector_isThreadSafeSingleton() {
        val instance1 = MediaSessionCollector.getInstance(context)
        val instance2 = MediaSessionCollector.getInstance(context)
        assertSame("MediaSessionCollector.getInstance must return the exact same instance", instance1, instance2)

        // Concurrent execution test
        val executor = Executors.newFixedThreadPool(8)
        val instances = java.util.Collections.synchronizedList(mutableListOf<MediaSessionCollector>())

        for (i in 0 until 50) {
            executor.submit {
                instances.add(MediaSessionCollector.getInstance(context))
            }
        }
        executor.shutdown()
        assertTrue("Executor must finish within 3 seconds", executor.awaitTermination(3, TimeUnit.SECONDS))

        for (inst in instances) {
            assertSame("All concurrent getInstance calls must return identical singleton", instance1, inst)
        }
    }

    @Test
    fun test_notificationListener_destroyDoesNotWipeMediaSessionCollector() {
        val collectorBefore = MediaSessionCollector.getInstance(context)

        // Simulate NotificationListener lifecycle
        val controller = Robolectric.buildService(NotificationListener::class.java).create()
        controller.destroy()

        val collectorAfter = MediaSessionCollector.getInstance(context)
        assertSame(
            "MediaSessionCollector instance must NOT be destroyed when NotificationListener is destroyed",
            collectorBefore,
            collectorAfter
        )
    }

    @Test
    fun test_shareManager_doesNotThrowFileUriExposedException() = runBlocking {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val success = ShareManager.shareBitmap(
            context = context,
            bitmap = bitmap,
            chooserTitle = "Security Verification"
        )
        // Verify method executes safely without FileUriExposedException
        val sharesDir = File(context.cacheDir, "shares")
        assertTrue("Shares directory must exist", sharesDir.exists())
    }

    @Test
    fun test_buildGradle_doesNotContainPlaintextPasswords() {
        val buildGradleFile = File("build.gradle.kts").let {
            if (it.exists()) it else File("app/build.gradle.kts")
        }
        assertTrue("build.gradle.kts must exist", buildGradleFile.exists())
        val content = buildGradleFile.readText()
        assertFalse(
            "build.gradle.kts must not contain hardcoded plaintext release password 'lirixrelease2026'",
            content.contains("lirixrelease2026")
        )
    }
}
