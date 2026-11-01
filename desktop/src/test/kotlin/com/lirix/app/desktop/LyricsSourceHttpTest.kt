package com.lirix.app.desktop

import com.lirix.app.feature.lyrics.LyricsSourceHttp
import com.lirix.app.feature.lyrics.LyricsSourceHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LyricsSourceHttpTest {
    private val hosts = setOf("source.example")
    private val url = "https://source.example/song"

    private class Reply(
        val code: Int = 200,
        val bytes: ByteArray = "Synthetic document".toByteArray(),
        val headers: Map<String, String> = emptyMap(),
        val length: Long = bytes.size.toLong(),
        val stream: InputStream? = null,
        val onDisconnect: () -> Unit = {}
    ) : HttpURLConnection(URL("https://source.example/song")) {
        val disconnected = AtomicBoolean(false)
        override fun connect() {}
        override fun usingProxy(): Boolean = false
        override fun disconnect() { disconnected.set(true); onDisconnect() }
        override fun getResponseCode(): Int = code
        override fun getInputStream(): InputStream = stream ?: ByteArrayInputStream(bytes)
        override fun getContentLengthLong(): Long = length
        override fun getContentType(): String = headers["Content-Type"] ?: "text/plain; charset=utf-8"
        override fun getHeaderField(name: String): String? = headers[name]
    }

    @Test fun onlyExplicitHttpsHostsAreOpenedAndRedirectsAreValidatedBeforeFollowing() = runBlocking {
        val opened = mutableListOf<URI>()
        val reply = Reply(code = 302, headers = mapOf("Location" to "https://other.example/song"))
        val client = LyricsSourceHttpClient({ uri -> opened += uri; reply })
        for (bad in listOf("http://source.example/song", "https://source.example.evil/song",
            "https://user@source.example/song", "https://source.example:8443/song", "$url#fragment")) {
            assertNull(client.get(bad, hosts))
        }
        assertEquals(0, opened.size)
        assertNull(client.get(url, hosts))
        assertEquals(listOf(URI(url)), opened)
        assertTrue(reply.disconnected.get())
        assertFalse(reply.instanceFollowRedirects)
    }

    @Test fun redirectBudgetIsFiniteAndRelativeRedirectCanReachDocument() = runBlocking {
        var calls = 0
        val redirects = LyricsSourceHttpClient({ calls++; Reply(302, headers = mapOf("Location" to "/again")) })
        assertNull(redirects.get(url, hosts, LyricsSourceHttp.Policy(maxRedirects = 2)))
        assertEquals(3, calls)
        val opened = mutableListOf<String>()
        val client = LyricsSourceHttpClient({ uri ->
            opened += uri.toString()
            if (opened.size == 1) Reply(302, headers = mapOf("Location" to "/result")) else Reply()
        })
        assertEquals("Synthetic document", client.get(url, hosts))
        assertEquals(listOf(url, "https://source.example/result"), opened)
    }

    @Test fun charsetAndActualByteCapApplyEvenWithoutContentLength() = runBlocking {
        val bytes = "Тестовый текст".toByteArray(charset("windows-1251"))
        val document = Reply(bytes = bytes, headers = mapOf("Content-Type" to "text/html; charset=windows-1251"))
        assertEquals("Тестовый текст", LyricsSourceHttpClient({ document }).get(url, hosts))
        val declaredOversize = Reply(bytes = byteArrayOf(1), length = 99L)
        assertNull(LyricsSourceHttpClient({ declaredOversize }).get(url, hosts, LyricsSourceHttp.Policy(maxBytes = 8)))
        val actualOversize = Reply(bytes = ByteArray(9), length = -1L)
        assertNull(LyricsSourceHttpClient({ actualOversize }).get(url, hosts, LyricsSourceHttp.Policy(maxBytes = 8)))
        assertTrue(document.disconnected.get())
        assertTrue(declaredOversize.disconnected.get())
        assertTrue(actualOversize.disconnected.get())
    }

    @Test fun rateLimitBlocksWholeHostAndHonorsRetryAfterWithoutRetrying() = runBlocking {
        var now = 1_000L
        var calls = 0
        val client = LyricsSourceHttpClient({ calls++; Reply(429, headers = mapOf("Retry-After" to "120")) }, { now })
        assertNull(client.get(url, hosts))
        now += 60_000L
        assertNull(client.get("https://source.example/another", hosts))
        assertEquals(1, calls)
        now += 60_001L
        assertNull(client.get(url, hosts))
        assertEquals(2, calls)
    }

    @Test fun forbiddenGetsMinimumCooldownButNotFoundDoesNotBlockOtherSongs() = runBlocking {
        var now = 1_000L
        var forbiddenCalls = 0
        val forbidden = LyricsSourceHttpClient({ forbiddenCalls++; Reply(403) }, { now })
        assertNull(forbidden.get(url, hosts))
        now += 59_999L
        assertNull(forbidden.get(url, hosts))
        assertEquals(1, forbiddenCalls)
        now += 2L
        assertNull(forbidden.get(url, hosts))
        assertEquals(2, forbiddenCalls)
        var missingCalls = 0
        val missing = LyricsSourceHttpClient({ missingCalls++; Reply(404) })
        assertNull(missing.get(url, hosts))
        assertNull(missing.get(url, hosts))
        assertEquals(2, missingCalls)
    }

    private fun blockingReply(started: CountDownLatch, released: CountDownLatch): Reply = Reply(
        length = -1L,
        stream = object : InputStream() {
            override fun read(): Int {
                started.countDown()
                if (!released.await(3, TimeUnit.SECONDS)) throw IOException("Test read was never disconnected")
                throw IOException("Disconnected")
            }
            override fun read(buffer: ByteArray, offset: Int, count: Int): Int = read()
        },
        onDisconnect = { released.countDown() }
    )

    @Test fun callerCancellationDisconnectsAnInFlightBlockingRead() = runBlocking {
        val started = CountDownLatch(1)
        val released = CountDownLatch(1)
        val reply = blockingReply(started, released)
        val request = async(Dispatchers.Default) {
            LyricsSourceHttpClient({ reply }).get(url, hosts)
        }
        assertTrue(withContext(Dispatchers.IO) { started.await(2, TimeUnit.SECONDS) })
        withTimeout(1_000L) { request.cancelAndJoin() }
        assertTrue(request.isCancelled)
        assertTrue(reply.disconnected.get())
        assertEquals(0L, released.count)
    }

    @Test fun internalDeadlineReturnsMissingAndDisconnectsRead() = runBlocking {
        val started = CountDownLatch(1)
        val released = CountDownLatch(1)
        val reply = blockingReply(started, released)
        val result = withTimeout(2_000L) {
            LyricsSourceHttpClient({ reply }).get(url, hosts, LyricsSourceHttp.Policy(totalTimeoutMs = 1_000L))
        }
        assertNull(result)
        assertEquals(0L, started.count)
        assertTrue(reply.disconnected.get())
        assertEquals(0L, released.count)
    }

    @Test fun outerDeadlineRemainsCallerCancellationInsteadOfSourceMissing() = runBlocking {
        val reply = blockingReply(CountDownLatch(1), CountDownLatch(1))
        try {
            withTimeout(1_000L) {
                LyricsSourceHttpClient({ reply }).get(url, hosts, LyricsSourceHttp.Policy(totalTimeoutMs = 5_000L))
            }
            fail("An outer timeout must escape instead of becoming a source miss")
        } catch (_: TimeoutCancellationException) {
            assertTrue(reply.disconnected.get())
        }
    }
}
