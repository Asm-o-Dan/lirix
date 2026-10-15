package com.lirix.app.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.feature.lyrics.KaraokeLine
import com.lirix.app.ui.theme.LirixThemeTokens

/**
 * Lyric line data model for synchronized karaoke display.
 */
data class LrcLine(
    val timestampMs: Long,
    val text: String
)

fun KaraokeLine.toLrcLine(): LrcLine = LrcLine(timestampMs, text)

/**
 * Synchronized lyric scroll panel with active line highlighting and auto-centering.
 * Spec: TASK-KMP-09 (.sdd/tasks/TASK-KMP-09.md)
 */
@Composable
fun LyricsScrollPanel(
    lines: List<LrcLine>,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors

    if (lines.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "🎤",
                    fontSize = 44.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Синхронизированный текст (LRC) отсутствует",
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Включите трек в поддерживаемом медиаплеере или загрузите .lrc файл",
                    color = colors.textMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    // Determine current active lyric line index
    val activeIndex = remember(lines, currentPositionMs) {
        val idx = lines.indexOfLast { it.timestampMs <= currentPositionMs }
        if (idx >= 0) idx else 0
    }

    val listState = rememberLazyListState()

    // Smoothly center the active lyric line when activeIndex changes
    LaunchedEffect(activeIndex) {
        if (activeIndex in lines.indices) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -180
            )
        }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 120.dp, horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(
            items = lines,
            key = { index, line -> "${index}_${line.timestampMs}" }
        ) { index, line ->
            val isActive = index == activeIndex

            val textColor by animateColorAsState(
                targetValue = if (isActive) colors.karaokeActive else colors.karaokeInactive,
                animationSpec = tween(durationMillis = 250),
                label = "LyricTextColor"
            )

            val interactionSource = remember { MutableInteractionSource() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null
                    ) {
                        onSeekTo(line.timestampMs)
                    }
                    .padding(vertical = 8.dp, horizontal = 12.dp)
            ) {
                Text(
                    text = line.text,
                    color = textColor,
                    fontSize = if (isActive) 22.sp else 16.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Start,
                    lineHeight = if (isActive) 30.sp else 24.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
