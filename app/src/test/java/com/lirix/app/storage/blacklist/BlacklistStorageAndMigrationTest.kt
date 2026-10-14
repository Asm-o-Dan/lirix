package com.lirix.app.storage.blacklist

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.ListeningSessionEntity
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlacklistStorageAndMigrationTest {

    @Test
    fun test_migration_10_to_11_creates_table_and_preserves_data() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v10 music_tracks
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

                    // Create v10 music_listening_sessions
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

                    // Insert sample track
                    db.execSQL("""
                        INSERT INTO `music_tracks` (`trackKey`, `title`, `artist`, `sourcePackage`)
                        VALUES ('key_migration_test', 'In The End', 'Linkin Park', 'com.spotify.music')
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Execute MIGRATION_10_11
        AppDatabase.MIGRATION_10_11.migrate(db)

        // Verify blacklist_rules table exists and can receive inserts
        val cursorTable = db.query("SELECT COUNT(*) FROM blacklist_rules")
        assertTrue("blacklist_rules table must exist after migration 10->11", cursorTable.moveToFirst())
        assertEquals(0, cursorTable.getInt(0))
        cursorTable.close()

        // Insert rule with enums as TEXT
        db.execSQL("""
            INSERT INTO `blacklist_rules` (`id`, `ruleType`, `pattern`, `matchMode`, `isEnabled`, `createdAt`)
            VALUES ('rule_migrated', 'ARTIST', 'Spam Band', 'EXACT', 1, 123456789)
        """.trimIndent())

        val cursorRule = db.query("SELECT id, ruleType, pattern, matchMode, isEnabled FROM blacklist_rules WHERE id = 'rule_migrated'")
        assertTrue(cursorRule.moveToFirst())
        assertEquals("rule_migrated", cursorRule.getString(0))
        assertEquals("ARTIST", cursorRule.getString(1))
        assertEquals("Spam Band", cursorRule.getString(2))
        assertEquals("EXACT", cursorRule.getString(3))
        assertEquals(1, cursorRule.getInt(4))
        cursorRule.close()

        // Verify existing track preserved
        val cursorTrack = db.query("SELECT title FROM music_tracks WHERE trackKey = 'key_migration_test'")
        assertTrue(cursorTrack.moveToFirst())
        assertEquals("In The End", cursorTrack.getString(0))
        cursorTrack.close()

        db.close()
    }

    @Test
    fun test_blacklist_dao_crud_and_flow() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dao = db.blacklistDao()

        val rule1 = BlacklistRuleEntity(
            id = "rule_1",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "Podcast",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
            isEnabled = true,
            createdAt = 1000L
        )
        val rule2 = BlacklistRuleEntity(
            id = "rule_2",
            ruleType = BlacklistRuleType.ARTIST,
            pattern = "Ad Jingle",
            matchMode = BlacklistMatchMode.EXACT,
            isEnabled = false,
            createdAt = 2000L
        )

        dao.insertRule(rule1)
        dao.insertRule(rule2)

        assertEquals(2, dao.countRules())

        val allRules = dao.getAllRules()
        assertEquals(2, allRules.size)
        // Check order DESC by createdAt: rule_2 (2000L) first, then rule_1 (1000L)
        assertEquals("rule_2", allRules[0].id)
        assertEquals("rule_1", allRules[1].id)

        val activeRules = dao.getActiveRules()
        assertEquals(1, activeRules.size)
        assertEquals("rule_1", activeRules[0].id)

        val fetchedRule = dao.getRuleById("rule_1")
        assertNotNull(fetchedRule)
        assertEquals("Podcast", fetchedRule?.pattern)

        // Update rule
        dao.updateRule(rule2.copy(isEnabled = true))
        assertEquals(2, dao.getActiveRules().size)

        // Test Flow
        val flowRules = dao.getAllRulesFlow().first()
        assertEquals(2, flowRules.size)

        // Delete rule
        dao.deleteRule("rule_1")
        assertEquals(1, dao.countRules())
        assertNull(dao.getRuleById("rule_1"))

        db.close()
    }

    @Test
    fun test_music_dao_batch_deletes_and_get_all_tracks() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val musicDao = db.musicDao()

        val track1 = TrackEntity(trackKey = "k1", title = "T1", artist = "A1", sourcePackage = "p1")
        val track2 = TrackEntity(trackKey = "k2", title = "T2", artist = "A2", sourcePackage = "p2")
        val track3 = TrackEntity(trackKey = "k3", title = "T3", artist = "A3", sourcePackage = "p3")

        musicDao.insertOrUpdateTrack(track1)
        musicDao.insertOrUpdateTrack(track2)
        musicDao.insertOrUpdateTrack(track3)

        val session1 = ListeningSessionEntity(trackKey = "k1", sourcePackage = "p1", startTimeMs = 1, endTimeMs = 2, durationMs = 1)
        val session2 = ListeningSessionEntity(trackKey = "k2", sourcePackage = "p2", startTimeMs = 3, endTimeMs = 4, durationMs = 1)

        musicDao.insertSession(session1)
        musicDao.insertSession(session2)

        val allTracks = musicDao.getAllTracks()
        assertEquals(3, allTracks.size)

        // Delete track1 and track2 by keys
        val deletedSessions = musicDao.deleteSessionsByTrackKeys(listOf("k1", "k2"))
        val deletedTracks = musicDao.deleteTracksByKeys(listOf("k1", "k2"))

        assertEquals(2, deletedSessions)
        assertEquals(2, deletedTracks)

        val remainingTracks = musicDao.getAllTracks()
        assertEquals(1, remainingTracks.size)
        assertEquals("k3", remainingTracks[0].trackKey)

        db.close()
    }
}
