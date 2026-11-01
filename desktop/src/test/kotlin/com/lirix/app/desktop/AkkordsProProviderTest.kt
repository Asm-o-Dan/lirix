package com.lirix.app.desktop

import com.lirix.app.feature.lyrics.AkkordsProProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AkkordsProProviderTest {
    private val url = "https://akkords.pro/songs/example-artist/example-song/"
    private fun page(artist: String = "Example Artist", title: String = "Example Song", canonical: String = url) = """
        <link rel="canonical" href="$canonical"><h1 class="entry-title">$title: аккорды для гитары</h1>
        <nav id="kadence-breadcrumbs"><a href="/songs/">Исполнители</a><a href="/songs/example-artist/">$artist</a></nav>
        <article><div class="entry-content"><p class="chords"><b>Fixture section:</b>
          <span>Am</span>      <span>C/G</span>
        Original fixture line
             <span>F#m7</span>
        <span>Keep this fixture sentence</span>
        </p></div></article>
        <b class="entry-author-name">Fixture Arranger</b><aside>Comments excluded</aside>
    """.trimIndent()
    private fun search(vararg rows: Triple<String, String, String>): String = """
        <form class="search-form"><input name="s"></form>
        ${rows.joinToString("") { (title, artist, path) -> "<article class='apx-song-card'><a class='apx-song-card__artist'>$artist</a><h2><a class='apx-song-card__post-link' href='$path'>$title: аккорды для гитары</a></h2></article>" }}
    """.trimIndent()
    private val match = Triple("Example Song", "Example Artist", "/songs/example-artist/example-song/")

    @Test fun chordWhitespacePlainLyricsAndAttributionAreIndependent() {
        val result = AkkordsProProvider.parse(page(), url)
        assertEquals("builtin:akkords-pro", result.sourceId)
        assertEquals("Akkords.pro", result.sourceName)
        assertEquals(url, result.sourceUrl)
        assertEquals("Fixture Arranger", result.author)
        assertNull(result.syncedLyricsLrc)
        assertTrue(result.chordsText!!.contains("Am      C/G"))
        assertTrue(result.chordsText!!.contains("\n"))
        assertTrue(result.plainLyrics!!.contains("Original fixture line"))
        assertTrue(result.plainLyrics!!.contains("Keep this fixture sentence"))
        assertFalse(result.plainLyrics!!.contains("F#m7"))
        assertFalse(result.plainLyrics!!.contains("C/G"))
        assertFalse(result.chordsText!!.contains("Comments excluded"))
    }

    @Test fun radioheadAndKinoMetadataShapesAreSupportedWithoutSlugGuessing() = runBlocking {
        listOf("Radiohead" to "Creep", "Кино (Виктор Цой)" to "Группа крови").forEach { (artist, title) ->
            val provider = AkkordsProProvider { request -> if (request.contains("?s=")) search(Triple(title, artist, "/songs/example-artist/example-song/")) else page(artist, title) }
            assertNotNull(provider.find(title, artist.substringBefore(" (")))
        }
    }

    @Test fun pageArtistAndTitleAreVerifiedAgain() = runBlocking {
        for (wrongPage in listOf(page(artist = "Other Artist"), page(title = "Other Song"))) {
            val provider = AkkordsProProvider { if (it.contains("?s=")) search(match) else wrongPage }
            assertNull(provider.find("Example Song", "Example Artist"))
        }
    }

    @Test fun searchDoesNotAcceptLiveVersionsOrForeignAndCatalogLinks() {
        val results = AkkordsProProvider.candidates(search(match,
            Triple("Example Song (Live)", "Example Artist", "/songs/example-artist/live/"),
            Triple("Example Song", "Other Artist", "/songs/other/example-song/"),
            Triple("Example Song", "Example Artist", "https://evil.example/songs/a/b/"),
            Triple("Example Song", "Example Artist", "/songs/example-artist/")), "Example Song", "Example Artist")
        assertEquals(listOf(url), results.map { it.url })
        assertTrue(AkkordsProProvider.candidates(search(match), "Example Song (Acoustic)", "Example Artist").isEmpty())
    }

    @Test fun ambiguityDoesNotLoadFirstAndEmptyResultsDoNotStorm() = runBlocking {
        var calls = 0
        val ambiguous = AkkordsProProvider { calls++; search(match, Triple("Example Song", "Example Artist", "/songs/example-artist/second/")) }
        assertNull(ambiguous.find("Example Song", "Example Artist"))
        assertEquals(1, calls)
        calls = 0
        val empty = AkkordsProProvider { calls++; search() }
        repeat(2) { assertNull(empty.find("ZZZLirixNoSuchSong", "Unknown Fixture Artist")) }
        assertEquals(1, calls)
    }

    @Test fun invalidPageCanonicalMissingChordsAndUnsafeUrlsFailClosed() {
        assertThrows(IllegalArgumentException::class.java) { AkkordsProProvider.parse(page(canonical = "https://akkords.pro/songs/other/song/"), url) }
        assertThrows(IllegalArgumentException::class.java) { AkkordsProProvider.parse(page().replace("class=\"chords\"", "class=\"other\""), url) }
        listOf("http://akkords.pro/songs/a/b/", "https://akkords.pro@evil.example/songs/a/b/", "$url?x=1", "$url#x", "https://akkords.pro/songs/a/%2f/", "https://akkords.pro/songs/a/").forEach {
            assertThrows(IllegalArgumentException::class.java) { AkkordsProProvider.canonicalUrl(it) }
        }
    }

    @Test fun nullNetworkAndChallengesFallBackButCancellationPropagates() = runBlocking {
        assertNull(AkkordsProProvider { null }.find("Example Song", "Example Artist"))
        var calls = 0
        val challenge = AkkordsProProvider { calls++; "<html>Challenge page</html>" }
        assertNull(challenge.find("Example Song", "Example Artist"))
        assertNull(challenge.find("Other Song", "Example Artist"))
        assertEquals(1, calls)
        try { AkkordsProProvider { throw CancellationException("cancel fixture") }.find("Example Song", "Example Artist"); fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
    }
}
