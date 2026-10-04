package com.eventengine.app

import com.eventengine.app.analytics.AnalyticsTimeframe
import com.eventengine.app.analytics.DayOfWeekActivity
import com.eventengine.app.analytics.ListeningArchetype
import com.eventengine.app.analytics.TimeOfDaySlot
import com.eventengine.app.analytics.TopObsessionItem
import com.eventengine.app.analytics.WrappedStats
import com.eventengine.app.analytics.WrappedStatsEngine
import com.eventengine.app.ingestion.LivePlaybackSnapshot
import com.eventengine.app.storage.ListeningSessionEntity
import com.eventengine.app.storage.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * TDD Unit-tests for WrappedStatsEngine (TASK-UI-03).
 * Validates timeframe filtering (TODAY, WEEK, MONTH, ALL_TIME),
 * top obsession determination, weekly compass, time of day distribution,
 * and listening archetype classification.
 */
class WrappedStatsEngineTest {

    private fun createTimestampAt(year: Int = 2026, month: Int = 9, day: Int = 2, hour: Int = 12, minute: Int = 0): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun startOfDayMs(refTimeMs: Long): Long {
        return Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = refTimeMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun test_calculate_stats_empty_history_returnsZeroDefaults() {
        val stats = WrappedStatsEngine.calculateStats(emptyList(), emptyList())
        assertEquals(0L, stats.totalListeningTimeMs)
        assertEquals(0, stats.uniqueTracksCount)
        assertEquals(0, stats.uniqueArtistsCount)
        assertTrue(stats.topArtists.isEmpty())
        assertTrue(stats.topTracks.isEmpty())
        assertNull("Top obsession must be null when history is empty", stats.topObsession)
        assertEquals(ListeningArchetype.CASUAL_LISTENER, stats.archetype)
        assertEquals(7, stats.weeklyActivity.size)
    }

    @Test
    fun test_timeframe_filtering_today_week_month_allTime() {
        val refTime = createTimestampAt(year = 2026, month = Calendar.OCTOBER, day = 2, hour = 15, minute = 30)
        val todayMidnight = startOfDayMs(refTime)

        val track1 = TrackEntity(trackKey = "t1", title = "Song Today", artist = "Artist A", sourcePackage = "com.spotify.music")
        val track2 = TrackEntity(trackKey = "t2", title = "Song 3 Days Ago", artist = "Artist B", sourcePackage = "com.spotify.music")
        val track3 = TrackEntity(trackKey = "t3", title = "Song 15 Days Ago", artist = "Artist C", sourcePackage = "com.spotify.music")
        val track4 = TrackEntity(trackKey = "t4", title = "Song 45 Days Ago", artist = "Artist D", sourcePackage = "com.spotify.music")
        val allTracks = listOf(track1, track2, track3, track4)

        val sessionToday = ListeningSessionEntity(id = 1, trackKey = "t1", sourcePackage = "com.spotify.music", startTimeMs = todayMidnight + 3600000L, endTimeMs = todayMidnight + 7200000L, durationMs = 3600000L)
        val session3DaysAgo = ListeningSessionEntity(id = 2, trackKey = "t2", sourcePackage = "com.spotify.music", startTimeMs = refTime - 3 * 86_400_000L, endTimeMs = refTime - 3 * 86_400_000L + 1800000L, durationMs = 1800000L)
        val session15DaysAgo = ListeningSessionEntity(id = 3, trackKey = "t3", sourcePackage = "com.spotify.music", startTimeMs = refTime - 15 * 86_400_000L, endTimeMs = refTime - 15 * 86_400_000L + 1200000L, durationMs = 1200000L)
        val session45DaysAgo = ListeningSessionEntity(id = 4, trackKey = "t4", sourcePackage = "com.spotify.music", startTimeMs = refTime - 45 * 86_400_000L, endTimeMs = refTime - 45 * 86_400_000L + 600000L, durationMs = 600000L)
        val allSessions = listOf(sessionToday, session3DaysAgo, session15DaysAgo, session45DaysAgo)

        // 1. TODAY: only sessionToday
        val statsToday = WrappedStatsEngine.calculateStats(allTracks, allSessions, timeframe = AnalyticsTimeframe.TODAY, referenceTimestampMs = refTime)
        assertEquals(3600000L, statsToday.totalListeningTimeMs)
        assertEquals(1, statsToday.uniqueTracksCount)
        assertEquals("Song Today", statsToday.topTracks.first().title)

        // 2. WEEK: sessionToday + session3DaysAgo
        val statsWeek = WrappedStatsEngine.calculateStats(allTracks, allSessions, timeframe = AnalyticsTimeframe.WEEK, referenceTimestampMs = refTime)
        assertEquals(3600000L + 1800000L, statsWeek.totalListeningTimeMs)
        assertEquals(2, statsWeek.uniqueTracksCount)

        // 3. MONTH: sessionToday + session3DaysAgo + session15DaysAgo
        val statsMonth = WrappedStatsEngine.calculateStats(allTracks, allSessions, timeframe = AnalyticsTimeframe.MONTH, referenceTimestampMs = refTime)
        assertEquals(3600000L + 1800000L + 1200000L, statsMonth.totalListeningTimeMs)
        assertEquals(3, statsMonth.uniqueTracksCount)

        // 4. ALL_TIME: all 4 sessions
        val statsAll = WrappedStatsEngine.calculateStats(allTracks, allSessions, timeframe = AnalyticsTimeframe.ALL_TIME, referenceTimestampMs = refTime)
        assertEquals(3600000L + 1800000L + 1200000L + 600000L, statsAll.totalListeningTimeMs)
        assertEquals(4, statsAll.uniqueTracksCount)
    }

    @Test
    fun test_timeframe_allTime_fallback_to_track_aggregates_when_sessions_empty() {
        val track1 = TrackEntity(
            trackKey = "t1",
            title = "Starboy",
            artist = "The Weeknd",
            sourcePackage = "com.spotify.music",
            playCount = 10,
            totalDurationMs = 1800000L
        )
        val track2 = TrackEntity(
            trackKey = "t2",
            title = "Numb",
            artist = "Linkin Park",
            sourcePackage = "ru.yandex.music",
            playCount = 5,
            totalDurationMs = 900000L
        )

        val stats = WrappedStatsEngine.calculateStats(listOf(track1, track2), emptyList(), timeframe = AnalyticsTimeframe.ALL_TIME)
        assertEquals(2700000L, stats.totalListeningTimeMs)
        assertEquals(2, stats.uniqueTracksCount)
        assertEquals(2, stats.uniqueArtistsCount)
        assertEquals("Starboy", stats.topTracks[0].title)
        assertEquals(10, stats.topTracks[0].playCount)
    }

    @Test
    fun test_top_obsession_threshold_three_plays() {
        val refTime = createTimestampAt(hour = 16)
        val trackObsessed = TrackEntity(
            trackKey = "obsession_key",
            title = "After Dark",
            artist = "Mr.Kitty",
            sourcePackage = "com.spotify.music",
            albumArtUri = "file:///art/after_dark.png"
        )
        val trackNormal = TrackEntity(
            trackKey = "normal_key",
            title = "Normal Song",
            artist = "Normal Artist",
            sourcePackage = "com.spotify.music"
        )

        // Case A: 2 plays in period -> topObsession should be null (< 3 threshold)
        val sessionsTwoPlays = listOf(
            ListeningSessionEntity(id = 1, trackKey = "obsession_key", sourcePackage = "com.spotify.music", startTimeMs = refTime - 100000, endTimeMs = refTime - 50000, durationMs = 50000, isCompleted = true),
            ListeningSessionEntity(id = 2, trackKey = "obsession_key", sourcePackage = "com.spotify.music", startTimeMs = refTime - 40000, endTimeMs = refTime - 10000, durationMs = 30000, isCompleted = true),
            ListeningSessionEntity(id = 3, trackKey = "normal_key", sourcePackage = "com.spotify.music", startTimeMs = refTime - 8000, endTimeMs = refTime - 2000, durationMs = 6000, isCompleted = true)
        )
        val statsTwo = WrappedStatsEngine.calculateStats(listOf(trackObsessed, trackNormal), sessionsTwoPlays, referenceTimestampMs = refTime)
        assertNull("topObsession must be null when playCountInPeriod < 3", statsTwo.topObsession)

        // Case B: 3 plays in period -> topObsession should be detected
        val sessionsThreePlays = sessionsTwoPlays + ListeningSessionEntity(
            id = 4, trackKey = "obsession_key", sourcePackage = "com.spotify.music", startTimeMs = refTime - 1000, endTimeMs = refTime, durationMs = 1000, isCompleted = true
        )
        val statsThree = WrappedStatsEngine.calculateStats(listOf(trackObsessed, trackNormal), sessionsThreePlays, referenceTimestampMs = refTime)
        assertNotNull("topObsession must not be null when playCountInPeriod >= 3", statsThree.topObsession)
        val obsession = statsThree.topObsession!!
        assertEquals("obsession_key", obsession.trackKey)
        assertEquals("After Dark", obsession.title)
        assertEquals("Mr.Kitty", obsession.artist)
        assertEquals("file:///art/after_dark.png", obsession.albumArtUri)
        assertEquals(3, obsession.playCountInPeriod)
        assertEquals(81000L, obsession.durationMsInPeriod)
    }

    @Test
    fun test_weekly_activity_compass_seven_days() {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        // Find a known Monday
        calendar.set(2026, Calendar.SEPTEMBER, 28, 12, 0, 0) // Mon Sep 28, 2026
        val mondayTime = calendar.timeInMillis

        val sessions = listOf(
            // Monday: 2 sessions
            ListeningSessionEntity(id = 1, trackKey = "t1", sourcePackage = "app", startTimeMs = mondayTime, endTimeMs = mondayTime + 60000L, durationMs = 60000L),
            ListeningSessionEntity(id = 2, trackKey = "t1", sourcePackage = "app", startTimeMs = mondayTime + 100000L, endTimeMs = mondayTime + 160000L, durationMs = 60000L),
            // Wednesday: 1 session
            ListeningSessionEntity(id = 3, trackKey = "t2", sourcePackage = "app", startTimeMs = mondayTime + 2 * 86_400_000L, endTimeMs = mondayTime + 2 * 86_400_000L + 120000L, durationMs = 120000L)
        )

        val stats = WrappedStatsEngine.calculateStats(emptyList(), sessions, timeframe = AnalyticsTimeframe.ALL_TIME)
        val weekly = stats.weeklyActivity

        assertEquals(7, weekly.size)
        val expectedDayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        assertEquals(expectedDayNames, weekly.map { it.dayName })

        val mondayActivity = weekly.find { it.dayName == "Пн" }!!
        assertEquals(2, mondayActivity.sessionCount)
        assertEquals(120000L, mondayActivity.totalDurationMs)

        val wednesdayActivity = weekly.find { it.dayName == "Ср" }!!
        assertEquals(1, wednesdayActivity.sessionCount)
        assertEquals(120000L, wednesdayActivity.totalDurationMs)

        val tuesdayActivity = weekly.find { it.dayName == "Вт" }!!
        assertEquals(0, tuesdayActivity.sessionCount)
        assertEquals(0L, tuesdayActivity.totalDurationMs)
    }

    @Test
    fun test_time_of_day_distribution_and_archetypes() {
        // Night session: 03:00 (NIGHT)
        val nightTime = createTimestampAt(hour = 3)
        // Day session: 14:00 (AFTERNOON)
        val dayTime = createTimestampAt(hour = 14)

        val trackNight = TrackEntity(trackKey = "k_night", title = "Night Track", artist = "Artist N", sourcePackage = "app")
        val trackDay = TrackEntity(trackKey = "k_day", title = "Day Track", artist = "Artist D", sourcePackage = "app")

        val nightSessions = (1..4).map { i ->
            ListeningSessionEntity(id = i.toLong(), trackKey = "k_night", sourcePackage = "app", startTimeMs = nightTime + i * 1000, endTimeMs = nightTime + i * 1000 + 30000, durationMs = 30000)
        }
        val daySessions = (5..6).map { i ->
            ListeningSessionEntity(id = i.toLong(), trackKey = "k_day", sourcePackage = "app", startTimeMs = dayTime + i * 1000, endTimeMs = dayTime + i * 1000 + 30000, durationMs = 30000)
        }

        // Total 6 sessions: 4 night (~66.7%), 2 day (~33.3%)
        val stats = WrappedStatsEngine.calculateStats(listOf(trackNight, trackDay), nightSessions + daySessions)
        val distribution = stats.timeOfDayDistribution

        assertEquals(4f / 6f, distribution[TimeOfDaySlot.NIGHT] ?: 0f, 0.01f)
        assertEquals(2f / 6f, distribution[TimeOfDaySlot.AFTERNOON] ?: 0f, 0.01f)
        assertEquals(ListeningArchetype.NIGHT_OWL, stats.archetype)
    }

    @Test
    fun test_lyrics_and_repeat_ratios_calculation() {
        val trackWithLyrics = TrackEntity(
            trackKey = "t_lyrics",
            title = "Sing Along",
            artist = "Pop Artist",
            sourcePackage = "com.spotify.music",
            syncedLyrics = "[00:01.00]Hello world",
            playCount = 4
        )
        val trackPlain = TrackEntity(
            trackKey = "t_plain",
            title = "Instrumental",
            artist = "Piano Artist",
            sourcePackage = "com.spotify.music",
            playCount = 2
        )

        val sessions = listOf(
            ListeningSessionEntity(id = 1, trackKey = "t_lyrics", sourcePackage = "app", startTimeMs = 1000, endTimeMs = 2000, durationMs = 1000),
            ListeningSessionEntity(id = 2, trackKey = "t_plain", sourcePackage = "app", startTimeMs = 3000, endTimeMs = 4000, durationMs = 1000)
        )

        val stats = WrappedStatsEngine.calculateStats(listOf(trackWithLyrics, trackPlain), sessions)
        // 1 of 2 tracks has lyrics -> 0.5f coverage
        assertEquals(0.5f, stats.lyricsCoverageRatio, 0.01f)
        // 2 tracks, repeat ratio depends on playCount or session count
        assertTrue(stats.repeatRatio >= 1.0f)
    }

    @Test
    fun testCalculateStats_withManyUnfinishedOldSessions_doesNotInflateTime() {
        val refTime = createTimestampAt(2026, 9, 2, 14, 0) // 14:00:00
        val liveTrackKey = "active_track_key"
        val liveTrack = TrackEntity(
            trackKey = liveTrackKey,
            title = "Starboy",
            artist = "The Weeknd",
            sourcePackage = "com.spotify.music",
            totalDurationMs = 230_000L
        )

        // 100 old unfinished sessions from earlier in the day (e.g. 3 hours ago)
        val threeHoursAgo = refTime - (3 * 3600 * 1000L)
        val oldSessions = (1..100).map { i ->
            ListeningSessionEntity(
                id = i.toLong(),
                trackKey = "old_track_$i",
                sourcePackage = "com.spotify.music",
                startTimeMs = threeHoursAgo + (i * 1000L),
                endTimeMs = threeHoursAgo + (i * 1000L) + 1000L,
                durationMs = 0L,
                isCompleted = false
            )
        }

        // 1 truly active session for the currently playing track (started 2 minutes ago = 120_000 ms)
        val activeSession = ListeningSessionEntity(
            id = 101L,
            trackKey = liveTrackKey,
            sourcePackage = "com.spotify.music",
            startTimeMs = refTime - 120_000L,
            endTimeMs = refTime,
            durationMs = 0L,
            isCompleted = false
        )

        val liveSnapshot = LivePlaybackSnapshot(
            packageName = "com.spotify.music",
            title = "Starboy",
            artist = "The Weeknd",
            isPlaying = true,
            basePositionMs = 120_000L,
            lastPositionUpdateTimeMs = 0L,
            timestamp = refTime,
            durationMs = 230_000L
        )

        val allSessions = oldSessions + activeSession
        val stats = WrappedStatsEngine.calculateStats(
            tracks = listOf(liveTrack),
            sessions = allSessions,
            timeframe = AnalyticsTimeframe.TODAY,
            referenceTimestampMs = refTime,
            livePlayback = liveSnapshot
        )

        // With strict active session isolation, the 100 old sessions contribute 0 ms,
        // and ONLY the 1 active session is extrapolated (approx 120_000 ms = 2 minutes).
        // Without isolation, 100 sessions * 3-4 hours = 300-400 hours!
        assertTrue(
            "totalListeningTimeMs must be reasonably small (< 15 minutes), but was ${stats.totalListeningTimeMs} ms",
            stats.totalListeningTimeMs < 15 * 60 * 1000L
        )
        assertTrue(
            "totalListeningTimeMs must account for the active track progress (>= 120_000 ms), but was ${stats.totalListeningTimeMs} ms",
            stats.totalListeningTimeMs >= 120_000L
        )
    }

    @Test
    fun testCalculateStats_todayTimeframe_doesNotExceed24Hours() {
        val refTime = createTimestampAt(2026, 9, 2, 20, 0)
        val track = TrackEntity(
            trackKey = "t1",
            title = "Long Day",
            artist = "Artist",
            sourcePackage = "com.spotify.music",
            totalDurationMs = 100_000_000L
        )

        // Multiple sessions whose sum nominally exceeds 24 hours (e.g. corrupted/buggy input)
        val massiveSessions = listOf(
            ListeningSessionEntity(id = 1, trackKey = "t1", sourcePackage = "com.spotify.music", startTimeMs = refTime - 50_000_000L, endTimeMs = refTime - 10_000_000L, durationMs = 50_000_000L, isCompleted = true),
            ListeningSessionEntity(id = 2, trackKey = "t1", sourcePackage = "com.spotify.music", startTimeMs = refTime - 60_000_000L, endTimeMs = refTime - 20_000_000L, durationMs = 50_000_000L, isCompleted = true)
        )

        val stats = WrappedStatsEngine.calculateStats(
            tracks = listOf(track),
            sessions = massiveSessions,
            timeframe = AnalyticsTimeframe.TODAY,
            referenceTimestampMs = refTime
        )

        // In TODAY timeframe, totalListeningTimeMs must never exceed 24 hours (86_400_000 ms)
        val maxTodayMs = 24 * 3600 * 1000L
        assertTrue(
            "TODAY totalListeningTimeMs ($stats.totalListeningTimeMs) must not exceed 24 hours ($maxTodayMs)",
            stats.totalListeningTimeMs <= maxTodayMs
        )
    }
}

