package com.lirix.app

import com.lirix.app.analytics.GamificationEngine
import com.lirix.app.storage.AchievementEntity
import com.lirix.app.storage.ListeningSessionEntity
import com.lirix.app.storage.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * TDD Unit-tests for GamificationEngine (TASK-WRP-02).
 * Validates progress tracking and unlocking of the 7 core achievements:
 * FIRST_TRACK, CENTURY_CLUB, MARATHON_LISTENER, NIGHT_OWL, GENRE_EXPLORER, REPEAT_FANATIC, LYRICS_CONNOISSEUR.
 */
class GamificationEngineTest {

    private fun getBaseAchievements(): List<AchievementEntity> = listOf(
        AchievementEntity(id = "FIRST_TRACK", title = "Первая нота", description = "Прослушан 1 трек", iconRes = "ic_music_note", maxProgress = 1),
        AchievementEntity(id = "CENTURY_CLUB", title = "Клуб сотни", description = "100 уникальных треков", iconRes = "ic_disc", maxProgress = 100),
        AchievementEntity(id = "MARATHON_LISTENER", title = "Марафонец", description = "Сессия > 2 часов", iconRes = "ic_timer", maxProgress = 1),
        AchievementEntity(id = "NIGHT_OWL", title = "Ночная сова", description = "Прослушивание между 02:00 и 05:00", iconRes = "ic_moon", maxProgress = 1),
        AchievementEntity(id = "GENRE_EXPLORER", title = "Меломан", description = "Более 10 разных исполнителей", iconRes = "ic_explore", maxProgress = 10),
        AchievementEntity(id = "REPEAT_FANATIC", title = "На повторе", description = "Один трек прослушан более 20 раз", iconRes = "ic_repeat", maxProgress = 20),
        AchievementEntity(id = "LYRICS_CONNOISSEUR", title = "Знаток текстов", description = "Открыто 10 текстов песен", iconRes = "ic_lyrics", maxProgress = 10)
    )

