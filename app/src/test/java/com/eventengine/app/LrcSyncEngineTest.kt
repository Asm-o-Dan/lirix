package com.eventengine.app

import com.eventengine.app.feature.lyrics.KaraokeLine
import com.eventengine.app.feature.lyrics.LrcSyncEngine
import com.eventengine.app.feature.lyrics.SyncLineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for LrcSyncEngine (LRC Tap-to-Sync Studio domain logic).
 * Spec: TASK-LRC-01 / .sdd/specs/media-ui/lrc_tap_sync_studio.md
 */
class LrcSyncEngineTest {

    @Test
    fun `parsePlainToSyncLines parses trimmed non-empty lines with sequential indices`() {
        val raw = """
            
            First line of the song   
            
            Second line is here
               Third line with spaces   
            
        """.trimIndent()

        val parsed = LrcSyncEngine.parsePlainToSyncLines(raw)

        assertEquals(3, parsed.size)
        assertEquals(SyncLineItem(0, "First line of the song", null), parsed[0])
        assertEquals(SyncLineItem(1, "Second line is here", null), parsed[1])
        assertEquals(SyncLineItem(2, "Third line with spaces", null), parsed[2])
    }

    @Test
    fun `parsePlainToSyncLines handles empty or blank input`() {
        assertTrue(LrcSyncEngine.parsePlainToSyncLines("").isEmpty())
        assertTrue(LrcSyncEngine.parsePlainToSyncLines("   \n\n   \t  \n").isEmpty())
    }

    @Test
    fun `recordTimestamp updates target line and clamps negative values`() {
        val lines = listOf(
            SyncLineItem(0, "Line 0", null),
            SyncLineItem(1, "Line 1", null)
        )

        val updated = LrcSyncEngine.recordTimestamp(lines, 0, 45230L)
        assertEquals(45230L, updated[0].timestampMs)
        assertEquals(null, updated[1].timestampMs)

        // Negative timestamp clamp
        val clamped = LrcSyncEngine.recordTimestamp(lines, 1, -500L)
        assertEquals(0L, clamped[1].timestampMs)

        // Out of bounds preserves list
        val oob = LrcSyncEngine.recordTimestamp(lines, 99, 1000L)
        assertEquals(lines, oob)
    }

    @Test
    fun `undoTimestamp steps back and clears previous timestamp`() {
        val lines = listOf(
            SyncLineItem(0, "Line 0", 1000L),
            SyncLineItem(1, "Line 1", 3500L),
            SyncLineItem(2, "Line 2", null)
        )

        // Currently on line 2 (Line 0 and 1 are marked)
        val (undone, targetIdx) = LrcSyncEngine.undoTimestamp(lines, currentIndex = 2)
        assertEquals(1, targetIdx)
        assertEquals(1000L, undone[0].timestampMs)
        assertEquals(null, undone[1].timestampMs)
        assertEquals(null, undone[2].timestampMs)
    }

    @Test
    fun `undoTimestamp handles boundary conditions gracefully`() {
        // Empty lines
        val (emptyRes, emptyIdx) = LrcSyncEngine.undoTimestamp(emptyList(), 0)
        assertTrue(emptyRes.isEmpty())
        assertEquals(0, emptyIdx)

        // At index 0 with marked line 0
        val singleMarked = listOf(SyncLineItem(0, "Line 0", 2500L))
        val (undone0, idx0) = LrcSyncEngine.undoTimestamp(singleMarked, 0)
        assertEquals(0, idx0)
        assertEquals(null, undone0[0].timestampMs)

        // When all lines are marked and currentIndex equals size
        val allMarked = listOf(
            SyncLineItem(0, "Line 0", 1000L),
            SyncLineItem(1, "Line 1", 2000L)
        )
        val (undoneEnd, idxEnd) = LrcSyncEngine.undoTimestamp(allMarked, 2)
        assertEquals(1, idxEnd)
        assertEquals(1000L, undoneEnd[0].timestampMs)
        assertEquals(null, undoneEnd[1].timestampMs)
    }

    @Test
    fun `formatTimestamp produces standard two-digit minutes seconds hundredths`() {
        assertEquals("[00:00.00]", LrcSyncEngine.formatTimestamp(0L))
        assertEquals("[00:05.43]", LrcSyncEngine.formatTimestamp(5430L))
        assertEquals("[02:05.43]", LrcSyncEngine.formatTimestamp(125430L))
        assertEquals("[12:34.56]", LrcSyncEngine.formatTimestamp(754560L))
        // Negative ms clamped to 0
        assertEquals("[00:00.00]", LrcSyncEngine.formatTimestamp(-1000L))
    }

