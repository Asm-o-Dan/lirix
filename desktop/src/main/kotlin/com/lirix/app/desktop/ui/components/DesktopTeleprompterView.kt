package com.lirix.app.desktop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.player.LrcLine
import com.lirix.app.desktop.ui.theme.desktopReadableForeground
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.ui.theme.LocalLirixTypography
import com.lirix.app.ui.theme.toSentenceCase
import kotlin.math.abs

/**
 * DesktopTeleprompterView: Lirix Desktop 2.0 Teleprompter Monitor.
 *
 * Spec: TASK-UI-PROMPTER-03 / TASK-UI-PROMPTER-FIX-01 / ADR-022
 *
 * Key Architectural Rules:
 * 1. Geometry: Left-aligned bookish ragged right edge poetry layout, max width 640 dp.
 * 2. Reading anchor: 40% for short lines, raised as needed to keep wrapped active text visible.
 * 3. Focus Marker: 3 dp vertical Accent bar strictly on the active line, 8 dp from text.
 * 4. Zero Layout Shift (ZLS): Fixed 44 dp slot reserved to the left of the marker with vector Play icon.
 * 5. Anti-Slop: Vector icons, no emojis, honest contrast >= 4.5:1.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DesktopTeleprompterView(
    lines: List<LrcLine>,
    currentPositionMs: Long,
    onSeekRequested: (Long) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    isDark: Boolean = LirixThemeTokens.colors.isDark
) {
    val colors = LirixThemeTokens.colors
    val typography = LocalLirixTypography.current

    val rawAccent = accentColor ?: colors.accentPrimary
    val effectiveAccent = remember(rawAccent, isDark, colors) {
        desktopReadableForeground(rawAccent, listOf(colors.appBackground, colors.surfaceCard, colors.surfaceElevated))
    }

    // Binary search / find the active line index for current timestamp
    val activeLineIndex = remember(lines, currentPositionMs) {
        activeDesktopLyricIndex(lines, currentPositionMs)
    }

    if (lines.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Ничего не играет. Запустите музыку в любом плеере",
                style = typography.lyricsFar,
                color = colors.textSecondary
            )
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val lazyListState = rememberLazyListState()
        val density = LocalDensity.current
        var activeRowHeightPx by remember(lines, activeLineIndex) { mutableIntStateOf(0) }

        // Keep the reading anchor for short lines; lift wrapped lines until they fit.
        val topPadding = with(density) {
            desktopPrompterAnchorPx(viewportHeight.roundToPx(), activeRowHeightPx, 16.dp.roundToPx()).toDp()
        }
        val bottomPadding = viewportHeight * 0.60f

        // Follow the active line and repeat positioning when its measured height changes.
        LaunchedEffect(lines, activeLineIndex, topPadding) {
            if (activeLineIndex in lines.indices) {
                lazyListState.animateScrollToItem(
                    index = activeLineIndex,
                    scrollOffset = 0
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp)
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, line -> "${line.timestampMs}_$index" }
                ) { index, line ->
                    val distance = abs(index - activeLineIndex)
                    val isActive = index == activeLineIndex

                    TeleprompterLineRow(
                        line = line,
                        isActive = isActive,
                        distance = distance,
                        effectiveAccent = effectiveAccent,
                        onSeekRequested = { onSeekRequested(line.timestampMs) },
                        modifier = if (isActive) Modifier.onSizeChanged { activeRowHeightPx = it.height } else Modifier
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TeleprompterLineRow(
    line: LrcLine,
    isActive: Boolean,
    distance: Int,
    effectiveAccent: Color,
    onSeekRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = LocalLirixTypography.current
    val colors = LirixThemeTokens.colors
    var isHovered by remember { mutableStateOf(false) }

    // Hover fade animation
    val hoverAlpha by animateFloatAsState(
        targetValue = if (isHovered) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "SeekHoverAlpha"
    )

    // Type scale & colors according to distance from active line
    val (textStyle, textColor) = when {
        isActive -> {
            typography.lyricsActive to colors.karaokeActive
        }
        distance == 1 -> {
            typography.lyricsNear to colors.karaokeInactive
        }
        else -> {
            typography.lyricsFar to colors.textMuted
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onSeekRequested() }
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false }
            .padding(vertical = 4.dp)
    ) {
        // ---------------------------------------------------------------------
        // 1. Fixed 44 dp Slot for Zero Layout Shift (placed left of marker)
        // ---------------------------------------------------------------------
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(26.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hoverAlpha > 0.02f) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = colors.surfaceElevated.copy(alpha = hoverAlpha),
                    border = BorderStroke(
                        1.dp,
                        colors.borderSubtle.copy(alpha = hoverAlpha)
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(hoverAlpha)
                        .clickable(enabled = isHovered) { onSeekRequested() }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Перемотка",
                            tint = effectiveAccent,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatPrompterTimestamp(line.timestampMs),
                            style = typography.timecodeAndBadges.copy(fontSize = 10.sp),
                            color = effectiveAccent,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // ---------------------------------------------------------------------
        // 2. Focus Marker: 3 dp vertical Accent bar strictly on active line (8dp from text)
        // ---------------------------------------------------------------------
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(if (isActive) effectiveAccent else Color.Transparent)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // ---------------------------------------------------------------------
        // 3. Poetic Lyrics Text: Bookish left alignment, Literata, Sentence case
        // ---------------------------------------------------------------------
        Text(
            text = line.text.toSentenceCase(),
            style = textStyle,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Formats milliseconds into compact timecode m:ss for the 44 dp seek slot.
 */
private fun formatPrompterTimestamp(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val sStr = if (seconds < 10) "0$seconds" else "$seconds"
    return "$minutes:$sStr"
}
