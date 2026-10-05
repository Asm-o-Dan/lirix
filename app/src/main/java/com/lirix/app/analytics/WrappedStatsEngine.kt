package com.lirix.app.analytics

import com.lirix.app.feature.MusicFeatureEngine
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.storage.ListeningSessionEntity
import com.lirix.app.storage.TrackEntity
import java.util.Calendar

typealias MusicTrackEntity = TrackEntity
typealias MusicListeningSessionEntity = ListeningSessionEntity

enum class AnalyticsTimeframe(val labelRu: String) {
    TODAY("Сегодня"),
    WEEK("7 дней"),
    MONTH("Месяц"),
    ALL_TIME("Всё время")
}

data class TopArtistItem(
    val artist: String,
    val playCount: Int,
    val durationMs: Long
)

data class TopTrackItem(
    val title: String,
    val artist: String,
    val playCount: Int,
    val durationMs: Long,
    val trackKey: String = "",
    val albumArtUri: String? = null
)

data class TopObsessionItem(
    val trackKey: String,
    val title: String,
    val artist: String,
    val albumArtUri: String?,
    val playCountInPeriod: Int,
    val durationMsInPeriod: Long
)

data class DayOfWeekActivity(
    val dayName: String,     // "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"
    val sessionCount: Int,
    val totalDurationMs: Long
)

enum class TimeOfDaySlot {
    NIGHT,
    MORNING,
    AFTERNOON,
    EVENING
}

enum class ListeningArchetype(val titleRu: String, val descriptionRu: String) {
    NIGHT_OWL("Ночной странник", "Более 35% ваших треков звучат глубокой ночью (00:00-06:00)."),
    LOYAL_REPEATER("Верный фанат", "Один любимый трек звучит снова и снова (повторов > 3)."),
    GENRE_NOMAD("Кочевник жанров", "Сотни разных исполнителей и постоянный поиск нового."),
    ACOUSTIC_BARD("Акустический бард", "Более половины треков сопровождаются текстами и аккордами."),
    DAY_WORKER("Дневной труженик", "Пик музыкальной активности приходится на рабочий день."),
    CASUAL_LISTENER("Меломан", "Сбалансированный музыкальный ритм каждый день."),

    // Поддержка обратной совместимости
    NIGHT_DREAMER("Ночной странник", "Более 35% ваших треков звучат глубокой ночью."),
    MARATHON_RUNNER("Марафонец", "Ваши музыкальные сессии длятся часами без остановки."),
    DISCOVERY_HUNTER("Музыкальный первооткрыватель", "Вы постоянно находите и исследуете новых артистов.")
}

data class WrappedStats(
    val totalListeningTimeMs: Long,
    val uniqueTracksCount: Int,
    val uniqueArtistsCount: Int,
    val repeatRatio: Float = 0f,            // playCount / uniqueTracks
    val lyricsCoverageRatio: Float = 0f,    // tracksWithLyrics / totalTracks
    val topArtists: List<TopArtistItem>,
    val topTracks: List<TopTrackItem>,
    val topObsession: TopObsessionItem? = null,
    val timeOfDayDistribution: Map<TimeOfDaySlot, Float>,
    val weeklyActivity: List<DayOfWeekActivity> = emptyList(),
    val archetype: ListeningArchetype
)

/**
 * On-device analytics engine calculating Music Wrapped stats.
 * Computes listening time, top artists/tracks, time of day distribution, user archetypes,
 * weekly activity, top obsession and timeframes (TODAY, WEEK, MONTH, ALL_TIME).
 *
 * Spec: TASK-UI-03 / .sdd/tasks/TASK-UI-03.md
 */
object WrappedStatsEngine {

    fun calculateEffectiveSessionDuration(
        session: MusicListeningSessionEntity,
        referenceTimestampMs: Long,
        livePlayback: LivePlaybackSnapshot? = null,
        activeSessionId: Long? = null,
        trackDurationMs: Long? = null
    ): Long {
        // ПРАВИЛО 1: Если сессия НЕ является текущей активной — возвращаем СТРОГО сохраненный durationMs!
        if (activeSessionId == null || session.id != activeSessionId) {
            return session.durationMs
        }

        // ПРАВИЛО 2: Для ЕДИНСТВЕННОЙ активной сессии вычисляем реальный прогресс
        val livePos = if (livePlayback != null && livePlayback.isPlaying) {
            if (livePlayback.lastPositionUpdateTimeMs > 0L) {
                livePlayback.currentPositionMs()
            } else {
                livePlayback.basePositionMs
            }
        } else {
            0L
        }

        // Ограничиваем максимальную длительность одной песни (не больше длительности трека или 15 минут)
        val maxTrackCap = trackDurationMs?.takeIf { it > 0L }
            ?: (livePlayback?.durationMs?.takeIf { it > 0L } ?: (15 * 60 * 1000L))

        val elapsedWallTime = (referenceTimestampMs - session.startTimeMs).coerceIn(0L, maxTrackCap)
        val effective = maxOf(session.durationMs, livePos, elapsedWallTime)

        return minOf(effective, maxTrackCap)
    }

