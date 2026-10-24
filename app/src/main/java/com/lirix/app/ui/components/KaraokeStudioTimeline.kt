package com.lirix.app.ui.components

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.R
import com.lirix.app.feature.lyrics.KaraokeStudioState
import com.lirix.app.feature.lyrics.KaraokeStudioEngine
import com.lirix.app.ui.theme.LirixThemeTokens

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
    labelPaint.textSize = with(density) { 11.sp.toPx() }
    val description = stringResource(R.string.studio_timeline_description)
    val cursorDescription = stringResource(R.string.studio_cursor_position, KaraokeStudioEngine.formatTime(state.cursorMs))
    val backLabel = stringResource(R.string.studio_cursor_back)
    val forwardLabel = stringResource(R.string.studio_cursor_forward)
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var ghostTime by remember { mutableStateOf<Long?>(null) }

    fun nearest(x: Float, width: Float): Int? {
        return StudioTimelineGeometry(latest.cursorMs, latest.spanMs, width.coerceAtLeast(1f), studioTimelineLimit(latest))
            .nearest(latest, x, hitRadius)
    }

    Canvas(
        modifier = Modifier.fillMaxWidth().height(144.dp).clipToBounds()
            .semantics {
                contentDescription = description
                stateDescription = cursorDescription
                customActions = listOf(
                    CustomAccessibilityAction(backLabel) {
                        interactionCallback(); cursorCallback((latest.cursorMs - 100).coerceAtLeast(0)); true
                    },
                    CustomAccessibilityAction(forwardLabel) {
                        interactionCallback()
                        cursorCallback(if (latest.cursorMs > studioTimelineLimit(latest) - 100) studioTimelineLimit(latest) else latest.cursorMs + 100)
                        true
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { point ->
                    interactionCallback()
                    val index = nearest(point.x, size.width.toFloat())
                    if (index != null) selectionCallback(index) else {
                        cursorCallback(StudioTimelineGeometry(latest.cursorMs, latest.spanMs, size.width.toFloat(), studioTimelineLimit(latest)).timeAt(point.x))
                    }
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startState = latest
                    val index = nearest(down.position.x, size.width.toFloat())
                    val originTime = index?.let { startState.lines[it].timeMs } ?: 0L
                    val geometry = StudioTimelineGeometry(startState.cursorMs, startState.spanMs,
                        size.width.toFloat(), studioTimelineLimit(startState))
                    val drag = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (drag != null) {
                        interactionCallback()
                        cursorCallback(startState.cursorMs)
                        draggedIndex = index
                        index?.let { selectionCallback(it) }
                        fun update(x: Float) {
                            val distance = x - down.position.x
                            if (index == null) cursorCallback(geometry.shifted(startState.cursorMs, -distance))
                            else ghostTime = geometry.shifted(originTime, distance)
                        }
                        try {
                            update(drag.position.x)
                            val completed = horizontalDrag(drag.id) { change ->
                                change.consume()
                                update(change.position.x)
                            }
                            if (completed && index != null) ghostTime?.let { commitCallback(index, it) }
                        } finally {
                            draggedIndex = null
                            ghostTime = null
                        }
                    }
                }
            }

    ) {
        drawRect(colors.surfaceCard)
        val center = size.width / 2
        val geometry = StudioTimelineGeometry(state.cursorMs, state.spanMs, size.width.coerceAtLeast(1f), studioTimelineLimit(state))
        if (waveform.isNotEmpty() && waveformDurationMs > 0) {
            val first = (((state.cursorMs - state.spanMs / 2).coerceAtLeast(0).toDouble() / waveformDurationMs) * waveform.size).toInt().coerceIn(0, waveform.lastIndex)
            val end = (((state.cursorMs.toDouble() + state.spanMs / 2) / waveformDurationMs) * waveform.size).toInt().coerceIn(first, waveform.lastIndex)
            val stride = ((end - first) / size.width.coerceAtLeast(1f)).toInt().coerceAtLeast(1)
            for (i in first..end step stride) {
                var value = 0f
                for (j in i..minOf(i + stride - 1, end)) value = maxOf(value, waveform[j])
                val time = (i.toDouble() / waveform.size * waveformDurationMs).toLong()
                val x = geometry.xAt(time)
                if (x in 0f..size.width) {
                    val amplitude = value.coerceIn(0f, 1f) * size.height * 0.22f
                    drawLine(colors.textMuted.copy(alpha = 0.4f), Offset(x, size.height * 0.6f - amplitude),
                        Offset(x, size.height * 0.6f + amplitude), strokeWidth = 2f)
                }
            }
        }
        val step = studioTickStep(state.spanMs, size.width, labelPaint.measureText(studioTickLabel(state.cursorMs, state.spanMs)))
        val rulerBottom = maxOf(30.dp.toPx(), labelPaint.fontMetrics.descent - labelPaint.fontMetrics.ascent + 8.dp.toPx())
        val labelBaseline = 4.dp.toPx() - labelPaint.fontMetrics.ascent
        val start = (state.cursorMs - state.spanMs / 2).coerceAtLeast(0) / step * step
        repeat((state.spanMs / step + 2).toInt()) { index ->
            if (start > Long.MAX_VALUE - index * step) return@repeat
            val time = start + index * step
            val x = geometry.xAt(time)
            if (x in 0f..size.width && time >= 0) {
                drawLine(colors.borderSubtle, Offset(x, rulerBottom), Offset(x, size.height))
                val label = studioTickLabel(time, state.spanMs)
                val labelX = x + 3.dp.toPx()
                if (labelX + labelPaint.measureText(label) <= size.width) {
                    drawContext.canvas.nativeCanvas.drawText(label, labelX, labelBaseline, labelPaint)
                }
            }
        }
        state.lines.forEachIndexed { index, line ->
            val time = if (index == draggedIndex) ghostTime ?: line.timeMs else line.timeMs
            if (time != null) {
                val x = geometry.xAt(time)
                if (x in -12f..(size.width + 12)) {
                    val selected = index == state.selectedIndex
                    val color = if (selected) colors.accentPrimary else colors.textSecondary
                    drawLine(color, Offset(x, rulerBottom + 4.dp.toPx()), Offset(x, size.height - 12.dp.toPx()), if (selected) 3.dp.toPx() else 1.dp.toPx())
                    drawCircle(color, 6.dp.toPx(), Offset(x, rulerBottom + 10.dp.toPx()), style = if (selected) androidx.compose.ui.graphics.drawscope.Fill else Stroke(2.dp.toPx()))
                }
            }
        }
        drawLine(colors.accentSecondary, Offset(center, 0f), Offset(center, size.height), 2.dp.toPx())
    }
}
