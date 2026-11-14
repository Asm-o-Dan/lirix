package com.lirix.app.feature.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.Locale
import kotlin.coroutines.coroutineContext

/** Typed transport outcome for rule fetches (SPEC-TEACH-06 R11). */
sealed interface RuleFetchResult {
    data class Body(val html: String, val finalUrl: String) : RuleFetchResult
    data class HttpError(val url: String, val code: Int) : RuleFetchResult
    data class Blocked(val url: String, val reason: BlockReason, val httpCode: Int? = null) : RuleFetchResult
    data class Timeout(val url: String) : RuleFetchResult
    data class Failure(val url: String) : RuleFetchResult
}

enum class BlockReason { UNSAFE_URL, FOREIGN_HOST, TOO_MANY_REDIRECTS, ACCESS_DENIED, RATE_LIMITED, TOO_LARGE }

/** Network boundary of the rule engine; tests replace it with HTML fixtures. */
fun interface RuleFetcher {
    suspend fun fetch(url: String, ruleDomain: String): RuleFetchResult
}

/**
 * URL policy shared by search URLs, result links and redirects: HTTPS only, the rule's
 * domain or its subdomains, no IP literals, loopback, local names, credentials or custom ports.
 */
object RuleUrlPolicy {
    private val IPV4_LITERAL = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")
    private val LOCAL_SUFFIXES = listOf(".local", ".localhost", ".internal", ".lan", ".home", ".arpa")

    fun normalizeDomain(domain: String): String =
        domain.trim().lowercase(Locale.ROOT).removePrefix("www.").trimEnd('.')

    fun hostMatchesDomain(host: String, domain: String): Boolean {
        val h = host.lowercase(Locale.ROOT).trimEnd('.')
        val d = normalizeDomain(domain)
        return d.isNotBlank() && (h == d || h.endsWith(".$d"))
    }

    /** Returns null when [url] may be fetched for a rule of [ruleDomain]. */
    fun check(url: String, ruleDomain: String): BlockReason? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return BlockReason.UNSAFE_URL
        if (!uri.scheme.equals("https", ignoreCase = true)) return BlockReason.UNSAFE_URL
        if (uri.rawUserInfo != null) return BlockReason.UNSAFE_URL
        if (uri.port != -1 && uri.port != 443) return BlockReason.UNSAFE_URL
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return BlockReason.UNSAFE_URL
        if (host.isBlank() || host == "localhost" || host.contains(':') || host.startsWith('[') ||
            IPV4_LITERAL.matches(host) || LOCAL_SUFFIXES.any { host.endsWith(it) } || !host.contains('.')
        ) {
            return BlockReason.UNSAFE_URL
        }
        if (!hostMatchesDomain(host, ruleDomain)) return BlockReason.FOREIGN_HOST
        return null
    }

    fun isPrivateAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress
        ) {
            return true
        }
        val bytes = address.address
        return when (address) {
            // 100.64.0.0/10 carrier-grade NAT
            is Inet4Address -> (bytes[0].toInt() and 0xFF) == 100 && (bytes[1].toInt() and 0xC0) == 64
            // fc00::/7 unique local
            is Inet6Address -> (bytes[0].toInt() and 0xFE) == 0xFC
            else -> false
        }
    }
}

/**
 * Bounded HTTP client for autonomous rules: 6 s connect / 8 s read, at most 5 validated
 * redirects, 2 MB bodies, 2 concurrent requests, no retries (403/429 are reported, not retried).
 * DNS answers are checked against private ranges before each hop.
 */
object RuleHttpClient : RuleFetcher {
    const val CONNECT_TIMEOUT_MS = 6_000
    const val READ_TIMEOUT_MS = 8_000
    const val MAX_REDIRECTS = 5
    const val MAX_BODY_BYTES = 2 * 1024 * 1024
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    private val concurrency = Semaphore(2)

    override suspend fun fetch(url: String, ruleDomain: String): RuleFetchResult =
        concurrency.withPermit { withContext(Dispatchers.IO) { fetchFollowingRedirects(url, ruleDomain) } }

    private suspend fun fetchFollowingRedirects(startUrl: String, ruleDomain: String): RuleFetchResult {
        var current = startUrl
        for (hop in 0..MAX_REDIRECTS) {
            coroutineContext.ensureActive()
            RuleUrlPolicy.check(current, ruleDomain)?.let { return RuleFetchResult.Blocked(current, it) }
            val host = URI(current).host
            val addresses = try {
                InetAddress.getAllByName(host)
            } catch (_: UnknownHostException) {
                return RuleFetchResult.Failure(current)
            }
            if (addresses.isEmpty() || addresses.any { RuleUrlPolicy.isPrivateAddress(it) }) {
                return RuleFetchResult.Blocked(current, BlockReason.UNSAFE_URL)
            }
            when (val step = request(current)) {
                is Step.Redirect -> current = step.location
                is Step.Done -> return step.result
            }
        }
        return RuleFetchResult.Blocked(startUrl, BlockReason.TOO_MANY_REDIRECTS)
    }

    private sealed interface Step {
        data class Redirect(val location: String) : Step
        data class Done(val result: RuleFetchResult) : Step
    }

    private suspend fun request(url: String): Step {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.5")
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
        }
        // Cancellation closes the socket instead of waiting for the read timeout.
        val cancelHandle = coroutineContext.job.invokeOnCompletion { connection.disconnect() }
        return try {
            val code = connection.responseCode
            when {
                code in 300..399 -> {
                    val location = connection.getHeaderField("Location")
                        ?: return Step.Done(RuleFetchResult.Failure(url))
                    val next = runCatching { URI(url).resolve(location.trim()).toString() }.getOrNull()
                        ?: return Step.Done(RuleFetchResult.Blocked(url, BlockReason.UNSAFE_URL))
                    Step.Redirect(next)
                }
                code == 401 || code == 403 -> Step.Done(RuleFetchResult.Blocked(url, BlockReason.ACCESS_DENIED, code))
                code == 429 -> Step.Done(RuleFetchResult.Blocked(url, BlockReason.RATE_LIMITED, code))
                code in 200..299 -> {
                    val declared = connection.contentLengthLong
                    if (declared > MAX_BODY_BYTES) return Step.Done(RuleFetchResult.Blocked(url, BlockReason.TOO_LARGE))
                    val bytes = connection.inputStream.use { readBounded(it) }
                        ?: return Step.Done(RuleFetchResult.Blocked(url, BlockReason.TOO_LARGE))
                    Step.Done(RuleFetchResult.Body(decode(bytes, connection.contentType), url))
                }
                else -> Step.Done(RuleFetchResult.HttpError(url, code))
            }
        } catch (_: SocketTimeoutException) {
            Step.Done(RuleFetchResult.Timeout(url))
        } catch (_: IOException) {
            coroutineContext.ensureActive()
            Step.Done(RuleFetchResult.Failure(url))
        } finally {
            cancelHandle.dispose()
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_BODY_BYTES) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    internal fun decode(bytes: ByteArray, contentType: String?): String {
        val declared = contentType?.let { Regex("""charset=["']?([\w-]+)""", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
        val sniffed = declared ?: Regex("""<meta[^>]+charset=["']?([\w-]+)""", RegexOption.IGNORE_CASE)
            .find(String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1))?.groupValues?.get(1)
        val charset = sniffed?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        return String(bytes, charset)
    }
}
