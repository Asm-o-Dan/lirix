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
}
