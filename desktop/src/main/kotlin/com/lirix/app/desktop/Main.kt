package com.lirix.app.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lirix.app.desktop.hud.DesktopHudWindow
import com.lirix.app.desktop.ingress.DesktopAudioMeterService
import com.lirix.app.desktop.ingress.DesktopGsmtcIngressProvider
import com.lirix.app.desktop.lyrics.DesktopLyricsService
import com.lirix.app.desktop.system.DiscordRpcManager
import com.lirix.app.desktop.system.GlobalHotkeysManager
import com.lirix.app.desktop.system.LirixTray
import com.lirix.app.desktop.system.WindowsTitleBarDarkTheme
import com.lirix.app.desktop.ui.DesktopDeckScreen
import com.lirix.app.desktop.ui.components.DesktopCompactWidget
import com.lirix.app.desktop.ui.theme.DesktopFonts
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.ColorPalettes
import com.lirix.app.ui.theme.LocalLirixColors
import com.lirix.app.ui.theme.LocalLirixTypography

fun main() = application {
    val coroutineScope = rememberCoroutineScope()
    val ingressProvider = remember { DesktopGsmtcIngressProvider() }
    val audioMeterService = remember { DesktopAudioMeterService(ingressProvider.bridgeProcessManager, coroutineScope) }
    val discordRpc = remember { DiscordRpcManager() }
    val lyricsService = remember { DesktopLyricsService(coroutineScope) }

    var isMainWindowVisible by remember { mutableStateOf(true) }
    var isHudVisible by remember { mutableStateOf(false) }
    var selectedTheme by remember { mutableStateOf(AppThemeType.GRAPHITE_CHALK) }

    val windowState = rememberWindowState(size = DpSize(1100.dp, 720.dp))
    val playback by ingressProvider.currentPlayback.collectAsState()
    val audioLevels by audioMeterService.audioLevels.collectAsState()
    val lyricsState by lyricsService.lyricsState.collectAsState()

    // Trigger reactive lyrics and chords fetching whenever active track changes
    LaunchedEffect(playback?.title, playback?.artist, playback?.album) {
        lyricsService.onTrackChanged(playback?.title, playback?.artist, playback?.album)
    }

    // Initialize Background Services (Discord RPC, Global Hotkeys and Connect Server)
    DisposableEffect(Unit) {
        discordRpc.start()
        val connectServer = com.lirix.app.desktop.cast.DesktopConnectServer.getInstance().apply { start() }

        val hotkeysManager = GlobalHotkeysManager(
            onToggleHud = { isHudVisible = !isHudVisible },
            onTogglePlayPause = { ingressProvider.playPause() },
            onSkipNext = { ingressProvider.skipNext() },
            onSkipPrevious = { ingressProvider.skipPrevious() }
        ).apply { start() }

        onDispose {
            connectServer.stop()
            hotkeysManager.stop()
            discordRpc.stop()
            audioMeterService.stop()
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

    var isCompactMode by remember { mutableStateOf(false) }

    // Dynamically adjust window size when toggling between Studio Deck and Compact Widget
    LaunchedEffect(isCompactMode) {
        if (isCompactMode) {
            windowState.size = DpSize(480.dp, 145.dp)
        } else {
            windowState.size = DpSize(1100.dp, 720.dp)
        }
    }

    val desktopTypography = remember { DesktopFonts.createPrompterTypography() }

    // Main Window (Studio Deck or Compact Player Widget)
    if (isMainWindowVisible) {
        Window(
            onCloseRequest = { isMainWindowVisible = false }, // Minimize to tray
            state = windowState,
            title = if (isCompactMode) "Lirix Mini Player" else "Lirix Music Hub — Studio Deck",
            resizable = !isCompactMode
        ) {
            // Apply native Win32 dark title bar based on current theme
            LaunchedEffect(selectedTheme) {
                WindowsTitleBarDarkTheme.apply(window, selectedTheme.isDark)
            }

            val currentTokens = ColorPalettes.getTokens(selectedTheme)

            CompositionLocalProvider(
                LocalLirixColors provides currentTokens,
                LocalLirixTypography provides desktopTypography
            ) {
                if (isCompactMode) {
                    DesktopCompactWidget(
                        playback = playback,
                        ingressProvider = ingressProvider,
                        lyricsState = lyricsState,
                        onExpandToStudio = { isCompactMode = false }
                    )
                } else {
                    DesktopDeckScreen(
                        playback = playback,
                        ingressProvider = ingressProvider,
                        selectedTheme = selectedTheme,
                        onThemeSelected = { newTheme ->
                            selectedTheme = newTheme
                        },
                        lyricsState = lyricsState,
                        isHudVisible = isHudVisible,
                        onToggleHud = { isHudVisible = !isHudVisible },
                        isCompactMode = false,
                        onToggleCompactMode = { isCompactMode = true },
                        audioLevels = audioLevels
                    )
                }
            }
        }
    }

    // Floating Click-Through Karaoke HUD Window
    val hudTokens = ColorPalettes.getTokens(selectedTheme)
    CompositionLocalProvider(
        LocalLirixColors provides hudTokens,
        LocalLirixTypography provides desktopTypography
    ) {
        DesktopHudWindow(
            visible = isHudVisible,
            onCloseRequest = { isHudVisible = false },
            playback = playback,
            lyricsLines = lyricsState.lrcLines
        )
    }
}
