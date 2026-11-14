package com.lirix.app.analytics

import com.lirix.app.storage.AchievementEntity
import com.lirix.app.storage.MusicListeningSessionEntity
import com.lirix.app.storage.MusicTrackEntity
import com.lirix.app.storage.TrackMetadataEntity

/**
 * What the listened music sounded like in a summary period, from stored catalogue facts.
 * Spec: .sdd/specs/wrapped-analytics/sound_summary_and_achievements_v1.md
 *
 * Every figure is weighted by registered listening time in the period and is shown only when at
 * least [SoundSummaryEngine.MIN_TRACKS] listened tracks carry that fact. Null or empty means
 * "not enough data", never zero.
 */
enum class SoundTrait { ENERGY, VALENCE, DANCEABILITY, ACOUSTICNESS }

/** Quadrant of average energy and positivity; both thresholds are 0.5. */
enum class PeriodMood { BRIGHT_DRIVE, DARK_DRIVE, BRIGHT_CALM, DARK_CALM }

enum class SoundRecordKind { FASTEST, MOST_ENERGETIC, CALMEST, HAPPIEST }

data class SoundMeter(val trait: SoundTrait, val percent: Int)
data class DecadeShare(val decade: Int, val percent: Int)
/** [value] is beats per minute for [SoundRecordKind.FASTEST] and percent 0..100 for the others. */
data class SoundRecord(val kind: SoundRecordKind, val trackKey: String, val title: String, val artist: String, val value: Int)

data class SoundSummary(
    val genres: List<GenreShare> = emptyList(),
    /** Listened tracks of the period with a known genre / with any confirmed catalogue row / all of them. */
    val tracksWithGenre: Int = 0,
    val tracksWithData: Int = 0,
    val tracksListened: Int = 0,
    val meters: List<SoundMeter> = emptyList(),
    val averageBpm: Int? = null,
    val mood: PeriodMood? = null,
    val decades: List<DecadeShare> = emptyList(),
    /** Share of listening time in a major key among tracks whose mode is known. */
    val majorPercent: Int? = null,
    val records: List<SoundRecord> = emptyList()
) {
    val isEmpty: Boolean get() = genres.isEmpty() && meters.isEmpty() && averageBpm == null && decades.isEmpty() && majorPercent == null
}

object SoundSummaryEngine {
    const val MIN_TRACKS = 5
    private const val TOP_GENRES = 5
    private const val TOP_DECADES = 4

