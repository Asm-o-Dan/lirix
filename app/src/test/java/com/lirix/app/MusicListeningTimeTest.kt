package com.lirix.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.analytics.AnalyticsTimeframe
import com.lirix.app.analytics.WrappedStatsEngine
import com.lirix.app.feature.MusicFeatureEngine
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.ListeningSessionEntity
import com.lirix.app.storage.MusicDao
import com.lirix.app.storage.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * TDD Unit-tests for dynamic listening time calculation and active session tracking (TEST-BUG-03 / TASK-BUG-03).
 * Verifies:
 * 1. testSessionClosingOnTrackSwitch:
 *    When switching tracks (isNewTrack == true), the previous track's session is not abandoned with duration = 0,
 *    but is closed with isCompleted = true, actual duration is calculated, and credited to previous track's totalDurationMs.
 * 2. testIncrementalProgressCommit:
 *    updateActiveSessionProgress commits progress to the active uncompleted session and credits delta to track.
 * 3. testWrappedStatsEngine_liveSessionDurationExtrapolation:
 *    calculateStats dynamically extrapolates active/uncompleted sessions using livePlayback position or elapsed wall time.
 */
@RunWith(AndroidJUnit4::class)
class MusicListeningTimeTest {

    private lateinit var db: AppDatabase
    private lateinit var musicDao: MusicDao
    private lateinit var engine: MusicFeatureEngine

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        musicDao = db.musicDao()
        engine = MusicFeatureEngine(musicDao, db.lyricsDao())
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testSessionClosingOnTrackSwitch() = runBlocking {
        val baseTime = 1_000_000L
        val pkg = "com.spotify.music"

        // 1. Play Track A (Starboy) at baseTime
        val trackA = engine.recordPlaybackSignal(
            title = "Starboy",
            artist = "The Weeknd",
            sourcePackage = pkg,
            playbackState = "PLAYING",
            timestamp = baseTime
        )
        assertNotNull(trackA)

        // Session for Track A created with durationMs = 0 at start
        val sessionAStart = musicDao.getLastSessionForPackage(pkg)
        assertNotNull(sessionAStart)
        assertEquals(trackA!!.trackKey, sessionAStart!!.trackKey)
        assertFalse(sessionAStart.isCompleted)

        // 2. 5 minutes later (300_000 ms), user/player switches to Track B (Numb)
        val switchTime = baseTime + 300_000L
        val trackB = engine.recordPlaybackSignal(
            title = "Numb",
            artist = "Linkin Park",
            sourcePackage = pkg,
            playbackState = "PLAYING",
            timestamp = switchTime
        )
        assertNotNull(trackB)

        // Verify Track A session was properly closed and credited
        val closedSessionA = musicDao.getSessionById(sessionAStart.id)
        assertNotNull("Session A must exist in DB", closedSessionA)
        assertTrue("Session A must be marked as completed on track switch", closedSessionA!!.isCompleted)
        assertEquals("Session A endTime must match switch timestamp", switchTime, closedSessionA.endTimeMs)
        assertEquals("Session A duration must be exactly 300_000ms", 300_000L, closedSessionA.durationMs)

        // Verify Track A totalDurationMs received the 300_000 ms credit
        val refreshedTrackA = musicDao.getTrackByKey(trackA.trackKey)
        assertNotNull(refreshedTrackA)
        assertEquals("Track A totalDurationMs must be credited with 300_000ms", 300_000L, refreshedTrackA!!.totalDurationMs)

        // Verify Track B has an active, new session
        val sessionB = musicDao.getLastSessionForPackage(pkg)
        assertNotNull(sessionB)
        assertEquals(trackB!!.trackKey, sessionB!!.trackKey)
        assertFalse("New session for Track B must be uncompleted", sessionB.isCompleted)
    }

