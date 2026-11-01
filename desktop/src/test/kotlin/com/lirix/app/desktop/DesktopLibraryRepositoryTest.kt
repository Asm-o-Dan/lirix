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
    private suspend fun DesktopLibraryRepository.recordAt(playback: LivePlaybackSnapshot?, mono: Long) =
        record(playback, mono, 1_700_000_000_000L + mono)
    @Test fun progressIsBatchedAndFlushedOnPauseTrackChangeAndExit() = runBlocking {
        val directory = Files.createTempDirectory("lirix-checkpoint-test").toFile()
        val db = Room.databaseBuilder<AppDatabase>(name = directory.resolve("db").absolutePath)
            .setDriver(BundledSQLiteDriver()).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = DesktopLibraryRepository(db, scope)
            val a = LivePlaybackSnapshot("player", "Song A", "Artist", isPlaying = true,
                basePositionMs = 0, lastPositionUpdateTimeMs = getSystemElapsedRealtimeMs(), durationMs = 180_000)
            val keyA = repo.keyFor(a)
            repo.recordAt(a, 0)
            (1L..14L).forEach { repo.recordAt(a, it * 1_000) }
            assertEquals(0L, db.musicDao().getTrackByKey(keyA)!!.totalDurationMs)
            assertEquals(0L, db.musicDao().getAllSessions().single().durationMs)
            repo.recordAt(a, 15_000)
            assertEquals(15_000L, db.musicDao().getTrackByKey(keyA)!!.totalDurationMs)
            repo.recordAt(a.copy(isPlaying = false), 16_000)
            assertEquals(16_000L, db.musicDao().getTrackByKey(keyA)!!.totalDurationMs)
            repo.recordAt(a.copy(isPlaying = false), 20_000)
            assertEquals(16_000L, db.musicDao().getTrackByKey(keyA)!!.totalDurationMs)
            repo.recordAt(a, 21_000)
            repo.recordAt(a, 22_000)
            // Track edits between checkpoints must survive the delayed persistence.
            repo.editNotes(keyA, "keep notes").join()
            val b = a.copy(title = "Song B")
            repo.recordAt(b, 23_000)
            assertEquals(18_000L, db.musicDao().getTrackByKey(keyA)!!.totalDurationMs)
            assertEquals("keep notes", db.musicDao().getTrackByKey(keyA)!!.userNotes)
            repo.recordAt(b, 24_000)
            repo.flush()
            repo.flush() // Repeated shutdown paths must not credit a checkpoint twice.
            val keyB = repo.keyFor(b)
            assertEquals(1_000L, db.musicDao().getTrackByKey(keyB)!!.totalDurationMs)
            val sessions = db.musicDao().getAllSessions()
            assertEquals(18_000L, sessions.first { it.trackKey == keyA }.durationMs)
            assertEquals(1_000L, sessions.first { it.trackKey == keyB }.durationMs)
            val stats = com.lirix.app.analytics.WrappedStatsEngine.calculateStats(db.musicDao().getAllTracks(), sessions,
                referenceTimestampMs = 1_700_000_024_000L)
            assertEquals(19_000L, stats.totalListeningTimeMs)
            assertEquals(19_000L, stats.weeklyActivity.sumOf { it.totalDurationMs })
        } finally {
            scope.cancel(); db.close()
            directory.listFiles()?.forEach { it.delete() }; directory.delete()
        }
        Unit
    }

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
            repo.recordAt(playback, 0)
            repo.recordAt(playback, 1_000)
            repo.recordAt(playback.copy(isPlaying = false), 2_000)
            repo.recordAt(playback.copy(isPlaying = false), 20_000)
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
            repository.recordAt(LivePlaybackSnapshot("player", "Song", "Blocked", isPlaying = true,
                basePositionMs = 0, lastPositionUpdateTimeMs = getSystemElapsedRealtimeMs()), 0)
            assertTrue(db.musicDao().getAllTracks().isEmpty())
            assertTrue(db.musicDao().getAllSessions().isEmpty())
        } finally { scope.cancel(); db.close() }
        directory.listFiles()?.forEach { it.delete() }; directory.delete()
        Unit
    }
}
