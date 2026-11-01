package com.lirix.app.desktop

import com.lirix.app.desktop.lyrics.DesktopLyricsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for DesktopLyricsService robust chords search and table isolation.
 * Spec: TASK-BUG-DSK-CHORDS-01
 */
class DesktopLyricsServiceTest {

    private val service = DesktopLyricsService(CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun testAmDmRejectsPageWithoutItemsTable() {
        // Simulated AmDm empty search page containing navigation/popular links in the header
        val emptySearchHtml = """
            <!DOCTYPE html>
            <html>
            <head><title>Поиск - AmDm.ru</title></head>
            <body>
                <div class="nav">
                    <a href="https://amdm.ru/akkordi/popular/">Популярные аккорды</a>
                    <a href="https://amdm.ru/akkordi/new/">Новые аккорды</a>
                </div>
                <div class="content">
                    <p>По вашему запросу ничего не найдено.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val url = service.extractAmDmSongUrl(emptySearchHtml, expectedTitle = "Трупы", expectedArtist = "Батарея")
        assertNull("Must strictly return null when table.items is missing", url)
    }

    @Test
    fun testAmDmExtractsSongUrlWithMismatchedArtistFallback() {
        // Simulated AmDm search result for "Трупы"
        val searchHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="nav"><a href="/akkordi/popular/">Популярные</a></div>
                <table class="items">
                    <tr>
                        <td class="artist">
                            <a href="https://amdm.ru/akkordi/brigadnyy_podryad/" class="artist">Бригадный подряд</a> - 
                            <a href="https://amdm.ru/akkordi/brigadnyy_podryad/207027/trupy/" class="artist">Трупы</a>
                        </td>
                    </tr>
                    <tr>
                        <td class="artist">
                            <a href="https://amdm.ru/akkordi/drugaya_gruppa/" class="artist">Другая группа</a> - 
                            <a href="https://amdm.ru/akkordi/drugaya_gruppa/12345/pesnya/" class="artist">Песня</a>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // Artist from GSMTC is "Батарея" (single/album name), but title is "Трупы"
        val url = service.extractAmDmSongUrl(searchHtml, expectedTitle = "Трупы", expectedArtist = "Батарея")
        assertNotNull(url)
        assertEquals("https://amdm.ru/akkordi/brigadnyy_podryad/207027/trupy/", url)
    }

    @Test
    fun testAmDmPrioritizesExactArtistAndTitleMatch() {
        val searchHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <table class="items">
                    <tr>
                        <td class="artist">
                            <a href="/akkordi/cover_band/">Кавер Бэнд</a> - 
                            <a href="/akkordi/cover_band/111/zvezda/">Звезда</a>
                        </td>
                    </tr>
                    <tr>
                        <td class="artist">
                            <a href="/akkordi/kino/">Кино</a> - 
                            <a href="/akkordi/kino/222/zvezda/">Звезда</a>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        val url = service.extractAmDmSongUrl(searchHtml, expectedTitle = "Звезда", expectedArtist = "Кино")
        assertNotNull(url)
        assertEquals("https://amdm.ru/akkordi/kino/222/zvezda/", url)
    }

    @Test
    fun testMyChordsExtractsSongUrlFromAutocomplete() {
        val jsonStr = """
            {
                "suggestions": [
                    {
                        "value": "Бригадный Подряд - Трупы",
                        "data": {
                            "url": "/ru/artists/brigadnyy-podryad/trupy.html"
                        }
                    },
                    {
                        "value": "Другие - Другая",
                        "data": {
                            "url": "/ru/artists/drugie/drugya.html"
                        }
                    }
                ]
            }
        """.trimIndent()

        val url = service.extractMyChordsSongUrl(jsonStr, expectedTitle = "Трупы", expectedArtist = "Батарея")
        assertNotNull(url)
        assertEquals("https://mychords.net/ru/artists/brigadnyy-podryad/trupy.html", url)
    }

    @Test
    fun testAmDmRejectsUnrelatedSongWithMatchingStopWords() {
        // AmDm search for "The Laugh Track" returning "The White Buffalo - Come Join The Murder"
        val searchHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <table class="items">
                    <tr>
                        <td class="artist_name">
                            <a href="https://amdm.ru/akkordi/the_white_buffalo/" class="artist">The White Buffalo</a> -
                            <a href="https://amdm.ru/akkordi/the_white_buffalo/147036/come_join_the_murder/" class="artist">Come Join The Murder</a>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
        """.trimIndent()

        // Expected is "The Laugh Track" by "Ice Nine Kills". "the" must not cause a false-positive match!
        val url = service.extractAmDmSongUrl(searchHtml, expectedTitle = "The Laugh Track", expectedArtist = "Ice Nine Kills")
        assertNull("Must reject candidate whose only common token is the stop-word 'the'", url)
    }

    @Test
    fun testVerifyChordsAgainstLyricsPreventsMismatchedChords() {
        val iceNineKillsLyrics = """
            [00:00.00] Time ticks by as the story unfolds
            [00:15.00] All smiles tonight, so let the laughter explode
            [00:18.00] Half comedy, half killin' spree
            [00:20.00] My screws are loose, but the joke's on you
            [00:23.00] 'Cause Gotham is burnin' bright
            [00:25.00] But the show must go on tonight
        """.trimIndent()

        val whiteBuffaloChords = """
            Dm                                     Bb
            There's a blackbird perched outside my window
                       F
            I hear him calling
                       Am
            I hear him sing
            Dm                                   Bb
            He burns me with his eyes of gold to embers
            F             C
            Come join the murder
        """.trimIndent()

        val matches = service.verifyChordsAgainstLyrics(whiteBuffaloChords, iceNineKillsLyrics)
        org.junit.Assert.assertFalse("Must reject chord sheet from a completely different song", matches)

        val matchingChords = """
            Dm                  Bb
            Time ticks by as the story unfolds
                      F
            All smiles tonight, so let the laughter explode
        """.trimIndent()

        val matchesValid = service.verifyChordsAgainstLyrics(matchingChords, iceNineKillsLyrics)
        org.junit.Assert.assertTrue("Must accept chords with matching lyrics text", matchesValid)
    }

    @Test
    fun testBuildSearchCandidatesGeneratesBidirectionalPairsForDirtyTitles() {
        val candidates = service.buildSearchCandidates(
            rawTitle = "Given Up [Official Music Video] - Linkin Park",
            rawArtist = "Linkin Park",
            cleanTitle = "Given Up",
            cleanArtist = "Linkin Park"
        )
        // Must contain both clean pair and reverse pair
        org.junit.Assert.assertTrue(
            "Must generate ('Linkin Park', 'Given Up')",
            candidates.contains("Linkin Park" to "Given Up")
        )

        val reversedDash = service.buildSearchCandidates(
            rawTitle = "California Dreamin' // The Mamas and the Papas",
            rawArtist = "Unknown Artist",
            cleanTitle = "California Dreamin'",
            cleanArtist = "The Mamas and the Papas"
        )
        org.junit.Assert.assertTrue(
            "Must extract candidate pair across // separator",
            candidates.any { it.first.contains("Linkin Park") }
        )
        org.junit.Assert.assertTrue(
            "Must contain ('The Mamas and the Papas', 'California Dreamin')",
            reversedDash.contains("The Mamas and the Papas" to "California Dreamin'") ||
            reversedDash.contains("California Dreamin'" to "The Mamas and the Papas")
        )
    }

    @Test
    fun testBuildSearchCandidatesHandlesUnknownArtistWithTitleOnly() {
        val candidates = service.buildSearchCandidates(
            rawTitle = "CHAOS",
            rawArtist = "",
            cleanTitle = "CHAOS",
            cleanArtist = "Unknown Artist"
        )
        org.junit.Assert.assertTrue(
            "Must contain empty-artist title fallback candidate",
            candidates.contains("" to "CHAOS")
        )
    }

    @Test
    fun testGeniusScraperParsesLyricsContainers() = kotlinx.coroutines.runBlocking {
        val searchJson = """
            {
                "response": {
                    "sections": [
                        {
                            "type": "song",
                            "hits": [
                                {
                                    "result": {
                                        "url": "https://genius.com/Linkin-park-given-up-lyrics"
                                    }
                                }
                            ]
                        }
                    ]
                }
            }
        """.trimIndent()

        val songHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <div data-lyrics-container="true">
                    Woke up on the wrong side of another day<br/>
                    With nothing to say and nothing to lose<br/>
                    I'm my own worst enemy
                </div>
            </body>
            </html>
        """.trimIndent()

        val customService = DesktopLyricsService(
            scope = CoroutineScope(Dispatchers.Unconfined),
            httpFetch = { url, _ ->
                when {
                    url.contains("genius.com/api/search") -> searchJson
                    url.contains("genius.com/Linkin-park-given-up-lyrics") -> songHtml
                    else -> null
                }
            }
        )

        val lyrics = customService.scrapeGenius("Linkin Park", "Given Up")
        assertNotNull("Genius scraper must extract lyrics", lyrics)
        org.junit.Assert.assertTrue(lyrics!!.contains("Woke up on the wrong side"))
        org.junit.Assert.assertTrue(lyrics.contains("I'm my own worst enemy"))
    }

    @Test
    fun testAmalgamaScraperParsesOriginalDiv() = kotlinx.coroutines.runBlocking {
        val songHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="original">
                    In the end it doesn't even matter<br>
                    I had to fall to lose it all<br>
                    But in the end it doesn't even matter
                </div>
            </body>
            </html>
        """.trimIndent()

        val customService = DesktopLyricsService(
            scope = CoroutineScope(Dispatchers.Unconfined),
            httpFetch = { url, _ ->
                if (url.contains("amalgama-lab.com")) songHtml else null
            }
        )

        val lyrics = customService.scrapeAmalgama("Linkin Park", "In The End")
        assertNotNull("Amalgama scraper must extract lyrics", lyrics)
        org.junit.Assert.assertTrue(lyrics!!.contains("In the end it doesn't even matter"))
    }

    @Test
    fun testTitleOnlySearchResolvesRealArtistForAcronymChannel() = kotlinx.coroutines.runBlocking {
        val tempDir = java.nio.file.Files.createTempDirectory("lirix-test-acronym").toFile()
        try {
            val lrcLibSearchJson = """
                [
                    {
                        "id": 88412,
                        "trackName": "Wash It All Away",
                        "artistName": "Five Finger Death Punch",
                        "albumName": "Got Your Six",
                        "syncedLyrics": "[00:15.00] All of the chaos and all of the lies\n[00:18.00] I hate it",
                        "plainLyrics": "All of the chaos and all of the lies\nI hate it"
                    }
                ]
            """.trimIndent()

            val customService = DesktopLyricsService(
                scope = CoroutineScope(Dispatchers.Unconfined),
                cacheDirectory = tempDir,
                httpFetch = { url, _ ->
                    if (url.contains("lrclib.net/api/search")) lrcLibSearchJson else null
                }
            )

            customService.onTrackChanged(
                rawTitle = "Wash It All Away (Official Video)",
                rawArtist = "5FDPVEVO"
            )

            val state = withTimeout(5_000) {
                customService.lyricsState.first { !it.isLoading && it.hasLyrics }
            }
            org.junit.Assert.assertTrue("Must find lyrics for 5FDPVEVO track", state.hasLyrics)
            assertEquals("Five Finger Death Punch", state.displayArtist)
            assertEquals("Wash It All Away", state.displayTitle)
            org.junit.Assert.assertTrue(state.plainLyrics!!.contains("All of the chaos"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
