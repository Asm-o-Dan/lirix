package com.lirix.app

import com.lirix.app.feature.lyrics.*
import com.lirix.app.ui.components.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KaraokeStudioDurationTest {
    @get:Rule val folder = TemporaryFolder()

    private fun file(evidence: StudioDurationEvidence, durationMs: Long): StudioDurationEvidence {
        val pending = evidence.invalidateFile()
        return pending.decoded(pending.fileGeneration, durationMs)
    }

    private fun session(timeMs: Long, durationMs: Long): KaraokeStudioState {
        val original = KaraokeStudioEngine.createSession("song", "Line", "[00:01.00] Line", durationMs)
        return (KaraokeStudioEngine.reduce(original, KaraokeStudioEdit.SetTime(0, timeMs)) as KaraokeStudioResult.Accepted).state
    }

    @Test fun longerReplacementAllowsMarkBeyondPreviousFile() {
        val short = file(StudioDurationEvidence(), 12_000)
        val state = session(1_000, short.effectiveMs)
        assertTrue(KaraokeStudioEngine.reduce(state, KaraokeStudioEdit.SetTime(0, 13_000)) is KaraokeStudioResult.Rejected)
        val longer = file(short, 20_000)
        val edited = KaraokeStudioEngine.reduce(state.copy(durationMs = longer.effectiveMs), KaraokeStudioEdit.SetTime(0, 13_000))
        assertTrue(edited is KaraokeStudioResult.Accepted)
        assertEquals(13_000L, (edited as KaraokeStudioResult.Accepted).state.lines.single().timeMs)
    }

    @Test fun shorterReplacementPreservesMarksOriginalAndHistoryButBlocksExport() {
        val evidence = file(StudioDurationEvidence(), 20_000)
        val state = session(18_000, evidence.effectiveMs)
        val shorter = state.copy(durationMs = file(evidence, 12_000).effectiveMs)
        assertEquals(state.lines, shorter.lines)
        assertEquals(state.originalLines, shorter.originalLines)
        assertEquals(state.undo, shorter.undo)
        assertEquals(state.redo, shorter.redo)
        assertEquals(18_000L, shorter.lines.single().timeMs)
        assertNotNull(KaraokeStudioEngine.validationError(shorter))
        assertNull(KaraokeStudioEngine.exportLrc(shorter))
    }

    @Test fun removeAndReopenNeverRetainFileAsSongEvidence() = runTest {
        val evidence = file(StudioDurationEvidence(), 12_000).invalidateFile()
        val state = session(18_000, 20_000).copy(durationMs = evidence.effectiveMs)
        val store = KaraokeStudioDraftStore(folder.newFolder())
        store.save(KaraokeStudioDraft(state, "Line", null, audioUri = null, originalDurationMs = evidence.originalDurationMs))
        val reopened = (store.load("song") as StudioDraftLoad.Found).draft
        assertNull(reopened.audioUri)
        assertEquals(0L, reopened.state.durationMs)
        assertEquals(state.lines, reopened.state.lines)
        assertNull(KaraokeStudioEngine.validationError(reopened.state))
    }

    @Test fun originalKnownDurationReturnsOnlyAfterFileRemoved() {
        val original = StudioDurationEvidence(originalDurationMs = 30_000)
        val local = file(original, 12_000)
        assertEquals(12_000L, local.effectiveMs)
        assertEquals(30_000L, local.invalidateFile().effectiveMs)
    }

    @Test fun matchingSourceWinsAndDifferentTrackCannotSupplyDuration() {
        val local = file(StudioDurationEvidence(originalDurationMs = 40_000), 12_000)
        val matching = local.source(true, 30_000)
        assertEquals(30_000L, matching.effectiveMs)
        assertTrue(kotlin.math.abs(matching.decodedFileDurationMs - matching.sourceDurationMs) > 3_000)
        assertEquals(30_000L, file(matching, 20_000).effectiveMs)
        assertEquals(30_000L, matching.invalidateFile().effectiveMs)
        assertEquals(12_000L, matching.source(false, 99_000).effectiveMs)
        assertEquals(40_000L, matching.invalidateFile().source(false, 99_000).effectiveMs)
    }

    @Test fun replacementRetryFailureCancellationAndLateResultCannotResurrectOldEvidence() {
        val old = file(StudioDurationEvidence(), 12_000)
        val replacement = old.invalidateFile()
        assertEquals(0L, replacement.effectiveMs) // New decode pending or failed: no evidence yet.
        assertEquals(replacement, replacement.decoded(old.fileGeneration, 12_000))
        val retry = replacement.invalidateFile()
        assertEquals(0L, retry.effectiveMs)
        assertEquals(retry, retry.decoded(replacement.fileGeneration, 20_000)) // Cancelled attempt completes late.
        val removed = retry.invalidateFile()
        assertEquals(removed, removed.decoded(retry.fileGeneration, 20_000))
        assertEquals(20_000L, retry.decoded(retry.fileGeneration, 20_000).effectiveMs)
    }

    @Test fun newAndLegacyDraftsPreserveContentButRequireFreshFileEvidence() = runTest {
        val directory = folder.newFolder()
        val store = KaraokeStudioDraftStore(directory)
        val state = session(18_000, 30_000).copy(durationMs = 12_000)
        val saved = KaraokeStudioDraft(state, "Line", "[00:01.00] Line", audioUri = "content://audio/file", originalDurationMs = 30_000)
        store.save(saved)
        val current = (store.load("song") as StudioDraftLoad.Found).draft
        assertEquals(30_000L, current.originalDurationMs)
        assertEquals(30_000L, current.state.durationMs)
        assertEquals(saved.audioUri, current.audioUri)
        assertEquals(state.lines, current.state.lines)
        assertEquals(state.originalLines, current.state.originalLines)
        assertEquals(12_000L, file(StudioDurationEvidence(current.originalDurationMs), 12_000).effectiveMs)
        val draftFile = directory.listFiles()!!.single()
        draftFile.writeText(JSONObject(draftFile.readText()).apply { remove("originalDurationMs") }.toString())
        val legacy = (store.load("song") as StudioDraftLoad.Found).draft
        assertEquals(0L, legacy.originalDurationMs)
        assertEquals(0L, legacy.state.durationMs)
        assertEquals(saved.audioUri, legacy.audioUri)
        assertEquals(state.lines, legacy.state.lines)
        assertEquals(state.originalLines, legacy.state.originalLines)
        assertEquals(saved.originalLrc, legacy.originalLrc)
        assertNull(KaraokeStudioEngine.validationError(legacy.state))
    }
}
