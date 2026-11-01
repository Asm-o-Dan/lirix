package com.lirix.app.desktop

import com.lirix.app.feature.lyrics.AdditionalLyricsResult
import com.lirix.app.feature.lyrics.AkkordsProProvider
import com.lirix.app.feature.lyrics.KugouLyricsProvider
import com.lirix.app.feature.lyrics.LrcSyncEngine
import com.lirix.app.feature.lyrics.LyricsOvhProvider
import com.lirix.app.translations.LyrsenseTranslationProvider
import com.lirix.app.translations.TranslationSearchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Opt-in network smoke: prints metadata/counts only, never source bodies, lyrics or accesskeys. */
class SourceExpansionLiveTest {
    private val missingTitle = "QQQNoSuchSong726Lirix"
    private val missingArtist = "ZZZLirixNoSuchArtist726"

    @Before fun requireExplicitLiveOptIn() {
        assumeTrue("Set LIRIX_SOURCE_LIVE=1 to run public network smoke", System.getenv("LIRIX_SOURCE_LIVE") == "1")
    }

    private fun report(result: AdditionalLyricsResult?, artist: String, title: String) {
        println("source-smoke source=${result?.sourceName ?: "missing"} artist=$artist title=$title " +
            "plainLines=${result?.plainLyrics?.lineSequence()?.count { it.isNotBlank() } ?: 0} " +
            "lrcLines=${result?.syncedLyricsLrc?.let { LrcSyncEngine.parseLrcLinesStrict(it).size } ?: 0} " +
            "chordLines=${result?.chordsText?.lineSequence()?.count { it.isNotBlank() } ?: 0} " +
            "attributed=${!result?.sourceUrl.isNullOrBlank()} authorPresent=${!result?.author.isNullOrBlank()}")
    }

    private suspend fun verifyOvh(artist: String, title: String) {
        val result = LyricsOvhProvider().find(title, artist)
        report(result, artist, title)
        assertNotNull("lyrics.ovh did not return a plain-text result", result)
        assertTrue("lyrics.ovh plain lyrics are empty", !result!!.plainLyrics.isNullOrBlank())
        assertNull(result.syncedLyricsLrc)
        assertNull(result.chordsText)
        assertEquals("builtin:lyrics-ovh", result.sourceId)
    }

    private suspend fun verifyKugou(artist: String, title: String, durationMs: Long) {
        val result = KugouLyricsProvider().find(title, artist, durationMs)
        report(result, artist, title)
        assertNotNull("KuGou did not return a verified timed result", result)
        val lines = LrcSyncEngine.parseLrcLinesStrict(result!!.syncedLyricsLrc.orEmpty()).filter { it.text.isNotBlank() }
        assertTrue("KuGou result has fewer than two timed lines", lines.size >= 2)
        assertTrue("KuGou timestamps do not advance", lines.any { it.timestampMs > 0 })
        assertTrue(!result.plainLyrics.isNullOrBlank())
        assertEquals("builtin:kugou", result.sourceId)
    }

    private suspend fun verifyAkkords(artist: String, title: String) {
        val result = AkkordsProProvider().find(title, artist)
        report(result, artist, title)
        assertNotNull("Akkords.pro did not return a verified chord sheet", result)
        assertTrue("Akkords.pro chord sheet is empty", !result!!.chordsText.isNullOrBlank())
        assertTrue("Akkords.pro plain lyrics are empty", !result.plainLyrics.isNullOrBlank())
        assertTrue("Akkords.pro attribution is missing", !result.author.isNullOrBlank() && !result.sourceUrl.isNullOrBlank())
        assertNull(result.syncedLyricsLrc)
        assertEquals("builtin:akkords-pro", result.sourceId)
    }

    private suspend fun verifyLyrsense(artist: String, title: String) {
        val result = LyrsenseTranslationProvider().find(title, artist)
        assertTrue("Lyrsense did not return a verified Russian translation", result is TranslationSearchResult.Found)
        val document = (result as TranslationSearchResult.Found).document
        println("source-smoke source=Lyrsense artist=$artist title=$title blocks=${document.blocks.size} " +
            "aligned=${document.aligned} language=${document.language} authorPresent=${document.translator.isNotBlank()}")
        assertEquals("ru", document.language)
        assertTrue(document.blocks.any { it.original.isNotBlank() && it.translated.isNotBlank() })
        assertTrue(document.aligned)
        assertTrue(document.translator.isNotBlank())
        assertEquals("Lyrsense", document.sourceName)
    }

    @Test fun ovhColdplayYellow() = runBlocking { verifyOvh("Coldplay", "Yellow") }
    @Test fun ovhAdeleHello() = runBlocking { verifyOvh("Adele", "Hello") }
    @Test fun ovhMissingTrack() = runBlocking {
        val result = LyricsOvhProvider().find(missingTitle, missingArtist)
        report(result, missingArtist, missingTitle)
        assertNull("lyrics.ovh accepted nonexistent metadata", result)
    }

    @Test fun kugouColdplayYellow() = runBlocking { verifyKugou("Coldplay", "Yellow", 266_000L) }
    @Test fun kugouRadioheadCreep() = runBlocking { verifyKugou("Radiohead", "Creep", 238_000L) }
    @Test fun kugouMissingTrack() = runBlocking {
        val result = KugouLyricsProvider().find(missingTitle, missingArtist, 200_000L)
        report(result, missingArtist, missingTitle)
        assertNull("KuGou accepted nonexistent metadata", result)
    }

    @Test fun akkordsRadioheadCreep() = runBlocking { verifyAkkords("Radiohead", "Creep") }
    @Test fun akkordsKinoGruppaKrovi() = runBlocking { verifyAkkords("Кино", "Группа крови") }
    @Test fun akkordsMissingTrack() = runBlocking {
        val result = AkkordsProProvider().find(missingTitle, missingArtist)
        report(result, missingArtist, missingTitle)
        assertNull("Akkords.pro accepted nonexistent metadata", result)
    }

    @Test fun lyrsenseRadioheadCreep() = runBlocking { verifyLyrsense("Radiohead", "Creep") }
    @Test fun lyrsenseAdeleHello() = runBlocking { verifyLyrsense("Adele", "Hello") }
    @Test fun lyrsenseMissingTrack() = runBlocking {
        val result = LyrsenseTranslationProvider().find(missingTitle, missingArtist)
        println("source-smoke source=Lyrsense artist=$missingArtist title=$missingTitle found=${result is TranslationSearchResult.Found}")
        assertTrue("Lyrsense accepted nonexistent metadata", result == TranslationSearchResult.Missing)
    }
}
