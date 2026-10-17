package com.lirix.app.desktop.hud

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.lirix.app.ui.theme.LocalLirixTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.awt.GraphicsEnvironment

/** Move/resize mode is reachable from the main window and Ctrl+Alt+L, even while click-through. */
@Composable
fun DesktopHudWindow(
    visible: Boolean,
    onCloseRequest: () -> Unit,
    playback: LivePlaybackSnapshot?,
    lyricsLines: List<LrcLine> = emptyList(),
    settings: HudSettings = HudSettings(),
    onSettingsChange: (HudSettings) -> Unit = {},
    syncOffsetMs: Long = 0,
    onHelp: () -> Unit = {}
) {
    val state = rememberWindowState(
        position = if (settings.x != null && settings.y != null) WindowPosition(settings.x.dp, settings.y.dp) else WindowPosition(Alignment.Center),
        size = DpSize(settings.width.dp, settings.height.dp)
    )
    val latest by rememberUpdatedState(settings)
    val update by rememberUpdatedState(onSettingsChange)
    LaunchedEffect(settings.width, settings.height) {
        val size = DpSize(settings.width.dp, settings.height.dp)
        if (state.size != size) state.size = size
    }
    LaunchedEffect(settings.x, settings.y) {
        val target = if (settings.x != null && settings.y != null) WindowPosition(settings.x.dp, settings.y.dp) else WindowPosition(Alignment.Center)
        if (state.position != target) state.position = target
    }
    if (!visible) return
    Window(onCloseRequest = onCloseRequest, state = state, title = "Lirix — Оверлей",
        transparent = true, undecorated = true, alwaysOnTop = true, resizable = !settings.locked,
        focusable = !settings.locked) {
        val colors = LirixThemeTokens.colors
        val typography = LocalLirixTypography.current
        val density = LocalDensity.current
        LaunchedEffect(settings.locked) { WindowsWindowStyles.setClickThrough(window, settings.locked) }
        LaunchedEffect(window) {
            val screens = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }
            if (screens.none { it.intersects(window.bounds) }) {
                val b = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
                window.setLocation(b.x + (b.width - window.width) / 2, b.y + (b.height - window.height) / 2)
            }
            snapshotFlow { state.size to state.position }.collect { (size, position) ->
                val absolute = position as? WindowPosition.Absolute
                val next = latest.copy(width = size.width.value, height = size.height.value,
                    x = absolute?.x?.value, y = absolute?.y?.value).normalized()
                if (next != latest) update(next)
            }
        }
        var positionMs by remember { mutableLongStateOf(0) }
        LaunchedEffect(playback, syncOffsetMs) {
            while (isActive) {
                positionMs = ((playback?.currentPositionMs() ?: 0) - syncOffsetMs).coerceAtLeast(0)
                delay(50)
            }
        }
        val active = lyricsLines.indexOfLast { it.timestampMs <= positionMs }
        val current = lyricsLines.getOrNull(active)?.text ?: if (lyricsLines.isNotEmpty()) "Скоро начнётся текст…" else playback?.title ?: "Ожидание музыки"
        val previous = lyricsLines.getOrNull(active - 1)?.text
        val next = lyricsLines.getOrNull(active + 1)?.text
        val scale = (minOf(state.size.width.value / 680f, state.size.height.value / 240f) * settings.textScale).coerceIn(.7f, 2.5f)
        Surface(modifier = Modifier.fillMaxSize().padding(5.dp), shape = RoundedCornerShape(16.dp),
            color = colors.appBackground.copy(alpha = settings.opacity), border = BorderStroke(1.dp, colors.borderSubtle)) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (!settings.locked) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            WindowDraggableArea(modifier = Modifier.weight(1f)) {
                                Text("Перетащите оверлей", color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                            }
                            TextButton(onClick = { onSettingsChange(settings.copy(textScale = settings.textScale - .1f)) }, contentPadding = PaddingValues(6.dp)) { Text("А−", color = colors.textPrimary) }
                            TextButton(onClick = { onSettingsChange(settings.copy(textScale = settings.textScale + .1f)) }, contentPadding = PaddingValues(6.dp)) { Text("А+", color = colors.textPrimary) }
                            TextButton(onClick = { onSettingsChange(settings.copy(locked = true)) }, contentPadding = PaddingValues(6.dp)) { Text("Закрепить", color = colors.accentPrimary) }
                            TextButton(onClick = onCloseRequest, contentPadding = PaddingValues(6.dp)) { Text("Закрыть", color = colors.textSecondary) }
                        }
                    }
                    Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (previous != null && state.size.height >= 220.dp) Text(previous, color = colors.textMuted,
                            fontSize = (15 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                        Text(current, color = colors.karaokeActive, fontFamily = typography.lyricsActive.fontFamily,
                            fontWeight = FontWeight.Medium, fontSize = (25 * scale).sp, maxLines = if (state.size.height >= 280.dp) 3 else 2,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                        if (next != null && state.size.height >= 220.dp) Text(next, color = colors.textSecondary,
                            fontSize = (15 * scale).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    }
                    Text("Ctrl+Alt+L — переместить и изменить размер · Ctrl+Alt+H — скрыть",
                        color = colors.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!settings.locked) {
                    Text("◢", color = colors.accentPrimary, fontSize = 22.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).size(28.dp).pointerInput(density) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                with(density) {
                                    state.size = DpSize((state.size.width + drag.x.toDp()).coerceIn(380.dp, 1800.dp),
                                        (state.size.height + drag.y.toDp()).coerceIn(180.dp, 900.dp))
                                }
                            }
                        })
                }
            }
        }
    }
}
