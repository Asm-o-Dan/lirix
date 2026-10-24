package com.lirix.app.ui.components

import com.lirix.app.feature.lyrics.KaraokeStudioEngine
import com.lirix.app.feature.lyrics.KaraokeStudioState
import kotlin.math.abs
import kotlin.math.roundToLong

/** One coordinate system for ruler drawing, hit testing, seeking and marker drags. */
internal class StudioTimelineGeometry(val cursorMs: Long, val spanMs: Long, val widthPx: Float, val limitMs: Long) {
    init {
        require(cursorMs >= 0 && spanMs > 0 && widthPx.isFinite() && widthPx > 0 && limitMs >= 0)
    }

    fun xAt(timeMs: Long): Float = (widthPx / 2.0 + (timeMs - cursorMs).toDouble() * widthPx / spanMs).toFloat()

    fun timeAt(x: Float): Long = shifted(cursorMs, x - widthPx / 2)

    fun shifted(originMs: Long, distancePx: Float): Long {
        val delta = (distancePx.toDouble() * spanMs / widthPx).roundToLong()
        val value = if (delta > 0 && originMs > Long.MAX_VALUE - delta) Long.MAX_VALUE else originMs + delta
        return value.coerceIn(0, limitMs)
    }

    fun nearest(state: KaraokeStudioState, x: Float, radiusPx: Float): Int? = state.lines.indices
        .filter { state.lines[it].timeMs != null }
        .minWithOrNull(compareBy<Int> { abs(xAt(state.lines[it].timeMs!!) - x) }
            .thenBy { if (it == state.selectedIndex) 0 else 1 }.thenBy { it })
        ?.takeIf { abs(xAt(state.lines[it].timeMs!!) - x) <= radiusPx }
}

internal fun studioTickLabel(timeMs: Long, spanMs: Long): String = KaraokeStudioEngine.formatTime(timeMs)
    .let { if (spanMs <= 5000) it.dropLast(2) else it.substringBefore('.') }

internal fun studioTickStep(spanMs: Long, widthPx: Float, labelWidthPx: Float): Long =
    listOf(500L, 1000L, 2000L, 5000L, 10000L, 15000L, 30000L, 60000L, 120000L)
        .firstOrNull { widthPx.toDouble() * it / spanMs >= labelWidthPx + 12f } ?: 120000L
