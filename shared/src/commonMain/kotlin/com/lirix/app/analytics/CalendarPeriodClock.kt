package com.lirix.app.analytics

import com.lirix.app.ingestion.getSystemElapsedRealtimeMs
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.util.TimeZone

data class CalendarPeriodTick(val referenceTimestampMs: Long, val dayStartMs: Long, val revision: Long = 0)

fun calendarPeriodTick(now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): CalendarPeriodTick {
    val id = zone.toZoneId()
    val start = java.time.Instant.ofEpochMilli(now).atZone(id).toLocalDate().atStartOfDay(id).toInstant().toEpochMilli()
    return CalendarPeriodTick(now, start)
}

/** Cold UI-only clock: emits on resume, midnight, timezone change or a wall-clock correction. */
fun calendarPeriodChanges(
    now: () -> Long = System::currentTimeMillis,
    monotonicNow: () -> Long = ::getSystemElapsedRealtimeMs,
    zone: () -> TimeZone = TimeZone::getDefault
) = flow {
    var previous: CalendarPeriodTick? = null
    var previousWall: Long? = null
    var previousMono: Long? = null
    var revision = 0L
    while (currentCoroutineContext().isActive) {
        val wall = now()
        val mono = monotonicNow()
        val currentZone = zone()
        val tick = calendarPeriodTick(wall, currentZone)
        val corrected = previousWall != null && previousMono != null &&
            kotlin.math.abs((wall - previousWall!!) - (mono - previousMono!!)) > 1_000L
        if (previous == null || previous!!.dayStartMs != tick.dayStartMs || corrected) {
            emit(tick.copy(revision = revision++))
            previous = tick
        }
        previousWall = wall; previousMono = mono
        val id = currentZone.toZoneId()
        val nextDay = java.time.Instant.ofEpochMilli(wall).atZone(id).toLocalDate().plusDays(1)
            .atStartOfDay(id).toInstant().toEpochMilli()
        // Midnight is exact; the minute check detects timezone/time edits while the screen stays open.
        delay(minOf(60_000L, (nextDay - wall).coerceAtLeast(1L)))
    }
}
