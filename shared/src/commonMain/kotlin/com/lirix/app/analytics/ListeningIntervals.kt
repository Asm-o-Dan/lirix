package com.lirix.app.analytics

import com.lirix.app.storage.ListeningSessionEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.util.TreeMap

@Serializable
data class ListeningInterval(val startMs: Long, val endMs: Long) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/** Persist actual PLAYING spans. Position, playback speed and wall-clock gaps never become duration. */
object ListeningIntervals {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(intervals: List<ListeningInterval>): String = json.encodeToString(intervals)

    fun read(session: ListeningSessionEntity): List<ListeningInterval> {
        if (session.listeningIntervalsJson.isNotBlank()) {
            return runCatching { json.decodeFromString<List<ListeningInterval>>(session.listeningIntervalsJson) }
                .getOrDefault(emptyList()).map { it.copy(startMs = it.startMs.coerceAtLeast(0L)) }
                .filter { it.endMs > it.startMs }
        }
        // Legacy rows cannot reveal pauses. Bound their estimate by the recorded physical span.
        val end = session.endTimeMs.coerceAtLeast(session.startTimeMs)
        val start = maxOf(0L, session.startTimeMs, end - session.durationMs.coerceAtLeast(0L))
        return if (end > start) listOf(ListeningInterval(start, end)) else emptyList()
    }

    fun confirmedSpan(previousMono: Long, nowMono: Long, previousWall: Long, nowWall: Long, wasPlaying: Boolean): ListeningInterval? {
        val elapsed = nowMono - previousMono
        if (!wasPlaying || elapsed <= 0L) return null
        // Re-anchor after a wall-clock correction; attributing the ambiguous gap would fabricate dates.
        if (kotlin.math.abs((nowWall - previousWall) - elapsed) > 1_000L) return null
        return ListeningInterval(previousWall, previousWall + elapsed)
    }

    fun append(session: ListeningSessionEntity, span: ListeningInterval): ListeningSessionEntity {
        val intervals = read(session).toMutableList()
        val previous = intervals.lastOrNull()
        if (previous != null && previous.endMs == span.startMs) {
            intervals[intervals.lastIndex] = previous.copy(endMs = span.endMs)
        } else intervals.add(span)
        return session.copy(endTimeMs = maxOf(session.endTimeMs, span.endMs),
            durationMs = intervals.sumOf { it.durationMs }, listeningIntervalsJson = encode(intervals))
    }

    /** Clip once for totals, tops and charts; never count overlapping sources twice. */
    fun project(sessions: List<ListeningSessionEntity>, fromMs: Long, untilMs: Long): List<ListeningSessionEntity> {
        val occupied = TreeMap<Long, Long>()
        return sessions.sortedWith(compareBy<ListeningSessionEntity> { it.startTimeMs }.thenBy { it.id }).mapNotNull { session ->
            val clipped = mutableListOf<ListeningInterval>()
            for (interval in read(session).sortedBy { it.startMs }) {
                var start = maxOf(fromMs, interval.startMs)
                val end = minOf(untilMs, interval.endMs)
                while (start < end) {
                    val covering = occupied.floorEntry(start)
                    if (covering != null && covering.value > start) { start = covering.value; continue }
                    val next = occupied.ceilingKey(start)
                    val availableEnd = minOf(end, next ?: end)
                    if (availableEnd > start) {
                        clipped.add(ListeningInterval(start, availableEnd))
                        occupied[start] = availableEnd
                    }
                    start = availableEnd
                }
            }
            if (clipped.isEmpty() && session.startTimeMs !in fromMs..untilMs) null
            else session.copy(startTimeMs = clipped.firstOrNull()?.startMs ?: session.startTimeMs,
                endTimeMs = clipped.lastOrNull()?.endMs ?: session.startTimeMs,
                durationMs = clipped.sumOf { it.durationMs }, listeningIntervalsJson = encode(clipped))
        }
    }

    fun dailyDurations(sessions: List<ListeningSessionEntity>): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        val zone = java.time.ZoneId.systemDefault()
        for (session in sessions) for (interval in read(session)) {
            var cursor = interval.startMs
            while (cursor < interval.endMs) {
                val day = java.time.Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
                val startDay = day.atStartOfDay(zone).toInstant().toEpochMilli()
                val nextDay = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val end = minOf(interval.endMs, nextDay)
                result[startDay] = (result[startDay] ?: 0L) + end - cursor
                cursor = end
            }
        }
        return result
    }

    fun overlapsLocalWindow(intervals: List<ListeningInterval>, startHour: Int, endHour: Int, endMinute: Int = 0): Boolean {
        val zone = java.time.ZoneId.systemDefault()
        for (interval in intervals) {
            var day = java.time.Instant.ofEpochMilli(interval.startMs).atZone(zone).toLocalDate()
            while (day.atStartOfDay(zone).toInstant().toEpochMilli() < interval.endMs) {
                val start = day.atTime(startHour, 0).atZone(zone).toInstant().toEpochMilli()
                val end = day.atTime(endHour, endMinute).atZone(zone).toInstant().toEpochMilli()
                if (interval.startMs < end && interval.endMs > start) return true
                day = day.plusDays(1)
            }
        }
        return false
    }
}