    private fun createTimestampAtHour(hour: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    @Test
    fun test_achievement_unlock_first_track() {
        val achievements = getBaseAchievements()
        val tracks = listOf(
            TrackEntity(trackKey = "t1", title = "First Song", artist = "Artist", sourcePackage = "com.spotify.music")
        )

        val updated = GamificationEngine.checkAchievements(achievements, tracks, emptyList())
        val firstTrackAch = updated.find { it.id == "FIRST_TRACK" }

        assertNotNull(firstTrackAch)
        assertTrue("FIRST_TRACK must be unlocked when tracks are present", firstTrackAch!!.isUnlocked)
        assertEquals(1, firstTrackAch.currentProgress)
        assertNotNull(firstTrackAch.unlockedAt)
    }

    @Test
    fun test_achievement_unlock_marathon_listener() {
        val achievements = getBaseAchievements()
        // Session >= 2 hours = 7200000 ms
        val sessions = listOf(
            ListeningSessionEntity(id = 1, trackKey = "t1", sourcePackage = "com.spotify.music", startTimeMs = 1000L, endTimeMs = 7201000L, durationMs = 7200000L)
        )

        val updated = GamificationEngine.checkAchievements(achievements, emptyList(), sessions)
        val marathonAch = updated.find { it.id == "MARATHON_LISTENER" }

        assertNotNull(marathonAch)
        assertTrue("MARATHON_LISTENER must be unlocked for 2h session", marathonAch!!.isUnlocked)
    }

    @Test
    fun test_achievement_unlock_night_owl() {
        val achievements = getBaseAchievements()
        val nightTime = createTimestampAtHour(3) // 03:00 AM
        val sessions = listOf(
            ListeningSessionEntity(id = 1, trackKey = "t_night", sourcePackage = "ru.yandex.music", startTimeMs = nightTime, endTimeMs = nightTime + 300000L, durationMs = 300000L)
        )

        val updated = GamificationEngine.checkAchievements(achievements, emptyList(), sessions)
        val nightOwlAch = updated.find { it.id == "NIGHT_OWL" }

        assertNotNull(nightOwlAch)
        assertTrue("NIGHT_OWL must be unlocked for playback between 02:00 and 05:00", nightOwlAch!!.isUnlocked)
    }

    @Test
    fun test_achievement_unlock_repeat_fanatic() {
        val achievements = getBaseAchievements()
        val track = TrackEntity(
            trackKey = "t_rep",
            title = "Obsession",
            artist = "Fav Artist",
            sourcePackage = "com.spotify.music",
            playCount = 21 // > 20
        )

        val updated = GamificationEngine.checkAchievements(achievements, listOf(track), emptyList())
        val repeatAch = updated.find { it.id == "REPEAT_FANATIC" }

        assertNotNull(repeatAch)
        assertTrue("REPEAT_FANATIC must be unlocked when playCount >= 20", repeatAch!!.isUnlocked)
    }

    @Test
    fun test_achievement_unlock_century_club_progress() {
        val achievements = getBaseAchievements()
        // 50 tracks -> progress 50, but not unlocked
        val fiftyTracks = (1..50).map { i ->
            TrackEntity(trackKey = "t$i", title = "Song $i", artist = "Artist $i", sourcePackage = "com.spotify.music")
        }

        val updated = GamificationEngine.checkAchievements(achievements, fiftyTracks, emptyList())
        val centuryAch = updated.find { it.id == "CENTURY_CLUB" }

        assertNotNull(centuryAch)
        assertFalse("CENTURY_CLUB must not unlock at 50 tracks", centuryAch!!.isUnlocked)
        assertEquals(50, centuryAch.currentProgress)
    }

    // ------------------------------------------------------------------------
    // TASK-WRP-02: 26 Achievements & 4 Tiers Coverage Tests
    // ------------------------------------------------------------------------

    private fun getAll26BaseAchievements(): List<AchievementEntity> = listOf(
        // Bronze (7)
        AchievementEntity("FIRST_TRACK", "Первая нота", "Прослушан 1 трек", "ic_music_note", maxProgress = 1, tier = "BRONZE", category = "LISTENING"),
        AchievementEntity("DISCOVERY_5", "Первые шаги", "Послушано 5 разных артистов", "ic_explore", maxProgress = 5, tier = "BRONZE", category = "EXPLORATION"),
        AchievementEntity("LYRICS_NOVICE", "Подпевала", "Открыт текст песни", "ic_lyrics", maxProgress = 1, tier = "BRONZE", category = "LYRICS"),
        AchievementEntity("CHORD_STRUMMER", "Первый аккорд", "Открыты аккорды к треку", "ic_music_note", maxProgress = 1, tier = "BRONZE", category = "LYRICS"),
        AchievementEntity("SHORT_SESSION", "Разминка", "15 минут музыки за сессию", "ic_timer", maxProgress = 1, tier = "BRONZE", category = "LISTENING"),
        AchievementEntity("NOTE_TAKER", "Заметки на полях", "Добавлена первая личная заметка к треку", "ic_edit", maxProgress = 1, tier = "BRONZE", category = "SPECIAL"),
        AchievementEntity("STREAK_3", "На волне", "Слушайте музыку 3 дня подряд", "ic_repeat", maxProgress = 3, tier = "BRONZE", category = "LISTENING"),

        // Silver (8)
        AchievementEntity("CENTURY_CLUB", "Клуб сотни", "100 уникальных треков в библиотеке", "ic_disc", maxProgress = 100, tier = "SILVER", category = "EXPLORATION"),
        AchievementEntity("ARTIST_DEVOTEE", "Преданный фанат", "Один исполнитель прослушан 15 раз", "ic_star", maxProgress = 15, tier = "SILVER", category = "LISTENING"),
        AchievementEntity("KARAOKE_REGULAR", "Караоке-бар", "10 треков с караоке LRC", "ic_lyrics", maxProgress = 10, tier = "SILVER", category = "LYRICS"),
        AchievementEntity("STREAK_7", "Музыкальная неделя", "Слушайте музыку 7 дней подряд", "ic_repeat", maxProgress = 7, tier = "SILVER", category = "LISTENING"),
        AchievementEntity("MARATHON_LISTENER", "Марафонец", "Сессия прослушивания более 2 часов", "ic_timer", maxProgress = 1, tier = "SILVER", category = "LISTENING"),
        AchievementEntity("MORNING_ENERGY", "Бодрое утро", "5 сессий с 06:00 до 10:00 утра", "ic_sun", maxProgress = 5, tier = "SILVER", category = "SPECIAL"),
        AchievementEntity("EVENING_CHILL", "Вечерний релакс", "10 сессий вечером (18:00 - 23:00)", "ic_moon", maxProgress = 10, tier = "SILVER", category = "SPECIAL"),
        AchievementEntity("GENRE_EXPLORER", "Меломан", "Более 15 разных исполнителей", "ic_explore", maxProgress = 15, tier = "SILVER", category = "EXPLORATION"),

        // Gold (7)
        AchievementEntity("LIBRARY_300", "Золотая фонотека", "300 уникальных треков в библиотеке", "ic_disc", maxProgress = 300, tier = "GOLD", category = "EXPLORATION"),
        AchievementEntity("REPEAT_FANATIC", "На повторе", "Один трек прослушан более 30 раз", "ic_repeat", maxProgress = 30, tier = "GOLD", category = "LISTENING"),
        AchievementEntity("KARAOKE_MASTER", "Звезда караоке", "25 треков с LRC-караоке", "ic_lyrics", maxProgress = 25, tier = "GOLD", category = "LYRICS"),
        AchievementEntity("NIGHT_OWL", "Ночная сова", "10 сессий глубокой ночью (01:00 - 05:00)", "ic_moon", maxProgress = 10, tier = "GOLD", category = "SPECIAL"),
        AchievementEntity("STREAK_30", "Железная привычка", "30 дней непрерывного прослушивания", "ic_repeat", maxProgress = 30, tier = "GOLD", category = "LISTENING"),
        AchievementEntity("MARATHON_5H", "Аудио-марафон 5ч", "Более 5 часов музыки за один день", "ic_timer", maxProgress = 1, tier = "GOLD", category = "LISTENING"),
        AchievementEntity("ALBUM_COLLECTOR", "Хранитель винила", "20 треков с обложками альбомов", "ic_disc", maxProgress = 20, tier = "GOLD", category = "EXPLORATION"),

        // Platinum (4)
        AchievementEntity("SECRET_MIDNIGHT", "Полуночная тайна", "Включен трек в первые 5 минут полуночи (00:00 - 00:05)", "ic_star", maxProgress = 1, tier = "PLATINUM", category = "SPECIAL"),
        AchievementEntity("SECRET_REPEAT_DAY", "Одержимость дня", "Один трек прослушан 15 раз за одни сутки", "ic_repeat", maxProgress = 1, tier = "PLATINUM", category = "LISTENING"),
        AchievementEntity("DISCOGRAPHY_BINGE", "Дискографический запой", "8 разных треков одного артиста за сутки", "ic_explore", maxProgress = 8, tier = "PLATINUM", category = "EXPLORATION"),
        AchievementEntity("PLATINUM_PERFECTION", "Абсолютный слух", "Разблокировано 20 любых достижений", "ic_star", maxProgress = 20, tier = "PLATINUM", category = "SPECIAL")
    )

    @Test
    fun test_streaks_calculation_3_7_30_days() {
        val achievements = getAll26BaseAchievements()

        // Create consecutive sessions over 7 distinct days
        val baseCalendar = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 1, 12, 0, 0)
        }

