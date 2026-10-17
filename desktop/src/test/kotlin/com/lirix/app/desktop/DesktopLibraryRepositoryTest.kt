package com.lirix.app.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.lirix.app.desktop.library.DesktopLibraryRepository
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.ingestion.getSystemElapsedRealtimeMs
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.blacklist.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DesktopLibraryRepositoryTest {
    @Test fun listeningNotesFavoriteAndLyricsPersistAcrossDatabaseReopen() = runBlocking {
        val directory = Files.createTempDirectory("lirix-library-test").toFile()
        val file = directory.resolve("library.db")
        fun open() = Room.databaseBuilder<AppDatabase>(name = file.absolutePath).setDriver(BundledSQLiteDriver()).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val db = open()
        try {
            val repo = DesktopLibraryRepository(db, scope)
            val playback = LivePlaybackSnapshot("player", "Song", "Artist", isPlaying = true,
                basePositionMs = 0, lastPositionUpdateTimeMs = getSystemElapsedRealtimeMs(), durationMs = 180_000)
            repo.record(playback, 0)
            repo.record(playback, 1_000)
            repo.record(playback.copy(isPlaying = false), 2_000)
            repo.record(playback.copy(isPlaying = false), 20_000)
            val key = repo.keyFor(playback)
            repo.favorite(key, true).join()
            repo.editNotes(key, "My notes").join()
            repo.saveLyrics(key, "My lyrics", "[00:01.00] My lyrics", "Am C")
            val track = db.musicDao().getTrackByKey(key)!!
            assertEquals(1, track.playCount)
            assertEquals(2_000, track.totalDurationMs)
            assertTrue(track.isFavorite)
            assertEquals("My notes", track.userNotes)
        } finally { scope.cancel(); db.close() }
        val reopened = open()
        try {
            val track = reopened.musicDao().getAllTracks().single()
            assertTrue(track.isFavorite)
            assertEquals("My notes", track.userNotes)
            assertEquals("[00:01.00] My lyrics", reopened.lyricsDao().getLyrics(track.trackKey)?.syncedLyricsLrc)
        } finally { reopened.close() }
        directory.listFiles()?.forEach { it.delete() }; directory.delete()
        Unit
    }

    @Test fun persistedBlacklistIsAppliedBeforeFirstSample() = runBlocking {
        val directory = Files.createTempDirectory("lirix-blacklist-test").toFile()
        val db = Room.databaseBuilder<AppDatabase>(name = directory.resolve("db").absolutePath).setDriver(BundledSQLiteDriver()).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            db.blacklistDao().insertRule(BlacklistRuleEntity("rule", BlacklistRuleType.ARTIST, "Blocked"))
            val repository = DesktopLibraryRepository(db, scope)
            repository.record(LivePlaybackSnapshot("player", "Song", "Blocked", isPlaying = true,
                basePositionMs = 0, lastPositionUpdateTimeMs = getSystemElapsedRealtimeMs()), 0)
            assertTrue(db.musicDao().getAllTracks().isEmpty())
            assertTrue(db.musicDao().getAllSessions().isEmpty())
        } finally { scope.cancel(); db.close() }
        directory.listFiles()?.forEach { it.delete() }; directory.delete()
        Unit
    }
}
