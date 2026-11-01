package com.lirix.app.desktop

import com.lirix.app.desktop.lyrics.DesktopLyricsService
import com.lirix.app.feature.lyrics.AdditionalLyricsResult
import com.lirix.app.storage.LyricsAttribution
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DesktopAdditionalLyricsIntegrationTest {
    @Test fun plainLrclibIsEnrichedWithRealKugouTimingAndAttributionSurvivesDiskCache() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("lirix-new-source-cache-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val calls = mutableListOf<String>()
            val service = DesktopLyricsService(scope, cacheDirectory = directory,
                additionalLookup = { id, _, _, duration ->
                    calls += id
                    when (id) {
                        "builtin:kugou" -> {
                            assertEquals(200_000L, duration)
                            AdditionalLyricsResult(id, "KuGou", "Different words", "[00:01.00] First\n[00:03.00] Second", sourceUrl = "https://lyrics.kugou.com/search?keyword=synthetic")
                        }
                        "builtin:akkords-pro" -> AdditionalLyricsResult(id, "Akkords.pro", chordsText = "Am\nFirst", sourceUrl = "https://akkords.pro/songs/artist/song/", author = "Arranger")
                        else -> null
                    }
                }, httpFetch = { url, _ -> if ("lrclib.net/api/get" in url) """{"plainLyrics":"Original LRCLIB words"}""" else null })
            service.onTrackChanged("Song", "Artist", durationMs = 200_000)
            val ready = withTimeout(5_000) { service.lyricsState.first { !it.isLoading && it.hasLyrics } }
            assertEquals("Original LRCLIB words", ready.plainLyrics)
            assertEquals(listOf(1_000L, 3_000L), ready.lrcLines.map { it.timestampMs })
            assertEquals("builtin:kugou", ready.source)
            assertEquals("Arranger", LyricsAttribution.decode(ready.sourceAttributionJson).chords?.author)
            assertEquals(listOf("builtin:kugou", "builtin:akkords-pro"), calls)
            val reopened = DesktopLyricsService(scope, cacheDirectory = directory, automaticLookup = { _, _, _, _, _ -> error("Disk cache must win") })
            reopened.onTrackChanged("Song", "Artist")
            val cached = withTimeout(5_000) { reopened.lyricsState.first { !it.isLoading && it.hasLyrics } }
            assertEquals(ready.sourceAttributionJson, cached.sourceAttributionJson)
            assertEquals(ready.plainLyrics, cached.plainLyrics)
        } finally { scope.cancel(); directory.deleteRecursively() }
    }

    @Test fun kugouMissPreservesPlainLrclibWithoutFakeTiming() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("lirix-new-source-miss-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val service = DesktopLyricsService(scope, cacheDirectory = directory,
                additionalLookup = { _, _, _, _ -> null },
                httpFetch = { url, _ -> if ("lrclib.net/api/get" in url) """{"plainLyrics":"Original LRCLIB words"}""" else null })
            service.onTrackChanged("Song", "Artist")
            val ready = withTimeout(5_000) { service.lyricsState.first { !it.isLoading && it.hasLyrics } }
            assertEquals("Original LRCLIB words", ready.plainLyrics)
            assertNull(ready.syncedLyricsLrc)
            assertTrue(ready.lrcLines.isEmpty())
        } finally { scope.cancel(); directory.deleteRecursively() }
    }
}
