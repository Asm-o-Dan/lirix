package com.lirix.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.analytics.*
import com.lirix.app.feature.MusicFeatureEngine
import com.lirix.app.storage.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class MonotonicListeningTimeTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: MusicDao
    private lateinit var engine: MusicFeatureEngine
    private val base = 1_700_000_000_000L
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = db.musicDao(); engine = MusicFeatureEngine(dao, db.lyricsDao())
    }
    @After fun cleanup() = db.close()
    private suspend fun signal(at: Long, state: String = "PLAYING", title: String = "Song", pkg: String = "player") =
        engine.recordPlaybackSignal(title, "Artist", sourcePackage = pkg, playbackState = state,
            timestamp = base + at, monotonicMs = at)

    @Test fun eightMinuteStartAndBothSeekDirectionsNeverCreditUnheardAudio() = runBlocking {
        val track = signal(0)!!
        engine.updateActiveSessionProgress("player", 490_000, base + 10_000, 10_000)
        assertEquals(10_000L, dao.getTrackByKey(track.trackKey)!!.totalDurationMs)
        engine.updateActiveSessionProgress("player", 3_600_000, base + 20_000, 20_000)
        engine.updateActiveSessionProgress("player", 1_000, base + 30_000, 30_000)
        assertEquals(30_000L, dao.getLastSessionForPackage("player")!!.durationMs)
        assertEquals(30_000L, dao.getTrackByKey(track.trackKey)!!.totalDurationMs)
    }
    @Test fun pauseBufferingAndResumeExcludeTheirEntireGaps() = runBlocking {
        val track = signal(0)!!
        signal(10_000, "PAUSED"); signal(90_000); signal(100_000, "BUFFERING")
        signal(120_000); signal(130_000, "STOPPED")
        val session = dao.getLastSessionForPackage("player")!!
        assertEquals(30_000L, session.durationMs)
        assertTrue(session.isCompleted)
        assertEquals(3, ListeningIntervals.read(session).size)
        assertEquals(1, dao.getTrackByKey(track.trackKey)!!.playCount)
        assertEquals(30_000L, dao.getTrackByKey(track.trackKey)!!.totalDurationMs)
    }
    @Test fun discoveringPausedOrBufferingPlayersDoesNotCreateListeningHistory() = runBlocking {
        signal(0, "PAUSED")
        signal(10_000, "BUFFERING")
        assertTrue(dao.getAllSessions().isEmpty())
        assertEquals(0, dao.getAllTracks().single().playCount)
        signal(20_000)
        signal(30_000, "PAUSED")
        assertEquals(10_000L, dao.getAllSessions().single().durationMs)
    }
    @Test fun newEngineInstancesAndConcurrentDuplicateSignalsCannotDoubleCredit() = runBlocking {
        val track = signal(0)!!
        engine = MusicFeatureEngine(dao, db.lyricsDao())
        coroutineScope { repeat(20) { launch { signal(10_000) } } }
        engine.updateActiveSessionProgress("player", 999_999, base + 10_000, 10_000)
        assertEquals(10_000L, dao.getTrackByKey(track.trackKey)!!.totalDurationMs)
        assertEquals(1, dao.getAllSessions().size)
        assertEquals(1, dao.getTrackByKey(track.trackKey)!!.playCount)
    }
    @Test fun trackSwitchClosesAndCreditsPreviousTrack() = runBlocking {
        val a = signal(0, title = "A")!!
        signal(300_000, title = "B")
        assertEquals(300_000L, dao.getTrackByKey(a.trackKey)!!.totalDurationMs)
        assertTrue(dao.getAllSessions().first().isCompleted)
        assertEquals(0L, dao.getAllSessions().last().durationMs)
    }
    @Test fun latePauseAfterNewerHeartbeatStillStopsAccrual() = runBlocking {
        val track = signal(0)!!
        engine.updateActiveSessionProgress("player", timestamp = base + 10_000, monotonicMs = 10_000)
        signal(9_999, "PAUSED")
        signal(20_000)
        signal(30_000, "PAUSED")
        assertEquals(20_000L, dao.getTrackByKey(track.trackKey)!!.totalDurationMs)
    }
    @Test fun oldPauseCannotOverrideANewerConfirmedPlayingState() = runBlocking {
        val track = signal(0)!!
        signal(10_000)
        signal(9_000, "PAUSED")
        signal(20_000, "PAUSED")
        assertEquals(20_000L, dao.getTrackByKey(track.trackKey)!!.totalDurationMs)
    }
    @Test fun inactiveSourcesAndUnverifiedNotificationsCannotStealTheClock() = runBlocking {
        val a = signal(0, pkg = "a")!!
        val b = signal(10_000, pkg = "b", title = "Other")!!
        signal(15_000, "PAUSED", pkg = "a")
        signal(16_000, "NOTIFICATION", pkg = "notification", title = "Notification")
        engine.updateActiveSessionProgress("b", 800_000, base + 20_000, 20_000)
        assertEquals(10_000L, dao.getTrackByKey(a.trackKey)!!.totalDurationMs)
        assertEquals(10_000L, dao.getTrackByKey(b.trackKey)!!.totalDurationMs)
        assertEquals(2, dao.getAllSessions().size)
    }
    @Test fun processRestartDoesNotCreditTheUnobservedGap() = runBlocking {
        val track = signal(0)!!; signal(10_000, "PAUSED")
        // A new DAO identity models loss of process-local monotonic anchors, with history preserved.
        engine = MusicFeatureEngine(object : MusicDao by dao {}, db.lyricsDao())
        signal(3_500_000, "PAUSED")
        signal(3_600_000); signal(3_610_000, "PAUSED")
        assertEquals(20_000L, dao.getTrackByKey(track.trackKey)!!.totalDurationMs)
        assertEquals(listOf(20_000L), dao.getAllSessions().map { it.durationMs })
        assertEquals(1, dao.getTrackByKey(track.trackKey)!!.playCount)
    }
    @Test fun wallClockCorrectionReanchorsWithoutCreditingTheAmbiguousGap() = runBlocking {
        val track = signal(0)!!
        engine.updateActiveSessionProgress("player", timestamp = base + 3_600_000, monotonicMs = 10_000)
        engine.updateActiveSessionProgress("player", timestamp = base + 3_610_000, monotonicMs = 20_000)
        assertEquals(10_000L, dao.getTrackByKey(track.trackKey)!!.totalDurationMs)
    }
    @Test fun midnightAllocationCannotShowEightMinutesAtThreePastMidnight() = runBlocking {
        val midnight = ZonedDateTime.parse("2026-10-21T00:00:00+03:00[Europe/Moscow]").toInstant().toEpochMilli()
        val oldZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/Moscow"))
        try {
            engine.recordPlaybackSignal("Song", "Artist", sourcePackage = "player", timestamp = midnight - 10_000, monotonicMs = 0)
            engine.updateActiveSessionProgress("player", 480_000, midnight + 180_000, 190_000)
            val stats = WrappedStatsEngine.calculateStats(dao.getAllTracks(), dao.getAllSessions(), AnalyticsTimeframe.TODAY, midnight + 180_000)
            assertEquals(180_000L, stats.totalListeningTimeMs)
            assertEquals(180_000L, stats.topTracks.single().durationMs)
            assertEquals(190_000L, dao.getAllTracks().single().totalDurationMs)
        } finally { java.util.TimeZone.setDefault(oldZone) }
    }
}
