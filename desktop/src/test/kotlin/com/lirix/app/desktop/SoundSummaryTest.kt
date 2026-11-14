package com.lirix.app.desktop

import com.lirix.app.analytics.AchievementCatalog
import com.lirix.app.analytics.AnalyticsTimeframe
import com.lirix.app.analytics.DecadeShare
import com.lirix.app.analytics.GamificationEngine
import com.lirix.app.analytics.GenreShare
import com.lirix.app.analytics.PeriodMood
import com.lirix.app.analytics.SoundAchievements
import com.lirix.app.analytics.SoundMeter
import com.lirix.app.analytics.SoundRecordKind
import com.lirix.app.analytics.SoundSummaryEngine
import com.lirix.app.analytics.SoundTrait
import com.lirix.app.storage.ListeningSessionEntity
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.TrackMetadataEntity
import com.lirix.app.storage.TrackMetadataStatus
import com.lirix.app.ui.i18n.InterfaceLanguage
import com.lirix.app.ui.i18n.LirixStrings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "How the period sounded" and sound achievements,
 * .sdd/specs/wrapped-analytics/sound_summary_and_achievements_v1.md. Expected numbers are worked out by hand in comments.
 */
class SoundSummaryTest {
    private val previousLanguage = LirixStrings.currentLanguage
    @After fun restoreLanguage() { LirixStrings.currentLanguage = previousLanguage }

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private var nextId = 1L

    private fun track(key: String, artist: String = "Band", totalMs: Long = 0) =
        TrackEntity(key, "Title $key", artist, sourcePackage = "player", totalDurationMs = totalMs)

    /** A legacy-style session of [seconds] that starts [daysAgo] days before now plus a slot so sessions never overlap. */
    private fun session(key: String, seconds: Long, daysAgo: Long, slot: Int): ListeningSessionEntity {
        val start = now - daysAgo * day + slot * 3_600_000L
        return ListeningSessionEntity(nextId++, key, "player", start, start + seconds * 1000, seconds * 1000, true)
    }

    private fun meta(key: String, genre: String? = null, year: Int? = null, bpm: Double? = null, mode: Int? = null, energy: Double? = null,
                     valence: Double? = null, dance: Double? = null, acoustic: Double? = null, instrumental: Double? = null, keyClass: Int? = null,
                     status: TrackMetadataStatus = TrackMetadataStatus.FOUND) =
        TrackMetadataEntity(key, status.name, genres = genre, releaseYear = year, bpm = bpm, musicalKey = keyClass, musicalMode = mode, energy = energy,
            valence = valence, danceability = dance, acousticness = acoustic, instrumentalness = instrumental, fetchedAt = 1)

    private val tracks = (1..6).map { track("t$it") } + track("u", artist = "Unknown Artist")
    private val week = listOf(
        session("t1", 300, 6, 0), session("t2", 200, 6, 1), session("t3", 100, 6, 2),
        session("t4", 200, 6, 3), session("t5", 100, 6, 4), session("t6", 100, 6, 5), session("u", 400, 6, 6)
    )
    private val older = session("t1", 1000, 20, 0)
    private val metadata = listOf(
        meta("t1", "Rock|Hard Rock", 1995, 100.0, 1, 0.8, 0.2, 0.5, 0.1),
        meta("t2", "Rock", 1998, 120.0, 0, 0.6, 0.4, 0.5, 0.1),
        meta("t3", "Metal", 2005, 180.0, 0, 1.0, 0.1, 0.3, 0.0),
        meta("t4", "Pop", 2015, 90.0, 1, 0.4, 0.9, 0.9, 0.5),
        meta("t5", "Pop", 2018, 60.0, 1, 0.2, 0.6, 0.7, 0.9),
        meta("u", "Comedy", 1950, 200.0, 1, 1.0, 1.0, 1.0, 1.0),
        meta("t6", status = TrackMetadataStatus.NOT_FOUND)
    )

