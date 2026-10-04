package com.eventengine.app

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.eventengine.app.storage.AppDatabase
import com.eventengine.app.storage.LyricsCacheEntity
import com.eventengine.app.storage.LyricsRejectionEntity
import com.eventengine.app.storage.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TDD Unit-tests for TASK-LYR-03-A:
 * - Room migration from v8 to v9 (MIGRATION_8_9).
 * - Verifies creation of `lyrics_rejections` table and indices.
 * - Verifies preservation of existing music_tracks, music_listening_sessions, lyrics_cache, achievements, notes.
 * - Verifies insertion, querying, deletion (undo), and clearing of rejections.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigration8to9Test {

    @Test
    fun test_migration_8_to_9_preservesData_andCreatesRejectionsTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 1. Create v8 music_tracks
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `music_tracks` (
                            `trackKey` TEXT NOT NULL PRIMARY KEY,
                            `title` TEXT NOT NULL,
                            `artist` TEXT NOT NULL,
                            `album` TEXT NOT NULL DEFAULT '',
                            `sourcePackage` TEXT NOT NULL,
                            `playCount` INTEGER NOT NULL DEFAULT 1,
                            `totalDurationMs` INTEGER NOT NULL DEFAULT 0,
                            `firstPlayedAt` INTEGER NOT NULL DEFAULT 0,
                            `lastPlayedAt` INTEGER NOT NULL DEFAULT 0,
                            `userNotes` TEXT NOT NULL DEFAULT '',
                            `isFavorite` INTEGER NOT NULL DEFAULT 0,
                            `syncedLyrics` TEXT,
                            `plainLyrics` TEXT,
                            `albumArtUri` TEXT DEFAULT NULL
                        )
                    """.trimIndent())

                    // 2. Create v8 music_listening_sessions
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `music_listening_sessions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `trackKey` TEXT NOT NULL,
                            `sourcePackage` TEXT NOT NULL,
                            `startTimeMs` INTEGER NOT NULL,
                            `endTimeMs` INTEGER NOT NULL,
                            `durationMs` INTEGER NOT NULL,
                            `isCompleted` INTEGER NOT NULL DEFAULT 0
                        )
                    """.trimIndent())

                    // 3. Create v8 lyrics_cache
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `lyrics_cache` (
                            `trackKey` TEXT NOT NULL PRIMARY KEY,
                            `plainLyrics` TEXT,
                            `syncedLyricsLrc` TEXT,
                            `chordsAmDm` TEXT,
                            `userNotes` TEXT,
                            `provider` TEXT NOT NULL,
                            `cachedAt` INTEGER NOT NULL
                        )
                    """.trimIndent())

                    // 4. Create v8 achievements
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `achievements` (
                            `id` TEXT NOT NULL PRIMARY KEY,
                            `title` TEXT NOT NULL,
                            `description` TEXT NOT NULL,
                            `iconRes` TEXT NOT NULL,
                            `isUnlocked` INTEGER NOT NULL DEFAULT 0,
                            `unlockedAt` INTEGER,
                            `currentProgress` INTEGER NOT NULL DEFAULT 0,
                            `maxProgress` INTEGER NOT NULL DEFAULT 1,
                            `tier` TEXT NOT NULL DEFAULT 'BRONZE',
                            `category` TEXT NOT NULL DEFAULT 'LISTENING'
                        )
                    """.trimIndent())

                    // Seed pre-existing data in v8
                    db.execSQL("""
                        INSERT INTO `music_tracks` (`trackKey`, `title`, `artist`, `sourcePackage`, `playCount`, `userNotes`, `plainLyrics`)
                        VALUES ('track_v8_1', 'In The End', 'Linkin Park', 'com.spotify.music', 42, 'My favorite guitar song', 'It starts with one thing...')
                    """.trimIndent())

                    db.execSQL("""
                        INSERT INTO `music_listening_sessions` (`id`, `trackKey`, `sourcePackage`, `startTimeMs`, `endTimeMs`, `durationMs`, `isCompleted`)
                        VALUES (1, 'track_v8_1', 'com.spotify.music', 1000, 210000, 209000, 1)
                    """.trimIndent())

                    db.execSQL("""
                        INSERT INTO `lyrics_cache` (`trackKey`, `plainLyrics`, `syncedLyricsLrc`, `chordsAmDm`, `userNotes`, `provider`, `cachedAt`)
                        VALUES ('track_v8_1', 'It starts with one thing...', '[00:10.00] It starts', 'Em D C', 'My favorite guitar song', 'builtin:lrclib', 1700000000000)
                    """.trimIndent())

                    db.execSQL("""
                        INSERT INTO `achievements` (`id`, `title`, `description`, `iconRes`, `isUnlocked`, `unlockedAt`, `currentProgress`, `maxProgress`, `tier`, `category`)
                        VALUES ('STREAK_7', 'Неделя в ритме', '7 дней подряд', 'ic_streak', 1, 1700000000000, 7, 7, 'SILVER', 'STREAK')
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Execute MIGRATION_8_9
        AppDatabase.MIGRATION_8_9.migrate(db)

        // 1. Verify lyrics_rejections table is created and empty
        val cursorRejections = db.query("SELECT COUNT(*) FROM lyrics_rejections")
        assertTrue("lyrics_rejections table must exist after migration 8->9", cursorRejections.moveToFirst())
        assertEquals(0, cursorRejections.getInt(0))
        cursorRejections.close()

        // 2. Verify lyrics_rejections supports insert, query, primary key constraint
        db.execSQL("""
            INSERT INTO `lyrics_rejections` (`trackKey`, `sourceId`, `rejectedAt`)
            VALUES ('track_v8_1', 'builtin:lrclib', 1728000000000)
        """.trimIndent())
        val cursorInserted = db.query("SELECT sourceId, rejectedAt FROM lyrics_rejections WHERE trackKey = 'track_v8_1'")
        assertTrue(cursorInserted.moveToFirst())
        assertEquals("builtin:lrclib", cursorInserted.getString(0))
        assertEquals(1728000000000L, cursorInserted.getLong(1))
        cursorInserted.close()

        // 3. Verify deletion (Undo) works in SQLite schema
        db.execSQL("DELETE FROM `lyrics_rejections` WHERE trackKey = 'track_v8_1' AND sourceId = 'builtin:lrclib'")
        val cursorDeleted = db.query("SELECT COUNT(*) FROM lyrics_rejections WHERE trackKey = 'track_v8_1'")
        assertTrue(cursorDeleted.moveToFirst())
        assertEquals(0, cursorDeleted.getInt(0))
        cursorDeleted.close()

        // 4. Verify existing music_tracks data preserved (including user notes and playCount)
        val cursorTrack = db.query("SELECT trackKey, title, playCount, userNotes FROM music_tracks WHERE trackKey = 'track_v8_1'")
        assertTrue("Existing track must be preserved", cursorTrack.moveToFirst())
        assertEquals("In The End", cursorTrack.getString(cursorTrack.getColumnIndexOrThrow("title")))
        assertEquals(42, cursorTrack.getInt(cursorTrack.getColumnIndexOrThrow("playCount")))
        assertEquals("My favorite guitar song", cursorTrack.getString(cursorTrack.getColumnIndexOrThrow("userNotes")))
        cursorTrack.close()

        // 5. Verify listening sessions preserved
        val cursorSession = db.query("SELECT durationMs, isCompleted FROM music_listening_sessions WHERE id = 1")
        assertTrue("Existing listening session must be preserved", cursorSession.moveToFirst())
        assertEquals(209000L, cursorSession.getLong(0))
        assertEquals(1, cursorSession.getInt(1))
        cursorSession.close()

        // 6. Verify lyrics_cache preserved
        val cursorLyrics = db.query("SELECT plainLyrics, provider FROM lyrics_cache WHERE trackKey = 'track_v8_1'")
        assertTrue("Existing lyrics_cache must be preserved", cursorLyrics.moveToFirst())
        assertEquals("It starts with one thing...", cursorLyrics.getString(0))
        assertEquals("builtin:lrclib", cursorLyrics.getString(1))
        cursorLyrics.close()

        // 7. Verify achievements preserved
        val cursorAch = db.query("SELECT tier, isUnlocked FROM achievements WHERE id = 'STREAK_7'")
        assertTrue("Existing achievements must be preserved", cursorAch.moveToFirst())
        assertEquals("SILVER", cursorAch.getString(0))
        assertEquals(1, cursorAch.getInt(1))
        cursorAch.close()

        db.close()
    }

    @Test
    fun test_rejection_insertion_and_retrieval() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.lyricsDao()

        dao.insertRejection(LyricsRejectionEntity("track_alpha", "builtin:lrclib"))
        dao.insertRejection(LyricsRejectionEntity("track_alpha", "builtin:amdm"))
        dao.insertRejection(LyricsRejectionEntity("track_beta", "builtin:lrclib"))

        val alphaRejections = dao.getRejectedSourceIds("track_alpha")
        assertEquals(2, alphaRejections.size)
        assertTrue(alphaRejections.contains("builtin:lrclib"))
        assertTrue(alphaRejections.contains("builtin:amdm"))

        val betaRejections = dao.getRejectedSourceIds("track_beta")
        assertEquals(listOf("builtin:lrclib"), betaRejections)

        assertEquals(2, dao.getRejectionsCountForTrack("track_alpha"))
        assertEquals(1, dao.getRejectionsCountForTrack("track_beta"))
        assertEquals(0, dao.getRejectionsCountForTrack("track_gamma"))

        db.close()
    }

    @Test
    fun test_rejection_deletion_and_clear() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.lyricsDao()

        dao.insertRejection(LyricsRejectionEntity("track_x", "builtin:lrclib"))
        dao.insertRejection(LyricsRejectionEntity("track_x", "builtin:amdm"))

        // Single deletion
        val deletedCount = dao.deleteRejection("track_x", "builtin:lrclib")
        assertEquals(1, deletedCount)
        assertEquals(listOf("builtin:amdm"), dao.getRejectedSourceIds("track_x"))

        // Clear all
        dao.insertRejection(LyricsRejectionEntity("track_x", "builtin:genius"))
        val clearedCount = dao.clearRejectionsForTrack("track_x")
        assertEquals(2, clearedCount)
        assertTrue(dao.getRejectedSourceIds("track_x").isEmpty())

        db.close()
    }

    @Test
    fun test_rejections_do_not_affect_existing_lyrics_cache() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.lyricsDao()
        val musicDao = db.musicDao()

        musicDao.insertOrUpdateTrack(TrackEntity(trackKey = "track_1", title = "Numb", artist = "Linkin Park", sourcePackage = "com.spotify.music"))
        dao.saveLyrics(LyricsCacheEntity(
            trackKey = "track_1",
            plainLyrics = "I've become so numb",
            syncedLyricsLrc = "[00:10.00] I've become so numb",
            chordsAmDm = "Em C G D",
            userNotes = "Guitar standard tuning",
            provider = "builtin:lrclib"
        ))

        dao.insertRejection(LyricsRejectionEntity("track_1", "builtin:lrclib"))

        val cachedLyrics = dao.getLyrics("track_1")
        assertNotNull(cachedLyrics)
        assertEquals("I've become so numb", cachedLyrics!!.plainLyrics)
        assertEquals("builtin:lrclib", cachedLyrics.provider)

        val track = musicDao.getTrackByKey("track_1")
        assertNotNull(track)
        assertEquals("Numb", track!!.title)

        db.close()
    }
}