        val sessions7Days = (0..6).map { dayOffset ->
            val sessionTime = baseCalendar.timeInMillis + (dayOffset * 86400000L)
            ListeningSessionEntity(
                id = (dayOffset + 1).toLong(),
                trackKey = "track_$dayOffset",
                sourcePackage = "com.spotify.music",
                startTimeMs = sessionTime,
                endTimeMs = sessionTime + 600000L,
                durationMs = 600000L
            )
        }

        val updated = GamificationEngine.checkAchievements(achievements, emptyList(), sessions7Days)

        val streak3 = updated.find { it.id == "STREAK_3" }
        assertNotNull(streak3)
        assertTrue("STREAK_3 must unlock on 7 consecutive days", streak3!!.isUnlocked)
        assertEquals(3, streak3.currentProgress)

        val streak7 = updated.find { it.id == "STREAK_7" }
        assertNotNull(streak7)
        assertTrue("STREAK_7 must unlock on 7 consecutive days", streak7!!.isUnlocked)
        assertEquals(7, streak7.currentProgress)

        val streak30 = updated.find { it.id == "STREAK_30" }
        assertNotNull(streak30)
        assertFalse("STREAK_30 must not unlock on 7 days", streak30!!.isUnlocked)
        assertEquals(7, streak30.currentProgress)
    }

    @Test
    fun test_time_slots_morning_evening_night_and_secret_midnight() {
        val achievements = getAll26BaseAchievements()

        // 1. Morning sessions (06:00 - 10:00) -> 5 sessions
        val morningSessions = (1..5).map { i ->
            val time = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8)
                set(Calendar.MINUTE, i * 5)
            }.timeInMillis
            ListeningSessionEntity(id = i.toLong(), trackKey = "m_$i", sourcePackage = "com.spotify.music", startTimeMs = time, endTimeMs = time + 300000L, durationMs = 300000L)
        }

        // 2. Evening sessions (18:00 - 23:00) -> 10 sessions
        val eveningSessions = (1..10).map { i ->
            val time = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 20)
                set(Calendar.MINUTE, i * 3)
            }.timeInMillis
            ListeningSessionEntity(id = (10 + i).toLong(), trackKey = "e_$i", sourcePackage = "com.spotify.music", startTimeMs = time, endTimeMs = time + 300000L, durationMs = 300000L)
        }

        // 3. Secret midnight session (00:00 - 00:05)
        val midnightTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 2)
        }.timeInMillis
        val midnightSession = ListeningSessionEntity(id = 99L, trackKey = "secret_track", sourcePackage = "com.spotify.music", startTimeMs = midnightTime, endTimeMs = midnightTime + 180000L, durationMs = 180000L)

        val allSessions = morningSessions + eveningSessions + listOf(midnightSession)
        val updated = GamificationEngine.checkAchievements(achievements, emptyList(), allSessions)

        val morningAch = updated.find { it.id == "MORNING_ENERGY" }
        assertNotNull(morningAch)
        assertTrue("MORNING_ENERGY must unlock for 5 morning sessions", morningAch!!.isUnlocked)

        val eveningAch = updated.find { it.id == "EVENING_CHILL" }
        assertNotNull(eveningAch)
        assertTrue("EVENING_CHILL must unlock for 10 evening sessions", eveningAch!!.isUnlocked)

        val secretMidnight = updated.find { it.id == "SECRET_MIDNIGHT" }
        assertNotNull(secretMidnight)
        assertTrue("SECRET_MIDNIGHT must unlock for playback at 00:02", secretMidnight!!.isUnlocked)
    }

    @Test
    fun test_karaoke_album_art_and_daily_marathon_unlocks() {
        val achievements = getAll26BaseAchievements()

        // 25 tracks with LRC-karaoke and 20 with album art
        val tracks = (1..25).map { i ->
            TrackEntity(
                trackKey = "karaoke_$i",
                title = "Title $i",
                artist = "Artist $i",
                sourcePackage = "com.spotify.music",
                syncedLyrics = "[00:10.00] Line $i",
                plainLyrics = "Line $i",
                albumArtUri = if (i <= 20) "/data/user/0/app/files/album_art/art_$i.webp" else null
            )
        }

        // 5 hours daily marathon session (5 * 3600 * 1000L = 18000000 ms)
        val session5h = listOf(
            ListeningSessionEntity(
                id = 101L,
                trackKey = "marathon_track",
                sourcePackage = "com.spotify.music",
                startTimeMs = 1000L,
                endTimeMs = 18001000L,
                durationMs = 18000000L
            )
        )

        val updated = GamificationEngine.checkAchievements(achievements, tracks, session5h)

        val karaokeRegular = updated.find { it.id == "KARAOKE_REGULAR" }
        assertNotNull(karaokeRegular)
        assertTrue("KARAOKE_REGULAR (10 tracks) must unlock", karaokeRegular!!.isUnlocked)

        val karaokeMaster = updated.find { it.id == "KARAOKE_MASTER" }
        assertNotNull(karaokeMaster)
        assertTrue("KARAOKE_MASTER (25 tracks) must unlock", karaokeMaster!!.isUnlocked)

        val albumCollector = updated.find { it.id == "ALBUM_COLLECTOR" }
        assertNotNull(albumCollector)
        assertTrue("ALBUM_COLLECTOR (20 tracks with art) must unlock", albumCollector!!.isUnlocked)

        val marathon5h = updated.find { it.id == "MARATHON_5H" }
        assertNotNull(marathon5h)
        assertTrue("MARATHON_5H must unlock for 5h listening in one day", marathon5h!!.isUnlocked)
    }

    @Test
    fun test_platinum_perfection_unlocks_when_20_achievements_completed() {
        val all26 = getAll26BaseAchievements()

        // Pre-unlock exactly 20 achievements (excluding PLATINUM_PERFECTION)
        val preUnlocked = all26.mapIndexed { index, ach ->
            if (index < 20 && ach.id != "PLATINUM_PERFECTION") {
                ach.copy(isUnlocked = true, currentProgress = ach.maxProgress, unlockedAt = System.currentTimeMillis())
            } else {
                ach
            }
        }

        val updated = GamificationEngine.checkAchievements(preUnlocked, emptyList(), emptyList())
        val perfection = updated.find { it.id == "PLATINUM_PERFECTION" }

        assertNotNull(perfection)
        assertTrue("PLATINUM_PERFECTION must unlock when 20 other achievements are completed", perfection!!.isUnlocked)
        assertEquals(20, perfection.currentProgress)
    }
}

