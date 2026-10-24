package com.lirix.app.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.desktop.lyrics.DesktopLyricsState
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.platform.MediaIngressProvider
import com.lirix.app.ui.theme.LirixThemeTokens
import java.io.File

/**
 * Compact floating Mini-Player widget inspired by AIMP's tray mini-dock.
 * Highly functional for side-by-side multitasking while coding or gaming.
 * Spec: TASK-UI-PROMPTER-FIX-01 §2
 */
@Composable
fun DesktopCompactWidget(
    playback: LivePlaybackSnapshot?,
    ingressProvider: MediaIngressProvider,
    lyricsState: DesktopLyricsState = DesktopLyricsState(),
    onExpandToStudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors
    val currentPositionMs = playback?.currentPositionMs() ?: 0L
    val durationMs = playback?.durationMs ?: 0L

    // Safely load album cover
    val albumArtBitmap = remember(playback?.albumArtUri) {
        playback?.albumArtUri?.let { path ->
            try {
                val file = File(path)
                if (file.exists() && file.isFile && file.length() > 0) {
                    file.inputStream().buffered().use { androidx.compose.ui.res.loadImageBitmap(it) }
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    // Active lyric line calculation
    val activeLineText = remember(lyricsState.lrcLines, currentPositionMs) {
        if (lyricsState.lrcLines.isEmpty()) {
            "..."
        } else {
            val matching = lyricsState.lrcLines.lastOrNull { it.timestampMs <= currentPositionMs }
            matching?.text ?: lyricsState.lrcLines.first().text
        }
    }

    Surface(
        modifier = modifier
            .width(380.dp)
            .height(110.dp),
        shape = RoundedCornerShape(14.dp),
        color = colors.surfaceCard,
        border = BorderStroke(1.dp, colors.borderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Album Art + Track Info + Studio Expand Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Mini 44x44 Album Art
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (albumArtBitmap != null) {
                        Image(
                            bitmap = albumArtBitmap,
                            contentDescription = "Cover",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = "Music",
                            tint = colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Track Info & Active Lyric Line
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (lyricsState.displayTitle.isNotBlank()) lyricsState.displayTitle else (playback?.title ?: com.lirix.app.ui.i18n.LirixStrings.message("Ожидание трека")),
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (lyricsState.displayArtist.isNotBlank()) lyricsState.displayArtist else (playback?.artist ?: "Lirix"),
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activeLineText,
                        color = colors.accentPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Return to Full Studio Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                        .clickable(onClick = onExpandToStudio)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Dashboard,
                            contentDescription = com.lirix.app.ui.i18n.LirixStrings.message("Студия"),
                            tint = colors.textPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = com.lirix.app.ui.i18n.LirixStrings.message("Студия"),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                }
            }

            // Bottom Row: Scrubber, Controls, and Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Time label
                Text(
                    text = formatTime(currentPositionMs),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textMuted
                )

                // Slider
                val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
                Slider(
                    value = progress,
                    onValueChange = { ratio ->
                        ingressProvider.seekTo((ratio * durationMs).toLong())
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accentPrimary,
                        activeTrackColor = colors.accentPrimary,
                        inactiveTrackColor = colors.borderSubtle
                    )
                )

                Text(
                    text = formatTime(durationMs),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textMuted
                )

                // Compact Transport Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactIconButton(
                        icon = Icons.Filled.SkipPrevious,
                        contentDescription = com.lirix.app.ui.i18n.LirixStrings.message("Предыдущий"),
                        onClick = { ingressProvider.skipPrevious() }
                    )
                    CompactIconButton(
                        icon = if (playback?.isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playback?.isPlaying == true) com.lirix.app.ui.i18n.LirixStrings.message("Пауза") else com.lirix.app.ui.i18n.LirixStrings.message("Воспроизведение"),
                        isPrimary = true,
                        onClick = { ingressProvider.playPause() }
                    )
                    CompactIconButton(
                        icon = Icons.Filled.SkipNext,
                        contentDescription = com.lirix.app.ui.i18n.LirixStrings.message("Следующий"),
                        onClick = { ingressProvider.skipNext() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false
) {
    val colors = LirixThemeTokens.colors

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (isPrimary) colors.accentPrimary else colors.surfaceElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isPrimary) colors.appBackground else colors.textPrimary,
            modifier = Modifier.size(14.dp)
        )
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000) % 60
    val m = (ms / (1000 * 60)) % 60
    return "%02d:%02d".format(m, s)
}