    @Test fun weekIsWeightedByListeningTimeOfThatWeek() {
        val summary = SoundSummaryEngine.build(tracks, week + older, metadata, AnalyticsTimeframe.WEEK, now)
        // Seconds: t1 300, t2 200, t3 100, t4 200, t5 100 -> 900 with data; Rock 500, Pop 300, Metal 100.
        assertEquals(listOf(GenreShare("Rock", 56), GenreShare("Pop", 33), GenreShare("Metal", 11)), summary.genres)
        assertEquals(5, summary.tracksWithGenre)
        assertEquals(5, summary.tracksWithData)
        assertEquals("the unknown-artist track is not a listened song", 6, summary.tracksListened)
        // energy 560/900, positivity 390/900, danceability 530/900, acousticness 240/900.
        assertEquals(listOf(SoundMeter(SoundTrait.ENERGY, 62), SoundMeter(SoundTrait.VALENCE, 43),
            SoundMeter(SoundTrait.DANCEABILITY, 59), SoundMeter(SoundTrait.ACOUSTICNESS, 27)), summary.meters)
        assertEquals("96000 / 900", 107, summary.averageBpm)
        assertEquals(PeriodMood.DARK_DRIVE, summary.mood)
        assertEquals(listOf(DecadeShare(1990, 56), DecadeShare(2010, 33), DecadeShare(2000, 11)), summary.decades)
        assertEquals("t1, t4, t5: 600 of 900", 67, summary.majorPercent)
        assertEquals(listOf(
            Triple(SoundRecordKind.FASTEST, "t3", 180), Triple(SoundRecordKind.MOST_ENERGETIC, "t3", 100),
            Triple(SoundRecordKind.CALMEST, "t5", 20), Triple(SoundRecordKind.HAPPIEST, "t4", 90)
        ), summary.records.map { Triple(it.kind, it.trackKey, it.value) })
        assertEquals("Title t3", summary.records.first().title)

        assertEquals("row and session order must not matter", summary,
            SoundSummaryEngine.build(tracks.reversed(), (week + older).reversed(), metadata.reversed(), AnalyticsTimeframe.WEEK, now))
    }

    @Test fun anotherPeriodGivesAnotherPicture() {
        // The month adds 1000 s of t1: Rock 1500, Pop 300, Metal 100 of 1900.
        val month = SoundSummaryEngine.build(tracks, week + older, metadata, AnalyticsTimeframe.MONTH, now)
        assertEquals(listOf(GenreShare("Rock", 79), GenreShare("Pop", 16), GenreShare("Metal", 5)), month.genres)
        assertEquals(PeriodMood.DARK_DRIVE, month.mood)

        val today = SoundSummaryEngine.build(tracks, week + older, metadata, AnalyticsTimeframe.TODAY, now)
        assertTrue(today.isEmpty)
        assertEquals(0, today.tracksListened)
        assertTrue(today.records.isEmpty())
    }

    @Test fun fewerThanFiveTracksWithAFactGiveNoFigureForIt() {
        val four = metadata.filterNot { it.trackKey == "t5" }
        val summary = SoundSummaryEngine.build(tracks, week, four, AnalyticsTimeframe.WEEK, now)
        assertTrue(summary.isEmpty)
        assertNull(summary.mood)
        assertTrue(summary.records.isEmpty())
        assertEquals(4, summary.tracksWithData)
        assertEquals(6, summary.tracksListened)

        // Genre is known for five, energy only for four: genres are shown, energy and the mood are not.
        val partial = metadata.map { if (it.trackKey == "t5") it.copy(energy = null) else it }
        val mixed = SoundSummaryEngine.build(tracks, week, partial, AnalyticsTimeframe.WEEK, now)
        assertEquals(3, mixed.genres.size)
        assertEquals(listOf(SoundTrait.VALENCE, SoundTrait.DANCEABILITY, SoundTrait.ACOUSTICNESS), mixed.meters.map { it.trait })
        assertNull(mixed.mood)
        assertEquals(listOf(SoundRecordKind.FASTEST, SoundRecordKind.HAPPIEST), mixed.records.map { it.kind })
    }

    @Test fun moodQuadrantsUseHalfAsTheBorder() {
        fun mood(energy: Double, valence: Double) = SoundSummaryEngine.build(tracks, week,
            (1..5).map { meta("t$it", energy = energy, valence = valence) }, AnalyticsTimeframe.WEEK, now).mood
        assertEquals(PeriodMood.BRIGHT_DRIVE, mood(0.5, 0.5))
        assertEquals(PeriodMood.DARK_DRIVE, mood(0.9, 0.49))
        assertEquals(PeriodMood.BRIGHT_CALM, mood(0.49, 0.8))
        assertEquals(PeriodMood.DARK_CALM, mood(0.1, 0.1))
    }

    @Test fun libraryWithoutSessionsUsesTrackTotalsOnlyForAllTime() {
        val legacy = (1..5).map { track("t$it", totalMs = if (it == 1) 600_000 else 100_000) }
        val all = SoundSummaryEngine.build(legacy, emptyList(), metadata, AnalyticsTimeframe.ALL_TIME, now)
        // Rock 600 + 100, Metal 100, Pop 200 of 1000.
        assertEquals(listOf(GenreShare("Rock", 70), GenreShare("Pop", 20), GenreShare("Metal", 10)), all.genres)
        assertTrue(SoundSummaryEngine.build(legacy, emptyList(), metadata, AnalyticsTimeframe.MONTH, now).isEmpty)
    }

