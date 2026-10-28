package com.lirix.app.desktop

import com.lirix.app.translations.LyricsTranslateProvider
import com.lirix.app.translations.RustoengTranslationProvider
import com.lirix.app.translations.TranslationSearchResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in network smoke test; never logs song texts. */
class LiveTranslationSourcesTest {
    private fun requireLive() = assumeTrue("Set LIRIX_TRANSLATIONS_LIVE=1 for real-source checks", System.getenv("LIRIX_TRANSLATIONS_LIVE") == "1")

    @Test fun rustoengRussianSongFindsEnglishTranslation() = runBlocking<Unit> {
        requireLive()
        val provider = RustoengTranslationProvider()
        val result = provider.find("Солдат", "5'nizza")
        val document = when (result) {
            is TranslationSearchResult.Found -> result.document
            is TranslationSearchResult.Candidates -> provider.load(result.values.single().sourceUrl)
            else -> error("Real Rustoeng search did not return a unique candidate")
        }
        assertEquals("en", document.language)
        assertEquals("Солдат", document.title)
        assertEquals("Rustoeng", document.sourceName)
        assertTrue(document.blocks.any { it.translated.isNotBlank() })
        assertTrue(document.blocks.all { it.original.isBlank() })
        assertFalse(document.aligned)
    }

    @Test fun lyricsTranslateRussianSongOffersEnglishCandidate() = runBlocking<Unit> {
        requireLive()
        val provider = LyricsTranslateProvider()
        val result = provider.find("Я свободен", "Кипелов", "en")
        val candidate = (result as? TranslationSearchResult.Candidates)?.values
            ?.firstOrNull { it.sourceUrl.contains("kipelov-ya-svoboden-english") }
            ?: error("Real LyricsTranslate search did not return the expected candidate")
        val document = provider.load(candidate.sourceUrl, "en")
        assertEquals("en", document.language)
        assertEquals("Я свободен", document.title)
        assertTrue(document.artist.equals("Kipelov", ignoreCase = true))
        assertTrue(document.blocks.any { it.translated.isNotBlank() })
    }
}
