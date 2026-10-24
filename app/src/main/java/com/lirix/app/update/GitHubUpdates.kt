package com.lirix.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AndroidRelease(val version: String, val url: String, val size: Long, val digest: String)

/** Public releases only. Never includes credentials or downloads an arbitrary URL from release text. */
object GitHubUpdates {
    private const val PREFIX = "https://github.com/Asm-o-Dan/lirix/releases/download/"
    internal fun version(value: String): List<Int>? = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$")
        .matchEntire(value)?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return null }

    internal fun newer(candidate: String, installed: String): Boolean {
        val a = version(candidate) ?: return false
        val b = version(installed) ?: return false
        return a.zip(b).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    }

    internal fun select(json: String, installed: String): AndroidRelease? {
        val releases = JSONArray(json)
        val candidates = mutableListOf<AndroidRelease>()
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft") || release.optBoolean("prerelease")) continue
            val tag = release.optString("tag_name")
            if (!newer(tag, installed)) continue
            val assets = release.optJSONArray("assets") ?: continue
            val apks = (0 until assets.length()).map { assets.getJSONObject(it) }.filter {
                it.optString("name").endsWith(".apk", true) && it.optString("state") == "uploaded"
            }
            // Ambiguous ABI/debug variants must be explicitly resolved by the publisher.
            if (apks.size != 1) continue
            val apk = apks.single()
            val url = apk.optString("browser_download_url")
            val expectedPrefix = "$PREFIX$tag/"
            val size = apk.optLong("size")
            if (!url.startsWith(expectedPrefix) || size !in 1..300_000_000L) continue
            val digest = apk.optString("digest").takeIf { Regex("sha256:[0-9a-fA-F]{64}").matches(it) }?.substringAfter(':') ?: continue
            candidates += AndroidRelease(tag.removePrefix("v"), url, size, digest)
        }
        return candidates.maxWithOrNull(compareBy<AndroidRelease> { version(it.version)!![0] }
            .thenBy { version(it.version)!![1] }.thenBy { version(it.version)!![2] })
    }

    private fun connection(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", "Lirix-Android-Updater")
        setRequestProperty("Accept", "application/vnd.github+json")
    }

    suspend fun check(context: Context): AndroidRelease? = withContext(Dispatchers.IO) {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        val connection = connection("https://api.github.com/repos/Asm-o-Dan/lirix/releases?per_page=30")
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    check(output.size() + n <= 2_000_000) { "Release metadata too large" }
                    output.write(buffer, 0, n)
                }
                output.toByteArray()
            }
            select(bytes.toString(Charsets.UTF_8), installed)
        } finally { connection.disconnect() }
    }

    suspend fun download(context: Context, release: AndroidRelease, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        require(release.url.startsWith(PREFIX))
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File.createTempFile("update-", ".part", directory)
        val target = File(directory, "update.apk")
        target.delete()
        val connection = connection(release.url).apply { setRequestProperty("Accept", "application/octet-stream") }
        try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            connection.inputStream.use { input -> partial.outputStream().use { output ->
                copyVerified(input, output, release.size, release.digest, progress)
            } }
            currentCoroutineContext().ensureActive()
            verify(context, partial)
            check(partial.renameTo(target)) { "Cannot save APK" }
            target
        } finally { connection.disconnect(); partial.delete() }
    }

    internal suspend fun copyVerified(input: InputStream, output: OutputStream, size: Long, digest: String,
                                      progress: (Float) -> Unit) {
        val hash = MessageDigest.getInstance("SHA-256")
        var count = 0L
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer)
            if (n < 0) break
            count += n
            check(count <= size) { "APK size mismatch" }
            output.write(buffer, 0, n)
            hash.update(buffer, 0, n)
            progress(count.toFloat() / size)
        }
        check(count == size) { "Incomplete APK" }
        val actual = hash.digest().joinToString("") { "%02x".format(it) }
        check(actual.equals(digest, true)) { "APK checksum mismatch" }
    }

    @Suppress("DEPRECATION")
    fun verify(context: Context, apk: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val candidate = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: error("Invalid APK")
        val installed = pm.getPackageInfo(context.packageName, flags)
        check(candidate.packageName == context.packageName) { "Wrong package" }
        val candidateVersion = if (Build.VERSION.SDK_INT >= 28) candidate.longVersionCode else candidate.versionCode.toLong()
        val installedVersion = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        check(candidateVersion > installedVersion) { "APK is not newer" }
        val old = if (Build.VERSION.SDK_INT >= 28) installed.signingInfo?.apkContentsSigners else installed.signatures
        val fresh = if (Build.VERSION.SDK_INT >= 28) candidate.signingInfo?.apkContentsSigners else candidate.signatures
        check(!old.isNullOrEmpty() && !fresh.isNullOrEmpty() && old.toSet() == fresh.toSet()) { "SIGNATURE_MISMATCH" }
    }
}
