package com.lirix.app.desktop

import com.lirix.app.translations.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LyrsenseTranslationProviderTest {
    private val url = "https://lyrsense.com/example_artist/example_song"
    private fun page(artist: String = "Example Artist", title: String = "Example Song", canonical: String = url) = """
        <link rel="canonical" href="$canonical">
        <span itemprop="name">Wrong schema name</span>
        <a class="brArtistLink"><span itemprop="name">$artist</span></a>
        <h2 id="fr_title">$title</h2><h2 id="ru_title">Учебный перевод</h2>
        <div id="songFlexLines">
          <div class="songTextLine"><span class="songTextLine__original">Original fixture<sup>1</sup></span><span class="songTextLine__translation">Перевод фикстуры</span></div>
          <div class="songTextLine"></div>
          <div class="songTextLine"><span class="songTextLine__original">Second fixture</span><span class="songTextLine__translation">Вторая фикстура</span></div>
        </div>
        <div id="author_var1"><span>Fixture Translator</span><a href="/authors/test">Страница автора</a></div>
        <div id="author_var2"><span>Other Translator</span></div><aside>Unrelated footer</aside>
    """.trimIndent()
    private fun search(vararg rows: Triple<String, String, String>): String = """
        <form action="/search"><input name="s"></form><ul>
        ${rows.joinToString("") { (name, artist, path) -> "<li class='element radioFilterElement radioElem_en'><a href='$path'>$name</a><span>$artist</span></li>" }}
        </ul>
    """.trimIndent()
    private val match = Triple("Example Song", "Example Artist", "/example_artist/example_song")

    @Test fun alignedRowsPreserveAuthorAndSeparatorsWithoutFootnotesOrOtherVariant() {
        val result = LyrsenseTranslationProvider.parse(page(), url)
        assertEquals("Example Artist", result.artist)
        assertEquals("Fixture Translator", result.translator)
        assertEquals("Lyrsense", result.sourceName)
        assertEquals("ru", result.language)
        assertEquals(url, result.sourceUrl)
        assertTrue(result.aligned)
        assertEquals(listOf("Original fixture", "", "Second fixture"), result.blocks.map { it.original })
        assertEquals(result, TranslationDocuments.decode(TranslationDocuments.encode(result)))
    }

    @Test fun twoActualMetadataShapesHaveIndependentExactIdentities() {
        listOf("Radiohead" to "Creep", "Adele" to "Hello").forEach { (artist, title) ->
            val results = LyrsenseTranslationProvider.searchCandidates(search(Triple(title, artist, "/example_artist/example_song")), title, artist)
            assertEquals(1, results.size)
            assertEquals(artist, LyrsenseTranslationProvider.parse(page(artist, title), url).artist)
        }
    }

    @Test fun searchRejectsWrongArtistVersionsForeignLinksAndCatalogs() {
        val html = search(match, Triple("Example Song", "Other Artist", "/other/song"),
            Triple("Example Song (Live)", "Example Artist", "/example_artist/live"),
            Triple("Example Song", "Example Artist", "https://evil.example/a/b"),
            Triple("Example Song", "Example Artist", "/example_artist"))
        assertEquals(listOf(url), LyrsenseTranslationProvider.searchCandidates(html, "Example Song", "Example Artist").map { it.sourceUrl })
        assertTrue(LyrsenseTranslationProvider.searchCandidates(search(match), "Example Song (Live)", "Example Artist").isEmpty())
    }

    @Test fun foundRequiresDetailIdentityAndUsesOnlyTwoFetches() = runBlocking {
        val requests = mutableListOf<String>()
        val provider = LyrsenseTranslationProvider { request -> requests.add(request); if (request.contains("/search?")) search(match) else page() }
        val found = provider.find("Example Song (Official Video)", "Example Artist") as TranslationSearchResult.Found
        assertEquals(url, found.document.sourceUrl)
        assertEquals(2, requests.size)
        val wrong = LyrsenseTranslationProvider { request -> if (request.contains("/search?")) search(match) else page(artist = "Other Artist") }
        assertEquals(TranslationSearchResult.Missing, wrong.find("Example Song", "Example Artist"))
    }

    @Test fun ambiguousSearchDoesNotAutomaticallyLoadOrChooseFirst() = runBlocking {
        var calls = 0
        val provider = LyrsenseTranslationProvider { calls++; search(match, Triple("Example Song", "Example Artist", "/example_artist/other_version")) }
        val result = provider.find("Example Song", "Example Artist") as TranslationSearchResult.Candidates
        assertEquals(2, result.values.size)
        assertEquals(1, calls)
    }

    @Test fun emptySearchIsCachedButRefreshCanRetry() = runBlocking {
        var calls = 0
        val provider = LyrsenseTranslationProvider { calls++; search() }
        repeat(2) { assertEquals(TranslationSearchResult.Missing, provider.find("ZZZLirixNoSuchSong", "Unknown Fixture Artist")) }
        assertEquals(1, calls)
        provider.find("ZZZLirixNoSuchSong", "Unknown Fixture Artist", refresh = true)
        assertEquals(2, calls)
    }

    @Test fun malformedAlignmentCanonicalRedirectAndChallengeFailClosed() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) { LyrsenseTranslationProvider.parse(page().replace("songTextLine__translation", "other"), url) }
        assertThrows(IllegalArgumentException::class.java) { LyrsenseTranslationProvider.parse(page(canonical = "https://lyrsense.com/other/song"), url) }
        var calls = 0
        val provider = LyrsenseTranslationProvider { calls++; "<html>Verification required</html>" }
        assertEquals(TranslationSearchResult.Missing, provider.find("Example Song", "Example Artist"))
        assertEquals(TranslationSearchResult.Missing, provider.find("Other Song", "Example Artist", refresh = true))
        assertEquals(1, calls)
    }

    @Test fun unsafeUrlsAreRejectedAndCancellationPropagates() = runBlocking {
        listOf("http://lyrsense.com/a/b", "https://lyrsense.com@evil.example/a/b", "$url?next=x", "$url#x", "https://lyrsense.com/a/%2fsecret", "https://lyrsense.com:443/a/b").forEach {
            assertThrows(IllegalArgumentException::class.java) { LyrsenseTranslationProvider.canonicalUrl(it) }
        }
        val provider = LyrsenseTranslationProvider { throw CancellationException("cancel fixture") }
        try { provider.find("Example Song", "Example Artist"); fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
    }
}