    fun build(
        tracks: List<MusicTrackEntity>,
        sessions: List<MusicListeningSessionEntity>,
        metadata: List<TrackMetadataEntity>,
        timeframe: AnalyticsTimeframe,
        referenceTimestampMs: Long
    ): SoundSummary {
        val trackByKey = tracks.associateBy { it.trackKey }
        val counted = ListeningIntervals.project(sessions, WrappedStatsEngine.periodStart(timeframe, referenceTimestampMs), referenceTimestampMs)
        val listenedMs: Map<String, Long> = when {
            counted.isNotEmpty() -> counted.groupBy { it.trackKey }.mapValues { entry -> entry.value.sumOf { it.durationMs.coerceAtLeast(0L) } }
            // Libraries recorded before sessions existed keep only per-track totals.
            timeframe == AnalyticsTimeframe.ALL_TIME -> tracks.associate { it.trackKey to it.totalDurationMs }
            else -> emptyMap()
        }
        val found = metadata.filter { it.status == "FOUND" }.associateBy { it.trackKey }
        // Sorted so that neither database order nor map order can change a result.
        val listened = listenedMs.entries.filter { (key, ms) -> ms > 0 && trackByKey[key]?.let { !WrappedStatsEngine.isUnknownArtist(it.artist) } == true }
            .sortedBy { it.key }.map { Listened(trackByKey.getValue(it.key), it.value, found[it.key]) }

        fun <T : Any> known(value: (TrackMetadataEntity) -> T?): List<Pair<Listened, T>> =
            listened.mapNotNull { item -> item.meta?.let(value)?.let { item to it } }.takeIf { it.size >= MIN_TRACKS }.orEmpty()

        fun average(value: (TrackMetadataEntity) -> Double?): Double? {
            val items = known(value)
            val weight = items.sumOf { it.first.ms }
            return if (weight > 0) items.sumOf { it.second * it.first.ms } / weight else null
        }

        fun <K : Comparable<K>> shares(items: List<Pair<Listened, K>>, limit: Int): List<Pair<K, Int>> {
            val total = items.sumOf { it.first.ms }
            if (total <= 0) return emptyList()
            return items.groupBy({ it.second }, { it.first.ms }).mapValues { it.value.sum() }.entries
                .sortedWith(compareByDescending<Map.Entry<K, Long>> { it.value }.thenBy { it.key })
                .take(limit).map { it.key to percent(it.value, total) }
        }

        val genreItems = known { it.genreList().firstOrNull() }
        val energy = average { it.energy }
        val valence = average { it.valence }
        val meters = listOf(
            SoundTrait.ENERGY to energy, SoundTrait.VALENCE to valence,
            SoundTrait.DANCEABILITY to average { it.danceability }, SoundTrait.ACOUSTICNESS to average { it.acousticness }
        ).mapNotNull { (trait, value) -> value?.let { SoundMeter(trait, unitPercent(it)) } }

        val modeItems = known { it.musicalMode?.takeIf { mode -> mode == 0 || mode == 1 } }
        val modeTotal = modeItems.sumOf { it.first.ms }

        fun record(kind: SoundRecordKind, value: (TrackMetadataEntity) -> Double?, highest: Boolean, shown: (Double) -> Int): SoundRecord? {
            val items = known(value)
            val best = (if (highest) items.maxWithOrNull(compareBy<Pair<Listened, Double>> { it.second }.thenByDescending { it.first.track.trackKey })
            else items.minWithOrNull(compareBy<Pair<Listened, Double>> { it.second }.thenBy { it.first.track.trackKey })) ?: return null
            return SoundRecord(kind, best.first.track.trackKey, best.first.track.title, best.first.track.artist, shown(best.second))
        }

        return SoundSummary(
            genres = shares(genreItems, TOP_GENRES).map { GenreShare(it.first, it.second) },
            tracksWithGenre = listened.count { it.meta?.genreList()?.isNotEmpty() == true },
            tracksWithData = listened.count { it.meta != null },
            tracksListened = listened.size,
            meters = meters,
            averageBpm = average { it.bpm }?.let { Math.round(it).toInt() },
            mood = if (energy != null && valence != null) when {
                energy >= 0.5 && valence >= 0.5 -> PeriodMood.BRIGHT_DRIVE
                energy >= 0.5 -> PeriodMood.DARK_DRIVE
                valence >= 0.5 -> PeriodMood.BRIGHT_CALM
                else -> PeriodMood.DARK_CALM
            } else null,
            decades = shares(known { it.releaseYear?.let { year -> year / 10 * 10 } }, TOP_DECADES).map { DecadeShare(it.first, it.second) },
            majorPercent = if (modeTotal > 0) percent(modeItems.filter { it.second == 1 }.sumOf { it.first.ms }, modeTotal) else null,
            records = listOfNotNull(
                record(SoundRecordKind.FASTEST, { it.bpm }, highest = true) { Math.round(it).toInt() },
                record(SoundRecordKind.MOST_ENERGETIC, { it.energy }, highest = true, shown = ::unitPercent),
                record(SoundRecordKind.CALMEST, { it.energy }, highest = false, shown = ::unitPercent),
                record(SoundRecordKind.HAPPIEST, { it.valence }, highest = true, shown = ::unitPercent)
            )
        )
    }

    private class Listened(val track: MusicTrackEntity, val ms: Long, val meta: TrackMetadataEntity?)

    private fun percent(part: Long, total: Long): Int = ((part * 100 + total / 2) / total).toInt()
    private fun unitPercent(value: Double): Int = Math.round(value * 100).toInt().coerceIn(0, 100)
}

/**
 * Achievements that depend on catalogue facts about tracks in the library. They exist only where
 * track data can be loaded (Android); progress is the number of library tracks with a confirmed fact.
 */
object SoundAchievements {
    const val CATEGORY = "SOUND"

