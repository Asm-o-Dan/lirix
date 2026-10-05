package com.lirix.app

import com.lirix.app.domain.Category
import com.lirix.app.domain.Event
import com.lirix.app.domain.EventSource
import com.lirix.app.domain.EventType
import com.lirix.app.feature.AggregatedLyricsProvider
import com.lirix.app.feature.FallbackLyricsScraper
import com.lirix.app.feature.LrcLibLyricsProvider
import com.lirix.app.feature.music.MusicTrackParser
import com.lirix.app.feature.OfflineLyricsAdapter
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LyricsAndTrackParserTest {

    // --------------------------------------------------------------------
    // 1. MusicTrackParser tests: extracting artist, title, album from notifications
    // --------------------------------------------------------------------

    @Test
    fun testParseTitleWithHyphenSeparator() {
        val event = Event(
            source = EventSource.NOTIFICATION,
            sourcePackage = "ru.yandex.music",
            appName = "Яндекс Музыка",
            title = "Кино - Группа крови",
            text = "Яндекс Музыка",
            category = Category.MUSIC
        )
        val parsed = MusicTrackParser.parse(event)
        assertEquals("Кино", parsed.artist)
        assertEquals("Группа крови", parsed.title)
    }

    @Test
    fun testParseTitleWithEmDashSeparator() {
        val event = Event(
            source = EventSource.NOTIFICATION,
            sourcePackage = "com.spotify.music",
            appName = "Spotify",
            title = "Miyagi & Эндшпиль — I Got Love",
            text = "",
            category = Category.MUSIC
        )
        val parsed = MusicTrackParser.parse(event)
        assertEquals("Miyagi & Эндшпиль", parsed.artist)
        assertEquals("I Got Love", parsed.title)
    }

    @Test
    fun testParseTextContainsArtistAndTitleWhenTitleIsAppName() {
        val event = Event(
            source = EventSource.NOTIFICATION,
            sourcePackage = "com.spotify.music",
            appName = "Spotify",
            title = "Spotify",
            text = "Linkin Park — Numb",
            category = Category.MUSIC
        )
        val parsed = MusicTrackParser.parse(event)
        assertEquals("Linkin Park", parsed.artist)
        assertEquals("Numb", parsed.title)
    }

    @Test
    fun testParseStandardMediaNotificationTitleTrackTextArtist() {
        val event = Event(
            source = EventSource.NOTIFICATION,
            sourcePackage = "com.vkmusic",
            appName = "VK Музыка",
            title = "Bohemian Rhapsody",
            text = "Queen",
            category = Category.MUSIC
        )
        val parsed = MusicTrackParser.parse(event)
        assertEquals("Bohemian Rhapsody", parsed.title)
        assertEquals("Queen", parsed.artist)
    }

    @Test
    fun testParseMediaNotificationWithAlbumInText() {
        val event = Event(
            source = EventSource.NOTIFICATION,
            sourcePackage = "com.apple.android.music",
            appName = "Apple Music",
            title = "In The End",
            text = "Linkin Park — Hybrid Theory",
            category = Category.MUSIC
        )
        val parsed = MusicTrackParser.parse(event)
        assertEquals("In The End", parsed.title)
        assertEquals("Linkin Park", parsed.artist)
        assertEquals("Hybrid Theory", parsed.album)
    }

    @Test
    fun testParseMediaSessionWithCombinedTrackField() {
        val event = Event(
            source = EventSource.MEDIA_SESSION,
            sourcePackage = "com.maxmpz.audioplayer",
            appName = "Poweramp",
            mediaTrack = "Eminem - Lose Yourself",
            mediaArtist = null,
            category = Category.MUSIC
        )
        val parsed = MusicTrackParser.parse(event)
        assertEquals("Eminem", parsed.artist)
        assertEquals("Lose Yourself", parsed.title)
    }

    @Test
    fun testParseMediaSessionWithSeparateTrackAndArtist() {
        val event = Event(
            source = EventSource.MEDIA_SESSION,
            sourcePackage = "com.spotify.music",
            appName = "Spotify",
            mediaTrack = "Starboy",
            mediaArtist = "The Weeknd",
            category = Category.MUSIC
        )
        val parsed = MusicTrackParser.parse(event)
        assertEquals("Starboy", parsed.title)
        assertEquals("The Weeknd", parsed.artist)
    }

    // --------------------------------------------------------------------
    // 2. LrcLibLyricsProvider tests: direct get, search fallback, clean names
    // --------------------------------------------------------------------

    @Test
    fun testLrcLibDirectFetchWithSyncedLrc() = runBlocking {
        val mockJson = """
            {
              "id": 100,
              "trackName": "Yesterday",
              "artistName": "The Beatles",
              "plainLyrics": "Yesterday all my troubles seemed so far away",
              "syncedLyrics": "[00:05.12] Yesterday all my troubles seemed so far away\n[00:10.50] Now it looks as though they're here to stay"
            }
        """.trimIndent()

        val provider = LrcLibLyricsProvider(httpGet = { mockJson })
        val track = TrackEntity(
            trackKey = "key1",
            title = "Yesterday (Remastered 2009)",
            artist = "The Beatles feat. Orchestra",
            sourcePackage = "com.spotify.music"
        )
        val result = provider.getLyrics(track)

        assertTrue(result.hasLyrics)
        assertEquals("LrcLib", result.source)
        assertNotNull(result.syncedLyrics)
        assertTrue(result.syncedLyrics!!.contains("[00:05.12]"))
        assertEquals("Yesterday all my troubles seemed so far away", result.plainLyrics)
    }

    @Test
    fun testLrcLibSearchFallbackWhenArtistIsBlank() = runBlocking {
        val mockSearchJson = """
            [
              {
                "id": 200,
                "trackName": "Numb",
                "artistName": "Linkin Park",
                "plainLyrics": "I've become so numb, I can't feel you there",
                "syncedLyrics": "[00:20.10] I've become so numb, I can't feel you there"
              }
            ]
        """.trimIndent()

        var calledSearch = false
        val provider = LrcLibLyricsProvider(httpGet = { url ->
            if (url.contains("/api/search")) {
                calledSearch = true
                mockSearchJson
            } else {
                null // direct /api/get is skipped or fails
            }
        })

        val track = TrackEntity(
            trackKey = "key2",
            title = "Numb",
            artist = "", // missing artist
            sourcePackage = "unknown"
        )
        val result = provider.getLyrics(track)

        assertTrue("Search endpoint must be called when artist is blank", calledSearch)
        assertTrue(result.hasLyrics)
        assertEquals("LrcLib (Search)", result.source)
        assertNotNull(result.syncedLyrics)
        assertTrue(result.syncedLyrics!!.contains("[00:20.10]"))
    }

    @Test
    fun testLrcLibNameSanitizers() {
        val cleanTrack = LrcLibLyricsProvider.cleanTrackName("Starboy (feat. Daft Punk) [Remix] - Radio Edit")
        assertEquals("Starboy", cleanTrack)

        val cleanArtist = LrcLibLyricsProvider.cleanArtistName("The Weeknd feat. Daft Punk")
        assertEquals("The Weeknd", cleanArtist)

        val lrc = "[01:23.45] First line\n[01:28.10] Second line"
        val plain = LrcLibLyricsProvider.cleanLrcToPlain(lrc)
        assertEquals("First line\nSecond line", plain)
    }

    // --------------------------------------------------------------------
    // 3. FallbackLyricsScraper tests
    // --------------------------------------------------------------------

    @Test
    fun testFallbackTextpesniScraping() = runBlocking {
        val searchHtml = """
            <html><body><a href="/song/12345-gruppa-krovi">Кино - Группа крови</a></body></html>
        """.trimIndent()

        val songHtml = """
            <html><body>
                <div class="song-text">
                    Теплое место на улице ждет<br/>
                    Отпечатков наших ног<br/>
                    Звездная пыль на сапогах
                </div>
            </body></html>
        """.trimIndent()

        val provider = FallbackLyricsScraper(httpGet = { url ->
            if (url.contains("textpesni.com/search")) searchHtml
            else songHtml
        })

        val track = TrackEntity(
            trackKey = "key_kino",
            title = "Группа крови",
            artist = "Кино",
            sourcePackage = "ru.yandex.music"
        )

        val result = provider.getLyrics(track)
        assertTrue(result.hasLyrics)
        assertEquals("Textpesni", result.source)
        assertTrue(result.lyricsText.contains("Теплое место на улице ждет"))
    }

    @Test
    fun testFallbackGeniusScraping() = runBlocking {
        val geniusSearchJson = """
            {
              "response": {
                "sections": [
                  {
                    "type": "song",
                    "hits": [
                      {
                        "result": {
                          "url": "https://genius.com/Linkin-park-in-the-end-lyrics"
                        }
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val geniusPageHtml = """
            <html><body>
                <div data-lyrics-container="true">It starts with one thing, I don't know why</div>
                <div data-lyrics-container="true">It doesn't even matter how hard you try</div>
            </body></html>
        """.trimIndent()

        val provider = FallbackLyricsScraper(httpGet = { url ->
            if (url.contains("genius.com/api/search")) geniusSearchJson
            else geniusPageHtml
        })

        val track = TrackEntity(
            trackKey = "key_lp",
            title = "In The End",
            artist = "Linkin Park",
            sourcePackage = "com.spotify.music"
        )

        val result = provider.getLyrics(track)
        assertTrue(result.hasLyrics)
        assertEquals("Genius", result.source)
        assertTrue(result.lyricsText.contains("It starts with one thing"))
    }

    @Test
    fun testFallbackAmDmScraping() = runBlocking {
        val amdmSearchHtml = """
            <html><body>
                <table class="items">
                    <tr>
                        <td class="artist_name">
                            <a href="https://amdm.ru/akkordi/ehvgenol/" class="artist">Эвгенол</a> -
                            <a href="https://amdm.ru/akkordi/ehvgenol/201320/hudozhnik/" class="artist">Художник</a>
                        </td>
                    </tr>
                </table>
                <div class="b-sidebar-right">
                    <ul class="b-sidebar-right-items">
                        <li>
                            <a href="https://amdm.ru/akkordi/ekaterina_yashnikova/218736/zmeyki_lesenki/">Змейки-лесенки</a>
                        </li>
                    </ul>
                </div>
            </body></html>
        """.trimIndent()

        val amdmSongHtml = """
            <html><body>
                <pre itemprop="chordsBlock" class="field__podbor_new podbor__text"><div class="podbor__keyword">[Куплет]:</div><div class="podbor__chord" data-chord="Dm"><span>Dm</span></div>     <div class="podbor__chord" data-chord="C"><span>C</span></div>       <div class="podbor__chord" data-chord="Bb"><span>Bb</span></div>            <div class="podbor__chord" data-chord="A"><span>A</span></div>
Жил художник один - ужасный почерк -
<div class="podbor__chord" data-chord="Dm"><span>Dm</span></div>     <div class="podbor__chord" data-chord="C"><span>C</span></div>       <div class="podbor__chord" data-chord="Bb"><span>Bb</span></div>             <div class="podbor__chord" data-chord="A"><span>A</span></div>
Он не писал картин... спасибо, Отче.
На этот мир смотрел сквозь объектив
И мир в нём страшно горел, но как красиво</pre>
            </body></html>
        """.trimIndent()

        val provider = FallbackLyricsScraper(httpGet = { url ->
            if (url.contains("amdm.ru/search")) amdmSearchHtml
            else amdmSongHtml
        })

        val track = TrackEntity(
            trackKey = "key_evgenol",
            title = "Художник",
            artist = "Эвгенол",
            sourcePackage = "ru.yandex.music"
        )

        val result = provider.getLyrics(track)
        assertTrue(result.hasLyrics)
        assertEquals("AmDm", result.source)
        assertTrue(result.lyricsText.contains("Жил художник один - ужасный почерк -"))
        assertTrue(result.lyricsText.contains("Он не писал картин... спасибо, Отче."))
        // Ensure chord tags are stripped from output
        assertFalse(result.lyricsText.contains("podbor__chord"))
        assertFalse(result.lyricsText.contains("data-chord"))
    }

    @Test
    fun testAmDmDoesNotLeakSidebarWhenNoSearchResults() = runBlocking {
        // AmDm search page when query has 0 results: only sidebar popular picks exist
        val amdmEmptySearchHtml = """
            <html><body>
                <div class="h1__info b-search-info">
                    <ul class="h1__tabs">
                        <li><span class="open">Все</span><sup>0</sup></li>
                    </ul>
                </div>
                <div class="b-sidebar-right daily_best_posts">
                    <div class="chords_day_activity">
                        <ul class="b-sidebar-right-items">
                            <li>
                                <a href="https://amdm.ru/akkordi/ekaterina_yashnikova/" class="artist">Екатерина Яшникова</a> -
                                <a href="https://amdm.ru/akkordi/ekaterina_yashnikova/218736/zmeyki_lesenki/" class="artist">Змейки-лесенки</a>
                            </li>
                        </ul>
                    </div>
                </div>
            </body></html>
        """.trimIndent()

        val zmeykiSongHtml = """
            <html><body>
                <pre itemprop="chordsBlock" class="field__podbor_new podbor__text">
                    Змейки-лесенки, детские песенки
                    Падали с неба цветные стекляшки
                </pre>
            </body></html>
        """.trimIndent()

        val provider = FallbackLyricsScraper(httpGet = { url ->
            if (url.contains("amdm.ru/search")) amdmEmptySearchHtml
            else zmeykiSongHtml
        })

        val track = TrackEntity(
            trackKey = "key_pritcha",
            title = "Притча о шмеле",
            artist = "Тот Самый",
            sourcePackage = "ru.yandex.music"
        )

        val result = provider.getLyrics(track)
        assertFalse("Must not return lyrics from sidebar when 0 search results", result.hasLyrics)
        assertFalse("Must not leak unrelated song text", result.lyricsText.contains("Змейки-лесенки"))
    }

    @Test
    fun testFallbackVsePesniScraping() = runBlocking {
        val vseSearchHtml = """
            <html><body>
                <ul class="search-results-list">
                    <li><a href="https://vse-pesni.com/song/evgenol-xudozhnik/">Эвгенол &#8212; Художник</a></li>
                </ul>
            </body></html>
        """.trimIndent()

        val vseSongHtml = """
            <html><body>
                <div class="song_text" itemprop="lyrics">
                    <div class="can_copy">
                        <p>Жил художник один &#8212; ужасный почерк &#8212;<br /> Он не писал картин&#8230; спасибо, Отче.<br /> На этот мир смотрел сквозь объектив<br /> И мир в нём страшно горел, но как красиво</p>
                        <div class="textwidget custom-html-widget"><div>AD BANNER</div></div>
                    </div>
                    <button class="copy-button" title="Копировать"></button>
                </div>
            </body></html>
        """.trimIndent()

        val provider = FallbackLyricsScraper(httpGet = { url ->
            if (url.contains("vse-pesni.com/?s=")) vseSearchHtml
            else vseSongHtml
        })

        val track = TrackEntity(
            trackKey = "key_evgenol_vse",
            title = "Художник",
            artist = "Эвгенол",
            sourcePackage = "com.spotify.music"
        )

        val result = provider.getLyrics(track)
        assertTrue(result.hasLyrics)
        assertEquals("Vse-Pesni", result.source)
        assertTrue(result.lyricsText.contains("Жил художник один — ужасный почерк —"))
        assertTrue(result.lyricsText.contains("Он не писал картин… спасибо, Отче."))
        assertFalse(result.lyricsText.contains("AD BANNER"))
        assertFalse(result.lyricsText.contains("Копировать"))
    }

    @Test
    fun testVsePesniDoesNotLeakSidebarWhenNoSearchResults() = runBlocking {
        // Vse-Pesni page when 0 results found: only sidebar popular links exist
        val vseEmptySearchHtml = """
            <html><body>
                <div id="content"><h1>Результаты поиска: Ничего не найдено</h1></div>
                <aside id="sidebar">
                    <div class="widget-title">Популярные тексты песен</div>
                    <ul>
                        <li><a href="https://vse-pesni.com/song/a8p4s-ya-budu-angelom-tvoim/">A8P4S - Я буду ангелом твоим</a></li>
                    </ul>
                </aside>
            </body></html>
        """.trimIndent()

        val provider = FallbackLyricsScraper(httpGet = { vseEmptySearchHtml })
        val track = TrackEntity(
            trackKey = "key_patolog",
            title = "Патологоанатом",
            artist = "Андрей Беркут, Александр Медведский",
            sourcePackage = "ru.yandex.music"
        )

        val result = provider.getLyrics(track)
        assertFalse("Must not return lyrics from sidebar when 0 search results", result.hasLyrics)
        assertFalse("Must not leak unrelated song text", result.lyricsText.contains("Я буду ангелом твоим"))
    }

    // --------------------------------------------------------------------
    // 4. Network failure & offline fallback tests
    // --------------------------------------------------------------------

    @Test
    fun testNetworkErrorFallsBackGracefullyToOffline() = runBlocking {
        // Network throws IOException or SecurityException (e.g. no internet permission)
        val failingLrcLib = LrcLibLyricsProvider(httpGet = { throw IOException("Connection refused / No route to host") })
        val failingFallback = FallbackLyricsScraper(httpGet = { throw SecurityException("Permission denied (missing INTERNET)") })
        val offlineAdapter = OfflineLyricsAdapter()

        val aggregated = AggregatedLyricsProvider(
            lrcLibProvider = failingLrcLib,
            fallbackScraper = failingFallback,
            offlineAdapter = offlineAdapter
        )

        val trackWithoutNotes = TrackEntity(
            trackKey = "key_err",
            title = "Some Track",
            artist = "Some Artist",
            sourcePackage = "com.spotify.music"
        )

        val resultEmpty = aggregated.getLyrics(trackWithoutNotes)
        assertFalse(resultEmpty.hasLyrics)
        assertEquals("Локальный офлайн-режим", resultEmpty.source)

        // With user notes, it safely returns user notes even during complete network blackout
        val trackWithNotes = trackWithoutNotes.copy(userNotes = "My favorite lines: Hello world")
        val resultNotes = aggregated.getLyrics(trackWithNotes)
        assertTrue(resultNotes.hasLyrics)
        assertTrue(resultNotes.isUserNote)
        assertEquals("Пользовательские заметки", resultNotes.source)
        assertEquals("My favorite lines: Hello world", resultNotes.lyricsText)
    }

    @Test
    fun testCachedRoomLyricsBypassesNetwork() = runBlocking {
        var networkCalls = 0
        val lrcLib = LrcLibLyricsProvider(httpGet = {
            networkCalls++
            null
        })

        val aggregated = AggregatedLyricsProvider(
            lrcLibProvider = lrcLib,
            fallbackScraper = FallbackLyricsScraper(httpGet = { networkCalls++; null })
        )

        val trackWithCachedLyrics = TrackEntity(
            trackKey = "cached_key",
            title = "Cached Song",
            artist = "Cached Artist",
            sourcePackage = "com.spotify.music",
            syncedLyrics = "[00:10.00] Synced line",
            plainLyrics = "Synced line"
        )

        val result = aggregated.getLyrics(trackWithCachedLyrics)
        assertTrue(result.hasLyrics)
        assertEquals("Локальный кэш Room", result.source)
        assertEquals(0, networkCalls)
        assertEquals("[00:10.00] Synced line", result.syncedLyrics)
    }

    @Test
    fun testLyricFindScraper() = runBlocking {
        val searchHtml = """
            <html>
                <body>
                    <div class="search-results">
                        <a href="/lyrics/coldplay-yellow">Coldplay - Yellow</a>
                        <a href="/lyrics/coldplay-fix-you">Coldplay - Fix You</a>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val songHtml = """
            <html>
                <body>
                    <div class="track-lyrics">
                        Look at the stars<br>
                        Look how they shine for you<br>
                        And everything you do<br>
                        Yeah they were all yellow
                    </div>
                </body>
            </html>
        """.trimIndent()

        val scraper = FallbackLyricsScraper(httpGet = { url ->
            if (url.contains("/search")) searchHtml
            else if (url.contains("/lyrics/coldplay-yellow")) songHtml
            else null
        })

        val track = TrackEntity(
            trackKey = "coldplay_yellow",
            title = "Yellow",
            artist = "Coldplay",
            sourcePackage = "com.spotify.music"
        )

        val result = scraper.scrapeLyricFind("Coldplay", "Yellow")
        assertNotNull(result)
        assertTrue(result!!.contains("Look at the stars"))
        assertTrue(result.contains("Yeah they were all yellow"))

        // Also verify FallbackLyricsScraper.getLyrics() cascade returns LyricFind
        val fullResult = scraper.getLyrics(track)
        assertTrue(fullResult.hasLyrics)
        assertEquals("LyricFind", fullResult.source)
        assertTrue(fullResult.lyricsText.contains("Look at the stars"))
    }

    // --------------------------------------------------------------------
    // 7. TASK-UI-02: parseLrcLinesStrict (HighFidelityKaraokePlayer)
    // --------------------------------------------------------------------

    @Test
    fun testParseLrcLinesStrictValidAndSorted() {
        val lrc = """
            [00:15.30] Second line
            [00:05.10] First line
            [01:02.500] Third line with 3 digits
            [02:10.5] Fourth line with 1 digit
        """.trimIndent()

        val parsed = com.lirix.app.ui.parseLrcLinesStrict(lrc)
        assertEquals(4, parsed.size)
        // Check sorted by timestamp
        assertEquals(5100L, parsed[0].timestampMs)
        assertEquals("First line", parsed[0].text)

        assertEquals(15300L, parsed[1].timestampMs)
        assertEquals("Second line", parsed[1].text)

        assertEquals(62500L, parsed[2].timestampMs)
        assertEquals("Third line with 3 digits", parsed[2].text)

        assertEquals(130500L, parsed[3].timestampMs)
        assertEquals("Fourth line with 1 digit", parsed[3].text)
    }

    @Test
    fun testParseLrcLinesStrictHandlesEmptyAndInvalidLines() {
        val lrc = """
            [ar:The Beatles]
            [ti:Yesterday]
            [00:01.00] 
            Invalid Line Without Timestamp
            [00:04.20]Valid Line
        """.trimIndent()

        val parsed = com.lirix.app.ui.parseLrcLinesStrict(lrc)
        assertEquals(1, parsed.size)
        assertEquals(4200L, parsed[0].timestampMs)
        assertEquals("Valid Line", parsed[0].text)

        assertTrue(com.lirix.app.ui.parseLrcLinesStrict("").isEmpty())
        assertTrue(com.lirix.app.ui.parseLrcLinesStrict("   ").isEmpty())
    }
}


