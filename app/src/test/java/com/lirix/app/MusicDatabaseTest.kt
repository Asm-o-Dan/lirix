package com.lirix.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.lirix.app.feature.blacklist.BlacklistPurgeEngine
import com.lirix.app.storage.AchievementDao
import com.lirix.app.storage.AchievementEntity
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.ListeningSessionEntity
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.storage.LyricsDao
import com.lirix.app.storage.MusicDao
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.blacklist.BlacklistDao
import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.blacklist.BlacklistRuleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Unit/Integration test verifying Room v6 DAOs (TASK-DB-01):
 * - LyricsDao CRUD operations and cache queries.
 * - AchievementDao CRUD, progress observation, and seed operations.
 * - MusicDao tracks and sessions persistence.
 */
@RunWith(AndroidJUnit4::class)
class MusicDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var lyricsDao: LyricsDao
    private lateinit var achievementDao: AchievementDao
    private lateinit var musicDao: MusicDao
    private lateinit var blacklistDao: BlacklistDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        lyricsDao = db.lyricsDao()
        achievementDao = db.achievementDao()
        musicDao = db.musicDao()
        blacklistDao = db.blacklistDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testLyricsDaoSaveAndGet() = runBlocking {
        val trackKey = "sha256_starboy_theweeknd"
        val entity = LyricsCacheEntity(
            trackKey = trackKey,
            plainLyrics = "I'm tryna put you in the worst mood",
            syncedLyricsLrc = "[00:15.30] I'm tryna put you in the worst mood",
            chordsAmDm = "Am C Dm G",
            userNotes = "User favorite",
            provider = "LRCLIB"
        )

        lyricsDao.saveLyrics(entity)

        val retrieved = lyricsDao.getLyrics(trackKey)
        assertNotNull("Lyrics must be retrieved from cache", retrieved)
        assertEquals(trackKey, retrieved!!.trackKey)
        assertEquals("LRCLIB", retrieved.provider)
        assertEquals("[00:15.30] I'm tryna put you in the worst mood", retrieved.syncedLyricsLrc)
        assertEquals("Am C Dm G", retrieved.chordsAmDm)
        assertEquals("User favorite", retrieved.userNotes)

        // Non-existent key should return null
        assertNull(lyricsDao.getLyrics("non_existent_key"))
    }

    @Test
    fun testAchievementDaoInsertAndObserve() = runBlocking {
        val ach1 = AchievementEntity(
            id = "FIRST_TRACK",
            title = "Первая нота",
            description = "Прослушан 1 трек",
            iconRes = "ic_music_note",
            isUnlocked = false,
            currentProgress = 0,
            maxProgress = 1
        )
        val ach2 = AchievementEntity(
            id = "CENTURY_CLUB",
            title = "Клуб сотни",
            description = "100 уникальных треков",
            iconRes = "ic_disc",
            isUnlocked = false,
            currentProgress = 10,
            maxProgress = 100
        )

        achievementDao.insertAll(listOf(ach1, ach2))

        val retrievedAch1 = achievementDao.getAchievement("FIRST_TRACK")
        assertNotNull(retrievedAch1)
        assertEquals("Первая нота", retrievedAch1!!.title)
        assertFalse(retrievedAch1.isUnlocked)

        // Update progress and unlock
        val updatedAch1 = retrievedAch1.copy(isUnlocked = true, currentProgress = 1, unlockedAt = System.currentTimeMillis())
        achievementDao.insertOrUpdate(updatedAch1)

        val allAchievements = achievementDao.observeAchievements().first()
        assertEquals(2, allAchievements.size)
        // First in order by isUnlocked DESC
        assertTrue(allAchievements[0].isUnlocked)
        assertEquals("FIRST_TRACK", allAchievements[0].id)
    }

    @Test
    fun testMusicDaoTracksAndSessions() = runBlocking {
        val track = TrackEntity(
            trackKey = "track_numb",
            title = "Numb",
            artist = "Linkin Park",
            sourcePackage = "com.spotify.music",
            playCount = 1
        )
        musicDao.insertOrUpdateTrack(track)

        val session = ListeningSessionEntity(
            trackKey = "track_numb",
            sourcePackage = "com.spotify.music",
            startTimeMs = 1000L,
            endTimeMs = 180000L,
            durationMs = 179000L,
            isCompleted = true
        )
        val sessionId = musicDao.insertSession(session)
        assertTrue(sessionId > 0)

        val retrievedSession = musicDao.getSessionById(sessionId)
        assertNotNull(retrievedSession)
        assertEquals("track_numb", retrievedSession!!.trackKey)
        assertEquals(179000L, retrievedSession.durationMs)
    }

    @Test
    fun testRecordPlaybackSignalNewTrackAndSession() = runBlocking {
        val engine = com.lirix.app.feature.MusicFeatureEngine(musicDao)
        val timestamp = 100000L

        // Ingest new track signal
        val track = engine.recordPlaybackSignal(
            title = "In The End",
            artist = "Linkin Park",
            album = "Hybrid Theory",
            sourcePackage = "com.spotify.music",
            playbackState = "PLAYING",
            timestamp = timestamp
        )

        assertNotNull("TrackEntity must be returned", track)
        assertEquals("In The End", track!!.title)
        assertEquals("Linkin Park", track.artist)
        assertEquals(1, track.playCount)
        assertEquals(timestamp, track.firstPlayedAt)
        assertEquals(timestamp, track.lastPlayedAt)

        // Verify persisted track in DB
        val dbTrack = musicDao.getTrackByKey(track.trackKey)
        assertNotNull("Track must be persisted in DB", dbTrack)
        assertEquals(track.trackKey, dbTrack!!.trackKey)
        assertEquals(1, dbTrack.playCount)

        // Verify persisted session in DB
        val lastSession = musicDao.getLastSessionForPackage("com.spotify.music")
        assertNotNull("Listening session must be created", lastSession)
        assertEquals(track.trackKey, lastSession!!.trackKey)
        assertEquals("com.spotify.music", lastSession.sourcePackage)
        assertEquals(timestamp, lastSession.startTimeMs)
        assertFalse(lastSession.isCompleted)
    }

    @Test
    fun testRecordPlaybackSignalSessionCollapsing() = runBlocking {
        val engine = com.lirix.app.feature.MusicFeatureEngine(musicDao)
        val t0 = 100000L

        // 1. Initial play signal
        val track1 = engine.recordPlaybackSignal(
            title = "Numb",
            artist = "Linkin Park",
            album = "Meteora",
            sourcePackage = "com.spotify.music",
            playbackState = "PLAYING",
            timestamp = t0
        )
        assertNotNull(track1)
        assertEquals(1, track1!!.playCount)

        // 2. Playback progress within 15 seconds (<= 30s threshold)
        val t1 = t0 + 15000L
        val track2 = engine.recordPlaybackSignal(
            title = "Numb",
            artist = "Linkin Park",
            album = "Meteora",
            sourcePackage = "com.spotify.music",
            playbackState = "PLAYING",
            timestamp = t1
        )
        assertNotNull(track2)
        // Play count should NOT increment (collapsed into same session)
        assertEquals("Play count should remain 1", 1, track2!!.playCount)
        assertEquals(15000L, track2.totalDurationMs)

        val session = musicDao.getLastSessionForPackage("com.spotify.music")
        assertNotNull(session)
        assertEquals(t0, session!!.startTimeMs)
        assertEquals(t1, session.endTimeMs)
        assertEquals(15000L, session.durationMs)
        assertFalse(session.isCompleted)

        // 3. Pause signal
        val t2 = t1 + 5000L
        engine.recordPlaybackSignal(
            title = "Numb",
            artist = "Linkin Park",
            album = "Meteora",
            sourcePackage = "com.spotify.music",
            playbackState = "PAUSED",
            timestamp = t2
        )
        val pausedSession = musicDao.getLastSessionForPackage("com.spotify.music")
        assertNotNull(pausedSession)
        assertEquals(20000L, pausedSession!!.durationMs)
        assertFalse("Session must remain open (not completed) on PAUSED", pausedSession.isCompleted)
    }

    // ------------------------------------------------------------------------
    // TASK-BUG-02: Lyrics Persistence & Dual-Write Verification
    // ------------------------------------------------------------------------

    @Test
    fun testRecordPlaybackSignal_preservesExistingLyrics_andHydratesFromCache() = runBlocking {
        val engine = com.lirix.app.feature.MusicFeatureEngine(musicDao)
        val trackKey = com.lirix.app.feature.MusicFeatureEngine.computeTrackKey("Numb", "Linkin Park", "Meteora")

        // 1. Initial track record
        engine.recordPlaybackSignal(
            title = "Numb",
            artist = "Linkin Park",
            album = "Meteora",
            sourcePackage = "com.spotify.music",
            playbackState = "PLAYING",
            timestamp = 1000L
        )

        // 2. Lyrics discovered and saved to music_tracks and lyrics_cache
        val samplePlain = "I've become so numb, I can't feel you there"
        val sampleSynced = "[00:20.10] I've become so numb, I can't feel you there"

        musicDao.updateLyrics(trackKey, samplePlain, sampleSynced)
        lyricsDao.saveLyrics(
            LyricsCacheEntity(
                trackKey = trackKey,
                plainLyrics = samplePlain,
                syncedLyricsLrc = sampleSynced,
                chordsAmDm = "Em C G D",
                userNotes = "",
                provider = "LRCLIB"
            )
        )

        val trackWithLyrics = musicDao.getTrackByKey(trackKey)
        assertNotNull(trackWithLyrics)
        assertEquals(samplePlain, trackWithLyrics!!.plainLyrics)
        assertEquals(sampleSynced, trackWithLyrics.syncedLyrics)

        // 3. Repeat playback signal occurs (e.g. track replayed or unpaused)
        val updatedTrack = engine.recordPlaybackSignal(
            title = "Numb",
            artist = "Linkin Park",
            album = "Meteora",
            sourcePackage = "com.spotify.music",
            playbackState = "PLAYING",
            timestamp = 50000L
        )

        assertNotNull(updatedTrack)
        // CRITICAL BUG-02 CHECK: lyrics must NOT be wiped to null!
        assertEquals("plainLyrics must be preserved across subsequent playback signals", samplePlain, updatedTrack!!.plainLyrics)
        assertEquals("syncedLyrics must be preserved across subsequent playback signals", sampleSynced, updatedTrack.syncedLyrics)

        // Verify database state directly
        val dbTrackAfterReplay = musicDao.getTrackByKey(trackKey)
        assertNotNull(dbTrackAfterReplay)
        assertEquals("Database plainLyrics must not be wiped", samplePlain, dbTrackAfterReplay!!.plainLyrics)
        assertEquals("Database syncedLyrics must not be wiped", sampleSynced, dbTrackAfterReplay.syncedLyrics)
    }

    @Test
    fun testDualWrite_lyricsSavedInCache_updatesMusicTracksEntity() = runBlocking {
        val trackKey = com.lirix.app.feature.MusicFeatureEngine.computeTrackKey("Starboy", "The Weeknd", "Starboy")

        // 1. Insert initial track without lyrics
        val initialTrack = TrackEntity(
            trackKey = trackKey,
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            sourcePackage = "com.spotify.music"
        )
        musicDao.insertOrUpdateTrack(initialTrack)

        // 2. Perform Dual-Write operation (cache + tracks)
        val plain = "I'm tryna put you in the worst mood, ah"
        val synced = "[00:15.30] I'm tryna put you in the worst mood, ah"

        // Write to cache
        lyricsDao.saveLyrics(
            LyricsCacheEntity(
                trackKey = trackKey,
                plainLyrics = plain,
                syncedLyricsLrc = synced,
                chordsAmDm = null,
                userNotes = "",
                provider = "LRCLIB"
            )
        )
        // Dual-write to music_tracks table
        musicDao.updateLyrics(trackKey, plain, synced)

        // 3. Verify both tables have synchronous lyrics data
        val cached = lyricsDao.getLyrics(trackKey)
        assertNotNull("lyrics_cache must contain lyrics", cached)
        assertEquals(plain, cached!!.plainLyrics)
        assertEquals(synced, cached.syncedLyricsLrc)

        val trackInDb = musicDao.getTrackByKey(trackKey)
        assertNotNull("music_tracks must contain track", trackInDb)
        assertEquals("music_tracks plainLyrics must be populated by dual-write", plain, trackInDb!!.plainLyrics)
        assertEquals("music_tracks syncedLyrics must be populated by dual-write", synced, trackInDb.syncedLyrics)
    }

    // ------------------------------------------------------------------------
    // TASK-BUG-05: PlayCount Deduplication & Protection Against Double/Triple Increment
    // ------------------------------------------------------------------------

    @Test
    fun test_rapid_consecutive_signals_do_not_duplicate_playCount() = runBlocking {
        val engine = com.lirix.app.feature.MusicFeatureEngine(musicDao)
        val t0 = 1_000_000L
        val pkg = "com.spotify.music"

        // 1. Five rapid signals within 100ms each (PLAYING -> UPDATE -> BUFFERING -> PLAYING)
        val states = listOf("PLAYING", "UPDATE", "BUFFERING", "PLAYING", "PLAYING")
        var currentTrack: TrackEntity? = null
        for ((index, state) in states.withIndex()) {
            currentTrack = engine.recordPlaybackSignal(
                title = "In The End",
                artist = "Linkin Park",
                album = "Hybrid Theory",
                sourcePackage = pkg,
                playbackState = state,
                timestamp = t0 + (index * 100L)
            )
        }

        assertNotNull(currentTrack)
        assertEquals("Rapid consecutive signals within 60s cooldown must have playCount = 1", 1, currentTrack!!.playCount)

        val trackInDb = musicDao.getTrackByKey(currentTrack.trackKey)
        assertNotNull(trackInDb)
        assertEquals("playCount in DB must remain 1 despite 5 rapid playback events", 1, trackInDb!!.playCount)
    }

    @Test
    fun test_pause_and_resume_preserves_single_playCount() = runBlocking {
        val engine = com.lirix.app.feature.MusicFeatureEngine(musicDao)
        val t0 = 2_000_000L
        val pkg = "com.spotify.music"

        // 1. Initial PLAYING signal
        val trackStart = engine.recordPlaybackSignal(
            title = "Numb",
            artist = "Linkin Park",
            album = "Meteora",
            sourcePackage = pkg,
            playbackState = "PLAYING",
            timestamp = t0
        )
        assertNotNull(trackStart)
        assertEquals(1, trackStart!!.playCount)

        // 2. Pause after 10s
        engine.recordPlaybackSignal(
            title = "Numb",
            artist = "Linkin Park",
            album = "Meteora",
            sourcePackage = pkg,
            playbackState = "PAUSED",
            timestamp = t0 + 10_000L
        )

        // 3. Resume 40s later (gap of 40s on the SAME track)
        val trackResume = engine.recordPlaybackSignal(
            title = "Numb",
            artist = "Linkin Park",
            album = "Meteora",
            sourcePackage = pkg,
            playbackState = "PLAYING",
            timestamp = t0 + 50_000L
        )
        assertNotNull(trackResume)
        assertEquals("Pause and resume of the same track must not increment playCount", 1, trackResume!!.playCount)

        val trackInDb = musicDao.getTrackByKey(trackStart.trackKey)
        assertNotNull(trackInDb)
        assertEquals("Database playCount must remain 1 after pause and resume", 1, trackInDb!!.playCount)
    }

    @Test
    fun test_track_switch_and_replay_after_cooldown_increments_playCount() = runBlocking {
        val engine = com.lirix.app.feature.MusicFeatureEngine(musicDao)
        val t0 = 3_000_000L
        val pkg = "com.spotify.music"

        // 1. Play Track A
        val trackA1 = engine.recordPlaybackSignal(
            title = "Faint",
            artist = "Linkin Park",
            album = "Meteora",
            sourcePackage = pkg,
            playbackState = "PLAYING",
            timestamp = t0
        )
        assertEquals(1, trackA1!!.playCount)

        // 2. Switch to Track B after 20s -> Track B gets playCount = 1
        val trackB = engine.recordPlaybackSignal(
            title = "Crawling",
            artist = "Linkin Park",
            album = "Hybrid Theory",
            sourcePackage = pkg,
            playbackState = "PLAYING",
            timestamp = t0 + 20_000L
        )
        assertNotNull(trackB)
        assertEquals("Track switch to new track must increment playCount for Track B", 1, trackB!!.playCount)

        // 3. Switch back to Track A after 70s (> 60s cooldown) -> Track A gets playCount = 2
        val trackA2 = engine.recordPlaybackSignal(
            title = "Faint",
            artist = "Linkin Park",
            album = "Meteora",
            sourcePackage = pkg,
            playbackState = "PLAYING",
            timestamp = t0 + 90_000L
        )
        assertNotNull(trackA2)
        assertEquals("Replaying Track A after 60s cooldown must increment playCount to 2", 2, trackA2!!.playCount)
    }

    // ------------------------------------------------------------------------
    // TASK-BUG-09: Pause Session Lifecycle & PlayCount Inflation Protection
    // ------------------------------------------------------------------------

    @Test
    fun test_pause_longer_than_cooldown_does_not_increment_playCount() = runBlocking {
        val engine = com.lirix.app.feature.MusicFeatureEngine(musicDao)
        val t0 = 5_000_000L
        val pkg = "org.telegram.messenger"

        // 1. Initial PLAYING signal
        val track1 = engine.recordPlaybackSignal(
            title = "Somewhere I Belong",
            artist = "Linkin Park",
            album = "Meteora",
            sourcePackage = pkg,
            playbackState = "PLAYING",
            timestamp = t0
        )
        assertNotNull(track1)
        assertEquals(1, track1!!.playCount)

        // 2. Pause after 15s
        val tPause = t0 + 15_000L
        engine.recordPlaybackSignal(
            title = "Somewhere I Belong",
            artist = "Linkin Park",
            album = "Meteora",
            sourcePackage = pkg,
            playbackState = "PAUSED",
            timestamp = tPause
        )
        val pausedSession = musicDao.getLastSessionForPackage(pkg)
        assertNotNull(pausedSession)
        assertFalse("Paused session must NOT be marked isCompleted", pausedSession!!.isCompleted)

        // 3. Resume after 80s (> 60s cooldown passed) on the same track
        val tResume = tPause + 80_000L
        val trackResume = engine.recordPlaybackSignal(
            title = "Somewhere I Belong",
            artist = "Linkin Park",
            album = "Meteora",
            sourcePackage = pkg,
            playbackState = "PLAYING",
            timestamp = tResume
        )
        assertNotNull(trackResume)
        assertEquals("Resuming playback after pause (>60s) must NOT increment playCount", 1, trackResume!!.playCount)

        val trackInDb = musicDao.getTrackByKey(track1.trackKey)
        assertNotNull(trackInDb)
        assertEquals("Database playCount must remain 1 after long pause and resume", 1, trackInDb!!.playCount)
    }

    // ------------------------------------------------------------------------
    // TASK-BLK-QA: Room Migration v10 -> v11, BlacklistDao & PurgeEngine
    // ------------------------------------------------------------------------

    @Test
    fun testMigration10To11_createsBlacklistTableAndIndices() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory SQLite database
            .callback(object : SupportSQLiteOpenHelper.Callback(10) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // 1. Create v10 music_tracks
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

                    // 2. Create v10 music_listening_sessions
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

                    // 3. Create v10 custom_lyrics_rules
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `custom_lyrics_rules` (
                            `id` TEXT NOT NULL PRIMARY KEY,
                            `domain` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `ruleJson` TEXT NOT NULL,
                            `isEnabled` INTEGER NOT NULL DEFAULT 1,
                            `priority` INTEGER NOT NULL DEFAULT 100,
                            `isBuiltIn` INTEGER NOT NULL DEFAULT 0,
                            `author` TEXT NOT NULL DEFAULT 'local',
                            `version` INTEGER NOT NULL DEFAULT 1,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL
                        )
                    """.trimIndent())

                    // Seed pre-existing records to verify data integrity
                    db.execSQL("""
                        INSERT INTO `music_tracks` (`trackKey`, `title`, `artist`, `sourcePackage`, `playCount`)
                        VALUES ('v10_track_1', 'Papercut', 'Linkin Park', 'com.spotify.music', 5)
                    """.trimIndent())

                    db.execSQL("""
                        INSERT INTO `custom_lyrics_rules` (
                            `id`, `domain`, `name`, `ruleJson`, `isEnabled`, `priority`, `isBuiltIn`, `author`, `version`, `createdAt`, `updatedAt`
                        ) VALUES (
                            'rule_v10', 'amalgama-lab.com', 'Test Rule', '{}', 1, 100, 0, 'local', 1, 1000, 1000
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val sqlDb = helper.writableDatabase

        // Execute MIGRATION_10_11
        AppDatabase.MIGRATION_10_11.migrate(sqlDb)

        // 1. Verify blacklist_rules table exists and is empty
        val cursorTable = sqlDb.query("SELECT COUNT(*) FROM blacklist_rules")
        assertTrue("blacklist_rules table must exist after migration 10->11", cursorTable.moveToFirst())
        assertEquals(0, cursorTable.getInt(0))
        cursorTable.close()

        // 2. Verify indices exist on blacklist_rules
        val cursorIndices = sqlDb.query("PRAGMA index_list('blacklist_rules')")
        val indexNames = mutableListOf<String>()
        while (cursorIndices.moveToNext()) {
            val nameColIndex = cursorIndices.getColumnIndex("name")
            if (nameColIndex >= 0) {
                indexNames.add(cursorIndices.getString(nameColIndex))
            }
        }
        cursorIndices.close()
        assertTrue("Index on isEnabled must be created", indexNames.contains("index_blacklist_rules_isEnabled"))
        assertTrue("Index on ruleType must be created", indexNames.contains("index_blacklist_rules_ruleType"))

        // 3. Verify insert and query on blacklist_rules table
        sqlDb.execSQL("""
            INSERT INTO `blacklist_rules` (`id`, `ruleType`, `pattern`, `matchMode`, `isEnabled`, `createdAt`)
            VALUES ('rule_migrated', 'TITLE', 'реклама', 'CONTAINS_IGNORE_CASE', 1, 1729000000000)
        """.trimIndent())

        val cursorRule = sqlDb.query("SELECT id, ruleType, pattern, matchMode, isEnabled FROM blacklist_rules WHERE id = 'rule_migrated'")
        assertTrue(cursorRule.moveToFirst())
        assertEquals("rule_migrated", cursorRule.getString(0))
        assertEquals("TITLE", cursorRule.getString(1))
        assertEquals("реклама", cursorRule.getString(2))
        assertEquals("CONTAINS_IGNORE_CASE", cursorRule.getString(3))
        assertEquals(1, cursorRule.getInt(4))
        cursorRule.close()

        // 4. Verify existing data in music_tracks and custom_lyrics_rules is preserved
        val cursorTrack = sqlDb.query("SELECT title, artist, playCount FROM music_tracks WHERE trackKey = 'v10_track_1'")
        assertTrue("Pre-existing track must be preserved", cursorTrack.moveToFirst())
        assertEquals("Papercut", cursorTrack.getString(0))
        assertEquals("Linkin Park", cursorTrack.getString(1))
        assertEquals(5, cursorTrack.getInt(2))
        cursorTrack.close()

        val cursorCustomRule = sqlDb.query("SELECT domain, name FROM custom_lyrics_rules WHERE id = 'rule_v10'")
        assertTrue("Pre-existing custom rule must be preserved", cursorCustomRule.moveToFirst())
        assertEquals("amalgama-lab.com", cursorCustomRule.getString(0))
        cursorCustomRule.close()

        sqlDb.close()
    }

    @Test
    fun testBlacklistDaoCrudAndFlow() = runBlocking {
        // 1. Initial count must be 0
        assertEquals(0, blacklistDao.countRules())
        assertEquals(0, blacklistDao.getAllRules().size)
        assertEquals(0, blacklistDao.getActiveRules().size)

        // 2. Insert two rules (one enabled, one disabled)
        val rule1 = BlacklistRuleEntity(
            id = "rule_title_spam",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "Реклама",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
            isEnabled = true,
            createdAt = 1000L
        )
        val rule2 = BlacklistRuleEntity(
            id = "rule_artist_off",
            ruleType = BlacklistRuleType.ARTIST,
            pattern = "Отключенный артист",
            matchMode = BlacklistMatchMode.EXACT,
            isEnabled = false,
            createdAt = 2000L
        )

        blacklistDao.insertRule(rule1)
        blacklistDao.insertRule(rule2)

        // 3. Verify countRules
        assertEquals(2, blacklistDao.countRules())

        // 4. Verify getRuleById
        val fetchedRule1 = blacklistDao.getRuleById("rule_title_spam")
        assertNotNull(fetchedRule1)
        assertEquals("Реклама", fetchedRule1!!.pattern)
        assertEquals(BlacklistRuleType.TITLE, fetchedRule1.ruleType)
        assertTrue(fetchedRule1.isEnabled)

        assertNull(blacklistDao.getRuleById("non_existent_id"))

        // 5. Verify getActiveRules (only enabled rule returned)
        val activeRules = blacklistDao.getActiveRules()
        assertEquals(1, activeRules.size)
        assertEquals("rule_title_spam", activeRules[0].id)

        // 6. Verify getAllRulesFlow (sorted by createdAt DESC -> rule2 then rule1)
        val flowRules = blacklistDao.getAllRulesFlow().first()
        assertEquals(2, flowRules.size)
        assertEquals("rule_artist_off", flowRules[0].id)
        assertEquals("rule_title_spam", flowRules[1].id)

        // 7. Verify updateRule
        val updatedRule2 = rule2.copy(isEnabled = true, pattern = "Включенный артист")
        blacklistDao.updateRule(updatedRule2)

        val activeRulesAfterUpdate = blacklistDao.getActiveRules()
        assertEquals(2, activeRulesAfterUpdate.size)
        val fetchedUpdated2 = blacklistDao.getRuleById("rule_artist_off")
        assertNotNull(fetchedUpdated2)
        assertTrue(fetchedUpdated2!!.isEnabled)
        assertEquals("Включенный артист", fetchedUpdated2.pattern)

        // 8. Verify deleteRule
        blacklistDao.deleteRule("rule_title_spam")
        assertEquals(1, blacklistDao.countRules())
        assertNull(blacklistDao.getRuleById("rule_title_spam"))
    }

    @Test
    fun testBlacklistPurgeEngine_previewAndCascadingDelete() = runBlocking {
        val purgeEngine = BlacklistPurgeEngine(db)

        // 1. Seed tracks
        val spamTrack1 = TrackEntity(
            trackKey = "spam_track_1",
            title = "Встроенная реклама в подкасте",
            artist = "Spam Media",
            sourcePackage = "org.telegram.messenger",
            playCount = 3
        )
        val spamTrack2 = TrackEntity(
            trackKey = "spam_track_2",
            title = "Реклама нового курса",
            artist = "Course Author",
            sourcePackage = "org.telegram.messenger",
            playCount = 1
        )
        val cleanTrack1 = TrackEntity(
            trackKey = "clean_track_1",
            title = "In The End",
            artist = "Linkin Park",
            sourcePackage = "com.spotify.music",
            playCount = 10
        )
        val cleanTrack2 = TrackEntity(
            trackKey = "clean_track_2",
            title = "Numb",
            artist = "Linkin Park",
            sourcePackage = "com.spotify.music",
            playCount = 15
        )

        musicDao.insertOrUpdateTrack(spamTrack1)
        musicDao.insertOrUpdateTrack(spamTrack2)
        musicDao.insertOrUpdateTrack(cleanTrack1)
        musicDao.insertOrUpdateTrack(cleanTrack2)

        // 2. Seed listening sessions
        // 2 sessions for spam_track_1
        musicDao.insertSession(ListeningSessionEntity(trackKey = "spam_track_1", sourcePackage = "org.telegram.messenger", startTimeMs = 1000L, endTimeMs = 2000L, durationMs = 1000L, isCompleted = true))
        musicDao.insertSession(ListeningSessionEntity(trackKey = "spam_track_1", sourcePackage = "org.telegram.messenger", startTimeMs = 3000L, endTimeMs = 4000L, durationMs = 1000L, isCompleted = true))
        // 1 session for spam_track_2
        musicDao.insertSession(ListeningSessionEntity(trackKey = "spam_track_2", sourcePackage = "org.telegram.messenger", startTimeMs = 5000L, endTimeMs = 6000L, durationMs = 1000L, isCompleted = true))
        // 2 sessions for clean_track_1
        musicDao.insertSession(ListeningSessionEntity(trackKey = "clean_track_1", sourcePackage = "com.spotify.music", startTimeMs = 7000L, endTimeMs = 8000L, durationMs = 1000L, isCompleted = true))
        musicDao.insertSession(ListeningSessionEntity(trackKey = "clean_track_1", sourcePackage = "com.spotify.music", startTimeMs = 8100L, endTimeMs = 8900L, durationMs = 800L, isCompleted = true))
        // 1 session for clean_track_2
        musicDao.insertSession(ListeningSessionEntity(trackKey = "clean_track_2", sourcePackage = "com.spotify.music", startTimeMs = 9000L, endTimeMs = 10000L, durationMs = 1000L, isCompleted = true))

        // Total 4 tracks and 6 sessions
        assertEquals(4, musicDao.getAllTracks().size)
        assertEquals(6, musicDao.getAllSessions().size)

        // 3. Define Rule: Filter title containing "реклама"
        val rule = BlacklistRuleEntity(
            id = "purge_rule_ad",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "реклама",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
            isEnabled = true
        )

        // 4. Test previewPurgeCount (must return 2 tracks without deleting anything)
        val previewCount = purgeEngine.previewPurgeCount(rule)
        assertEquals("Preview count should detect 2 matching spam tracks", 2, previewCount)
        // Ensure no DB changes happened during preview
        assertEquals("Tracks count must remain unchanged after preview", 4, musicDao.getAllTracks().size)
        assertEquals("Sessions count must remain unchanged after preview", 6, musicDao.getAllSessions().size)

        // 5. Execute cascading purge
        val report = purgeEngine.purgeMatchingTracks(rule)
        assertEquals("Report must show 2 deleted tracks", 2, report.deletedTracksCount)
        assertEquals("Report must show 3 deleted sessions", 3, report.deletedSessionsCount)

        // 6. Verify matching tracks and sessions are deleted from DB
        assertNull("Spam track 1 must be deleted", musicDao.getTrackByKey("spam_track_1"))
        assertNull("Spam track 2 must be deleted", musicDao.getTrackByKey("spam_track_2"))

        // 7. Verify non-matching tracks and sessions remain completely intact
        val remainingTracks = musicDao.getAllTracks()
        assertEquals(2, remainingTracks.size)
        assertNotNull("Clean track 1 must remain", musicDao.getTrackByKey("clean_track_1"))
        assertNotNull("Clean track 2 must remain", musicDao.getTrackByKey("clean_track_2"))

        val remainingSessions = musicDao.getAllSessions()
        assertEquals(3, remainingSessions.size)
        assertTrue("Remaining sessions belong only to clean tracks", remainingSessions.all { it.trackKey.startsWith("clean_track") })

        // 8. Test non-matching rule returns (0, 0)
        val nonMatchingRule = BlacklistRuleEntity(
            id = "rule_non_matching",
            ruleType = BlacklistRuleType.ARTIST,
            pattern = "Queen",
            matchMode = BlacklistMatchMode.EXACT,
            isEnabled = true
        )
        val previewZero = purgeEngine.previewPurgeCount(nonMatchingRule)
        assertEquals(0, previewZero)

        val reportZero = purgeEngine.purgeMatchingTracks(nonMatchingRule)
        assertEquals(0, reportZero.deletedTracksCount)
        assertEquals(0, reportZero.deletedSessionsCount)
        assertEquals(2, musicDao.getAllTracks().size)
        assertEquals(3, musicDao.getAllSessions().size)
    }

    @Test
    fun testSuggestionsQueries_returnExpectedDistinctOrderedValues() = runBlocking {
        musicDao.clearAllTracks()
        musicDao.clearAllSessions()

        // Insert tracks
        val track1 = TrackEntity(
            trackKey = "t1",
            title = "Track A",
            artist = "Artist Alpha",
            sourcePackage = "com.spotify.music",
            playCount = 10,
            lastPlayedAt = 1000L
        )
        val track2 = TrackEntity(
            trackKey = "t2",
            title = "Track B",
            artist = "Artist Beta",
            sourcePackage = "com.spotify.music",
            playCount = 50,
            lastPlayedAt = 500L
        )
        val track3 = TrackEntity(
            trackKey = "t3",
            title = "Track C",
            artist = "Artist Alpha",
            sourcePackage = "ru.yandex.music",
            playCount = 5,
            lastPlayedAt = 2000L
        )
        musicDao.insertOrUpdateTrack(track1)
        musicDao.insertOrUpdateTrack(track2)
        musicDao.insertOrUpdateTrack(track3)

        // Insert sessions
        musicDao.insertSession(ListeningSessionEntity(trackKey = "t1", sourcePackage = "com.spotify.music", startTimeMs = 100, endTimeMs = 200, durationMs = 100))
        musicDao.insertSession(ListeningSessionEntity(trackKey = "t2", sourcePackage = "ru.yandex.music", startTimeMs = 200, endTimeMs = 300, durationMs = 100))
        musicDao.insertSession(ListeningSessionEntity(trackKey = "t3", sourcePackage = "com.spotify.music", startTimeMs = 300, endTimeMs = 400, durationMs = 100))

        val packages = musicDao.getDistinctSourcePackages(10)
        assertEquals(2, packages.size)
        assertEquals("com.spotify.music", packages[0])
        assertEquals("ru.yandex.music", packages[1])

        val artists = musicDao.getTopArtists(10)
        assertEquals(2, artists.size)
        assertEquals("Artist Beta", artists[0])
        assertEquals("Artist Alpha", artists[1])

        val titles = musicDao.getRecentTrackTitles(10)
        assertEquals(3, titles.size)
        assertEquals("Track C", titles[0])
        assertEquals("Track A", titles[1])
        assertEquals("Track B", titles[2])
    }
}




