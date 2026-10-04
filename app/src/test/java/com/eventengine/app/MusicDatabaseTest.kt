package com.eventengine.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventengine.app.storage.AchievementDao
import com.eventengine.app.storage.AchievementEntity
import com.eventengine.app.storage.AppDatabase
import com.eventengine.app.storage.ListeningSessionEntity
import com.eventengine.app.storage.LyricsCacheEntity
import com.eventengine.app.storage.LyricsDao
import com.eventengine.app.storage.MusicDao
import com.eventengine.app.storage.TrackEntity
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

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        lyricsDao = db.lyricsDao()
        achievementDao = db.achievementDao()
        musicDao = db.musicDao()
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
        val engine = com.eventengine.app.feature.MusicFeatureEngine(musicDao)
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
        val engine = com.eventengine.app.feature.MusicFeatureEngine(musicDao)
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
        assertTrue("Session must be marked completed on PAUSED", pausedSession.isCompleted)
    }

    // ------------------------------------------------------------------------
    // TASK-BUG-02: Lyrics Persistence & Dual-Write Verification
    // ------------------------------------------------------------------------

    @Test
    fun testRecordPlaybackSignal_preservesExistingLyrics_andHydratesFromCache() = runBlocking {
        val engine = com.eventengine.app.feature.MusicFeatureEngine(musicDao)
        val trackKey = com.eventengine.app.feature.MusicFeatureEngine.computeTrackKey("Numb", "Linkin Park", "Meteora")

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
        val trackKey = com.eventengine.app.feature.MusicFeatureEngine.computeTrackKey("Starboy", "The Weeknd", "Starboy")

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
        val engine = com.eventengine.app.feature.MusicFeatureEngine(musicDao)
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
        val engine = com.eventengine.app.feature.MusicFeatureEngine(musicDao)
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
        val engine = com.eventengine.app.feature.MusicFeatureEngine(musicDao)
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
}



