package com.lirix.app

import com.lirix.app.analytics.*
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.storage.*
import java.time.ZonedDateTime
import java.util.TimeZone
import kotlin.test.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ListeningIntervalsTest {
    private fun instant(text: String) = ZonedDateTime.parse(text).toInstant().toEpochMilli()
    private fun track(key: String = "key") = TrackEntity(key, key, "Artist", sourcePackage = "player", totalDurationMs = 1)
    private fun session(spans: List<ListeningInterval>, key: String = "key", id: Long = 1) = ListeningSessionEntity(
        id, key, "player", spans.first().startMs, spans.last().endMs, spans.sumOf { it.durationMs },
        listeningIntervalsJson = ListeningIntervals.encode(spans))
    private fun inZone(zone: String, body: () -> Unit) {
        val previous = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        try { body() } finally { TimeZone.setDefault(previous) }
    }

    @Test fun livePositionAndPausedWallTimeNeverExpandConfirmedHistory() {
        val row = session(listOf(ListeningInterval(1_000, 11_000)))
        val live = LivePlaybackSnapshot("player", "key", "Artist", isPlaying = true,
            basePositionMs = 3_600_000, lastPositionUpdateTimeMs = 0)
        val stats = WrappedStatsEngine.calculateStats(listOf(track()), listOf(row), referenceTimestampMs = 60_000, livePlayback = live)
        assertEquals(10_000L, stats.totalListeningTimeMs)
        assertEquals(10_000L, stats.topTracks.single().durationMs)
        assertEquals(10_000L, stats.topArtists.single().durationMs)
        assertEquals(10_000L, stats.weeklyActivity.sumOf { it.totalDurationMs })
    }

    @Test fun midnightClipsBothPlayingSpansAndExcludesAPause() = inZone("Europe/Moscow") {
        val midnight = instant("2026-10-21T00:00:00+03:00[Europe/Moscow]")
        val row = session(listOf(ListeningInterval(midnight - 60_000, midnight + 10_000),
            ListeningInterval(midnight + 120_000, midnight + 180_000)))
        val stats = WrappedStatsEngine.calculateStats(listOf(track()), listOf(row), AnalyticsTimeframe.TODAY, midnight + 180_000)
        assertEquals(70_000L, stats.totalListeningTimeMs)
        assertEquals(70_000L, stats.weeklyActivity.sumOf { it.totalDurationMs })
        assertEquals(130_000L, row.durationMs)
    }

    @Test fun legacyEightMinutesAtThreePastMidnightAreBoundedWithoutRewritingHistory() = inZone("Europe/Moscow") {
        val midnight = instant("2026-10-21T00:00:00+03:00[Europe/Moscow]")
        val legacy = ListeningSessionEntity(1, "key", "player", midnight, midnight + 180_000, 480_000)
        val stats = WrappedStatsEngine.calculateStats(listOf(track()), listOf(legacy), AnalyticsTimeframe.TODAY, midnight + 180_000)
        assertEquals(180_000L, stats.totalListeningTimeMs)
        assertEquals(480_000L, legacy.durationMs)
        assertEquals("", legacy.listeningIntervalsJson)
    }

    @Test fun timezoneChangesRebucketTheSamePersistedInstants() {
        val end = instant("2026-10-21T00:30:00+03:00[Europe/Moscow]")
        val row = session(listOf(ListeningInterval(end - 3_600_000, end)))
        inZone("Europe/Moscow") {
            assertEquals(1_800_000L, WrappedStatsEngine.calculateStats(listOf(track()), listOf(row), AnalyticsTimeframe.TODAY, end).totalListeningTimeMs)
        }
        inZone("UTC") {
            assertEquals(3_600_000L, WrappedStatsEngine.calculateStats(listOf(track()), listOf(row), AnalyticsTimeframe.TODAY, end).totalListeningTimeMs)
        }
    }

    @Test fun daylightSavingDaysUseRealCalendarBoundaries() = inZone("America/New_York") {
        for ((start, end, hours) in listOf(
            Triple("2026-03-08T00:00:00-05:00[America/New_York]", "2026-03-09T00:00:00-04:00[America/New_York]", 23L),
            Triple("2026-11-01T00:00:00-04:00[America/New_York]", "2026-11-02T00:00:00-05:00[America/New_York]", 25L))) {
            val row = session(listOf(ListeningInterval(instant(start), instant(end))))
            assertEquals(hours * 3_600_000, ListeningIntervals.dailyDurations(listOf(row)).values.single())
            assertEquals(hours * 3_600_000 - 1, WrappedStatsEngine.calculateStats(listOf(track()), listOf(row),
                AnalyticsTimeframe.TODAY, instant(end) - 1).totalListeningTimeMs)
        }
    }

    @Test fun overlappingSourcesAreAllocatedOnceForTotalsTopsAndCharts() {
        val a = session(listOf(ListeningInterval(1_000, 11_000)), id = 1)
        val b = session(listOf(ListeningInterval(6_000, 16_000)), "other", 2)
        val stats = WrappedStatsEngine.calculateStats(listOf(track(), track("other")), listOf(a, b), referenceTimestampMs = 20_000)
        assertEquals(15_000L, stats.totalListeningTimeMs)
        assertEquals(15_000L, stats.topTracks.sumOf { it.durationMs })
        assertEquals(15_000L, stats.weeklyActivity.sumOf { it.totalDurationMs })
    }

    @Test fun skippedAndRepeatedLocalMidnightsAreHandled() {
        for ((zone, start, end) in listOf(
            Triple("America/Sao_Paulo", "2018-11-04T01:00:00-02:00[America/Sao_Paulo]", "2018-11-05T00:00:00-02:00[America/Sao_Paulo]"),
            Triple("America/Havana", "2026-11-01T00:00:00-04:00[America/Havana]", "2026-11-02T00:00:00-05:00[America/Havana]"))) {
            inZone(zone) {
                val row = session(listOf(ListeningInterval(instant(start), instant(end))))
                assertEquals(instant(end) - instant(start), ListeningIntervals.dailyDurations(listOf(row)).values.single())
                assertEquals(instant(start), calendarPeriodTick(instant(start) + 1_000).dayStartMs)
                assertEquals(instant(end) - instant(start) - 1, WrappedStatsEngine.calculateStats(listOf(track()), listOf(row),
                    AnalyticsTimeframe.TODAY, instant(end) - 1).totalListeningTimeMs)
            }
        }
    }

    @Test fun archivesPreserveIntervalsAndAcceptLegacyVersionOneChecksums() {
        val legacy = ListeningSessionEntity(1, "key", "player", 1_000, 11_000, 10_000)
        val oldShape = LibraryArchives.encode(LibrarySnapshot(tracks = listOf(track()), sessions = listOf(legacy)), "test")
        assertFalse(oldShape.contains("listeningIntervalsJson"))
        val versionOne = oldShape.replace("\"version\":2", "\"version\":1")
        assertEquals(legacy, LibraryArchives.decode(versionOne).data.sessions.single())
        val current = session(listOf(ListeningInterval(1_000, 3_000), ListeningInterval(9_000, 11_000)))
        assertEquals(current, LibraryArchives.decode(LibraryArchives.encode(
            LibrarySnapshot(tracks = listOf(track()), sessions = listOf(current)), "test")).data.sessions.single())
    }

    @Test fun midnightAchievementsAndStreaksUsePlayingSpansRatherThanPauseGaps() = inZone("Europe/Moscow") {
        val midnight = instant("2026-10-21T00:00:00+03:00[Europe/Moscow]")
        val achievements = listOf(
            AchievementEntity("SECRET_MIDNIGHT", "Midnight", "", "", maxProgress = 1),
            AchievementEntity("STREAK_7", "Streak", "", "", maxProgress = 7))
        val across = session(listOf(ListeningInterval(midnight - 10_000, midnight + 10_000)))
        val result = GamificationEngine.checkAchievements(achievements, listOf(track()), listOf(across))
        assertTrue(result.first { it.id == "SECRET_MIDNIGHT" }.isUnlocked)
        assertEquals(2, result.first { it.id == "STREAK_7" }.currentProgress)
        val paused = session(listOf(ListeningInterval(midnight - 10_000, midnight - 5_000),
            ListeningInterval(midnight + 600_000, midnight + 610_000)))
        assertFalse(GamificationEngine.checkAchievements(achievements, listOf(track()), listOf(paused))
            .first { it.id == "SECRET_MIDNIGHT" }.isUnlocked)
    }

    @Test fun openSummaryGetsAnExactMidnightRefreshWithoutProgressPolling() = runTest {
        val midnight = instant("2026-10-21T00:00:00+03:00[Europe/Moscow]")
        val values = mutableListOf<CalendarPeriodTick>()
        val job = launch { calendarPeriodChanges(
            now = { midnight - 1_000 + testScheduler.currentTime }, monotonicNow = { testScheduler.currentTime },
            zone = { TimeZone.getTimeZone("Europe/Moscow") }).take(2).toList(values) }
        runCurrent()
        assertEquals(1, values.size)
        advanceTimeBy(1_001); runCurrent()
        assertEquals(2, values.size)
        assertEquals(midnight, values.last().dayStartMs)
        job.join()
    }
}
