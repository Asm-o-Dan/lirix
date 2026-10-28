package com.lirix.app.desktop

import com.lirix.app.translations.LyricsTranslateProvider
import com.lirix.app.translations.TranslationSearchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

/** Real-source metadata and form/DOM structure; lyric bodies are synthetic. */
class LyricsTranslateRussianEnglishTest {
    private fun fixture(name: String): String = checkNotNull(javaClass.getResourceAsStream("/translations/lyricstranslate-ru-en/$name"))
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private class Connection(uri: URI, private val html: String) : HttpURLConnection(uri.toURL()) {
        val sent = ByteArrayOutputStream()
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = 200
        override fun getContentType() = "text/html; charset=utf-8"
        override fun getInputStream() = ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
        override fun getOutputStream() = sent
        override fun getContentLengthLong() = html.toByteArray(Charsets.UTF_8).size.toLong()
    }
    private fun oneResult(title: String, artist: String): String = """<form id="lyricstranslatesearch-searchsong-form">
        <div class="table__trow"><a class="title-link" href="/en/kipelov-ya-svoboden-english">$title</a>
        <span class="block-1-table__author"><span class="text">$artist</span></span>
        <a href="/en/translator/MSK"><img alt="MSK"></a></div></form>"""

    @Test fun observedCyrillicToEnglishSearchYieldsConfirmedCandidatesAndLoadsRequestedLanguage() = runBlocking<Unit> {
        val connections = mutableListOf<Connection>()
        val provider = LyricsTranslateProvider { uri ->
            Connection(uri, when (connections.size) { 0 -> fixture("form.html"); 1 -> fixture("results.html"); else -> fixture("song.html") })
                .also { connections += it }
        }
        val result = provider.find("Я свободен", "Кипелов", "en") as TranslationSearchResult.Candidates
        assertEquals(2, result.values.size)
        assertEquals(listOf("MSK", "OreWell"), result.values.map { it.translator })
        assertTrue(connections[1].sent.toString("UTF-8").contains("formtree%5Bltsearchtranslationlanguage%5D%5B%5D=328"))
        assertEquals("POST", connections[1].requestMethod)
        val document = provider.load(result.values.first().sourceUrl, "en")
        assertEquals("en", document.language)
        assertEquals("Я свободен", document.title)
        assertEquals("Kipelov", document.artist)
        assertEquals("MSK", document.translator)
        assertEquals("Synthetic original text", document.blocks.single().original)
        assertEquals("Synthetic translated text", document.blocks.single().translated)
        assertFalse(document.aligned)
    }

    @Test fun singleTransliteratedTitleOrCrossScriptArtistNeverAutoSaves() = runBlocking<Unit> {
        val aliasTitle = oneResult("Я свободен (Ya svoboden)", "Kipelov")
        val aliasArtist = oneResult("Я свободен", "Kipelov")
        suspend fun find(result: String, requestedArtist: String): TranslationSearchResult {
            var requests = 0
            val provider = LyricsTranslateProvider { uri -> Connection(uri, if (requests++ == 0) fixture("form.html") else result) }
            return provider.find("Я свободен", requestedArtist, "en")
        }
        assertTrue(find(aliasTitle, "Kipelov") is TranslationSearchResult.Candidates)
        assertTrue(find(aliasArtist, "Кипелов") is TranslationSearchResult.Candidates)
        assertTrue(find(aliasTitle, "Кипелов") is TranslationSearchResult.Candidates)
    }

    @Test fun matchingRealMetadataCanAutoFindButEditionAndCollaboratorRequireConfirmation() = runBlocking<Unit> {
        suspend fun find(queryTitle: String, queryArtist: String, resultTitle: String, resultArtist: String): TranslationSearchResult {
            var requests = 0
            val provider = LyricsTranslateProvider { uri -> Connection(uri, when (requests++) {
                0 -> fixture("form.html")
                1 -> oneResult(resultTitle, resultArtist)
                else -> fixture("song.html")
            }) }
            return provider.find(queryTitle, queryArtist, "en")
        }
        assertTrue(find("Я свободен", "Kipelov", "Я свободен", "Kipelov") is TranslationSearchResult.Found)
        assertTrue(find("Я свободен (2024 Remastered)", "Kipelov", "Я свободен", "Kipelov") is TranslationSearchResult.Candidates)
        assertTrue(find("Я свободен (30th Anniversary Edition)", "Kipelov", "Я свободен", "Kipelov") is TranslationSearchResult.Candidates)
        assertTrue(find("Я свободен", "Kipelov feat Guest", "Я свободен", "Kipelov") is TranslationSearchResult.Candidates)
        assertTrue(find("Я свободен", "Kipelov", "Я свободен (2020 Remastered)", "Kipelov") is TranslationSearchResult.Candidates)
    }

    @Test fun parenthesizedArtistDisambiguationNeverAutoMatchesDifferentIdentity() = runBlocking<Unit> {
        suspend fun find(queryArtist: String, resultArtist: String, documentArtist: String): TranslationSearchResult {
            var requests = 0
            val provider = LyricsTranslateProvider { uri -> Connection(uri, when (requests++) {
                0 -> fixture("form.html")
                1 -> oneResult("Я свободен", resultArtist)
                else -> fixture("song.html").replace(">Kipelov</a>", ">$documentArtist</a>")
            }) }
            return provider.find("Я свободен", queryArtist, "en")
        }
        // The row must not be accepted just because artistKey truncates both qualifiers.
        assertTrue(find("Kipelov (UK)", "Kipelov (US)", "Kipelov (US)") is TranslationSearchResult.Candidates)
        assertTrue(find("Kipelov", "Kipelov (US)", "Kipelov (US)") is TranslationSearchResult.Candidates)
        assertTrue(find("Kipelov (UK (Official))", "Kipelov (US (Official))", "Kipelov (US (Official))") is TranslationSearchResult.Candidates)
        assertTrue(find("Kipelov (UK)", "Kipelov (UK)", "Kipelov (UK)") is TranslationSearchResult.Found)
        // A changed document artist is caught even when the search row initially matched.
        assertTrue(find("Kipelov (UK)", "Kipelov (UK)", "Kipelov (US)") is TranslationSearchResult.Candidates)
        // A known channel label is presentation, not an artist identity distinction.
        assertTrue(find("Kipelov (Official)", "Kipelov", "Kipelov") is TranslationSearchResult.Found)
    }

    @Test fun wrongArtistAndWrongTargetCannotBecomeFound() = runBlocking<Unit> {
        var calls = 0
        val provider = LyricsTranslateProvider { uri -> Connection(uri, when (calls++) {
            0 -> fixture("form.html")
            else -> oneResult("Я свободен", "Another Artist")
        }) }
        assertTrue(provider.find("Я свободен", "Kipelov", "en") is TranslationSearchResult.Missing)
        val wrongTarget = LyricsTranslateProvider { uri -> Connection(uri, fixture("song.html").replace("English translation", "Spanish translation")) }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { wrongTarget.load("https://lyricstranslate.com/en/kipelov-ya-svoboden-english", "en") } }
    }
}