    fun calculateStats(
        tracks: List<MusicTrackEntity>,
        sessions: List<MusicListeningSessionEntity>,
        timeframe: AnalyticsTimeframe = AnalyticsTimeframe.ALL_TIME,
        referenceTimestampMs: Long = System.currentTimeMillis(),
        livePlayback: LivePlaybackSnapshot? = null
    ): WrappedStats {
        if (tracks.isEmpty() && sessions.isEmpty()) {
            return WrappedStats(
                totalListeningTimeMs = 0L,
                uniqueTracksCount = 0,
                uniqueArtistsCount = 0,
                repeatRatio = 0f,
                lyricsCoverageRatio = 0f,
                topArtists = emptyList(),
                topTracks = emptyList(),
                topObsession = null,
                timeOfDayDistribution = emptyMap(),
                weeklyActivity = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").map {
                    DayOfWeekActivity(it, 0, 0L)
                },
                archetype = ListeningArchetype.CASUAL_LISTENER
            )
        }

        val filteredSessions = when (timeframe) {
            AnalyticsTimeframe.TODAY -> {
                val startToday = startOfDayMs(referenceTimestampMs)
                sessions.filter { it.startTimeMs >= startToday && it.startTimeMs <= referenceTimestampMs }
            }
            AnalyticsTimeframe.WEEK -> {
                val startWeek = referenceTimestampMs - 7 * 86_400_000L
                sessions.filter { it.startTimeMs >= startWeek && it.startTimeMs <= referenceTimestampMs }
            }
            AnalyticsTimeframe.MONTH -> {
                val startMonth = referenceTimestampMs - 30 * 86_400_000L
                sessions.filter { it.startTimeMs >= startMonth && it.startTimeMs <= referenceTimestampMs }
            }
            AnalyticsTimeframe.ALL_TIME -> sessions
        }

        val trackMap = tracks.associateBy { it.trackKey }

        val totalListeningTimeMs: Long
        val uniqueTracksCount: Int
        val uniqueArtistsCount: Int
        val repeatRatio: Float
        val lyricsCoverageRatio: Float
        val topArtists: List<TopArtistItem>
        val topTracks: List<TopTrackItem>
        val topObsession: TopObsessionItem?

        val activeSession: MusicListeningSessionEntity? = if (livePlayback != null && livePlayback.isPlaying) {
            val liveTrackKey = MusicFeatureEngine.computeTrackKey(livePlayback.title, livePlayback.artist, livePlayback.album)
            filteredSessions.lastOrNull { s ->
                !s.isCompleted && (s.trackKey == liveTrackKey || s.sourcePackage == livePlayback.packageName)
            } ?: filteredSessions.lastOrNull { !it.isCompleted && (referenceTimestampMs - it.endTimeMs < 60_000L) }
        } else {
            filteredSessions.lastOrNull { !it.isCompleted && (referenceTimestampMs - it.endTimeMs < 30_000L) }
        }
        val activeSessionId: Long? = activeSession?.id

