package com.lirix.app.desktop

import com.lirix.app.desktop.ui.*
import com.lirix.app.feature.lyrics.LrcSyncEngine
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DesktopDraftsTest {
    private fun track() = TrackEntity("key", "Title", "Artist", "", "test", plainLyrics = "First line\nSecond line")

    @Test fun undoToOriginalIsClean() {
        val draft = DesktopDraft("original")
        draft.value = "changed"
        assertTrue(draft.dirty)
        draft.value = "original"
        assertFalse(draft.dirty)
    }

    @Test fun failureRetainsContentAndRetryAcknowledgesOnlySuccess() = runBlocking {
        val draft = DesktopDraft("old")
        draft.value = "edited"
        assertFalse(draft.save { error("disk full") })
        assertEquals("edited", draft.value)
        assertEquals("disk full", draft.error)
        assertTrue(draft.dirty)
        assertFalse(draft.saving)
        assertTrue(draft.save { assertEquals("edited", it) })
        assertFalse(draft.dirty)
        assertNull(draft.error)
    }

    @Test fun duplicateSaveIsBlockedAndLaterEditsRemainDirty() = runBlocking {
        val draft = DesktopDraft("old")
        draft.value = "first"
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val saving = async { draft.save { entered.complete(Unit); finish.await() } }
        entered.await()
        assertTrue(draft.saving)
        assertFalse(draft.save { fail("duplicate writer") })
        draft.value = "later"
        finish.complete(Unit)
        assertTrue(saving.await())
        assertTrue(draft.dirty)
    }

    @Test fun cancellationDoesNotAcknowledgeOrLeaveBusy() = runBlocking {
        val draft = DesktopDraft("old")
        draft.value = "edited"
        val entered = CompletableDeferred<Unit>()
        val task = launch { draft.save { entered.complete(Unit); awaitCancellation() } }
        entered.await()
        task.cancelAndJoin()
        assertFalse(draft.saving)
        assertTrue(draft.dirty)
        assertNull(draft.error)
    }

    @Test fun lateChordLoadPreservesTextEditsAndDoesNotBecomeUserEdit() {
        val draft = DesktopLyricsDraft(track())
        draft.content.value = draft.content.value.copy(plain = "edited", lrc = "[00:01.00] edited")
        draft.loadChords("Am C")
        assertEquals("edited", draft.content.value.plain)
        assertEquals("[00:01.00] edited", draft.content.value.lrc)
        assertEquals("Am C", draft.content.value.chords)
        assertTrue(draft.content.dirty)
        draft.content.value = draft.content.value.copy(chords = "G")
        draft.loadChords("late duplicate")
        assertEquals("G", draft.content.value.chords)
        val clean = DesktopLyricsDraft(track())
        clean.loadChords("Am")
        assertFalse(clean.content.dirty)
    }

    @Test fun windowRecreationReusesDraftsAndExplicitDiscardRemovesThem() {
        val store = DesktopDraftStore()
        val original = store.lyricsFor(track())
        original.content.value = original.content.value.copy(plain = "edited")
        val sync = store.syncFor(track())
        sync.value = LrcSyncEngine.recordTimestamp(sync.value, 0, 1200)
        store.notes["key"] = "note"
        assertSame(original, store.lyricsFor(track().copy(title = "new metadata")))
        assertSame(sync, store.syncFor(track()))
        assertEquals(1200L, store.syncFor(track()).value.first().timestampMs)
        assertTrue(store.hasUnsavedChanges)
        store.lyrics.clear(); store.sync.clear(); store.notes.clear()
        assertFalse(store.hasUnsavedChanges)
    }
}
