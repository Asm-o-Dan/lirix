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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.player.LrcLine
import com.lirix.app.ui.theme.ContrastClampHelper
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
 * 2. 40% Viewport Height Anchor: Active sung line is locked at 40% height with spring autoscroll.
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
    val effectiveAccent = remember(rawAccent, isDark) {
        ContrastClampHelper.clampAccentContrast(rawAccent, isDark)
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
                color = if (isDark) LirixThemeTokens.Mist else LirixThemeTokens.Pencil
            )
        }
        return
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportHeight = maxHeight
        val lazyListState = rememberLazyListState()

        // 40% top padding locks the active line at 40% viewport height
        val topPadding = viewportHeight * 0.40f
        val bottomPadding = viewportHeight * 0.60f

        // Smoothly animate active line to 40% height anchor
        LaunchedEffect(activeLineIndex) {
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
                        isDark = isDark,
                        onSeekRequested = { onSeekRequested(line.timestampMs) }
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
    isDark: Boolean,
    onSeekRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = LocalLirixTypography.current
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
            typography.lyricsActive to (if (isDark) LirixThemeTokens.Chalk else LirixThemeTokens.Ink)
        }
        distance == 1 -> {
            typography.lyricsNear to (if (isDark) LirixThemeTokens.Mist else LirixThemeTokens.Pencil)
        }
        else -> {
            val baseColor = if (isDark) LirixThemeTokens.Mist else LirixThemeTokens.Pencil
            typography.lyricsFar to baseColor
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
                    color = (if (isDark) LirixThemeTokens.Slate else LirixThemeTokens.Vellum).copy(alpha = hoverAlpha),
                    border = BorderStroke(
                        1.dp,
                        (if (isDark) LirixThemeTokens.Hairline else LirixThemeTokens.HairlineLight).copy(alpha = hoverAlpha)
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
