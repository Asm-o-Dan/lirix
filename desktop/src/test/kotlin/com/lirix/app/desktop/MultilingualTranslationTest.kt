package com.lirix.app.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.lirix.app.storage.AppDatabase
import com.lirix.app.translations.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files

class MultilingualTranslationTest {
    private val base = "https://lyricstranslate.com"
    private val url = "$base/en/example-translation.html"
    private fun document(language: String) = SongTranslation(url, "Example", "Artist", "", "Author",
        listOf(TranslationBlock("Synthetic original", "Synthetic translation")), language, "LyricsTranslate", false)
    private fun page(language: String = "Spanish") = """<meta charset="utf-8"><h1>Example - $language translation</h1>
        <div class="top__artists"><a class="top__tag">Artist</a></div>
        <div data-panel="original"><h2 class="translate__title">Example</h2><div id="original-lyrics">Synthetic original</div></div>
        <div data-panel="translation"><h2 class="translate__title">Ejemplo</h2><div id="translation-body">Synthetic translation</div></div>"""
    private val form = """<form id="lyricstranslatesearch-searchsong-form" method="post" action="/en/songs">
        <input type="hidden" name="form_id" value="test"><select name="formtree[ltsearchtranslationlanguage][]"><option value="42">Spanish</option></select></form>"""
    private fun results(title: String = "Example", artist: String = "Artist") = """<form id="lyricstranslatesearch-searchsong-form">
        <div class="table__trow"><a class="title-link" href="/en/example-translation.html">$title</a>
        <span class="block-1-table__author"><span class="text">$artist</span></span><a href="/en/translator/person"><img alt="Author"></a></div></form>"""
    private class Connection(uri: URI, val text: String = "", val status: Int = 200, val redirect: String? = null) : HttpURLConnection(uri.toURL()) {
        val sent = ByteArrayOutputStream()
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = "text/html; charset=utf-8"
        override fun getInputStream() = ByteArrayInputStream(text.toByteArray())
        override fun getOutputStream() = sent
        override fun getContentLengthLong() = text.toByteArray().size.toLong()
        override fun getHeaderField(name: String?) = if (name == "Location") redirect else null
    }

    @Test fun postsObservedFormFollowsRedirectAndVerifiesPage() = runBlocking {
        val connections = mutableListOf<Connection>()
        val provider = LyricsTranslateProvider { uri ->
            val connection = when (connections.size) {
                0 -> Connection(uri, form)
                1 -> Connection(uri, status = 302, redirect = "/en/songs/0/Artist/Example/0/none/0?tlang=42")
                2 -> Connection(uri, results())
                else -> Connection(uri, page())
            }
            connections += connection
            connection
        }
        val found = provider.find("Example", "Artist", "es") as TranslationSearchResult.Found
        assertEquals("es", found.document.language)
        assertEquals(4, connections.size)
        assertEquals("POST", connections[1].requestMethod)
        assertTrue(connections[1].sent.toString("UTF-8").contains("formtree%5Bltsearchtranslationlanguage%5D%5B%5D=42"))
        assertEquals("Lirix/1.4 song-translation-import", connections[0].getRequestProperty("User-Agent"))
    }

    @Test fun candidatesRequireExactSongAndArtistAndPreserveAuthor() {
        assertEquals("Author", LyricsTranslateProvider.candidates(results().toByteArray(), "$base/en/songs", "Example", "Artist").single().translator)
        assertTrue(LyricsTranslateProvider.candidates(results(artist = "Other").toByteArray(), "$base/en/songs", "Example", "Artist").isEmpty())
        assertTrue(LyricsTranslateProvider.candidates(results(title = "Example (Live)").toByteArray(), "$base/en/songs", "Example", "Artist").isEmpty())
    }

    @Test fun titleFallbackStillRequiresExactArtistIdentity() = runBlocking {
        val connections = mutableListOf<Connection>()
        val provider = LyricsTranslateProvider { uri ->
            val connection = when (connections.size) {
                0 -> Connection(uri, form)
                1 -> Connection(uri, "<form id=lyricstranslatesearch-searchsong-form></form>")
                2 -> Connection(uri, results())
                else -> Connection(uri, page())
            }
            connections += connection
            connection
        }
        assertTrue(provider.find("Example", "ArtistVEVO", "es") is TranslationSearchResult.Found)
        assertTrue(connections[2].sent.toString("UTF-8").contains("formtree%5Bltsearchtextartist%5D=&"))
        assertEquals(4, connections.size)
    }

