package com.lirix.app.desktop

import com.lirix.app.desktop.lyrics.DesktopLyricsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
}