    @Test
    fun `formatTimeLabel formats human readable mm ss`() {
        assertEquals("00:00", LrcSyncEngine.formatTimeLabel(0L))
        assertEquals("01:23", LrcSyncEngine.formatTimeLabel(83000L))
        assertEquals("03:45", LrcSyncEngine.formatTimeLabel(225000L))
    }

    @Test
    fun `buildLrcString generates standard LRC format and roundtrips with parseLrcLinesStrict`() {
        val lines = listOf(
            SyncLineItem(0, "Is this the real life?", 15200L),
            SyncLineItem(1, "Is this just fantasy?", 21450L),
            SyncLineItem(2, "Caught in a landslide", 28900L),
            SyncLineItem(3, "Unmarked line", null)
        )

        val lrc = LrcSyncEngine.buildLrcString(lines)
        val expected = """
            [00:15.20] Is this the real life?
            [00:21.45] Is this just fantasy?
            [00:28.90] Caught in a landslide
        """.trimIndent()

        assertEquals(expected, lrc)

        // Strict parser roundtrip check
        val parsedBack = LrcSyncEngine.parseLrcLinesStrict(lrc)
        assertEquals(3, parsedBack.size)
        assertEquals("Is this the real life?", parsedBack[0].text)
        assertEquals(15200L, parsedBack[0].timestampMs)
        assertEquals("Is this just fantasy?", parsedBack[1].text)
        assertEquals(21450L, parsedBack[1].timestampMs)
        assertEquals("Caught in a landslide", parsedBack[2].text)
        assertEquals(28900L, parsedBack[2].timestampMs)
    }

    @Test
    fun `isMonotonic validates chronological non-decreasing timestamps`() {
        val monotonic = listOf(
            SyncLineItem(0, "A", 1000L),
            SyncLineItem(1, "B", 2500L),
            SyncLineItem(2, "C", 2500L), // equal is acceptable
            SyncLineItem(3, "D", 5000L)
        )
        assertTrue(LrcSyncEngine.isMonotonic(monotonic))
        assertTrue(LrcSyncEngine.findNonMonotonicIndices(monotonic).isEmpty())

        val nonMonotonic = listOf(
            SyncLineItem(0, "A", 1000L),
            SyncLineItem(1, "B", 5000L),
            SyncLineItem(2, "C", 3000L), // regressed!
            SyncLineItem(3, "D", 7000L)
        )
        assertFalse(LrcSyncEngine.isMonotonic(nonMonotonic))
        assertEquals(listOf(2), LrcSyncEngine.findNonMonotonicIndices(nonMonotonic))
    }

    @Test
    fun `validateAndSanitize repairs non-monotonic timestamp regressions`() {
        val corrupted = listOf(
            SyncLineItem(0, "A", 1000L),
            SyncLineItem(1, "B", 5000L),
            SyncLineItem(2, "C", 3000L), // regression
            SyncLineItem(3, "D", 2000L)  // regression
        )

        val sanitized = LrcSyncEngine.validateAndSanitize(corrupted, minGapMs = 200L)
        assertTrue(LrcSyncEngine.isMonotonic(sanitized))
        assertEquals(1000L, sanitized[0].timestampMs)
        assertEquals(5000L, sanitized[1].timestampMs)
        assertEquals(5200L, sanitized[2].timestampMs) // fixed to 5000 + 200
        assertEquals(5400L, sanitized[3].timestampMs) // fixed to 5200 + 200
    }

    @Test
    fun `progress and markedCount accurately report synchronization status`() {
        val lines = listOf(
            SyncLineItem(0, "A", 1000L),
            SyncLineItem(1, "B", 2000L),
            SyncLineItem(2, "C", null),
            SyncLineItem(3, "D", null)
        )

        assertEquals(2, LrcSyncEngine.markedCount(lines))
        assertEquals(0.5f, LrcSyncEngine.progress(lines), 0.001f)

        assertEquals(0, LrcSyncEngine.markedCount(emptyList()))
        assertEquals(0f, LrcSyncEngine.progress(emptyList()), 0.001f)
    }
}
