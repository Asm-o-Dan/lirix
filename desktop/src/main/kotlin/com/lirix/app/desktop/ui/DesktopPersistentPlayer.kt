package com.lirix.app.desktop.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.desktop.ingress.AudioLevels
import com.lirix.app.desktop.ui.components.*
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.platform.MediaIngressProvider
import com.lirix.app.ui.player.formatDurationMs
import com.lirix.app.ui.theme.LirixThemeTokens

/** One transport for every workspace page. No duplicate playback or source state. */
@Composable
internal fun DesktopPersistentPlayer(playback: LivePlaybackSnapshot?, ingress: MediaIngressProvider, audioLevels: AudioLevels? = null) {
    val c = LirixThemeTokens.colors
    var position by remember(playback) { mutableLongStateOf(playback?.currentPositionMs() ?: 0L) }
    LaunchedEffect(playback) { desktopPlaybackPositions(playback).collect { position = it } }
    val duration = playback?.durationMs?.coerceAtLeast(0L) ?: 0L
    var dragging by remember(playback?.title, playback?.artist) { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(0f) }
    val offset by DesktopSyncOffsetStore.offsetMs.collectAsState()
    var options by remember { mutableStateOf(false) }
    Surface(color = c.surfaceCard, tonalElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(.8f)) {
                Text(playback?.title ?: com.lirix.app.ui.i18n.LirixStrings.message("Нет активного трека"), color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(playback?.artist.orEmpty(), color = c.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = ingress::skipPrevious, enabled = playback != null) { Icon(Icons.Default.SkipPrevious, com.lirix.app.ui.i18n.LirixStrings.message("Предыдущий трек")) }
            FilledIconButton(onClick = ingress::playPause, enabled = playback != null) {
                Icon(if (playback?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (playback?.isPlaying == true) com.lirix.app.ui.i18n.LirixStrings.message("Пауза") else com.lirix.app.ui.i18n.LirixStrings.message("Воспроизведение"))
            }
            IconButton(onClick = ingress::skipNext, enabled = playback != null) { Icon(Icons.Default.SkipNext, com.lirix.app.ui.i18n.LirixStrings.message("Следующий трек")) }
            Column(Modifier.weight(1f)) {
                Slider(value = if (dragging) fraction else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { dragging = true; fraction = it }, enabled = playback != null && duration > 0,
                    onValueChangeFinished = { if (dragging) { ingress.seekTo((fraction * duration).toLong()); dragging = false } },
                    modifier = Modifier.fillMaxWidth().height(28.dp))
                Text("${formatDurationMs(if (dragging) (fraction * duration).toLong() else position)} / ${formatDurationMs(duration)}",
                    color = c.textSecondary, fontSize = 11.sp)
            }
            Box {
                IconButton(onClick = { options = true }) { Icon(Icons.Default.MoreVert, com.lirix.app.ui.i18n.LirixStrings.message("Сдвиг текста и уровни звука")) }
                DropdownMenu(options, { options = false }) {
                    Text(com.lirix.app.ui.i18n.LirixStrings.message("Сдвиг текста: {0} с", offset / 1000.0), color = c.textPrimary, modifier = Modifier.padding(12.dp))
                    Row {
                        TextButton(onClick = { DesktopSyncOffsetStore.setOffsetMs(offset - 100L) }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("−0,1 с")) }
                        TextButton(onClick = { DesktopSyncOffsetStore.setOffsetMs(offset + 100L) }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("+0,1 с")) }
                        TextButton(onClick = { DesktopSyncOffsetStore.setOffsetMs(DesktopSyncOffsetStore.DEFAULT_OFFSET_MS) }) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Сбросить")) }
                    }
                    if (audioLevels?.isActive == true) AnalogVuMeter(audioLevels, playback?.isPlaying == true, Modifier.width(260.dp).padding(12.dp))
                }
            }
        }
    }
}
