package com.lirix.app.feature.blacklist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.ListeningSessionEntity
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.blacklist.BlacklistRuleType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BlacklistPurgeEngineTest {

    @Test
    fun test_find_matching_tracks_and_preview() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val purgeEngine = BlacklistPurgeEngine(db)

        db.musicDao().insertOrUpdateTrack(TrackEntity(trackKey = "t1", title = "Morning News", artist = "Host", sourcePackage = "com.radio"))
        db.musicDao().insertOrUpdateTrack(TrackEntity(trackKey = "t2", title = "Evening News", artist = "Host", sourcePackage = "com.radio"))
        db.musicDao().insertOrUpdateTrack(TrackEntity(trackKey = "t3", title = "Cool Rock Song", artist = "Rock Band", sourcePackage = "com.spotify"))

        val rule = BlacklistRuleEntity(
            id = "rule_title_news",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "News",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE
        )

        val count = purgeEngine.previewPurgeCount(rule)
        assertEquals(2, count)

        val keys = purgeEngine.findMatchingTrackKeys(rule)
        assertEquals(listOf("t1", "t2"), keys)

        val matches = purgeEngine.findMatchingTracks(rule)
        assertEquals(2, matches.size)
        assertEquals(listOf("t1", "t2"), matches.map { it.trackKey })

        db.close()
    }

    @Test
    fun test_purge_matching_tracks_cascade_and_empty_check() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val purgeEngine = BlacklistPurgeEngine(db)

        // Seed 3 tracks and 4 sessions
        db.musicDao().insertOrUpdateTrack(TrackEntity(trackKey = "t1", title = "Spam Track", artist = "Spammer", sourcePackage = "com.spam"))
        db.musicDao().insertOrUpdateTrack(TrackEntity(trackKey = "t2", title = "Another Spam", artist = "Spammer", sourcePackage = "com.spam"))
        db.musicDao().insertOrUpdateTrack(TrackEntity(trackKey = "t3", title = "Good Track", artist = "Good Artist", sourcePackage = "com.good"))

        db.musicDao().insertSession(ListeningSessionEntity(trackKey = "t1", sourcePackage = "com.spam", startTimeMs = 10, endTimeMs = 20, durationMs = 10))
        db.musicDao().insertSession(ListeningSessionEntity(trackKey = "t1", sourcePackage = "com.spam", startTimeMs = 30, endTimeMs = 40, durationMs = 10))
        db.musicDao().insertSession(ListeningSessionEntity(trackKey = "t2", sourcePackage = "com.spam", startTimeMs = 50, endTimeMs = 60, durationMs = 10))
        db.musicDao().insertSession(ListeningSessionEntity(trackKey = "t3", sourcePackage = "com.good", startTimeMs = 70, endTimeMs = 80, durationMs = 10))

        val rule = BlacklistRuleEntity(
            id = "rule_artist_spammer",
            ruleType = BlacklistRuleType.ARTIST,
            pattern = "Spammer",
            matchMode = BlacklistMatchMode.EXACT
        )

        val report = purgeEngine.purgeMatchingTracks(rule)
        assertEquals(2, report.deletedTracksCount)
        assertEquals(3, report.deletedSessionsCount)

        // Remaining tracks & sessions
        val remainingTracks = db.musicDao().getAllTracks()
        assertEquals(1, remainingTracks.size)
        assertEquals("t3", remainingTracks[0].trackKey)

        val remainingSessions = db.musicDao().getAllSessions()
        assertEquals(1, remainingSessions.size)
        assertEquals("t3", remainingSessions[0].trackKey)

        // Repeat purge should return 0, 0
        val secondReport = purgeEngine.purgeMatchingTracks(rule)
        assertEquals(0, secondReport.deletedTracksCount)
        assertEquals(0, secondReport.deletedSessionsCount)

        db.close()
    }

    @Test
    fun undo_restores_tracks_with_their_listening_history_and_leaves_other_data_alone() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val purgeEngine = BlacklistPurgeEngine(db)
        val dao = db.musicDao()

        dao.insertOrUpdateTrack(TrackEntity(trackKey = "t1", title = "Spam Track", artist = "Spammer", sourcePackage = "com.spam", isFavorite = true))
        dao.insertOrUpdateTrack(TrackEntity(trackKey = "t2", title = "Good Track", artist = "Good Artist", sourcePackage = "com.good"))
        dao.insertSession(ListeningSessionEntity(trackKey = "t1", sourcePackage = "com.spam", startTimeMs = 10, endTimeMs = 25, durationMs = 15))
        dao.insertSession(ListeningSessionEntity(trackKey = "t2", sourcePackage = "com.good", startTimeMs = 30, endTimeMs = 40, durationMs = 10))
        dao.insertSession(ListeningSessionEntity(trackKey = "t1", sourcePackage = "com.spam", startTimeMs = 50, endTimeMs = 90, durationMs = 40))
        val tracksBefore = dao.getAllTracks().sortedBy { it.trackKey }
        val sessionsBefore = dao.getAllSessions()

        val rule = BlacklistRuleEntity(id = "r", ruleType = BlacklistRuleType.ARTIST, pattern = "Spammer", matchMode = BlacklistMatchMode.EXACT)
        val (report, backup) = purgeEngine.purgeMatchingTracksWithBackup(rule)
        assertEquals(1, report.deletedTracksCount)
        assertEquals(2, report.deletedSessionsCount)
        assertEquals(listOf("t2"), dao.getAllSessions().map { it.trackKey })

        // Playback goes on between the purge and the undo.
        dao.insertSession(ListeningSessionEntity(trackKey = "t2", sourcePackage = "com.good", startTimeMs = 100, endTimeMs = 130, durationMs = 30))

        purgeEngine.restore(backup)

        assertEquals(tracksBefore, dao.getAllTracks().sortedBy { it.trackKey })
        val sessionsAfter = dao.getAllSessions()
        assertEquals("Every removed session is back, unchanged", sessionsBefore, sessionsAfter.filter { it.startTimeMs < 100 })
        assertEquals("The session recorded meanwhile is kept", 1, sessionsAfter.count { it.startTimeMs == 100L })
        assertEquals(4, sessionsAfter.size)

        // A second undo of the same backup changes nothing.
        purgeEngine.restore(backup)
        assertEquals(4, dao.getAllSessions().size)
        db.close()
    }

    @Test
    fun undo_does_not_overwrite_a_track_recorded_again_after_the_purge() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val purgeEngine = BlacklistPurgeEngine(db)
        val dao = db.musicDao()
        dao.insertOrUpdateTrack(TrackEntity(trackKey = "t1", title = "Spam Track", artist = "Spammer", sourcePackage = "com.spam"))

        val rule = BlacklistRuleEntity(id = "r", ruleType = BlacklistRuleType.ARTIST, pattern = "Spammer", matchMode = BlacklistMatchMode.EXACT)
        val (_, backup) = purgeEngine.purgeMatchingTracksWithBackup(rule)
        dao.insertOrUpdateTrack(TrackEntity(trackKey = "t1", title = "Spam Track", artist = "Spammer", sourcePackage = "com.spam", plainLyrics = "typed by the user after the purge"))

        purgeEngine.restore(backup)

        assertEquals("typed by the user after the purge", dao.getAllTracks().single().plainLyrics)
        db.close()
    }

    @Test
    fun purge_without_matches_reports_nothing_and_keeps_everything() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val purgeEngine = BlacklistPurgeEngine(db)
        db.musicDao().insertOrUpdateTrack(TrackEntity(trackKey = "t1", title = "Good Track", artist = "Good Artist", sourcePackage = "com.good"))

        val rule = BlacklistRuleEntity(id = "r", ruleType = BlacklistRuleType.ARTIST, pattern = "Spammer", matchMode = BlacklistMatchMode.EXACT)
        val (report, backup) = purgeEngine.purgeMatchingTracksWithBackup(rule)

        assertEquals(PurgeReport(0, 0), report)
        assertEquals(PurgeBackup(emptyList(), emptyList()), backup)
        assertEquals(1, db.musicDao().getAllTracks().size)
        db.close()
    }
}
