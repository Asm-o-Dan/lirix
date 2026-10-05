package com.lirix.app.analytics

import com.lirix.app.storage.AchievementEntity
import com.lirix.app.storage.MusicListeningSessionEntity
import com.lirix.app.storage.MusicTrackEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class AchievementTier(val titleRu: String, val badge: String, val colorHex: Long) {
    BRONZE("Бронза", "🥉", 0xFFCD7F32),
    SILVER("Серебро", "🥈", 0xFFC0C0C0),
    GOLD("Золото", "🥇", 0xFFFFD700),
    PLATINUM("Платина", "💎", 0xFF00E5FF)
}

/**
 * Gamification progress tracker and unlock evaluator.
 * Evaluates 26 music achievements across listening history and session time-series.
 *
 * Spec: TASK-WRP-02 / .sdd/specs/wrapped-analytics/overview.md
 */
object GamificationEngine {

    fun checkAchievements(
        currentAchievements: List<AchievementEntity>,
        tracks: List<MusicTrackEntity>,
        sessions: List<MusicListeningSessionEntity>
    ): List<AchievementEntity> {
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        // Метрики по трекам
        val uniqueArtistsCount = tracks.map { it.artist.trim() }.filter { it.isNotBlank() }.distinct().size
        val maxPlayCount = tracks.maxOfOrNull { it.playCount } ?: 0
        val lyricsCount = tracks.count { !it.plainLyrics.isNullOrBlank() || !it.syncedLyrics.isNullOrBlank() }
        val karaokeCount = tracks.count { !it.syncedLyrics.isNullOrBlank() }
        val albumArtCount = tracks.count { !it.albumArtUri.isNullOrBlank() }
        val hasNoteCount = tracks.count { it.userNotes.isNotBlank() }

        val maxArtistPlays = tracks.groupBy { it.artist.trim().lowercase() }.values.maxOfOrNull { group ->
            group.sumOf { it.playCount }
        } ?: 0

        // Метрики по сессиям
        val cal = Calendar.getInstance()
        var morningSessionCount = 0   // 06:00 - 10:00 (hour in 6..9)
        var eveningSessionCount = 0   // 18:00 - 23:00 (hour in 18..22)
        var nightSessionCount = 0     // 01:00 - 05:00 (hour in 1..4)
        var hasMidnightSession = false // 00:00 - 00:05 (hour == 0 && minute in 0..5)

        for (session in sessions) {
            cal.timeInMillis = session.startTimeMs
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val minute = cal.get(Calendar.MINUTE)

            if (hour in 6..9) morningSessionCount++
            if (hour in 18..22) eveningSessionCount++
            if (hour in 1..4 || hour in 2..5) nightSessionCount++
            if (hour == 0 && minute in 0..5) hasMidnightSession = true
        }

        val hasMarathonSession = sessions.any { it.durationMs >= 2 * 3600 * 1000L }
        val hasShortSession = sessions.any { it.durationMs >= 15 * 60 * 1000L }

        // Дневной марафон >= 5 часов
        val dailyDurationMap = mutableMapOf<String, Long>()
        for (session in sessions) {
            val dayKey = sdf.format(Date(session.startTimeMs))
            dailyDurationMap[dayKey] = (dailyDurationMap[dayKey] ?: 0L) + session.durationMs
        }
        val maxDailyDurationMs = dailyDurationMap.values.maxOfOrNull { it } ?: 0L
        val has5hDailyMarathon = maxDailyDurationMs >= 5 * 3600 * 1000L

        // Стрики дней
        val maxStreakDays = calculateMaxStreak(sessions)

        // Секретные повторы в сутки (15 повторов одного трека за одни сутки)
        val sessionsPerTrackPerDay = mutableMapOf<String, Int>()
        for (session in sessions) {
            val day = sdf.format(Date(session.startTimeMs))
            val key = "$day|${session.trackKey}"
            sessionsPerTrackPerDay[key] = (sessionsPerTrackPerDay[key] ?: 0) + 1
        }
        val maxRepeatsInDay = sessionsPerTrackPerDay.values.maxOfOrNull { it } ?: 0
        val hasSecretRepeatDay = maxRepeatsInDay >= 15

        // Дискографический запой (8 разных треков одного артиста за сутки)
        val trackToArtist = tracks.associate { it.trackKey to it.artist.trim().lowercase() }
        val artistTracksPerDay = mutableMapOf<String, MutableSet<String>>()
        for (session in sessions) {
            val artist = trackToArtist[session.trackKey] ?: continue
            val day = sdf.format(Date(session.startTimeMs))
            val key = "$day|$artist"
            artistTracksPerDay.getOrPut(key) { mutableSetOf() }.add(session.trackKey)
        }
        val maxTracksOfOneArtistInDay = artistTracksPerDay.values.maxOfOrNull { it.size } ?: 0
        val hasDiscographyBinge = maxTracksOfOneArtistInDay >= 8

        // Первый проход (все ачивки кроме PLATINUM_PERFECTION)
        val firstPass = currentAchievements.map { ach ->
            if (ach.isUnlocked) return@map ach
            if (ach.id == "PLATINUM_PERFECTION") return@map ach

            val progress: Int
            val shouldUnlock: Boolean

            when (ach.id) {
                // 🥉 Тир 1: Бронза
                "FIRST_TRACK" -> {
                    shouldUnlock = tracks.isNotEmpty()
                    progress = if (shouldUnlock) ach.maxProgress else 0
                }
                "DISCOVERY_5" -> {
                    progress = minOf(uniqueArtistsCount, ach.maxProgress)
                    shouldUnlock = uniqueArtistsCount >= ach.maxProgress
                }
                "LYRICS_NOVICE" -> {
                    shouldUnlock = lyricsCount >= 1
                    progress = if (shouldUnlock) ach.maxProgress else 0
                }
                "CHORD_STRUMMER" -> {
                    val hasChords = tracks.any { it.userNotes.contains("chord", ignoreCase = true) || !it.plainLyrics.isNullOrBlank() }
                    shouldUnlock = hasChords
                    progress = if (shouldUnlock) ach.maxProgress else 0
                }
                "SHORT_SESSION" -> {
                    shouldUnlock = hasShortSession
                    progress = if (shouldUnlock) ach.maxProgress else 0
                }
                "NOTE_TAKER" -> {
                    shouldUnlock = hasNoteCount >= 1
                    progress = if (shouldUnlock) ach.maxProgress else 0
                }
                "STREAK_3" -> {
                    progress = minOf(maxStreakDays, ach.maxProgress)
                    shouldUnlock = maxStreakDays >= ach.maxProgress
                }

                // 🥈 Тир 2: Серебро
                "CENTURY_CLUB" -> {
                    val count = tracks.size
                    progress = minOf(count, ach.maxProgress)
                    shouldUnlock = count >= ach.maxProgress
                }
                "ARTIST_DEVOTEE" -> {
                    progress = minOf(maxArtistPlays, ach.maxProgress)
                    shouldUnlock = maxArtistPlays >= ach.maxProgress
                }
                "KARAOKE_REGULAR" -> {
                    progress = minOf(karaokeCount, ach.maxProgress)
                    shouldUnlock = karaokeCount >= ach.maxProgress
                }
                "STREAK_7" -> {
                    progress = minOf(maxStreakDays, ach.maxProgress)
                    shouldUnlock = maxStreakDays >= ach.maxProgress
                }
                "MARATHON_LISTENER" -> {
                    shouldUnlock = hasMarathonSession
                    progress = if (shouldUnlock) ach.maxProgress else 0
                }
                "MORNING_ENERGY" -> {
                    progress = minOf(morningSessionCount, ach.maxProgress)
                    shouldUnlock = morningSessionCount >= ach.maxProgress
                }
                "EVENING_CHILL" -> {
                    progress = minOf(eveningSessionCount, ach.maxProgress)
                    shouldUnlock = eveningSessionCount >= ach.maxProgress
                }
                "GENRE_EXPLORER" -> {
                    progress = minOf(uniqueArtistsCount, ach.maxProgress)
                    shouldUnlock = uniqueArtistsCount >= ach.maxProgress
                }

                // 🥇 Тир 3: Золото
                "LIBRARY_300" -> {
                    val count = tracks.size
                    progress = minOf(count, ach.maxProgress)
                    shouldUnlock = count >= ach.maxProgress
                }
                "REPEAT_FANATIC" -> {
                    progress = minOf(maxPlayCount, ach.maxProgress)
                    shouldUnlock = maxPlayCount >= ach.maxProgress
                }
                "KARAOKE_MASTER" -> {
                    progress = minOf(karaokeCount, ach.maxProgress)
                    shouldUnlock = karaokeCount >= ach.maxProgress
                }
                "NIGHT_OWL" -> {
                    if (ach.maxProgress == 1) {
                        shouldUnlock = nightSessionCount >= 1
                        progress = if (shouldUnlock) 1 else 0
                    } else {
                        progress = minOf(nightSessionCount, ach.maxProgress)
                        shouldUnlock = nightSessionCount >= ach.maxProgress
                    }
                }
                "STREAK_30" -> {
                    progress = minOf(maxStreakDays, ach.maxProgress)
                    shouldUnlock = maxStreakDays >= ach.maxProgress
                }
                "MARATHON_5H" -> {
                    shouldUnlock = has5hDailyMarathon
                    progress = if (shouldUnlock) ach.maxProgress else 0
                }
                "ALBUM_COLLECTOR" -> {
                    progress = minOf(albumArtCount, ach.maxProgress)
                    shouldUnlock = albumArtCount >= ach.maxProgress
                }

                // 💎 Тир 4: Платина / Секретные
                "SECRET_MIDNIGHT" -> {
                    shouldUnlock = hasMidnightSession
                    progress = if (shouldUnlock) ach.maxProgress else 0
                }
                "SECRET_REPEAT_DAY" -> {
                    shouldUnlock = hasSecretRepeatDay
                    progress = if (shouldUnlock) ach.maxProgress else 0
                }
                "DISCOGRAPHY_BINGE" -> {
                    progress = minOf(maxTracksOfOneArtistInDay, ach.maxProgress)
                    shouldUnlock = hasDiscographyBinge
                }

                // Обратная совместимость с v1
                "LYRICS_CONNOISSEUR" -> {
                    progress = minOf(lyricsCount, ach.maxProgress)
                    shouldUnlock = lyricsCount >= ach.maxProgress
                }

                else -> {
                    return@map ach
                }
            }

            if (shouldUnlock) {
                ach.copy(
                    isUnlocked = true,
                    unlockedAt = ach.unlockedAt ?: now,
                    currentProgress = ach.maxProgress
                )
            } else {
                ach.copy(
                    currentProgress = progress
                )
            }
        }

        // Второй проход: PLATINUM_PERFECTION
        val unlockedOthersCount = firstPass.count { it.id != "PLATINUM_PERFECTION" && it.isUnlocked }

        return firstPass.map { ach ->
            if (ach.id == "PLATINUM_PERFECTION") {
                if (ach.isUnlocked) {
                    ach
                } else {
                    val progress = minOf(unlockedOthersCount, ach.maxProgress)
                    val shouldUnlock = unlockedOthersCount >= ach.maxProgress
                    if (shouldUnlock) {
                        ach.copy(
                            isUnlocked = true,
                            unlockedAt = ach.unlockedAt ?: now,
                            currentProgress = ach.maxProgress
                        )
                    } else {
                        ach.copy(
                            currentProgress = progress
                        )
                    }
                }
            } else {
                ach
            }
        }
    }

