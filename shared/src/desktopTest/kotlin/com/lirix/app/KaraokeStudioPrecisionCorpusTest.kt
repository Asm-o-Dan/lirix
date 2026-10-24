package com.lirix.app

import com.lirix.app.feature.lyrics.*
import org.junit.Assert.*
import org.junit.Test

class KaraokeStudioPrecisionCorpusTest {
    @Test fun completeSongCorporaKeepExactTimesAndOccurrencesThroughExportAndPlaybackParser() {
        for (name in listOf("sakura-1888.txt", "molihua-1763.txt")) {
            val text = requireNotNull(javaClass.getResourceAsStream("/fixtures/romanization/$name"))
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val initial = KaraokeStudioEngine.createSession(name, text, null, 14400000)
            // Beyond 99 minutes, all stamps have non-centisecond precision; repeated lyrics stay distinct.
            val stamps = initial.lines.indices.map { 6000001L + it * 1001L }
            val state = initial.copy(lines = initial.lines.mapIndexed { index, line -> line.copy(timeMs = stamps[index]) })
            val lrc = requireNotNull(KaraokeStudioEngine.exportLrc(state))
            assertTrue(lrc.startsWith("[100:00.001]"))
            val restored = KaraokeStudioEngine.createSession(name, text, lrc, 14400000)
            assertEquals(stamps, restored.lines.map { it.timeMs })
            assertEquals(initial.lines.map { it.text }, restored.lines.map { it.text })
            val playback = LrcSyncEngine.parseLrcLinesStrict(lrc)
            assertEquals(stamps, playback.map { it.timestampMs })
            assertEquals(initial.lines.map { it.text }, playback.map { it.text })
        }
    }

    @Test fun unrepresentableTimesAreRejectedRatherThanWrappingOrBecomingZero() {
        val parsed = LrcSyncEngine.parseLrcLinesStrict("[999999999999999999999999:00.001] Too big\n[153722867280913:00.000] Overflow\n[00:01.001] Valid")
        assertEquals(1, parsed.size)
        assertEquals(1001L, parsed.single().timestampMs)
        assertEquals("Valid", parsed.single().text)
    }
}