        if (filteredSessions.isNotEmpty()) {
            val rawTotalListeningTimeMs = filteredSessions.sumOf { session ->
                val trackDuration = trackMap[session.trackKey]?.totalDurationMs
                calculateEffectiveSessionDuration(
                    session = session,
                    referenceTimestampMs = referenceTimestampMs,
                    livePlayback = livePlayback,
                    activeSessionId = activeSessionId,
                    trackDurationMs = trackDuration
                )
            }
            totalListeningTimeMs = if (timeframe == AnalyticsTimeframe.TODAY) {
                rawTotalListeningTimeMs.coerceAtMost(24 * 3600 * 1000L)
            } else {
                rawTotalListeningTimeMs
            }
            val sessionCountsByTrack = filteredSessions.groupingBy { it.trackKey }.eachCount()
            val sessionDurationsByTrack = filteredSessions.groupBy { it.trackKey }
                .mapValues { (key, list) ->
                    val trackDuration = trackMap[key]?.totalDurationMs
                    list.sumOf { session ->
                        calculateEffectiveSessionDuration(
                            session = session,
                            referenceTimestampMs = referenceTimestampMs,
                            livePlayback = livePlayback,
                            activeSessionId = activeSessionId,
                            trackDurationMs = trackDuration
                        )
                    }
                }

            uniqueTracksCount = sessionCountsByTrack.keys.size
            val uniqueArtists = sessionCountsByTrack.keys
                .mapNotNull { trackMap[it]?.artist?.trim() }
                .filter { it.isNotBlank() }
                .distinct()
            uniqueArtistsCount = uniqueArtists.size

            val totalPlays = filteredSessions.size
            repeatRatio = if (uniqueTracksCount > 0) totalPlays.toFloat() / uniqueTracksCount else 0f

            val tracksWithLyrics = sessionCountsByTrack.keys.count { key ->
                val t = trackMap[key]
                t?.syncedLyrics != null || t?.plainLyrics != null
            }
            lyricsCoverageRatio = if (uniqueTracksCount > 0) tracksWithLyrics.toFloat() / uniqueTracksCount else 0f

            val artistAggregates = mutableMapOf<String, Pair<Int, Long>>()
            for ((key, count) in sessionCountsByTrack) {
                val artistName = trackMap[key]?.artist?.trim()?.ifBlank { "Unknown Artist" } ?: "Unknown Artist"
                val dur = sessionDurationsByTrack[key] ?: 0L
                val current = artistAggregates.getOrDefault(artistName, Pair(0, 0L))
                artistAggregates[artistName] = Pair(current.first + count, current.second + dur)
            }
            topArtists = artistAggregates.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Pair<Int, Long>>> { it.value.first }
                        .thenByDescending { it.value.second }
                )
                .take(5)
                .map { TopArtistItem(artist = it.key, playCount = it.value.first, durationMs = it.value.second) }