    fun definitions(): List<AchievementEntity> = listOf(
        AchievementEntity("SOUND_CARD_INDEX", "Картотека", "Данные каталогов найдены для 25 треков", "ic_disc", maxProgress = 25, tier = "BRONZE", category = CATEGORY),
        AchievementEntity("SOUND_GENRES_3", "Палитра", "В библиотеке треки 3 разных жанров", "ic_explore", maxProgress = 3, tier = "BRONZE", category = CATEGORY),
        AchievementEntity("SOUND_NO_WORDS", "Без слов", "5 инструментальных треков", "ic_music_note", maxProgress = 5, tier = "BRONZE", category = CATEGORY),
        AchievementEntity("SOUND_GENRE_LOYAL", "Верность жанру", "50 треков одного жанра", "ic_star", maxProgress = 50, tier = "SILVER", category = CATEGORY),
        AchievementEntity("SOUND_SPRINTER", "Спринтер", "10 треков быстрее 160 уд/мин", "ic_timer", maxProgress = 10, tier = "SILVER", category = CATEGORY),
        AchievementEntity("SOUND_SLOW_DANCE", "Медленный танец", "10 треков медленнее 80 уд/мин", "ic_timer", maxProgress = 10, tier = "SILVER", category = CATEGORY),
        AchievementEntity("SOUND_CHARGE", "Заряд", "25 треков с высокой энергией", "ic_sun", maxProgress = 25, tier = "SILVER", category = CATEGORY),
        AchievementEntity("SOUND_QUIET_HARBOR", "Тихая гавань", "15 спокойных треков", "ic_moon", maxProgress = 15, tier = "SILVER", category = CATEGORY),
        AchievementEntity("SOUND_SUNNY_SIDE", "Солнечная сторона", "20 светлых по настроению треков", "ic_sun", maxProgress = 20, tier = "SILVER", category = CATEGORY),
        AchievementEntity("SOUND_BLUE_HOUR", "Синий час", "20 мрачных по настроению треков", "ic_moon", maxProgress = 20, tier = "SILVER", category = CATEGORY),
        AchievementEntity("SOUND_DANCE_FLOOR", "Танцпол", "20 танцевальных треков", "ic_music_note", maxProgress = 20, tier = "SILVER", category = CATEGORY),
        AchievementEntity("SOUND_UNPLUGGED", "Без розетки", "10 акустических треков", "ic_music_note", maxProgress = 10, tier = "SILVER", category = CATEGORY),
        AchievementEntity("SOUND_RETRO", "Ретро", "10 треков, выпущенных до 1990 года", "ic_disc", maxProgress = 10, tier = "SILVER", category = CATEGORY),
        AchievementEntity("SOUND_GENRES_7", "Всеядный", "В библиотеке треки 7 разных жанров", "ic_explore", maxProgress = 7, tier = "GOLD", category = CATEGORY),
        AchievementEntity("SOUND_TIME_TRAVELER", "Путешественник во времени", "Треки из 5 разных десятилетий", "ic_explore", maxProgress = 5, tier = "GOLD", category = CATEGORY),
        AchievementEntity("SOUND_ALL_KEYS", "Все двенадцать", "Треки во всех 12 тональностях", "ic_star", maxProgress = 12, tier = "GOLD", category = CATEGORY)
    )

    /** Achievement id -> current value. Unknown-artist tracks and unconfirmed rows are not counted. */
    fun metrics(tracks: List<MusicTrackEntity>, metadata: List<TrackMetadataEntity>): Map<String, Int> {
        val library = tracks.filterNot { WrappedStatsEngine.isUnknownArtist(it.artist) }.map { it.trackKey }.toSet()
        val rows = metadata.filter { it.status == "FOUND" && it.trackKey in library }.distinctBy { it.trackKey }
        val genres = rows.mapNotNull { it.genreList().firstOrNull()?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
        fun count(value: (TrackMetadataEntity) -> Double?, test: (Double) -> Boolean) = rows.count { row -> value(row)?.let(test) == true }
        return mapOf(
            "SOUND_CARD_INDEX" to rows.size,
            "SOUND_GENRES_3" to genres.distinct().size,
            "SOUND_GENRES_7" to genres.distinct().size,
            "SOUND_GENRE_LOYAL" to (genres.groupingBy { it }.eachCount().values.maxOrNull() ?: 0),
            "SOUND_NO_WORDS" to count({ it.instrumentalness }) { it >= 0.7 },
            "SOUND_SPRINTER" to count({ it.bpm }) { it > 160.0 },
            "SOUND_SLOW_DANCE" to count({ it.bpm }) { it < 80.0 },
            "SOUND_CHARGE" to count({ it.energy }) { it >= 0.8 },
            "SOUND_QUIET_HARBOR" to count({ it.energy }) { it <= 0.35 },
            "SOUND_SUNNY_SIDE" to count({ it.valence }) { it >= 0.7 },
            "SOUND_BLUE_HOUR" to count({ it.valence }) { it <= 0.3 },
            "SOUND_DANCE_FLOOR" to count({ it.danceability }) { it >= 0.7 },
            "SOUND_UNPLUGGED" to count({ it.acousticness }) { it >= 0.7 },
            "SOUND_RETRO" to rows.count { row -> row.releaseYear?.let { it < 1990 } == true },
            "SOUND_TIME_TRAVELER" to rows.mapNotNull { it.releaseYear?.let { year -> year / 10 } }.distinct().size,
            "SOUND_ALL_KEYS" to rows.mapNotNull { it.musicalKey?.takeIf { key -> key in 0..11 } }.distinct().size
        )
    }
}
