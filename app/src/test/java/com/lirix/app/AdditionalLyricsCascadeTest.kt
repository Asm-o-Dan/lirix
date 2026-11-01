package com.lirix.app

import com.lirix.app.feature.*
import com.lirix.app.feature.lyrics.AdditionalLyricsResult
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.LyricsAttribution
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AdditionalLyricsCascadeTest {
    private val track = TrackEntity("source-test", "Song", "Artist", sourcePackage = "test")
    private val plain = object : LyricsProvider {
        override suspend fun getLyrics(track: TrackEntity) = LyricsResult(true, "LRCLIB original", "LRCLIB", plainLyrics = "LRCLIB original", sourceId = LyricsSourceIds.LRCLIB)
    }
    @Test fun kugouAddsTimingToLrclibPlainAndKeepsOriginalWordsAndSeparateChordsAttribution() = runBlocking {
        val calls = mutableListOf<String>()
        val provider = AggregatedLyricsProvider(lrcLibProvider = plain, fallbackScraper = null, additionalLookup = { id, _ ->
            calls += id
            when (id) {
                LyricsSourceIds.KUGOU -> AdditionalLyricsResult(id, "KuGou", "Other plain", "[00:01.00] First\n[00:03.00] Second", sourceUrl = "https://lyrics.kugou.com/search?keyword=test")
                LyricsSourceIds.AKKORDS_PRO -> AdditionalLyricsResult(id, "Akkords.pro", chordsText = "Am\nSynthetic", sourceUrl = "https://akkords.pro/songs/artist/song/", author = "Arranger")
                else -> null
            }
        })
        val result = provider.getLyrics(track)
        assertEquals("LRCLIB original", result.plainLyrics)
        assertNotNull(result.syncedLyrics)
        assertEquals(LyricsSourceIds.KUGOU, result.sourceId)
        val attribution = LyricsAttribution.decode(result.sourceAttributionJson)
        assertEquals(LyricsSourceIds.KUGOU, attribution.lyrics?.sourceId)
        assertEquals("Arranger", attribution.chords?.author)
        assertEquals(listOf(LyricsSourceIds.KUGOU, LyricsSourceIds.AKKORDS_PRO), calls)
    }
    @Test fun rejectionSkipsRequestsAndKugouMissPreservesLrclibPlain() = runBlocking {
        val calls = mutableListOf<String>()
        val provider = AggregatedLyricsProvider(lrcLibProvider = plain, fallbackScraper = null, additionalLookup = { id, _ -> calls += id; null })
        val result = provider.getLyrics(track, setOf(LyricsSourceIds.KUGOU, LyricsSourceIds.AKKORDS_PRO), true)
        assertTrue(calls.isEmpty())
        assertEquals("LRCLIB original", result.plainLyrics)
        assertNull(result.syncedLyrics)
        assertEquals(LyricsSourceIds.LRCLIB, result.sourceId)
    }
    @Test fun ovhIsPlainFallbackAndRejectedBackendCannotBeCalled() = runBlocking {
        val calls = mutableListOf<String>()
        val provider = AggregatedLyricsProvider(lrcLibProvider = null, fallbackScraper = null, additionalLookup = { id, _ ->
            calls += id
            if (id == LyricsSourceIds.LYRICS_OVH) AdditionalLyricsResult(id, "lyrics.ovh", plainLyrics = "Synthetic fallback") else null
        })
        assertEquals("Synthetic fallback", provider.getLyrics(track).plainLyrics)
        calls.clear()
        val missing = provider.getLyrics(track, setOf(LyricsSourceIds.LYRICS_OVH), true)
        assertFalse(missing.hasLyrics)
        assertFalse(LyricsSourceIds.LYRICS_OVH in calls)
    }
}
