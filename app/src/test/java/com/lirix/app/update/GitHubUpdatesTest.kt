package com.lirix.app.update

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GitHubUpdatesTest {
    // Published SHA-256 standard vector; expected hash is not calculated by the implementation.
    private val abcDigest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test @Config(sdk = [26])
    @Suppress("DEPRECATION")
    fun rejectsWrongPackageDowngradeAndWrongSignerBeforeInstaller() {
        val context = org.mockito.Mockito.mock(android.content.Context::class.java)
        val pm = org.mockito.Mockito.mock(android.content.pm.PackageManager::class.java)
        org.mockito.Mockito.`when`(context.packageName).thenReturn("com.lirix.app")
        org.mockito.Mockito.`when`(context.packageManager).thenReturn(pm)
        fun info(name: String, code: Int, signer: String) = android.content.pm.PackageInfo().apply {
            packageName = name; versionCode = code
            signatures = arrayOf(android.content.pm.Signature(signer.toByteArray()))
            applicationInfo = android.content.pm.ApplicationInfo().apply {
                metaData = android.os.Bundle().apply { putString("com.lirix.translation.variant", com.lirix.app.BuildConfig.TRANSLATION_VARIANT) }
            }
        }
        val installed = info("com.lirix.app", 19, "permanent")
        val file = java.io.File("candidate.apk")
        org.mockito.Mockito.`when`(pm.getPackageInfo("com.lirix.app", (android.content.pm.PackageManager.GET_SIGNATURES or android.content.pm.PackageManager.GET_META_DATA))).thenReturn(installed)
        for (bad in listOf(info("evil.package", 20, "permanent"), info("com.lirix.app", 19, "permanent"),
            info("com.lirix.app", 18, "permanent"), info("com.lirix.app", 20, "other-key"),
            info("com.lirix.app", 20, "permanent").apply { applicationInfo!!.metaData = null },
            info("com.lirix.app", 20, "permanent").apply { applicationInfo!!.metaData!!.putString("com.lirix.translation.variant", "unknown") },
            info("com.lirix.app", 20, "permanent").apply { applicationInfo!!.metaData!!.putString("com.lirix.translation.variant", if (com.lirix.app.BuildConfig.TRANSLATION_VARIANT == "private") "compact" else "private") })) {
            org.mockito.Mockito.`when`(pm.getPackageArchiveInfo(file.absolutePath, (android.content.pm.PackageManager.GET_SIGNATURES or android.content.pm.PackageManager.GET_META_DATA))).thenReturn(bad)
            var rejected = false
            try { GitHubUpdates.verify(context, file) } catch (_: IllegalStateException) { rejected = true }
            assertTrue(rejected)
        }
        org.mockito.Mockito.`when`(pm.getPackageArchiveInfo(file.absolutePath, (android.content.pm.PackageManager.GET_SIGNATURES or android.content.pm.PackageManager.GET_META_DATA)))
            .thenReturn(info("com.lirix.app", 20, "permanent"))
        GitHubUpdates.verify(context, file)
    }

    @Test fun verifiesExactBytesAndProgressAgainstKnownSha256Vector() = runBlocking {
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Float>()
        GitHubUpdates.copyVerified(ByteArrayInputStream("abc".toByteArray()), output, 3, abcDigest) { progress += it }
        assertArrayEquals("abc".toByteArray(), output.toByteArray())
        assertEquals(1f, progress.last())
    }

    @Test fun refusesTruncatedOversizedAndSameLengthCorruptedTransfers() = runBlocking {
        for (payload in listOf("ab", "abcd", "abd")) {
            var rejected = false
            try {
                GitHubUpdates.copyVerified(ByteArrayInputStream(payload.toByteArray()), ByteArrayOutputStream(), 3, abcDigest) {}
            } catch (_: IllegalStateException) { rejected = true }
            assertTrue(payload, rejected)
        }
    }

    @Test fun cancellationStopsBeforeWritingMoreChunks() = runBlocking {
        val output = ByteArrayOutputStream()
        val job = launch(start = CoroutineStart.LAZY) {
            GitHubUpdates.copyVerified(ByteArrayInputStream(ByteArray(200_000)), output, 200_000, "0".repeat(64)) {
                coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            }
        }
        job.start(); job.join()
        assertTrue(job.isCancelled)
        assertEquals(65536, output.size())
    }
    private fun release(tag: String = "v1.6.0", prerelease: Boolean = false, draft: Boolean = false,
                        url: String = "https://github.com/Asm-o-Dan/lirix/releases/download/$tag/lirix-$tag-private.apk",
                        digest: String = "sha256:" + "a".repeat(64), copies: Int = 1): JSONObject {
        val assets = JSONArray()
        repeat(copies) { assets.put(JSONObject().put("name", "lirix-$tag-private.apk").put("state", "uploaded")
            .put("size", 19116225).put("browser_download_url", url).put("digest", digest)) }
        return JSONObject().put("tag_name", tag).put("prerelease", prerelease).put("draft", draft).put("assets", assets)
    }

    @Test fun comparesNumericVersionsWithoutDowngradesOrPreviewTags() {
        assertTrue(GitHubUpdates.newer("v1.10.0", "1.9.9"))
        assertTrue(GitHubUpdates.newer("2.0.0", "1.99.99"))
        for (candidate in listOf("v1.5.1", "v1.4.99", "v1.6.0-beta", "latest", "v999999999999.0.0"))
            assertFalse(candidate, GitHubUpdates.newer(candidate, "1.5.1"))
    }

    @Test fun picksHighestStableAndroidReleaseRegardlessOfListOrder() {
        val json = JSONArray().put(release("v1.6.0")).put(release("v3.0.0", prerelease = true))
            .put(release("v4.0.0", draft = true)).put(release("v1.10.0")).toString()
        val result = GitHubUpdates.select(json, "1.5.1")!!
        assertEquals("1.10.0", result.version)
        assertEquals(19116225L, result.size)
        assertEquals("a".repeat(64), result.digest)
    }

    @Test fun refusesForeignUrlsMissingDigestsAndAmbiguousApks() {
        for (bad in listOf(release(url = "https://evil.example/app.apk"),
            release(url = "https://github.com/Asm-o-Dan/lirix/releases/download/v1.9.0/a.apk"),
            release(digest = ""), release(digest = "sha256:broken"), release(copies = 2), release(copies = 0))) {
            assertNull(GitHubUpdates.select(JSONArray().put(bad).toString(), "1.5.1"))
        }
    }
    @Test fun eachVariantOnlyReceivesItsOwnExactAssetAndNeverGuessesLegacyOrOpposite() {
        val both = release()
        val compact = JSONObject(both.getJSONArray("assets").getJSONObject(0).toString())
            .put("name", "lirix-v1.6.0-compact.apk")
            .put("browser_download_url", "https://github.com/Asm-o-Dan/lirix/releases/download/v1.6.0/lirix-v1.6.0-compact.apk")
        both.getJSONArray("assets").put(compact)
        val json = JSONArray().put(both).toString()
        assertTrue(GitHubUpdates.select(json, "1.5.1", "private")!!.url.endsWith("-private.apk"))
        assertTrue(GitHubUpdates.select(json, "1.5.1", "compact")!!.url.endsWith("-compact.apk"))
        assertNull(GitHubUpdates.select(json, "1.5.1", "other"))
        assertNull(GitHubUpdates.select(JSONArray().put(release()).toString(), "1.5.1", "compact"))
        for (name in listOf("lirix-v1.6.0.apk", "lirix-v1.6.0-compact.apk", "lirix-v1.6.0-private-debug.apk")) {
            val misleading = release()
            misleading.getJSONArray("assets").getJSONObject(0).put("name", name)
            assertNull(GitHubUpdates.select(JSONArray().put(misleading).toString(), "1.5.1", "private"))
        }
    }

}
