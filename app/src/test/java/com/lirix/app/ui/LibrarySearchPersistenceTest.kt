package com.lirix.app.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibrarySearchPersistenceTest {
    @Test fun savedEditsEmitFreshSearchableLyricsAndChords() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val updates = Channel<List<LyricsCacheEntity>>(Channel.UNLIMITED)
        val observer = launch { database.lyricsDao().observeAllLyrics().collect { updates.send(it) } }
        try {
            assertTrue(withTimeout(5000) { updates.receive() }.isEmpty())
            val track = TrackEntity("a", "Song", "Artist", sourcePackage = "test", plainLyrics = "Stale")
            database.musicDao().insertOrUpdateTrack(track)
            val saved = LyricsCacheEntity("a", "New shore", null, "Am G F", null, "USER")
            database.lyricsDao().saveLyrics(saved)
            val emitted = withTimeout(5000) { updates.receive() }.single()
            assertNotNull(librarySearchMatch(track, emitted, "new shore", setOf(LibrarySearchField.TEXT), "Unknown"))
            assertNotNull(librarySearchMatch(track, emitted, "Am G", setOf(LibrarySearchField.CHORDS), "Unknown"))
            database.lyricsDao().saveLyrics(saved.copy(plainLyrics = "Revised words", chordsAmDm = "Dm C"))
            val revised = withTimeout(5000) { updates.receive() }.single()
            assertNull(librarySearchMatch(track, revised, "new shore", setOf(LibrarySearchField.TEXT), "Unknown"))
            assertNull(librarySearchMatch(track, revised, "Am G", setOf(LibrarySearchField.CHORDS), "Unknown"))
            assertNotNull(librarySearchMatch(track, revised, "Dm C", setOf(LibrarySearchField.CHORDS), "Unknown"))
        } finally {
            observer.cancelAndJoin()
            updates.close()
            database.close()
        }
    }
}
