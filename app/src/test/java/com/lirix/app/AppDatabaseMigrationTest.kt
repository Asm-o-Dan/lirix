package com.lirix.app

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.lirix.app.storage.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit-tests for Room v6 Migration (TASK-DB-02).
 * Verifies that MIGRATION_5_6 creates lyrics_cache and achievements tables,
 * seeds the 7 core achievements, drops legacy tables (events, financial_transactions,
 * study_items, rule_entities), and preserves music tracks and sessions.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @Test
    fun test_migration_5_to_6() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v5 schema
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
                            `plainLyrics` TEXT
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `events` (
                            `id` TEXT NOT NULL PRIMARY KEY,
                            `timestamp` INTEGER NOT NULL,
                            `source` TEXT NOT NULL,
                            `eventType` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `confidence` REAL NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `financial_transactions` (
                            `id` TEXT NOT NULL PRIMARY KEY,
                            `amount` REAL NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `study_items` (
                            `id` TEXT NOT NULL PRIMARY KEY,
                            `subject` TEXT NOT NULL
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `rule_entities` (
                            `id` TEXT NOT NULL PRIMARY KEY,
                            `ruleName` TEXT NOT NULL
                        )
                    """.trimIndent())

                    // Seed test data in v5
                    db.execSQL("INSERT INTO music_tracks (trackKey, title, artist, sourcePackage, playCount) VALUES ('test_key', 'Numb', 'Linkin Park', 'com.spotify.music', 5)")
                    db.execSQL("INSERT INTO events (id, timestamp, source, eventType, category, confidence) VALUES ('ev1', 1000, 'NOTIFICATION', 'UNKNOWN', 'OTHER', 1.0)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Execute MIGRATION_5_6 directly
        AppDatabase.MIGRATION_5_6.migrate(db)

        // 1. Verify music_tracks data preserved
        val cursorTracks = db.query("SELECT * FROM music_tracks WHERE trackKey = 'test_key'")
        assertTrue("music_tracks must be preserved across migration", cursorTracks.moveToFirst())
        assertEquals("Numb", cursorTracks.getString(cursorTracks.getColumnIndexOrThrow("title")))
        cursorTracks.close()

        // 2. Verify lyrics_cache table exists
        val cursorLyrics = db.query("SELECT count(*) FROM lyrics_cache")
        assertTrue("lyrics_cache table must exist in v6", cursorLyrics.moveToFirst())
        cursorLyrics.close()

        // 3. Verify achievements table exists and has 7 seeded records
        val cursorAch = db.query("SELECT count(*) FROM achievements")
        assertTrue(cursorAch.moveToFirst())
        assertEquals("7 default achievements must be seeded", 7, cursorAch.getInt(0))
        cursorAch.close()

        // 4. Verify legacy tables are dropped
        val cursorTables = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name IN ('events', 'financial_transactions', 'study_items', 'rule_entities')")
        assertFalse("Legacy tables must be dropped in v6", cursorTables.moveToFirst())
        cursorTables.close()

        db.close()
    }

    @Test
    fun test_migration_6_to_7() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v6 schema of music_tracks without albumArtUri
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
                            `plainLyrics` TEXT
                        )
                    """.trimIndent())

                    // Insert pre-existing v6 track
                    db.execSQL("""
                        INSERT INTO music_tracks (trackKey, title, artist, sourcePackage, playCount)
                        VALUES ('track_v6', 'In The End', 'Linkin Park', 'com.spotify.music', 10)
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Execute MIGRATION_6_7 directly
        AppDatabase.MIGRATION_6_7.migrate(db)

        // 1. Verify existing track data preserved and albumArtUri is NULL by default
        val cursor = db.query("SELECT trackKey, title, artist, albumArtUri FROM music_tracks WHERE trackKey = 'track_v6'")
        assertTrue("Track record must exist after migration 6->7", cursor.moveToFirst())
        assertEquals("In The End", cursor.getString(cursor.getColumnIndexOrThrow("title")))
        val artUriIndex = cursor.getColumnIndexOrThrow("albumArtUri")
        assertTrue("albumArtUri must be NULL for existing v6 tracks", cursor.isNull(artUriIndex))
        cursor.close()

        // 2. Verify albumArtUri column accepts update
        db.execSQL("UPDATE music_tracks SET albumArtUri = '/data/user/0/app/files/album_art/track_v6.webp' WHERE trackKey = 'track_v6'")
        val updatedCursor = db.query("SELECT albumArtUri FROM music_tracks WHERE trackKey = 'track_v6'")
        assertTrue(updatedCursor.moveToFirst())
        assertEquals("/data/user/0/app/files/album_art/track_v6.webp", updatedCursor.getString(0))
        updatedCursor.close()

        db.close()
    }

    @Test
    fun test_migration_7_to_8() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v7 achievements table (without tier and category)
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `achievements` (
                            `id` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `description` TEXT NOT NULL,
                            `iconRes` TEXT NOT NULL,
                            `isUnlocked` INTEGER NOT NULL DEFAULT 0,
                            `unlockedAt` INTEGER,
                            `currentProgress` INTEGER NOT NULL DEFAULT 0,
                            `maxProgress` INTEGER NOT NULL DEFAULT 1,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    // Create v7 music_tracks table
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

                    // Insert pre-existing v7 user progress: 1 unlocked achievement
                    db.execSQL("""
                        INSERT INTO `achievements` (`id`, `title`, `description`, `iconRes`, `isUnlocked`, `unlockedAt`, `currentProgress`, `maxProgress`)
                        VALUES ('FIRST_TRACK', 'Первая нота', 'Прослушан 1 трек', 'ic_music_note', 1, 1700000000000, 1, 1)
                    """.trimIndent())

                    // Insert pre-existing v7 track
                    db.execSQL("""
                        INSERT INTO `music_tracks` (`trackKey`, `title`, `artist`, `sourcePackage`, `playCount`)
                        VALUES ('track_v7', 'Numb', 'Linkin Park', 'com.spotify.music', 25)
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Execute MIGRATION_7_8 directly
        AppDatabase.MIGRATION_7_8.migrate(db)

        // 1. Verify tier and category columns exist and have correct defaults
        val cursorAch = db.query("SELECT id, tier, category, isUnlocked, currentProgress FROM achievements WHERE id = 'FIRST_TRACK'")
        assertTrue("FIRST_TRACK achievement must exist after migration", cursorAch.moveToFirst())
        assertEquals("FIRST_TRACK", cursorAch.getString(cursorAch.getColumnIndexOrThrow("id")))
        assertEquals("BRONZE", cursorAch.getString(cursorAch.getColumnIndexOrThrow("tier")))
        assertEquals("LISTENING", cursorAch.getString(cursorAch.getColumnIndexOrThrow("category")))
        // Existing user progress MUST be preserved: isUnlocked == 1
        assertEquals(1, cursorAch.getInt(cursorAch.getColumnIndexOrThrow("isUnlocked")))
        assertEquals(1, cursorAch.getInt(cursorAch.getColumnIndexOrThrow("currentProgress")))
        cursorAch.close()

        // 2. Verify total 26 achievements seeded
        val cursorTotal = db.query("SELECT COUNT(*) FROM achievements")
        assertTrue(cursorTotal.moveToFirst())
        assertEquals("Total achievements in v8 must be exactly 26", 26, cursorTotal.getInt(0))
        cursorTotal.close()

        // 3. Verify specific new achievements from each tier exist
        val cursorNewTiers = db.query("SELECT id, tier FROM achievements WHERE id IN ('STREAK_7', 'STREAK_30', 'PLATINUM_PERFECTION')")
        assertTrue(cursorNewTiers.moveToFirst())
        val foundIds = mutableMapOf<String, String>()
        do {
            foundIds[cursorNewTiers.getString(0)] = cursorNewTiers.getString(1)
        } while (cursorNewTiers.moveToNext())
        cursorNewTiers.close()

        assertEquals("SILVER", foundIds["STREAK_7"])
        assertEquals("GOLD", foundIds["STREAK_30"])
        assertEquals("PLATINUM", foundIds["PLATINUM_PERFECTION"])

        // 4. Verify existing track data in music_tracks remains preserved
        val cursorTrack = db.query("SELECT trackKey, title, playCount FROM music_tracks WHERE trackKey = 'track_v7'")
        assertTrue(cursorTrack.moveToFirst())
        assertEquals("Numb", cursorTrack.getString(cursorTrack.getColumnIndexOrThrow("title")))
        assertEquals(25, cursorTrack.getInt(cursorTrack.getColumnIndexOrThrow("playCount")))
        cursorTrack.close()

        db.close()
    }
}