    @Test fun accessFailuresBackOffAndDoNotFollowForeignRedirects() = runBlocking {
        var requests = 0
        val provider = LyricsTranslateProvider { uri -> requests++; Connection(uri, status = 403) }
        repeat(2) {
            try { provider.find("Example", "Artist", "es"); fail("expected failure") } catch (_: IllegalStateException) {}
        }
        assertEquals(1, requests)
        val redirects = LyricsTranslateProvider { uri -> Connection(uri, status = 302, redirect = "https://evil.example/en/songs") }
        try { redirects.find("Example", "Artist", "es"); fail("unsafe redirect") } catch (_: IllegalArgumentException) {}
    }

    @Test fun languageSelectionIsValidatedAndPersisted() {
        var stored = ""
        val preferences = MemoryTranslationLanguagePreferences("invalid") { stored = it }
        assertEquals("ru", preferences.language.value)
        preferences.setLanguage("zh")
        assertEquals("zh", stored)
        assertEquals("zh", MemoryTranslationLanguagePreferences(stored).language.value)
        assertThrows(IllegalArgumentException::class.java) { preferences.setLanguage("unknown") }
    }

    @Test fun roomAndCoordinatorKeepLanguagesIndependentAndProtectManualSelections() = runBlocking<Unit> {
        val file = Files.createTempFile("lirix-language-test", ".db")
        val db = Room.databaseBuilder<AppDatabase>(file.toString()).setDriver(BundledSQLiteDriver()).build()
        try {
            val dao = db.songTranslationDao()
            val preferences = MemoryTranslationLanguagePreferences()
            var calls = 0
            val coordinator = AutomaticSongTranslations(dao, { _, _, _ -> error("legacy path") }, preferences = preferences,
                searchLanguage = { _, _, language, _ -> calls++; TranslationSearchResult.Found(document(language)) })
            coordinator.resolve("track", "Example", "Artist")
            coordinator.setLanguage("es")
            coordinator.resolve("track", "Example", "Artist")
            assertEquals(1, dao.get("track", "ru").size)
            assertEquals(1, dao.observe("track", "es").first().size)
            assertEquals(AutomaticTranslationState.Ready, coordinator.states.value[coordinator.stateKey("track", "es")])
            assertFalse(dao.saveAutomaticIfEmpty(SongTranslationEntity("track", "es", "$base/en/other.html", TranslationDocuments.encode(document("es")), 20)))
            coordinator.setLanguage("ru")
            coordinator.resolve("track", "Example", "Artist")
            assertEquals(2, calls)
            assertTrue(dao.get("track", "zh").isEmpty())
        } finally { db.close(); Files.deleteIfExists(file) }
    }

    @Test fun liveMetadataOnlySearchAndLoadAllTargetLanguages() = runBlocking {
        assumeTrue(System.getenv("LIRIX_TRANSLATIONS_LIVE") == "1")
        val provider = LyricsTranslateProvider()
        for (language in TranslationLanguages.labels.keys) {
            val result = provider.find("Dernière danse", "Indila", language)
            val document = when (result) {
                is TranslationSearchResult.Found -> result.document
                is TranslationSearchResult.Candidates -> provider.load(result.values.first().sourceUrl, language)
                else -> error("No live $language translation")
            }
            assertEquals(language, document.language)
            assertEquals(TranslationMetadata.artistKey("Indila"), TranslationMetadata.artistKey(document.artist))
            assertEquals(TranslationMetadata.titleKey("Dernière danse"), TranslationMetadata.titleKey(document.title))
            println("LyricsTranslate live: $language, ${document.blocks.size} independent text block(s), ${document.sourceUrl}")
            delay(1000)
        }
        val browserTrack = provider.find("Hellraiser (30th Anniversary Edition - Official Animated Video)", "OzzyOsbourneVEVO", "es")
        assertTrue("Browser channel metadata must find a verified Spanish translation", browserTrack is TranslationSearchResult.Found || browserTrack is TranslationSearchResult.Candidates)
        println("LyricsTranslate live: browser channel metadata matched a verified Spanish candidate")
    }
}
