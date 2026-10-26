package com.lirix.app

import com.lirix.app.feature.*
import com.lirix.app.feature.lyrics.LookupTarget
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AutomaticLookupProviderTest {
    @Test fun fullCorpusLyricsIgnorePrivateNotesAndNeverInvokeLegacyDiscoveryWriter() = runBlocking {
        for (name in listOf("sakura-1888", "molihua-1763")) {
            val body = javaClass.classLoader!!.getResource("fixtures/lookup/$name.json")!!.readText()
            var writes = 0; var requests = 0
            val primary = LrcLibLyricsProvider { requests++; body }
            val provider = AggregatedLyricsProvider(lrcLibProvider = primary, fallbackScraper = null,
                onLyricsDiscovered = { _, _, _ -> writes++ })
            val track = TrackEntity("$name-key", "unseen-$name", "unseen artist", sourcePackage = "test", userNotes = "PRIVATE note C Am")
            val candidate = provider.acquireAutomatic(track, LookupTarget.LYRICS, emptySet())
            assertEquals(JsonHelper.extractStringField(body, "plainLyrics"), candidate.plain!!.text)
            assertFalse(candidate.plain!!.text.contains("PRIVATE")); assertEquals(LyricsSourceIds.LRCLIB, candidate.plain!!.sourceId)
            assertEquals(1, requests); assertEquals(0, writes)
            assertTrue(OfflineLyricsAdapter().getLyrics(track).isUserNote) // Legacy fallback contract unchanged.
        }
    }
    @Test fun missingChordsWithCachedWordsAndNotesUsesActualChordSource() = runBlocking {
        var primaryCalls = 0
        val fallback = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity): LyricsResult {
                assertEquals("", track.userNotes)
                return LyricsResult(true, "words", "AmDm", plainLyrics = "words", sourceId = LyricsSourceIds.AMDM, chords = "Am F C G")
            }
        }
        val provider = AggregatedLyricsProvider(lrcLibProvider = LrcLibLyricsProvider { primaryCalls++; null }, fallbackScraper = fallback)
        val track = TrackEntity("key", "unseen title", "artist", sourcePackage = "test", userNotes = "private", plainLyrics = "already accepted")
        val result = provider.acquireAutomatic(track, LookupTarget.CHORDS, emptySet())
        assertNull(result.plain); assertEquals("Am F C G", result.chords!!.text); assertEquals(LyricsSourceIds.AMDM, result.chords!!.sourceId)
        assertEquals(0, primaryCalls)
        assertNull(provider.acquireAutomatic(track, LookupTarget.CHORDS, setOf(LyricsSourceIds.AMDM)).chords)
    }
    @Test fun nullAndUnexpectedNoteProviderRemainIncompleteNotSongContent() = runBlocking {
        val noteProvider = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity) = LyricsResult(true, "secret", "notes", isUserNote = true,
                plainLyrics = "secret", sourceId = LyricsSourceIds.USER_NOTE)
        }
        val provider = AggregatedLyricsProvider(lrcLibProvider = noteProvider, fallbackScraper = null)
        val result = provider.acquireAutomatic(TrackEntity("k", "other title", "other artist", sourcePackage = "test"), LookupTarget.LYRICS, emptySet())
        assertNull(result.plain); assertNull(result.synced); assertNull(result.chords)
    }

    @Test fun blankOriginCannotBeRelabelledAsKnownPrimary() = runBlocking {
        val unknown = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity) = LyricsResult(true, "unknown words", "unknown", plainLyrics = "unknown words")
        }
        val result = AggregatedLyricsProvider(lrcLibProvider = unknown, fallbackScraper = null)
            .acquireAutomatic(TrackEntity("k", "unseen", "artist", sourcePackage = "test"), LookupTarget.LYRICS, emptySet())
        assertNull(result.plain); assertNull(result.synced); assertNull(result.chords)
    }

    @Test fun swallowedSourceCancellationCannotAdvanceToNextProvider() = runBlocking {
        var fallbackCalls = 0
        val slow = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity): LyricsResult {
                try { kotlinx.coroutines.delay(10_000) } catch (_: kotlinx.coroutines.CancellationException) { /* simulate legacy source */ }
                return LyricsResult(false, "", "slow", sourceId = LyricsSourceIds.LRCLIB)
            }
        }
        val fallback = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity): LyricsResult {
                fallbackCalls++; return LyricsResult(true, "should never run", "fallback", sourceId = LyricsSourceIds.AMDM)
            }
        }
        val provider = AggregatedLyricsProvider(lrcLibProvider = slow, fallbackScraper = fallback)
        try {
            kotlinx.coroutines.withTimeout(50) { provider.acquireAutomatic(
                TrackEntity("k", "unseen", "artist", sourcePackage = "test"), LookupTarget.LYRICS, emptySet()) }
            fail("deadline must propagate")
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) { }
        assertEquals(0, fallbackCalls)
    }
}
