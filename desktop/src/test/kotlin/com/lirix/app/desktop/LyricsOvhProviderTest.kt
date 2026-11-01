package com.lirix.app.desktop

import com.lirix.app.feature.lyrics.LyricsOvhProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class LyricsOvhProviderTest {
    @Test fun exactArtistTitleUseEncodedPathSegmentsAndOnlyOneRequest() = runBlocking {
        val urls = mutableListOf<String>()
        val provider = LyricsOvhProvider { url ->
            urls += url
            """{"lyrics":"  Synthetic first line\r\nSynthetic second line  "}"""
        }
        val found = provider.find(" Song / remix?#%+ ", " Артист & Artist ")!!
        assertEquals(listOf("https://api.lyrics.ovh/v1/%D0%90%D1%80%D1%82%D0%B8%D1%81%D1%82%20%26%20Artist/Song%20%2F%20remix%3F%23%25%2B"), urls)
        assertEquals("Synthetic first line\nSynthetic second line", found.plainLyrics)
        assertEquals("builtin:lyrics-ovh", found.sourceId)
        assertEquals("lyrics.ovh", found.sourceName)
        assertEquals(urls.single(), found.sourceUrl)
        assertNull(found.syncedLyricsLrc)
        assertNull(found.chordsText)
        assertNull(found.author)
    }

    @Test fun missingMetadataSkipsHttp() = runBlocking {
        var calls = 0
        val provider = LyricsOvhProvider { calls++; error("Must not fetch") }
        assertNull(provider.find("", "Artist"))
        assertNull(provider.find("Song", "  "))
        assertEquals(0, calls)
    }

    @Test fun missingMalformedErrorAndHtmlResponsesAreNotLyrics() = runBlocking {
        val responses = listOf(null, "", "not json", "[]", "{}", "null",
            """{"lyrics":null}""", """{"lyrics":4}""", """{"lyrics":true}""",
            """{"lyrics":"   "}""", """{"error":"Not found"}""",
            """{"error":"Blocked","lyrics":"Not lyrics"}""",
            """{"lyrics":"<html><body>Challenge</body></html>"}""",
            """{"lyrics":"<p>Server failure</p>"}""")
        for (response in responses) {
            var calls = 0
            val provider = LyricsOvhProvider { calls++; response }
            assertNull("Unexpected success for synthetic invalid response", provider.find("Song", "Artist"))
            assertEquals(1, calls)
        }
    }

    @Test fun timestampLookingPlainTextNeverBecomesSynchronizedLyrics() = runBlocking {
        val found = LyricsOvhProvider { """{"lyrics":"[00:01.00]Synthetic plain\n[00:02.00]Still plain"}""" }
            .find("Song", "Artist")!!
        assertTrue(found.plainLyrics!!.startsWith("[00:01.00]"))
        assertNull(found.syncedLyricsLrc)
    }

    @Test fun transportFailureFallsBackButCallerCancellationEscapes() = runBlocking {
        assertNull(LyricsOvhProvider { throw IOException("offline") }.find("Song", "Artist"))
        try {
            LyricsOvhProvider { throw CancellationException("switched track") }.find("Song", "Artist")
            fail("Cancellation must escape the provider")
        } catch (expected: CancellationException) {
            assertEquals("switched track", expected.message)
        }
    }
}