    @Test fun metricsCountOnlyConfirmedFactsOfLibraryTracks() {
        val library = (1..8).map { track("a$it") } + track("u", artist = "Unknown Artist")
        val rows = listOf(
            meta("a1", "Rock", 1975, 160.0, energy = 0.8, valence = 0.7, dance = 0.7, acoustic = 0.7, instrumental = 0.7, keyClass = 0),
            meta("a2", "rock", 1989, 160.5, energy = 0.79, valence = 0.69, dance = 0.69, acoustic = 0.69, instrumental = 0.69, keyClass = 0),
            meta("a3", "Pop", 1990, 80.0, energy = 0.35, valence = 0.3, keyClass = 11),
            meta("a4", "Jazz|Blues", 2001, 79.9, energy = 0.36, valence = 0.31, keyClass = 12),
            meta("a5"),
            meta("a6", "Folk", 1960, status = TrackMetadataStatus.AMBIGUOUS),
            meta("gone", "Metal", 1930, 200.0, energy = 1.0),
            meta("u", "Comedy", 1940, 200.0, energy = 1.0)
        )
        val expected = mapOf(
            "SOUND_CARD_INDEX" to 5, "SOUND_GENRES_3" to 3, "SOUND_GENRES_7" to 3, "SOUND_GENRE_LOYAL" to 2, "SOUND_NO_WORDS" to 1,
            "SOUND_SPRINTER" to 1, "SOUND_SLOW_DANCE" to 1, "SOUND_CHARGE" to 1, "SOUND_QUIET_HARBOR" to 1, "SOUND_SUNNY_SIDE" to 1,
            "SOUND_BLUE_HOUR" to 1, "SOUND_DANCE_FLOOR" to 1, "SOUND_UNPLUGGED" to 1, "SOUND_RETRO" to 2, "SOUND_TIME_TRAVELER" to 4,
            "SOUND_ALL_KEYS" to 2
        )
        assertEquals(expected, SoundAchievements.metrics(library, rows))
        assertEquals("every sound achievement has a metric", expected.keys, SoundAchievements.definitions().map { it.id }.toSet())
        assertTrue(SoundAchievements.metrics(library, emptyList()).values.all { it == 0 })
    }

    @Test fun achievementsUnlockFromCatalogueFactsAndLeaveOthersAlone() {
        val library = (1..3).map { track("a$it") }
        val rows = listOf(meta("a1", "Rock", instrumental = 0.9), meta("a2", "Pop"), meta("a3", "Jazz"))
        val all = AchievementCatalog.defaults() + SoundAchievements.definitions()
        assertEquals("ids are unique", all.size, all.map { it.id }.toSet().size)

        val without = GamificationEngine.checkAchievements(all, library, emptyList()).associateBy { it.id }
        val with = GamificationEngine.checkAchievements(all, library, emptyList(), rows).associateBy { it.id }
        assertTrue(without.values.none { it.category == SoundAchievements.CATEGORY && (it.isUnlocked || it.currentProgress != 0) })

        assertTrue(with.getValue("SOUND_GENRES_3").isUnlocked)
        assertEquals(3, with.getValue("SOUND_GENRES_3").currentProgress)
        assertFalse(with.getValue("SOUND_GENRES_7").isUnlocked)
        assertEquals(3, with.getValue("SOUND_GENRES_7").currentProgress)
        assertEquals(3, with.getValue("SOUND_CARD_INDEX").currentProgress)
        assertEquals(1, with.getValue("SOUND_NO_WORDS").currentProgress)
        assertFalse(with.getValue("SOUND_ALL_KEYS").isUnlocked)
        // Catalogue facts change nothing outside their category, except the "20 of any" counter.
        (without.keys - "PLATINUM_PERFECTION").filter { without.getValue(it).category != SoundAchievements.CATEGORY }.forEach { id ->
            assertEquals(id, without.getValue(id).copy(unlockedAt = null), with.getValue(id).copy(unlockedAt = null))
        }
        assertTrue(with.getValue("FIRST_TRACK").isUnlocked)
        assertEquals(without.getValue("PLATINUM_PERFECTION").currentProgress + 1, with.getValue("PLATINUM_PERFECTION").currentProgress)

        // Progress never exceeds the goal and an unlocked achievement stays unlocked when data disappears.
        val many = (1..9).map { meta("a${(it - 1) % 3 + 1}", "Genre $it") }
        assertEquals(3, GamificationEngine.checkAchievements(all, library, emptyList(), many).first { it.id == "SOUND_GENRES_3" }.currentProgress)
        val kept = GamificationEngine.checkAchievements(with.values.toList(), library, emptyList(), emptyList()).first { it.id == "SOUND_GENRES_3" }
        assertTrue(kept.isUnlocked)
    }

    @Test fun everyAchievementTextHasAnEnglishVariant() {
        LirixStrings.currentLanguage = InterfaceLanguage.EN
        (AchievementCatalog.defaults() + SoundAchievements.definitions()).forEach { achievement ->
            listOf(achievement.title, achievement.description).forEach { text ->
                val english = LirixStrings.message(text)
                assertNotEquals("no English for: $text", text, english)
                assertFalse(text, english.any { it in 'а'..'я' || it in 'А'..'Я' })
            }
        }
        LirixStrings.currentLanguage = InterfaceLanguage.RU
        assertEquals("Спринтер", LirixStrings.message("Спринтер"))
    }
}
