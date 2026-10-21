package com.lirix.app

import com.lirix.app.feature.lyrics.*
import com.lirix.app.ui.components.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KaraokeStudioDraftStoreTest {
    @get:Rule val folder = TemporaryFolder()
    private fun draft(key: String = "song-a"): KaraokeStudioDraft {
        val initial = KaraokeStudioEngine.createSession(key, "First\nRepeat\nRepeat", null, 10000)
        val edited = (KaraokeStudioEngine.reduce(initial, KaraokeStudioEdit.SetTime(1, 2200)) as KaraokeStudioResult.Accepted).state
        return KaraokeStudioDraft(edited.copy(selectedIndex = 1, cursorMs = 2800, mode = StudioMode.PRECISE), "First\nRepeat\nRepeat", null)
    }

    @Test fun reopenedStoreRestoresOccurrencesCursorModeAndOriginalWithoutHistory() = runTest {
        val directory = folder.newFolder()
        KaraokeStudioDraftStore(directory).save(draft())
        val restored = (KaraokeStudioDraftStore(directory).load("song-a") as StudioDraftLoad.Found).draft
        assertEquals(draft().state.lines, restored.state.lines)
        assertEquals(draft().state.originalLines, restored.state.originalLines)
        assertEquals(2800, restored.state.cursorMs)
        assertEquals(StudioMode.PRECISE, restored.state.mode)
        assertTrue(restored.state.undo.isEmpty())
    }

    @Test fun tracksRemainIsolatedAndKeysCannotEscapeDraftDirectory() = runTest {
        val directory = folder.newFolder()
        val store = KaraokeStudioDraftStore(directory)
        store.save(draft("../../song-a"))
        store.save(draft("song-b"))
        assertEquals(2, directory.listFiles()!!.size)
        assertTrue(directory.listFiles()!!.all { it.name.matches(Regex("[0-9a-f]{64}\\.json")) })
        assertTrue(store.load("missing") is StudioDraftLoad.Missing)
        assertEquals("song-b", (store.load("song-b") as StudioDraftLoad.Found).draft.state.trackKey)
    }

    @Test fun corruptUnknownVersionAndWrongTrackDoNotModifySongOrEraseFile() = runTest {
        val directory = folder.newFolder()
        val store = KaraokeStudioDraftStore(directory)
        store.save(draft())
        val file = directory.listFiles()!!.single()
        val valid = file.readText()
        for (payload in listOf("{bad", valid.replace("\"version\":1", "\"version\":999"), valid.replace("song-a", "song-other"))) {
            file.writeText(payload)
            assertTrue(store.load("song-a") is StudioDraftLoad.Invalid)
            assertEquals(payload, file.readText())
        }
    }

    @Test fun interruptedAtomicReplacementRecoversLastGoodDraft() = runTest {
        val directory = folder.newFolder()
        val store = KaraokeStudioDraftStore(directory)
        store.save(draft())
        val base = directory.listFiles()!!.single()
        val atomic = android.util.AtomicFile(base)
        val interrupted = atomic.startWrite()
        interrupted.write("incomplete".toByteArray())
        interrupted.close()
        assertTrue(store.load("song-a") is StudioDraftLoad.Found)
    }

    @Test fun failedSaveReportsFailureInsteadOfSuccess() = runTest {
        val path = folder.newFile()
        assertTrue(runCatching { KaraokeStudioDraftStore(path).save(draft()) }.isFailure)
    }

    @Test fun applyingKeepsOriginalAndAudioAssociationRecoverable() = runTest {
        val directory = folder.newFolder()
        val store = KaraokeStudioDraftStore(directory)
        val initial = draft().copy(audioUri = "content://documents/song-a")
        store.save(initial.copy(expectedPlain = "new saved text", expectedLrc = "[00:01.00] new saved text"))
        val reopened = (store.load("song-a") as StudioDraftLoad.Found).draft
        assertEquals(initial.originalPlain, reopened.originalPlain)
        assertEquals(initial.originalLrc, reopened.originalLrc)
        assertEquals(initial.audioUri, reopened.audioUri)
        assertEquals("new saved text", reopened.expectedPlain)
    }

    @Test fun explicitRestartArchivesInsteadOfDeletingLastDraft() = runTest {
        val directory = folder.newFolder()
        val store = KaraokeStudioDraftStore(directory)
        store.save(draft())
        val bytes = directory.listFiles()!!.single().readBytes()
        store.archive("song-a")
        assertTrue(store.load("song-a") is StudioDraftLoad.Missing)
        assertArrayEquals(bytes, directory.listFiles()!!.single().readBytes())
    }
}
