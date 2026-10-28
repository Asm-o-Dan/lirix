package com.lirix.app.desktop

import com.lirix.app.translations.RustoengTranslationProvider
import com.lirix.app.translations.TranslationSearchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

class RustoengTranslationProviderTest {
    private fun fixture(name: String): String = checkNotNull(javaClass.getResourceAsStream("/translations/rustoeng/$name"))
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    private class Connection(uri: URI, private val html: String = "", private val status: Int = 200,
                             private val location: String? = null, private val mime: String = "text/html; charset=utf-8") : HttpURLConnection(uri.toURL()) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = mime
        override fun getContentLengthLong() = html.toByteArray(Charsets.UTF_8).size.toLong()
        override fun getInputStream() = ByteArrayInputStream(html.toByteArray(Charsets.UTF_8))
        override fun getHeaderField(name: String?) = if (name == "Location") location else null
    }

    @Test fun searchFindsExactRussianSongAndKeepsTranslationOnly() = runBlocking<Unit> {
        val requests = mutableListOf<URI>()
        val provider = RustoengTranslationProvider { uri ->
            requests += uri
            Connection(uri, if (uri.rawQuery != null) fixture("search.html") else fixture("5nizza.html"))
        }
        val found = provider.find("Солдат", "5nizza") as TranslationSearchResult.Found
        assertEquals("en", found.document.language)
        assertEquals("Солдат", found.document.title)
        assertEquals("5’Nizza", found.document.artist)
        assertEquals("Rustoeng", found.document.sourceName)
        assertFalse(found.document.aligned)
        assertEquals("", found.document.blocks.single().original)
        assertEquals("Invented first line\nInvented second line", found.document.blocks.single().translated)
        assertFalse(found.document.blocks.single().translated.contains("Video of English translation"))
        assertFalse(found.document.blocks.single().translated.contains("youtube.com"))
        assertTrue(found.document.sourceUrl.contains("#lirix-v1="))
        assertTrue(requests.all { it.rawFragment == null })
        assertEquals(3, requests.size)
        assertEquals("/", requests.first().path)
        assertTrue(requests.first().rawQuery.startsWith("s="))
        assertFalse(requests.any { it.path == "/evil/" })
        assertTrue(provider.find("Солдат", "Different Artist") is TranslationSearchResult.Missing)
    }

    @Test fun h2AndH3SectionsStaySeparateAndIgnoreShareWidget() = runBlocking<Unit> {
        val search = fixture("search.html").replace("5nizza/", "okudzhava-bulat-4/").replace("5’Nizza", "Okudzhava, Bulat")
        val provider = RustoengTranslationProvider { uri -> Connection(uri, if (uri.rawQuery != null) search else fixture("okudzhava.html")) }
        val first = (provider.find("Дерзость, или Разговор Перед Боем", "Okudzhava, Bulat") as TranslationSearchResult.Found).document
        val second = (provider.find("Лесной Вальс", "Okudzhava, Bulat") as TranslationSearchResult.Found).document
        assertEquals("Invented first song", first.blocks.single().translated)
        assertEquals("Invented second song", second.blocks.single().translated)
        assertNotEquals(first.sourceUrl, second.sourceUrl)
        assertEquals("Invented second song", provider.load(second.sourceUrl).blocks.single().translated)
        assertFalse(second.blocks.single().translated.contains("NOT LYRICS"))
    }

    @Test fun changedArtistAndHeadingAfterDiscoveryCannotLoad() = runBlocking<Unit> {
        var html = fixture("5nizza.html")
        val provider = RustoengTranslationProvider { uri -> Connection(uri, if (uri.rawQuery != null) fixture("search.html") else html) }
        val url = (provider.find("Солдат", "5nizza") as TranslationSearchResult.Found).document.sourceUrl
        html = html.replace("5’Nizza</h1>", "Different Artist</h1>")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { provider.load(url) } }
        html = fixture("5nizza.html").replace("Soldier (Солдат)", "Different (Другая)")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { provider.load(url) } }
    }

    @Test fun malformedAndUnsafeReferencesAreRejectedBeforeHttp() = runBlocking<Unit> {
        var opened = 0
        val provider = RustoengTranslationProvider { uri -> opened++; Connection(uri) }
        val bad = listOf("http://rustoeng.com/5nizza/#lirix-v1=abc", "https://rustoeng.com.evil/5nizza/#lirix-v1=abc",
            "https://user@rustoeng.com/5nizza/#lirix-v1=abc", "https://rustoeng.com:443/5nizza/#lirix-v1=abc",
            "https://rustoeng.com/5nizza/?x=1#lirix-v1=abc", "https://rustoeng.com/%2f/#lirix-v1=abc",
            "https://rustoeng.com/5nizza/#lirix-v1=" + "a".repeat(1025), "https://rustoeng.com/5nizza/#lirix-v1=abc=")
        for (url in bad) assertThrows(IllegalArgumentException::class.java) { runBlocking { provider.load(url) } }
        val controls = Base64.getUrlEncoder().withoutPadding().encodeToString("[\"a\\n\",\"song\"]".toByteArray())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { provider.load("https://rustoeng.com/5nizza/#lirix-v1=$controls") } }
        assertEquals(0, opened)
    }

    @Test fun unknownHeadingDuplicateAndWrongResultAreNotAccepted() = runBlocking<Unit> {
        var html = fixture("5nizza.html")
        val provider = RustoengTranslationProvider { uri -> Connection(uri, if (uri.rawQuery != null) fixture("search.html") else html) }
        html = html.replace("<div class=\"sharedaddy\">", "<h3>Unexpected subsection</h3><div class=\"sharedaddy\">")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { provider.find("Солдат", "5nizza") } }
        html = fixture("5nizza.html").replace("<div class=\"sharedaddy\">", "<h3>Another (Солдат)</h3><div class=\"sharedaddy\">")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { provider.find("Солдат", "5nizza") } }
        html = fixture("5nizza.html")
        assertTrue(provider.find("Другая", "5nizza") is TranslationSearchResult.Missing)
    }

    @Test fun sectionIdentitySurvivesReorderingAndRejectsDuplicateArtistHeaders() = runBlocking<Unit> {
        var html = fixture("okudzhava.html")
        val search = fixture("search.html").replace("5nizza/", "okudzhava-bulat-4/").replace("5’Nizza", "Okudzhava, Bulat")
        val provider = RustoengTranslationProvider { uri -> Connection(uri, if (uri.rawQuery != null) search else html) }
        val url = (provider.find("Лесной Вальс", "Okudzhava, Bulat") as TranslationSearchResult.Found).document.sourceUrl
        val a = "<h2>Audacity, or Conversation Before the Battle (Дерзость, или Разговор Перед Боем)</h2><p>Invented first song</p>"
        val b = "<h3>A Forest Waltz (Лесной Вальс)</h3><p>Invented second song</p>"
        html = html.replace(a + b, b + a)
        assertEquals("Invented second song", provider.load(url).blocks.single().translated)
        html = html.replace("</header>", "<h1 class=\"entry-title\">Impostor</h1></header>")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { provider.load(url) } }
    }

    @Test fun emptySearchIsMissingButChangedMarkupIsFailure() = runBlocking<Unit> {
        val empty = "<html><body class=\"search-no-results\"><main id=\"main\"></main></body></html>"
        val provider = RustoengTranslationProvider { uri -> Connection(uri, empty) }
        assertTrue(provider.find("Солдат", "5nizza") is TranslationSearchResult.Missing)
        val changed = RustoengTranslationProvider { uri -> Connection(uri, "<html><main id=\"main\"><article>Changed layout</article></main></html>") }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { changed.find("Солдат", "5nizza") } }
    }

    @Test fun blockedRequestsAndUnsafeRedirectAreFailuresNotMissing() = runBlocking<Unit> {
        var requests = 0
        val blocked = RustoengTranslationProvider { uri -> requests++; Connection(uri, status = 403) }
        repeat(2) { assertThrows(IllegalStateException::class.java) { runBlocking { blocked.find("Солдат", "5nizza") } } }
        assertEquals(1, requests)
        val redirect = RustoengTranslationProvider { uri -> Connection(uri, status = 302, location = "https://evil.example/5nizza/") }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { redirect.find("Солдат", "5nizza") } }
    }

    @Test fun sharingWidgetWithoutHeadingTerminatesSectionBeforeFollowingEditorialText() = runBlocking<Unit> {
        val html = fixture("5nizza.html").replace(
            "<div class=\"sharedaddy\"><h3>Share this:</h3><p>NOT LYRICS</p></div>",
            "<div class=\"sd-sharing\"><p>Share</p></div><p>Editorial text, not a song</p>"
        )
        val provider = RustoengTranslationProvider { uri -> Connection(uri, if (uri.rawQuery != null) fixture("search.html") else html) }
        val doc = (provider.find("Солдат", "5nizza") as TranslationSearchResult.Found).document
        assertEquals("Invented first line\nInvented second line", doc.blocks.single().translated)
        assertFalse(doc.blocks.single().translated.contains("Editorial"))
    }

    @Test fun differentRemasterAndCollaboratorCannotBecomeExactAutoMatch() = runBlocking<Unit> {
        val provider = RustoengTranslationProvider { uri -> Connection(uri, if (uri.rawQuery != null) fixture("search.html") else fixture("5nizza.html")) }
        assertTrue(provider.find("Солдат (2024 Remastered)", "5nizza") !is TranslationSearchResult.Found)
        assertTrue(provider.find("Солдат (30th Anniversary Edition)", "5nizza") !is TranslationSearchResult.Found)
        assertTrue(provider.find("Солдат", "5nizza feat Guest") !is TranslationSearchResult.Found)
        val original = (provider.find("Солдат", "5nizza") as TranslationSearchResult.Found).document.sourceUrl
        val changed = RustoengTranslationProvider { uri -> Connection(uri, fixture("5nizza.html").replace(
            "Soldier (Солдат)", "Soldier (Солдат (2024 Remastered))")) }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { changed.load(original) } }
    }

    @Test fun crossScriptArtistAliasRequiresConfirmationAndRevalidatesArticleArtist() = runBlocking<Unit> {
        val search = fixture("search.html").replace("5’Nizza", "Kipelov")
        var page = fixture("5nizza.html").replace("5’Nizza", "Kipelov")
        val provider = RustoengTranslationProvider { uri -> Connection(uri, if (uri.rawQuery != null) search else page) }
        val result = provider.find("Солдат", "Кипелов") as TranslationSearchResult.Candidates
        assertEquals(1, result.values.size)
        assertEquals("Kipelov", result.values.single().artist)
        assertEquals("Солдат", provider.load(result.values.single().sourceUrl).title)
        page = page.replace("Kipelov</h1>", "Another</h1>")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { provider.load(result.values.single().sourceUrl) } }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { provider.find("Солдат", "Кипелов") } }
    }

    @Test fun blockedLoadHonoursSameBackoffAsSearch() = runBlocking<Unit> {
        val good = RustoengTranslationProvider { uri -> Connection(uri, if (uri.rawQuery != null) fixture("search.html") else fixture("5nizza.html")) }
        val url = (good.find("Солдат", "5nizza") as TranslationSearchResult.Found).document.sourceUrl
        var requests = 0
        val blocked = RustoengTranslationProvider { uri -> requests++; Connection(uri, status = 429) }
        repeat(2) { assertThrows(IllegalStateException::class.java) { runBlocking { blocked.load(url) } } }
        assertThrows(IllegalStateException::class.java) { runBlocking { blocked.find("Солдат", "5nizza") } }
        assertEquals(1, requests)
    }

    @Test fun linkedLyricsArePreservedButAmbiguousEmbeddedMediaFailsClosed() = runBlocking<Unit> {
        var page = fixture("5nizza.html").replace("Invented first line<br>Invented second line", "Invented <a href=\"/info/\">first</a> line<br>Invented second line")
        val provider = RustoengTranslationProvider { uri -> Connection(uri, if (uri.rawQuery != null) fixture("search.html") else page) }
        val doc = (provider.find("Солдат", "5nizza") as TranslationSearchResult.Found).document
        assertEquals("Invented first line\nInvented second line", doc.blocks.single().translated)
        page = page.replace("Invented <a href=\"/info/\">first</a> line", "Invented <iframe src=\"https://example.test/\"></iframe> line")
        assertThrows(IllegalArgumentException::class.java) { runBlocking { provider.find("Солдат", "5nizza") } }
    }


    @Test fun unexplainedStandaloneTextFailsClosed() = runBlocking<Unit> {
        val page = fixture("5nizza.html").replace("<div class=\"sharedaddy\">", "<div>Unexpected stray text</div><div class=\"sharedaddy\">")
        val provider = RustoengTranslationProvider { uri -> Connection(uri, if (uri.rawQuery != null) fixture("search.html") else page) }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { provider.find("Солдат", "5nizza") } }
    }

    @Test fun rejectsUnsupportedDeclaredCharsetRatherThanMisdecode() = runBlocking<Unit> {
        val provider = RustoengTranslationProvider { uri -> Connection(uri, fixture("search.html"), mime = "text/html; charset=iso-8859-1") }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { provider.find("Солдат", "5nizza") } }
    }

}
