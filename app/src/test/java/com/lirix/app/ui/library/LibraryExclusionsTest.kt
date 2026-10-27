package com.lirix.app.ui.library

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryExclusionsTest {
    private val prefs by lazy {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("library-exclusions-test", Context.MODE_PRIVATE)
    }
    @Before fun setUp() { prefs.edit().clear().commit() }
    @After fun cleanUp() { prefs.edit().clear().commit() }

    @Test fun removalAndRestoreSurviveNewManager() = runBlocking {
        val first = LibraryExclusions(prefs)
        assertNotNull(first.remove("track-a"))
        assertNotNull(first.remove("track-b"))
        val reopened = LibraryExclusions(prefs)
        assertEquals(setOf("track-a", "track-b"), reopened.removed.value)
        assertTrue(reopened.restore("track-a"))
        assertEquals(setOf("track-b"), LibraryExclusions(prefs).removed.value)
    }

    @Test fun concurrentUpdatesDoNotLoseOtherTracks() = runBlocking {
        val store = LibraryExclusions(prefs)
        val keys = (1..30).map { "song-$it" }
        keys.map { key -> async { assertNotNull(store.remove(key)) } }.awaitAll()
        assertEquals(keys.toSet(), LibraryExclusions(prefs).removed.value)
        keys.take(15).map { key -> async { assertTrue(store.restore(key)) } }.awaitAll()
        assertEquals(keys.drop(15).toSet(), store.removed.value)
        assertNull(store.remove(""))
        assertEquals(keys.drop(15).toSet(), LibraryExclusions(prefs).removed.value)
    }

    @Test fun oldUndoCannotReverseLaterRemoval() = runBlocking {
        val store = LibraryExclusions(prefs)
        val old = requireNotNull(store.remove("a"))
        assertNotNull(store.remove("b"))
        assertTrue(store.restore("a"))
        val newer = requireNotNull(store.remove("a"))
        assertTrue(store.restore("a", old))
        assertEquals(setOf("a", "b"), store.removed.value)
        assertTrue(store.restore("a", newer))
        assertEquals(setOf("b"), LibraryExclusions(prefs).removed.value)
    }

    @Test fun failedCommitDoesNotLeakIntoLaterSuccessfulOperation() = runBlocking {
        val failing = FaultingPreferences(prefs)
        val store = LibraryExclusions(failing)
        failing.falseCommits = 2 // failed write and failed compensation both mutate in-memory prefs
        assertNull(store.remove("failed"))
        assertTrue(store.removed.value.isEmpty())
        assertTrue(LibraryExclusions(prefs).removed.value.isEmpty())
        assertNotNull(store.remove("saved"))
        assertEquals(setOf("saved"), store.removed.value)
        assertEquals(setOf("saved"), LibraryExclusions(prefs).removed.value)
        failing.falseCommits = 1
        assertFalse(store.restore("saved"))
        assertEquals(setOf("saved"), store.removed.value)
        assertNotNull(store.remove("another"))
        assertEquals(setOf("saved", "another"), LibraryExclusions(prefs).removed.value)
    }

    @Test fun exceptionsReportFailureAndDoNotLeakIntoNextWrite() = runBlocking {
        val failing = FaultingPreferences(prefs)
        val store = LibraryExclusions(failing)
        failing.throwCommit = true
        assertNull(store.remove("failed"))
        assertTrue(store.removed.value.isEmpty())
        assertNotNull(store.remove("ok"))
        assertEquals(setOf("ok"), LibraryExclusions(prefs).removed.value)
        failing.throwRead = true
        val broken = LibraryExclusions(failing)
        assertTrue(broken.initializationFailed)
        assertNull(broken.remove("x"))
        assertFalse(broken.restore("ok"))
    }

    @Test fun cancellationIsNotReportedAsSuccess() = runBlocking {
        val failing = FaultingPreferences(prefs)
        val store = LibraryExclusions(failing)
        failing.cancelCommit = true
        failing.falseCommits = 1 // compensation also reports failure after mutating the visible map
        try { store.remove("cancelled"); fail("cancellation must propagate") }
        catch (_: CancellationException) { }
        assertTrue(store.removed.value.isEmpty())
        assertTrue(LibraryExclusions(prefs).removed.value.isEmpty())
        assertNotNull(store.remove("ok"))
        assertEquals(setOf("ok"), LibraryExclusions(prefs).removed.value)
    }

    private class FaultingPreferences(private val real: SharedPreferences) : SharedPreferences by real {
        var falseCommits = 0
        var throwCommit = false
        var cancelCommit = false
        var throwRead = false
        override fun getStringSet(key: String?, default: Set<String>?): Set<String>? {
            if (throwRead) throw IllegalStateException("read failed")
            return real.getStringSet(key, default)
        }
        override fun edit(): SharedPreferences.Editor {
            val editor = real.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                    editor.putStringSet(key, values)
                    return this
                }
                override fun commit(): Boolean {
                    // Android updates the process-visible prefs map even when disk commit fails.
                    val result = editor.commit()
                    if (cancelCommit) { cancelCommit = false; throw CancellationException("cancelled") }
                    if (throwCommit) { throwCommit = false; throw IllegalStateException("write failed") }
                    if (falseCommits > 0) { falseCommits--; return false }
                    return result
                }
            }
        }
    }
}
