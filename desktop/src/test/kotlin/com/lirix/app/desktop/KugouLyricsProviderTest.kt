package com.lirix.app.desktop

import com.lirix.app.feature.lyrics.KugouLyricsProvider
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class KugouLyricsProviderTest {
    private fun row(id: String = "1", title: String = "Song", artist: String = "Artist", duration: Long = 200_000) = buildJsonObject {
        put("id", id); put("accesskey", "public_search_fixture"); put("song", title); put("singer", artist); put("duration", duration)
    }
    private fun search(vararg rows: JsonObject) = buildJsonObject { put("status", 200); put("candidates", JsonArray(rows.toList())) }.toString()
    private fun download(lrc: String) = buildJsonObject {
        put("status", 200); put("fmt", "lrc"); put("content", Base64.getEncoder().encodeToString(lrc.toByteArray()))
    }.toString()

    @Test fun exactIdentityVersionAndDurationAreRequired() {
        val body = search(row("1", "Song (Live)"), row("2", artist = "Other"), row("3", duration = 250_000), row("4"))
        assertEquals(listOf("4"), KugouLyricsProvider.candidates(body, "Song", "Artist", 200_000).map { it.id })
        assertTrue(KugouLyricsProvider.candidates(search(row("1"), row("2", duration = 250_000)), "Song", "Artist", null).isEmpty())
    }

    @Test fun realTimedPayloadIsRequiredAndNoPlainTextGetsInventedTiming() {
        assertNull(KugouLyricsProvider.parseDownload(download("Plain line\nSecond plain line")))
        assertNull(KugouLyricsProvider.parseDownload(download("[00:00.00] First\n[00:00.00] Second")))
        assertNull(KugouLyricsProvider.parseDownload("{\"status\":200,\"fmt\":\"lrc\",\"content\":\"notbase64%%\"}"))
        assertNotNull(KugouLyricsProvider.parseDownload(download("[00:01.00] First synthetic line\n[00:03.00] Second synthetic line")))
    }

    @Test fun searchAndTwoDownloadsBoundTheLookupAndMissingSkipsDownloads() = runBlocking {
        var calls = 0
        val provider = KugouLyricsProvider { url -> calls++; if ("/search?" in url) search(row("1"), row("2"), row("3")) else download("plain") }
        assertNull(provider.find("Song", "Artist", 200_000))
        assertEquals(3, calls)
        calls = 0
        assertNull(KugouLyricsProvider { calls++; search() }.find("Missing", "Artist"))
        assertEquals(1, calls)
    }

    @Test fun successfulLrcCarriesSafeAttributionWithoutAccesskey() = runBlocking {
        val result = KugouLyricsProvider { url -> if ("/search?" in url) search(row()) else download("[00:01.00] Synthetic one\n[00:03.00] Synthetic two") }.find("Song", "Artist", 200_000)!!
        assertEquals("builtin:kugou", result.sourceId)
        assertNotNull(result.syncedLyricsLrc)
        assertFalse(result.sourceUrl.orEmpty().contains("accesskey"))
        assertEquals("Synthetic one\nSynthetic two", result.plainLyrics)
    }

    @Test fun parentCancellationRemainsCancellation() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val provider = KugouLyricsProvider { entered.complete(Unit); awaitCancellation() }
        val task = launch { provider.find("Song", "Artist") }
        entered.await(); task.cancelAndJoin()
        assertTrue(task.isCancelled)
    }
}