    @Test
    fun testIncrementalProgressCommit() = runBlocking {
        val startTime = 2_000_000L
        val pkg = "com.spotify.music"

        // Start playing track
        val track = engine.recordPlaybackSignal(
            title = "Blinding Lights",
            artist = "The Weeknd",
            sourcePackage = pkg,
            playbackState = "PLAYING",
            timestamp = startTime
        )
        assertNotNull(track)

        val initialSession = musicDao.getLastSessionForPackage(pkg)
        assertNotNull(initialSession)
        assertEquals(0L, initialSession!!.durationMs)

        // Heartbeat tick at +15s (15_000ms position)
        engine.updateActiveSessionProgress(
            sourcePackage = pkg,
            currentPlaybackPositionMs = 15_000L,
            timestamp = startTime + 15_000L
        )

        val sessionAt15s = musicDao.getLastSessionForPackage(pkg)
        assertNotNull(sessionAt15s)
        assertEquals("Session ID must remain identical without duplicate sessions", initialSession.id, sessionAt15s!!.id)
        assertEquals(15_000L, sessionAt15s.durationMs)
        assertEquals(startTime + 15_000L, sessionAt15s.endTimeMs)

        val trackAt15s = musicDao.getTrackByKey(track!!.trackKey)
        assertNotNull(trackAt15s)
        assertEquals(15_000L, trackAt15s!!.totalDurationMs)

        // Heartbeat tick at +60s (60_000ms position)
        engine.updateActiveSessionProgress(
            sourcePackage = pkg,
            currentPlaybackPositionMs = 60_000L,
            timestamp = startTime + 60_000L
        )

        val sessionAt60s = musicDao.getLastSessionForPackage(pkg)
        assertNotNull(sessionAt60s)
        assertEquals(60_000L, sessionAt60s!!.durationMs)

        val trackAt60s = musicDao.getTrackByKey(track.trackKey)
        assertNotNull(trackAt60s)
        assertEquals("Total duration must reflect incremental progress (60_000ms)", 60_000L, trackAt60s!!.totalDurationMs)
    }

    @Test
    fun testWrappedStatsEngine_liveSessionDurationExtrapolation() {
        val now = 5_000_000L
        val fiveMinutesAgo = now - 300_000L // 5 minutes ago

        val track = TrackEntity(
            trackKey = "live_track",
            title = "In The End",
            artist = "Linkin Park",
            sourcePackage = "com.spotify.music"
        )

        // Session started 5 minutes ago, but uncompleted and stored with durationMs = 0 (or lagging)
        val activeSession = ListeningSessionEntity(
            id = 10,
            trackKey = "live_track",
            sourcePackage = "com.spotify.music",
            startTimeMs = fiveMinutesAgo,
            endTimeMs = now,
            durationMs = 0L,
            isCompleted = false
        )

        // Case 1: Extrapolation via livePlayback snapshot
        val liveSnapshot = LivePlaybackSnapshot(
            packageName = "com.spotify.music",
            title = "In The End",
            artist = "Linkin Park",
            isPlaying = true,
            basePositionMs = 300_000L,
            lastPositionUpdateTimeMs = 0L,
            timestamp = now
        )

        val statsWithLive = WrappedStatsEngine.calculateStats(
            tracks = listOf(track),
            sessions = listOf(activeSession),
            timeframe = AnalyticsTimeframe.TODAY,
            referenceTimestampMs = now,
            livePlayback = liveSnapshot
        )

        assertTrue(
            "totalListeningTimeMs must be at least 300_000ms (5 min) accounting for live playback",
            statsWithLive.totalListeningTimeMs >= 300_000L
        )

        // Case 2: Extrapolation via elapsed wall time when livePlayback is null but session is uncompleted and recent
        val statsWallTime = WrappedStatsEngine.calculateStats(
            tracks = listOf(track),
            sessions = listOf(activeSession),
            timeframe = AnalyticsTimeframe.TODAY,
            referenceTimestampMs = now,
            livePlayback = null
        )

        assertEquals(
            "Active session must extrapolate by elapsed wall time (5 minutes = 300_000ms)",
            300_000L,
            statsWallTime.totalListeningTimeMs
        )
    }
}
