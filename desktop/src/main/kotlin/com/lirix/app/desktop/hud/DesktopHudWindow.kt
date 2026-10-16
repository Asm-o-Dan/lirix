package com.lirix.app.desktop.hud

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.ui.player.LrcLine
import com.lirix.app.ui.theme.LirixThemeTokens

/**
 * Floating, click-through karaoke HUD window for gaming and desktop multitasking.
 * Spec: TASK-KMP-11 (.sdd/tasks/TASK-KMP-11.md)
 */
@Composable
fun DesktopHudWindow(
    visible: Boolean,
    onCloseRequest: () -> Unit,
    playback: LivePlaybackSnapshot?,
    lyricsLines: List<LrcLine> = emptyList(),
    initialLocked: Boolean = true
) {
    if (!visible) return

    val windowState = rememberWindowState(
        position = WindowPosition(Alignment.BottomCenter),
        size = DpSize(620.dp, 160.dp)
    )

    var isLocked by remember { mutableStateOf(initialLocked) }

    Window(
        onCloseRequest = onCloseRequest,
        state = windowState,
        title = "Lirix Karaoke HUD",
        transparent = true,
        undecorated = true,
        alwaysOnTop = true,
        resizable = false
    ) {
        val colors = LirixThemeTokens.colors

        // Apply Win32 click-through extended styles
        LaunchedEffect(isLocked, window) {
            WindowsWindowStyles.setClickThrough(window, isLocked)
        }

        // Active lyric computation
        val currentPositionMs = playback?.currentPositionMs() ?: 0L
        val activeIndex = remember(lyricsLines, currentPositionMs) {
            if (lyricsLines.isEmpty()) -1
            else {
                val idx = lyricsLines.indexOfLast { it.timestampMs <= currentPositionMs }
                if (idx >= 0) idx else 0
            }
        }

        val prevLine = if (activeIndex > 0 && activeIndex in lyricsLines.indices) lyricsLines[activeIndex - 1].text else null
        val currLine = if (activeIndex in lyricsLines.indices) lyricsLines[activeIndex].text else (playback?.title ?: "Lirix HUD — Ожидание трека")
        val nextLine = if (activeIndex in 0 until lyricsLines.size - 1) lyricsLines[activeIndex + 1].text else null

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp)),
                color = if (isLocked) colors.appBackground.copy(alpha = 0.82f) else colors.surfaceCard,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    width = if (isLocked) 1.dp else 2.dp,
                    color = if (isLocked) colors.borderSubtle.copy(alpha = 0.5f) else colors.accentPrimary
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Bar (Visible and draggable in Unlocked mode)
                    if (!isLocked) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        window.setLocation(
                                            window.x + dragAmount.x.toInt(),
                                            window.y + dragAmount.y.toInt()
                                        )
                                    }
                                }
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DragHandle,
                                    contentDescription = null,
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Перетащите HUD за эту панель",
                                    color = colors.textMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Lock button
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.accentPrimary)
                                        .clickable { isLocked = true }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Lock,
                                        contentDescription = null,
                                        tint = colors.appBackground,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "Зафиксировать (Сквозной клик)",
                                        color = colors.appBackground,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Close HUD button
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(colors.surfaceElevated)
                                        .clickable { onCloseRequest() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Закрыть HUD",
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Lyrics 3-Line Display Capsule
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Previous Line
                        if (prevLine != null) {
                            Text(
                                text = prevLine,
                                color = colors.textMuted.copy(alpha = 0.6f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                        }

                        // Current Active Line (Accent highlighted)
                        Text(
                            text = currLine,
                            color = colors.karaokeActive,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )

                        // Next Line
                        if (nextLine != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = nextLine,
                                color = colors.textSecondary.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Indicator Pill at bottom in Locked mode
                    if (isLocked) {
                        Text(
                            text = "Lirix HUD • Нажмите Ctrl+Alt+H для настройки положения",
                            color = colors.textMuted.copy(alpha = 0.45f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}
