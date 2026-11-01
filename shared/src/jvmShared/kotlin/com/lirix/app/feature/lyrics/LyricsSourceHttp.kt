package com.lirix.app.feature.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.Charset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

object LyricsSourceHttp {
    data class Policy(
        val maxBytes: Int = 2 * 1024 * 1024,
        val totalTimeoutMs: Long = 12_000L,
        val connectTimeoutMs: Int = 4_000,
        val readTimeoutMs: Int = 6_000,
        val maxRedirects: Int = 2
    ) {
        init {
            require(maxBytes > 0 && totalTimeoutMs > 0 && connectTimeoutMs > 0 && readTimeoutMs > 0)
            require(maxRedirects in 0..3)
        }
    }

    private val client = LyricsSourceHttpClient()

    suspend fun get(url: String, allowedHosts: Set<String>, policy: Policy = Policy()): String? =
        client.get(url, allowedHosts, policy)
}

/**
 * Small native transport shared by the additional providers; injectable for offline tests.
 * The deadline cancels and disconnects the request. OS DNS/connect implementations can still
 * take time to release their native call; the IO worker remains structured rather than leaking.
 */
class LyricsSourceHttpClient(
    private val openConnection: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val cooldownUntil = ConcurrentHashMap<String, Long>()

    suspend fun get(url: String, allowedHosts: Set<String>, policy: LyricsSourceHttp.Policy = LyricsSourceHttp.Policy()): String? {
        currentCoroutineContext().ensureActive()
        val hosts = allowedHosts.map { it.lowercase(Locale.ROOT) }.toSet()
        val first = validatedUri(url, hosts) ?: return null
        val activeConnection = AtomicReference<HttpURLConnection?>()
        // Only our deadline becomes a miss. Cancellation of the caller remains cancellation.
        return withTimeoutOrNull(policy.totalTimeoutMs) {
            coroutineScope {
                suspendCancellableCoroutine { continuation ->
                    val worker = launch(Dispatchers.IO) {
                        try {
                            continuation.resume(read(first, hosts, policy, activeConnection))
                        } catch (error: CancellationException) {
                            continuation.cancel(error)
                        } catch (_: Exception) {
                            continuation.resume(null)
                        } finally {
                            activeConnection.getAndSet(null)?.let { runCatching { it.disconnect() } }
                        }
                    }
                    continuation.invokeOnCancellation {
                        // Java socket reads are not reliably interruptible. Closing the connection
                        // releases the read as well as cancelling the coroutine that owns it.
                        activeConnection.getAndSet(null)?.let { runCatching { it.disconnect() } }
                        worker.cancel()
                    }
                }
            }
        }
    }

    private suspend fun read(
        first: URI,
        hosts: Set<String>,
        policy: LyricsSourceHttp.Policy,
        active: AtomicReference<HttpURLConnection?>
    ): String? {
        var uri = first
        repeat(policy.maxRedirects + 1) { redirectCount ->
            currentCoroutineContext().ensureActive()
            val host = uri.host.lowercase(Locale.ROOT)
            if ((cooldownUntil[host] ?: 0L) > nowMillis()) return null
            val connection = openConnection(uri)
            active.set(connection)
            try {
                currentCoroutineContext().ensureActive()
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.useCaches = false
                connection.connectTimeout = minOf(policy.connectTimeoutMs.toLong(), policy.totalTimeoutMs).toInt()
                connection.readTimeout = minOf(policy.readTimeoutMs.toLong(), policy.totalTimeoutMs).toInt()
                connection.setRequestProperty("User-Agent", "Lirix/1.5 lyrics-source")
                connection.setRequestProperty("Accept", "application/json,text/html,text/plain;q=0.9")
                connection.setRequestProperty("Accept-Encoding", "identity")
                when (connection.responseCode) {
                    301, 302, 303, 307, 308 -> {
                        if (redirectCount >= policy.maxRedirects) return null
                        val location = connection.getHeaderField("Location") ?: return null
                        uri = validatedUri(uri.resolve(location).toString(), hosts) ?: return null
                    }
                    403, 429 -> {
                        recordCooldown(host, connection.getHeaderField("Retry-After"))
                        return null
                    }
                    200 -> {
                        if (connection.contentLengthLong > policy.maxBytes) return null
                        val bytes = connection.inputStream.use { stream ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = stream.read(buffer)
                                if (count < 0) break
                                if (count > policy.maxBytes - output.size()) return null
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        val charset = Regex("charset\\s*=\\s*[\"']?([a-zA-Z0-9_-]+)", RegexOption.IGNORE_CASE)
                            .find(connection.contentType.orEmpty())?.groupValues?.get(1)
                            ?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
                        return bytes.toString(charset)
                    }
                    else -> return null
                }
            } finally {
                active.compareAndSet(connection, null)
                runCatching { connection.disconnect() }
            }
        }
        return null
    }

    private fun recordCooldown(host: String, retryAfter: String?) {
        val now = nowMillis()
        val seconds = retryAfter?.trim()?.toLongOrNull()?.coerceAtLeast(0L)
        val retryMs = if (seconds != null) {
            if (seconds > Long.MAX_VALUE / 1000L) Long.MAX_VALUE else seconds * 1000L
        } else {
            runCatching {
                ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now
            }.getOrDefault(0L)
        }
        val duration = maxOf(60_000L, retryMs)
        if (cooldownUntil.size >= 64 && !cooldownUntil.containsKey(host)) {
            cooldownUntil.entries.minByOrNull { it.value }?.let { cooldownUntil.remove(it.key, it.value) }
        }
        val until = if (duration > Long.MAX_VALUE - now) Long.MAX_VALUE else now + duration
        cooldownUntil.merge(host, until) { previous, next -> maxOf(previous, next) }
    }

    private fun validatedUri(url: String, allowedHosts: Set<String>): URI? = runCatching {
        val uri = URI(url)
        require(uri.scheme.equals("https", true))
        require(uri.host?.lowercase(Locale.ROOT) in allowedHosts)
        require(uri.userInfo == null && uri.fragment == null && uri.port in setOf(-1, 443))
        uri
    }.getOrNull()
}
