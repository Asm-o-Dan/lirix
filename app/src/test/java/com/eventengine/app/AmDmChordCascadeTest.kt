package com.eventengine.app

import com.eventengine.app.feature.AggregatedLyricsProvider
import com.eventengine.app.feature.FallbackLyricsScraper
import com.eventengine.app.feature.LyricsProvider
import com.eventengine.app.feature.LyricsResult
import com.eventengine.app.feature.LyricsSourceIds
import com.eventengine.app.feature.lyrics.AmDmChordParser
import com.eventengine.app.storage.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD Unit-tests for TASK-BUG-08:
 * - Independent AmDm chord scraping and storage in LyricsResult.chords.
 * - Scraping AmDm extracts both clean lyrics (karaoke) and formatted chords.
 * - Hybrid mode: LRCLIB provides synced LRC lyrics while AmDm provides chords.
 * - Real snippet verification for "Аберрация — Продолжаем бой".
 */
class AmDmChordCascadeTest {

    @Test
    fun test_amdm_scraper_extracts_both_clean_lyrics_and_formatted_chords() = runBlocking {
        val amdmHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <table class="items">
                    <tr><td>
                        <a href="/akkordi/gruppa/123/pesnya/">Песня</a>
                        <a href="/akkordi/gruppa/">Группа</a>
                    </td></tr>
                </table>
                <div class="podbor__text">
                    <pre itemprop="chordsBlock">
                    <div class="podbor__chord" data-chord="Am"><span>Am</span></div>          <div class="podbor__chord" data-chord="C"><span>C</span></div>
                    Белый снег, серый лед,
                    <div class="podbor__chord" data-chord="Dm"><span>Dm</span></div>          <div class="podbor__chord" data-chord="G"><span>G</span></div>
                    На растрескавшейся земле.
                    </pre>
                </div>
            </body>
            </html>
        """.trimIndent()

        val scraper = FallbackLyricsScraper(httpGet = { amdmHtml })
        val track = TrackEntity(
            trackKey = "gruppa_pesnya",
            title = "Песня",
            artist = "Группа",
            sourcePackage = "com.spotify.music"
        )

        val result = scraper.getLyrics(track)

        assertTrue("Must find lyrics on AmDm", result.hasLyrics)
        assertEquals(LyricsSourceIds.AMDM, result.sourceId)

        // 1. Plain lyrics must contain clean song words for karaoke/reading without tags
        assertNotNull(result.plainLyrics)
        assertTrue(result.plainLyrics!!.contains("Белый снег, серый лед"))
        assertTrue(result.plainLyrics!!.contains("На растрескавшейся земле"))

        // 2. Chords must be preserved with chord symbols and alignment
        assertNotNull("LyricsResult.chords must be populated from AmDm", result.chords)
        assertTrue("Chords must contain Am", result.chords!!.contains("Am"))
        assertTrue("Chords must contain C", result.chords!!.contains("C"))
        assertTrue("Chords must contain Dm", result.chords!!.contains("Dm"))
        assertTrue("Chords must contain G", result.chords!!.contains("G"))
        assertTrue("Chords must retain lyrics lines underneath", result.chords!!.contains("Белый снег, серый лед"))
    }

    @Test
    fun test_hybrid_lrclib_synced_lyrics_with_amdm_chords() = runBlocking {
        val track = TrackEntity(
            trackKey = "artist_song",
            title = "Song With Chords",
            artist = "Cool Artist",
            sourcePackage = "com.spotify.music"
        )

        // LRCLIB returns timed karaoke LRC lyrics, but NO chords
        val mockLrcLib = object : LyricsProvider {
            override suspend fun getLyrics(
                track: TrackEntity,
                rejectedSourceIds: Set<String>,
                forceNetwork: Boolean
            ): LyricsResult {
                return LyricsResult(
                    hasLyrics = true,
                    lyricsText = "[00:12.00] Line one\n[00:15.00] Line two",
                    source = "LRCLIB",
                    syncedLyrics = "[00:12.00] Line one\n[00:15.00] Line two",
                    plainLyrics = "Line one\nLine two",
                    sourceId = LyricsSourceIds.LRCLIB,
                    chords = null
                )
            }
        }

        // AmDm provider returns chords for this song
        val mockAmDm = object : LyricsProvider {
            override suspend fun getLyrics(
                track: TrackEntity,
                rejectedSourceIds: Set<String>,
                forceNetwork: Boolean
            ): LyricsResult {
                return LyricsResult(
                    hasLyrics = true,
                    lyricsText = "Line one\nLine two",
                    source = "AmDm",
                    syncedLyrics = null,
                    plainLyrics = "Line one\nLine two",
                    sourceId = LyricsSourceIds.AMDM,
                    chords = "Em       Am\nLine one\nC        D\nLine two"
                )
            }
        }

        val aggregator = AggregatedLyricsProvider(
            lrcLibProvider = mockLrcLib,
            fallbackScraper = mockAmDm
        )

        val hybridResult = aggregator.getLyrics(track, emptySet(), false)

        assertTrue(hybridResult.hasLyrics)
        assertEquals("Primary source is LRCLIB for lyrics", LyricsSourceIds.LRCLIB, hybridResult.sourceId)
        assertNotNull("Synced lyrics must come from LRCLIB", hybridResult.syncedLyrics)
        assertEquals("[00:12.00] Line one\n[00:15.00] Line two", hybridResult.syncedLyrics)

        // Spec TASK-BUG-08: If LRCLIB had no chords, AggregatedLyricsProvider queries AmDm
        // and returns hybrid result with chords attached
        assertNotNull("Hybrid result must contain chords fetched from AmDm", hybridResult.chords)
        assertTrue(hybridResult.chords!!.contains("Em       Am"))
    }

    @Test
    fun test_aberratsiya_prodolzhaem_boy_chords_extracted() {
        val amdmRealSnippet = """
            <div class="podbor__text">
            <pre itemprop="chordsBlock">
            <div class="podbor__keyword">Вступление:</div>
            <div class="podbor__chord" data-chord="Bm"><span>Bm</span></div> <div class="podbor__chord" data-chord="G"><span>G</span></div> <div class="podbor__chord" data-chord="D"><span>D</span></div> <div class="podbor__chord" data-chord="A"><span>A</span></div> x2

            <div class="podbor__chord" data-chord="Bm"><span>Bm</span></div>
            За окном рассвет, но в сердце тьма,
            <div class="podbor__chord" data-chord="G"><span>G</span></div>
            Мы стоим на краю обрыва.
            <div class="podbor__chord" data-chord="D"><span>D</span></div>             <div class="podbor__chord" data-chord="A"><span>A</span></div>
            Продолжаем бой, пока есть силы дышать.
            </pre>
            </div>
        """.trimIndent()

        val parsedChords = AmDmChordParser.parseAmDmHtml(amdmRealSnippet)

        assertNotNull("Chords parser must successfully parse AmDm snippet for Аберрация - Продолжаем бой", parsedChords)
        assertTrue("Parsed chords must include Bm", parsedChords!!.contains("Bm"))
        assertTrue("Parsed chords must include G", parsedChords.contains("G"))
        assertTrue("Parsed chords must include D", parsedChords.contains("D"))
        assertTrue("Parsed chords must include A", parsedChords.contains("A"))
        assertTrue("Parsed chords must include text line", parsedChords.contains("Продолжаем бой, пока есть силы дышать"))
        assertTrue("Must not contain raw html span tags", !parsedChords.contains("<span>"))
        assertTrue("Must not contain raw html div tags", !parsedChords.contains("<div"))
    }
}