    private fun calculateMaxStreak(sessions: List<MusicListeningSessionEntity>): Int {
        if (sessions.isEmpty()) return 0
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val uniqueDates = sessions.map { sdf.format(Date(it.startTimeMs)) }
            .distinct()
            .sorted()
        if (uniqueDates.isEmpty()) return 0

        var maxStreak = 1
        var currentStreak = 1
        val cal = Calendar.getInstance()

        for (i in 1 until uniqueDates.size) {
            val prevDate = sdf.parse(uniqueDates[i - 1]) ?: continue
            val currDate = sdf.parse(uniqueDates[i]) ?: continue
            cal.time = prevDate
            cal.add(Calendar.DAY_OF_YEAR, 1)
            if (sdf.format(cal.time) == uniqueDates[i]) {
                currentStreak++
                if (currentStreak > maxStreak) {
                    maxStreak = currentStreak
                }
            } else {
                currentStreak = 1
            }
        }
        return maxStreak
    }
}

/**
 * Top-level signature per TASK-WRP-02 contract specification.
 */
fun checkAchievements(
    currentAchievements: List<AchievementEntity>,
    tracks: List<MusicTrackEntity>,
    sessions: List<MusicListeningSessionEntity>
): List<AchievementEntity> = GamificationEngine.checkAchievements(currentAchievements, tracks, sessions)
