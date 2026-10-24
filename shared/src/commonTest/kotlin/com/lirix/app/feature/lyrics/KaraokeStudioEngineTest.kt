package com.lirix.app.feature.lyrics

import kotlin.test.*

class KaraokeStudioEngineTest {
    private fun session(lrc: String = "[00:01.000] First\n[00:02.000] Repeat\n[00:03.000] Repeat") =
        KaraokeStudioEngine.createSession("song-a", "", lrc, 10_000)
    private fun accepted(state: KaraokeStudioState, edit: KaraokeStudioEdit) =
        assertIs<KaraokeStudioResult.Accepted>(KaraokeStudioEngine.reduce(state, edit)).state

    @Test fun importsOccurrencesAndEqualTimestampsWithoutSorting() {
        val state = session("[00:03.000][00:01.000] Same\n[00:01.000] Same")
        assertEquals(listOf(3000L, 1000L, 1000L), state.lines.map { it.timeMs })
        assertEquals(3, state.lines.map { it.id }.toSet().size)
        assertNull(KaraokeStudioEngine.exportLrc(state))
        val repaired = accepted(state, KaraokeStudioEdit.SetTime(0, 1000))
        assertNotNull(KaraokeStudioEngine.exportLrc(repaired))
    }

    @Test fun malformedTimeNeverBecomesSilentlySortedValidLrc() {
        val state = session("[00:99.000] First\n[00:02.000] Second")
        assertNull(state.lines.first().timeMs)
        assertNull(KaraokeStudioEngine.exportLrc(state))
    }

    @Test fun rejectsCrossingNeighborAndPreservesState() {
        val state = session()
        assertIs<KaraokeStudioResult.Rejected>(KaraokeStudioEngine.reduce(state, KaraokeStudioEdit.SetTime(1, 500)))
        assertEquals(2000, state.lines[1].timeMs)
        assertTrue(state.undo.isEmpty())
    }

    @Test fun tailShiftIsAtomicAndLeavesUnmarkedLinesUntouched() {
        val original = session().copy(lines = session().lines.mapIndexed { i, line -> if (i == 1) line.copy(timeMs = null) else line })
        val shifted = accepted(original, KaraokeStudioEdit.ShiftTail(0, 500))
        assertEquals(listOf(1500L, null, 3500L), shifted.lines.map { it.timeMs })
        assertIs<KaraokeStudioResult.Rejected>(KaraokeStudioEngine.reduce(original, KaraokeStudioEdit.ShiftTail(0, -1500)))
        assertIs<KaraokeStudioResult.Rejected>(KaraokeStudioEngine.reduce(original, KaraokeStudioEdit.ShiftTail(0, Long.MAX_VALUE)))
        assertEquals(listOf(1000L, null, 3000L), original.lines.map { it.timeMs })
    }

    @Test fun oneCommittedDragCreatesOneUndoAndNewEditInvalidatesRedo() {
        val original = session()
        val moved = accepted(original, KaraokeStudioEdit.SetTime(1, 2200))
        assertEquals(1, moved.undo.size)
        val undone = accepted(moved, KaraokeStudioEdit.Undo)
        assertEquals(original.lines, undone.lines)
        assertEquals(moved.lines, accepted(undone, KaraokeStudioEdit.Redo).lines)
        val other = accepted(undone, KaraokeStudioEdit.Nudge(1, -50))
        assertTrue(other.redo.isEmpty())
    }

    @Test fun stampCompensatesOnceAndPreciseEditsDoNot() {
        val original = session()
        val tapped = accepted(original, KaraokeStudioEdit.Stamp(1, 2400, 200))
        assertEquals(2200, tapped.lines[1].timeMs)
        assertEquals(2250, accepted(tapped, KaraokeStudioEdit.Nudge(1, 50)).lines[1].timeMs)
        assertEquals(2400, accepted(tapped, KaraokeStudioEdit.SetTime(1, 2400)).lines[1].timeMs)
    }

    @Test fun restoreOriginalRemainsUndoableAfterSeveralEdits() {
        val changed = accepted(session(), KaraokeStudioEdit.Nudge(1, 50))
        val restored = accepted(changed, KaraokeStudioEdit.RestoreOriginal)
        assertEquals(session().lines, restored.lines)
        assertEquals(changed.lines, accepted(restored, KaraokeStudioEdit.Undo).lines)
    }

    @Test fun textChangesNeverAttachOldTimingsToDifferentWords() {
        val changed = accepted(session(), KaraokeStudioEdit.ReplaceText("Inserted\nFirst\nRepeat"))
        assertTrue(changed.lines.all { it.timeMs == null })
        assertEquals(session().lines, accepted(changed, KaraokeStudioEdit.Undo).lines)
    }

    @Test fun timeInputRejectsInvalidSecondsFractionsAndOverflow() {
        assertEquals(62_003, KaraokeStudioEngine.parseTimeInput("01:02.003"))
        assertEquals(62_300, KaraokeStudioEngine.parseTimeInput("1:02.3"))
        listOf("", "1.2", "01:60", "-1:03", "1:02.1234", "99999999999999999999:02").forEach {
            assertNull(KaraokeStudioEngine.parseTimeInput(it), it)
        }
    }

    @Test fun exportPreservesOrderAndExactMillisecondPrecision() {
        val state = accepted(session(), KaraokeStudioEdit.SetTime(1, 2059))
        assertEquals("[00:01.000] First\n[00:02.059] Repeat\n[00:03.000] Repeat", KaraokeStudioEngine.exportLrc(state))
    }

    @Test fun multipleImportConflictsCanBeRepairedOneAtATime() {
        var state = session("[00:03] A\n[00:02] B\n[00:05] C\n[00:04] D")
        state = accepted(state, KaraokeStudioEdit.SetTime(0, 1000))
        assertNull(KaraokeStudioEngine.exportLrc(state))
        state = accepted(state, KaraokeStudioEdit.SetTime(2, 3000))
        assertNotNull(KaraokeStudioEngine.exportLrc(state))
    }

    @Test fun historyIsBoundedAndNavigationDoesNotMutateContent() {
        var state = session()
        repeat(120) { state = accepted(state, KaraokeStudioEdit.SetTime(1, if (it % 2 == 0) 2100 else 2200)) }
        assertEquals(100, state.undo.size)
        assertEquals(state.undo, state.copy(cursorMs = 8000, mode = StudioMode.PRECISE).undo)
    }
}
