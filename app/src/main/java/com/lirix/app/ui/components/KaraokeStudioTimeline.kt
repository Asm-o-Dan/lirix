package com.lirix.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lirix.app.feature.lyrics.KaraokeStudioState
import com.lirix.app.feature.lyrics.KaraokeStudioEngine
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlin.math.abs

internal fun studioTimelineLimit(state: KaraokeStudioState): Long {
    if (state.durationMs > 0) return state.durationMs
    val last = maxOf(state.cursorMs, state.lines.mapNotNull { it.timeMs }.maxOrNull() ?: 0L)
    return if (last > Long.MAX_VALUE - 30000) Long.MAX_VALUE else last + 30000
}

/** A drag only commits at release; cancelling leaves both content and undo untouched. */
@Composable
fun KaraokeStudioTimeline(
    state: KaraokeStudioState,
    onCursor: (Long) -> Unit,
    onSelect: (Int) -> Unit,
    onCommit: (Int, Long) -> Unit,
    onInteraction: () -> Unit,
    waveform: List<Float> = emptyList(),
    waveformDurationMs: Long = 0
) {
    val colors = LirixThemeTokens.colors
    val latest by rememberUpdatedState(state)
    val cursorCallback by rememberUpdatedState(onCursor)
    val selectionCallback by rememberUpdatedState(onSelect)
    val commitCallback by rememberUpdatedState(onCommit)
    val interactionCallback by rememberUpdatedState(onInteraction)
    val density = LocalDensity.current
    val hitRadius = with(density) { 24.dp.toPx() }
    val labelPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    labelPaint.color = colors.textSecondary.toArgb()
    labelPaint.textSize = with(density) { 11.dp.toPx() }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var ghostTime by remember { mutableStateOf<Long?>(null) }

    fun nearest(x: Float, width: Float): Int? {
        val pxPerMs = width / latest.spanMs.toFloat()
        return latest.lines.indices.filter { latest.lines[it].timeMs != null }
            .minByOrNull { abs(width / 2 + (latest.lines[it].timeMs!! - latest.cursorMs) * pxPerMs - x) }
            ?.takeIf { abs(width / 2 + (latest.lines[it].timeMs!! - latest.cursorMs) * pxPerMs - x) <= hitRadius }
    }

    Canvas(
        modifier = Modifier.fillMaxWidth().height(144.dp)
            .semantics { contentDescription = "Шкала времени. Для точной настройки также доступны время строки и кнопки сдвига." }
            .pointerInput(Unit) {
                detectTapGestures { point ->
                    interactionCallback()
                    val index = nearest(point.x, size.width.toFloat())
                    if (index != null) selectionCallback(index) else {
                        val time = latest.cursorMs + ((point.x - size.width / 2f) * latest.spanMs / size.width).toLong()
                        cursorCallback(time.coerceIn(0, studioTimelineLimit(latest)))
                    }
                }
            }
            .pointerInput(Unit) {
                var originTime = 0L
                var originCursor = 0L
                var delta = 0f
                detectHorizontalDragGestures(
                    onDragStart = { point ->
                        interactionCallback()
                        draggedIndex = nearest(point.x, size.width.toFloat())
                        draggedIndex?.let { selectionCallback(it) }
                        originTime = draggedIndex?.let { latest.lines[it].timeMs } ?: 0L
                        originCursor = latest.cursorMs
                        delta = 0f
                    },
                    onDragEnd = {
                        val index = draggedIndex
                        val time = ghostTime
                        if (index != null && time != null) commitCallback(index, time)
                        draggedIndex = null
                        ghostTime = null
                    },
                    onDragCancel = { draggedIndex = null; ghostTime = null },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        delta += amount
                        val shift = (delta * latest.spanMs / size.width).toLong()
                        if (draggedIndex == null) cursorCallback((originCursor - shift).coerceIn(0, studioTimelineLimit(latest)))
                        else ghostTime = (originTime + shift).coerceIn(0, studioTimelineLimit(latest))
                    }
                )
            }
    ) {
        drawRect(colors.surfaceCard)
        val center = size.width / 2
        val scale = size.width / state.spanMs
        if (waveform.isNotEmpty() && waveformDurationMs > 0) {
            val first = (((state.cursorMs - state.spanMs / 2).coerceAtLeast(0).toDouble() / waveformDurationMs) * waveform.size).toInt().coerceIn(0, waveform.lastIndex)
            val end = (((state.cursorMs.toDouble() + state.spanMs / 2) / waveformDurationMs) * waveform.size).toInt().coerceIn(first, waveform.lastIndex)
            val stride = ((end - first) / size.width.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
            for (i in first..end step stride) {
                var value = 0f
                for (j in i..minOf(i + stride - 1, end)) value = maxOf(value, waveform[j])
                val time = (i.toDouble() / waveform.size * waveformDurationMs).toLong()
                val x = center + (time - state.cursorMs) * scale
                if (x in 0f..size.width) {
                    val amplitude = value.coerceIn(0f, 1f) * size.height * 0.22f
                    drawLine(colors.textMuted.copy(alpha = 0.4f), Offset(x, size.height * 0.6f - amplitude),
                        Offset(x, size.height * 0.6f + amplitude), strokeWidth = 2f)
                }
            }
        }
        val step = when { state.spanMs <= 5000 -> 500L; state.spanMs <= 20000 -> 2000L; else -> 10000L }
        val start = (state.cursorMs - state.spanMs / 2).coerceAtLeast(0) / step * step
        repeat((state.spanMs / step + 2).toInt()) { index ->
            val time = start + index * step
            val x = center + (time - state.cursorMs) * scale
            if (x in 0f..size.width && time >= 0) {
                drawLine(colors.borderSubtle, Offset(x, 26.dp.toPx()), Offset(x, size.height))
                drawContext.canvas.nativeCanvas.drawText(KaraokeStudioEngine.formatTime(time).substringBefore('.'), x + 3.dp.toPx(), 18.dp.toPx(), labelPaint)
            }
        }
        state.lines.forEachIndexed { index, line ->
            val time = if (index == draggedIndex) ghostTime ?: line.timeMs else line.timeMs
            if (time != null) {
                val x = center + (time - state.cursorMs) * scale
                if (x in -12f..(size.width + 12)) {
                    val selected = index == state.selectedIndex
                    val color = if (selected) colors.accentPrimary else colors.textSecondary
                    drawLine(color, Offset(x, 32.dp.toPx()), Offset(x, size.height - 12.dp.toPx()), if (selected) 3.dp.toPx() else 1.dp.toPx())
                    drawCircle(color, 6.dp.toPx(), Offset(x, 38.dp.toPx()), style = if (selected) androidx.compose.ui.graphics.drawscope.Fill else Stroke(2.dp.toPx()))
                }
            }
        }
        drawLine(colors.accentSecondary, Offset(center, 0f), Offset(center, size.height), 2.dp.toPx())
    }
}
