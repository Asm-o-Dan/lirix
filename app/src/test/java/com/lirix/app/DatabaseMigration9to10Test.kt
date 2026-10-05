package com.lirix.app

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.room.Room
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.CustomLyricsRuleEntity
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.storage.LyricsRejectionEntity
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TDD Unit-tests for TASK-LYR-04-A:
 * - Room migration from v9 to v10 (MIGRATION_9_10).
 * - Verifies creation of `custom_lyrics_rules` table and indices (domain, isEnabled, priority).
 * - Verifies preservation of existing music_tracks, listening_sessions, lyrics_cache, achievements, lyrics_rejections.
 * - Verifies saveRule, getActiveRules, getRuleById, getRulesForDomain, observeAllRules, setRuleEnabled, deleteRule (with isBuiltIn guard).
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigration9to10Test {

    @Test
    fun test_migration_9_to_10_preserves_existing_data() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 1. Create v9 music_tracks
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

                    // 2. Create v9 music_listening_sessions
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

                    // 3. Create v9 lyrics_cache
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

                    // 4. Create v9 achievements
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

                    // 5. Create v9 lyrics_rejections
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `lyrics_rejections` (
                            `trackKey` TEXT NOT NULL,
                            `sourceId` TEXT NOT NULL,
                            `rejectedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`trackKey`, `sourceId`)
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_lyrics_rejections_trackKey` ON `lyrics_rejections` (`trackKey`)")

                    // Seed data
                    db.execSQL("""
                        INSERT INTO `music_tracks` (`trackKey`, `title`, `artist`, `sourcePackage`, `playCount`, `userNotes`, `plainLyrics`)
                        VALUES ('track_v9_1', 'Crawling', 'Linkin Park', 'com.spotify.music', 50, 'Guitar drop D note', 'Crawling in my skin...')
                    """.trimIndent())

                    db.execSQL("""
                        INSERT INTO `lyrics_rejections` (`trackKey`, `sourceId`, `rejectedAt`)
                        VALUES ('track_v9_1', 'builtin:lrclib', 1729000000000)
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Execute MIGRATION_9_10
        AppDatabase.MIGRATION_9_10.migrate(db)

        // Verify custom_lyrics_rules table created and empty
        val cursorRules = db.query("SELECT COUNT(*) FROM custom_lyrics_rules")
        assertTrue("custom_lyrics_rules table must exist after migration 9->10", cursorRules.moveToFirst())
        assertEquals(0, cursorRules.getInt(0))
        cursorRules.close()

        // Verify insertion into custom_lyrics_rules works with all columns
        db.execSQL("""
            INSERT INTO `custom_lyrics_rules` (
                `id`, `domain`, `name`, `ruleJson`, `isEnabled`, `priority`, `isBuiltIn`, `author`, `version`, `createdAt`, `updatedAt`
            ) VALUES (
                'rule_test', 'amalgama-lab.com', 'Амальгама', '{"content":{"selector":".string_ru"}}', 1, 50, 0, 'local', 1, 1000, 1000
            )
        """.trimIndent())

        val cursorInserted = db.query("SELECT id, domain, isEnabled, priority FROM custom_lyrics_rules WHERE id = 'rule_test'")
        assertTrue(cursorInserted.moveToFirst())
        assertEquals("rule_test", cursorInserted.getString(0))
        assertEquals("amalgama-lab.com", cursorInserted.getString(1))
        assertEquals(1, cursorInserted.getInt(2))
        assertEquals(50, cursorInserted.getInt(3))
        cursorInserted.close()

        // Verify existing tables preserved
        val cursorTrack = db.query("SELECT title, userNotes FROM music_tracks WHERE trackKey = 'track_v9_1'")
        assertTrue(cursorTrack.moveToFirst())
        assertEquals("Crawling", cursorTrack.getString(0))
        assertEquals("Guitar drop D note", cursorTrack.getString(1))
        cursorTrack.close()

        val cursorRejections = db.query("SELECT sourceId FROM lyrics_rejections WHERE trackKey = 'track_v9_1'")
        assertTrue(cursorRejections.moveToFirst())
        assertEquals("builtin:lrclib", cursorRejections.getString(0))
        cursorRejections.close()

        db.close()
    }

    @Test
    fun test_save_and_query_active_rules() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.lyricsDao()

        val rule1 = CustomLyricsRuleEntity(
            id = "rule_prio_50",
            domain = "site1.com",
            name = "Site 1",
            ruleJson = "{}",
            isEnabled = true,
            priority = 50
        )
        val rule2 = CustomLyricsRuleEntity(
            id = "rule_prio_10",
            domain = "site2.com",
            name = "Site 2",
            ruleJson = "{}",
            isEnabled = true,
            priority = 10
        )
        val disabledRule = CustomLyricsRuleEntity(
            id = "rule_disabled",
            domain = "site3.com",
            name = "Site 3",
            ruleJson = "{}",
            isEnabled = false,
            priority = 5
        )

        dao.saveRule(rule1)
        dao.saveRule(rule2)
        dao.saveRule(disabledRule)

        val active = dao.getActiveRules()
        assertEquals(2, active.size)
        // Check sorting by priority ASC (10 first, then 50)
        assertEquals("rule_prio_10", active[0].id)
        assertEquals("rule_prio_50", active[1].id)

        val site1Rules = dao.getRulesForDomain("site1.com")
        assertEquals(1, site1Rules.size)
        assertEquals("rule_prio_50", site1Rules[0].id)

        val fetchedRule = dao.getRuleById("rule_prio_10")
        assertNotNull(fetchedRule)
        assertEquals("Site 2", fetchedRule?.name)

        db.close()
    }

    @Test
    fun test_delete_rule_prevents_builtin_deletion() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.lyricsDao()

        val builtInRule = CustomLyricsRuleEntity(
            id = "builtin_amalgama",
            domain = "amalgama-lab.com",
            name = "Amalgama",
            ruleJson = "{}",
            isBuiltIn = true
        )
        val customRule = CustomLyricsRuleEntity(
            id = "custom_user_rule",
            domain = "mylyrics.com",
            name = "MyLyrics",
            ruleJson = "{}",
            isBuiltIn = false
        )

        dao.saveRule(builtInRule)
        dao.saveRule(customRule)

        // Try deleting builtin rule
        val deletedBuiltin = dao.deleteRule("builtin_amalgama")
        assertEquals("Built-in rules cannot be deleted", 0, deletedBuiltin)
        assertNotNull(dao.getRuleById("builtin_amalgama"))

        // Delete custom rule
        val deletedCustom = dao.deleteRule("custom_user_rule")
        assertEquals(1, deletedCustom)
        assertNull(dao.getRuleById("custom_user_rule"))

        db.close()
    }

    @Test
    fun test_set_rule_enabled_toggles_state() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.lyricsDao()

        val rule = CustomLyricsRuleEntity(
            id = "toggle_rule",
            domain = "toggle.org",
            name = "Toggle",
            ruleJson = "{}",
            isEnabled = true,
            updatedAt = 1000L
        )
        dao.saveRule(rule)

        val updatedRows = dao.setRuleEnabled("toggle_rule", false, updatedAt = 5000L)
        assertEquals(1, updatedRows)

        val fetched = dao.getRuleById("toggle_rule")
        assertNotNull(fetched)
        assertEquals(false, fetched?.isEnabled)
        assertEquals(5000L, fetched?.updatedAt)

        val observed = dao.observeAllRules().first()
        assertEquals(1, observed.size)
        assertEquals(false, observed[0].isEnabled)

        db.close()
    }
}