            topTracks = sessionCountsByTrack.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> { it.value }
                        .thenByDescending { sessionDurationsByTrack[it.key] ?: 0L }
                )
                .take(5)
                .map { entry ->
                    val t = trackMap[entry.key]
                    TopTrackItem(
                        title = t?.title ?: "Трек #${entry.key.take(6)}",
                        artist = t?.artist ?: "Неизвестный исполнитель",
                        playCount = entry.value,
                        durationMs = sessionDurationsByTrack[entry.key] ?: 0L,
                        trackKey = entry.key,
                        albumArtUri = t?.albumArtUri
                    )
                }

            val maxObsessionEntry = sessionCountsByTrack.entries
                .filter { it.value >= 3 }
                .maxWithOrNull(
                    compareBy<Map.Entry<String, Int>> { it.value }
                        .thenBy { sessionDurationsByTrack[it.key] ?: 0L }
                )
            topObsession = maxObsessionEntry?.let { entry ->
                val t = trackMap[entry.key]
                TopObsessionItem(
                    trackKey = entry.key,
                    title = t?.title ?: "Трек #${entry.key.take(6)}",
                    artist = t?.artist ?: "Неизвестный исполнитель",
                    albumArtUri = t?.albumArtUri,
                    playCountInPeriod = entry.value,
                    durationMsInPeriod = sessionDurationsByTrack[entry.key] ?: 0L
                )
            }
        } else if (timeframe == AnalyticsTimeframe.ALL_TIME && tracks.isNotEmpty()) {
            totalListeningTimeMs = tracks.sumOf { it.totalDurationMs }
            uniqueTracksCount = tracks.map { it.trackKey }.distinct().size
            uniqueArtistsCount = tracks.map { it.artist.trim() }.filter { it.isNotBlank() }.distinct().size
            val totalPlays = tracks.sumOf { it.playCount }
            repeatRatio = if (uniqueTracksCount > 0) totalPlays.toFloat() / uniqueTracksCount else 0f
            val tracksWithLyrics = tracks.count { it.syncedLyrics != null || it.plainLyrics != null }
            lyricsCoverageRatio = if (uniqueTracksCount > 0) tracksWithLyrics.toFloat() / uniqueTracksCount else 0f

            val artistAggregates = mutableMapOf<String, Pair<Int, Long>>()
            for (track in tracks) {
                val artistName = track.artist.trim().ifBlank { "Unknown Artist" }
                val current = artistAggregates.getOrDefault(artistName, Pair(0, 0L))
                artistAggregates[artistName] = Pair(
                    current.first + track.playCount,
                    current.second + track.totalDurationMs
                )
            }
            topArtists = artistAggregates.entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Pair<Int, Long>>> { it.value.first }
                        .thenByDescending { it.value.second }
                )
                .take(5)
                .map { TopArtistItem(artist = it.key, playCount = it.value.first, durationMs = it.value.second) }

            topTracks = tracks
                .sortedWith(
                    compareByDescending<MusicTrackEntity> { it.playCount }
                        .thenByDescending { it.totalDurationMs }
                )
                .take(5)
                .map {
                    TopTrackItem(
                        title = it.title,
                        artist = it.artist,
                        playCount = it.playCount,
                        durationMs = it.totalDurationMs,
                        trackKey = it.trackKey,
                        albumArtUri = it.albumArtUri
                    )
                }

            val maxObsessionTrack = tracks.filter { it.playCount >= 3 }
                .maxWithOrNull(
                    compareBy<MusicTrackEntity> { it.playCount }
                        .thenBy { it.totalDurationMs }
                )
            topObsession = maxObsessionTrack?.let {
                TopObsessionItem(
                    trackKey = it.trackKey,
                    title = it.title,
                    artist = it.artist,
                    albumArtUri = it.albumArtUri,
                    playCountInPeriod = it.playCount,
                    durationMsInPeriod = it.totalDurationMs
                )
            }
        } else {
            totalListeningTimeMs = 0L
            uniqueTracksCount = 0
            uniqueArtistsCount = 0
            repeatRatio = 0f
            lyricsCoverageRatio = 0f
            topArtists = emptyList()
            topTracks = emptyList()
            topObsession = null
        }

        // Time of day distribution
        val slotCounts = mutableMapOf<TimeOfDaySlot, Int>()
        for (slot in TimeOfDaySlot.entries) {
            slotCounts[slot] = 0
        }
        val cal = Calendar.getInstance()
        for (session in filteredSessions) {
            cal.timeInMillis = session.startTimeMs
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val slot = when (hour) {
                in 0..5 -> TimeOfDaySlot.NIGHT
                in 6..11 -> TimeOfDaySlot.MORNING
                in 12..17 -> TimeOfDaySlot.AFTERNOON
                else -> TimeOfDaySlot.EVENING
            }
            slotCounts[slot] = (slotCounts[slot] ?: 0) + 1
        }
        val totalSessions = filteredSessions.size.toFloat()
        val timeOfDayDistribution = if (totalSessions > 0f) {
            slotCounts.mapValues { it.value / totalSessions }
        } else {
            emptyMap()
        }

        // Weekly activity (Mon..Sun)
        val dayNames = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
        val dayCounts = IntArray(7)
        val dayDurations = LongArray(7)
        for (session in filteredSessions) {
            cal.timeInMillis = session.startTimeMs
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val idx = when (dow) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> 0
            }
            dayCounts[idx] += 1
            dayDurations[idx] += calculateEffectiveSessionDuration(
                session = session,
                referenceTimestampMs = referenceTimestampMs,
                livePlayback = livePlayback,
                activeSessionId = activeSessionId,
                trackDurationMs = trackMap[session.trackKey]?.totalDurationMs
            )
        }
        val weeklyActivity = dayNames.mapIndexed { i, name ->
            DayOfWeekActivity(
                dayName = name,
                sessionCount = dayCounts[i],
                totalDurationMs = dayDurations[i]
            )
        }

        // Archetype classification
        val nightFraction = timeOfDayDistribution[TimeOfDaySlot.NIGHT] ?: 0f
        val afternoonFraction = timeOfDayDistribution[TimeOfDaySlot.AFTERNOON] ?: 0f
        val archetype = when {
            nightFraction > 0.35f -> ListeningArchetype.NIGHT_OWL
            repeatRatio > 3.0f -> ListeningArchetype.LOYAL_REPEATER
            lyricsCoverageRatio > 0.50f -> ListeningArchetype.ACOUSTIC_BARD
            uniqueTracksCount > 0 && (uniqueArtistsCount.toFloat() / uniqueTracksCount.toFloat()) > 0.70f -> ListeningArchetype.GENRE_NOMAD
            afternoonFraction > 0.35f -> ListeningArchetype.DAY_WORKER
            else -> ListeningArchetype.CASUAL_LISTENER
        }

        return WrappedStats(
            totalListeningTimeMs = totalListeningTimeMs,
            uniqueTracksCount = uniqueTracksCount,
            uniqueArtistsCount = uniqueArtistsCount,
            repeatRatio = repeatRatio,
            lyricsCoverageRatio = lyricsCoverageRatio,
            topArtists = topArtists,
            topTracks = topTracks,
            topObsession = topObsession,
            timeOfDayDistribution = timeOfDayDistribution,
            weeklyActivity = weeklyActivity,
            archetype = archetype
        )
    }

    private fun startOfDayMs(timestamp: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

/**
 * Top-level signature per TASK-WRP-01 and TASK-UI-03 contracts.
 */
fun calculateStats(
    tracks: List<MusicTrackEntity>,
    sessions: List<MusicListeningSessionEntity>,
    timeframe: AnalyticsTimeframe = AnalyticsTimeframe.ALL_TIME,
    referenceTimestampMs: Long = System.currentTimeMillis(),
    livePlayback: LivePlaybackSnapshot? = null
): WrappedStats = WrappedStatsEngine.calculateStats(tracks, sessions, timeframe, referenceTimestampMs, livePlayback)
