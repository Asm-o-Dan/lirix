package com.lirix.app.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lirix.app.desktop.hud.DesktopHudWindow
import com.lirix.app.desktop.ingress.DesktopGsmtcIngressProvider
import com.lirix.app.desktop.system.DiscordRpcManager
import com.lirix.app.desktop.system.GlobalHotkeysManager
import com.lirix.app.desktop.system.LirixTray
import com.lirix.app.desktop.ui.DesktopDeckScreen
import com.lirix.app.ui.player.LrcLine
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.ColorPalettes
import com.lirix.app.ui.theme.LocalLirixColors

fun main() = application {
    val ingressProvider = remember { DesktopGsmtcIngressProvider() }
    val discordRpc = remember { DiscordRpcManager() }

    var isMainWindowVisible by remember { mutableStateOf(true) }
    var isHudVisible by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(AppThemeType.SPOTIFY_MOOD) }

    val windowState = rememberWindowState(size = DpSize(1100.dp, 720.dp))
    val playback by ingressProvider.currentPlayback.collectAsState()

    val demoLrcLines = remember {
        listOf(
            LrcLine(0L, "♪ Lirix Studio Desktop Hub ♪"),
            LrcLine(4000L, "Звёзды падают с небес на асфальт"),
            LrcLine(10000L, "Ночью город зажигает огни"),
            LrcLine(17000L, "Если веришь ты в свою мечту"),
            LrcLine(24000L, "Значит, мы в этом мире не одни"),
            LrcLine(32000L, "И звучит винил сквозь тишину"),
            LrcLine(40000L, "Lirix держит ритм в унисон"),
            LrcLine(48000L, "Проплывают стрелки на часах"),
            LrcLine(56000L, "Это наш с тобой любимый сон"),
            LrcLine(64000L, "♪ Музыкальный проигрыш ♪")
        )
    }

    // Initialize Background Services (Discord RPC and Global Hotkeys)
    DisposableEffect(Unit) {
        discordRpc.start()

        val hotkeysManager = GlobalHotkeysManager(
            onToggleHud = { isHudVisible = !isHudVisible },
            onTogglePlayPause = { ingressProvider.playPause() },
            onSkipNext = { ingressProvider.skipNext() },
            onSkipPrevious = { ingressProvider.skipPrevious() }
        ).apply { start() }

        onDispose {
            hotkeysManager.stop()
            discordRpc.stop()
            ingressProvider.stop()
        }
    }

    // Sync Discord Rich Presence when track or playback status changes
    LaunchedEffect(playback) {
        discordRpc.updatePresence(playback)
    }

    // Windows System Tray Integration
    LirixTray(
        onOpenMainWindow = { isMainWindowVisible = true },
        onToggleHud = { isHudVisible = !isHudVisible },
        onTogglePlayPause = { ingressProvider.playPause() },
        onSkipNext = { ingressProvider.skipNext() },
        onExit = ::exitApplication,
        isPlaying = playback?.isPlaying == true,
        trackTitle = playback?.title
    )

    // Main Studio Deck Window
    if (isMainWindowVisible) {
        Window(
            onCloseRequest = { isMainWindowVisible = false }, // Minimize to tray
            state = windowState,
            title = "Lirix Music Hub — Studio Deck"
        ) {
            val currentTokens = ColorPalettes.getTokens(selectedTheme)

            CompositionLocalProvider(LocalLirixColors provides currentTokens) {
                DesktopDeckScreen(
                    playback = playback,
                    ingressProvider = ingressProvider,
                    selectedTheme = selectedTheme,
                    onThemeSelected = { newTheme ->
                        selectedTheme = newTheme
                    }
                )
            }
        }
    }

    // Floating Click-Through Karaoke HUD Window
    val hudTokens = ColorPalettes.getTokens(selectedTheme)
    CompositionLocalProvider(LocalLirixColors provides hudTokens) {
        DesktopHudWindow(
            visible = isHudVisible,
            onCloseRequest = { isHudVisible = false },
            playback = playback,
            lyricsLines = demoLrcLines
        )
    }
}
