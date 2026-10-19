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
import com.lirix.app.desktop.ui.DesktopWorkspace
import com.lirix.app.desktop.ui.DesktopDraftStore
import com.lirix.app.desktop.hud.HudSettingsStore
import com.lirix.app.desktop.library.DesktopLibraryRepository
import com.lirix.app.desktop.system.DesktopAction
import com.lirix.app.platform.JvmPreferencesThemeStore
import com.lirix.app.storage.createDesktopDatabase
import kotlinx.coroutines.*
import androidx.compose.material3.*
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.*
import java.awt.Dimension
import com.lirix.app.desktop.ui.components.DesktopCompactWidget
import com.lirix.app.desktop.ui.theme.DesktopFonts
import com.lirix.app.desktop.ui.theme.buildDesktopThemePalette
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.LocalLirixColors
import com.lirix.app.ui.theme.LocalLirixTypography

fun main() = application {
    val coroutineScope = rememberCoroutineScope()
    val ingressProvider = remember { DesktopGsmtcIngressProvider() }
    val audioMeterService = remember { DesktopAudioMeterService(ingressProvider.bridgeProcessManager, coroutineScope) }
    val discordRpc = remember { DiscordRpcManager() }
    val database = remember { createDesktopDatabase() }
    val repository = remember { DesktopLibraryRepository(database, coroutineScope) }
    val lyricsService = remember { DesktopLyricsService(coroutineScope, repository) }
    val translationProvider = remember { com.lirix.app.translations.SongTranslationProviders() }
    val automaticTranslations = remember { com.lirix.app.translations.AutomaticSongTranslations(database.songTranslationDao(), translationProvider::find, preferences = com.lirix.app.platform.JvmTranslationLanguagePreferences(), searchLanguage = translationProvider::findLanguage) }

    var isMainWindowVisible by remember { mutableStateOf(true) }
    var isHudVisible by remember { mutableStateOf(false) }
    val themeStore = remember { JvmPreferencesThemeStore() }
    val selectedTheme by themeStore.selectedTheme.collectAsState()
    val hudStore = remember { HudSettingsStore() }
    val hudSettings by hudStore.state.collectAsState()
    var helpVisible by remember { mutableStateOf(false) }
    var isCompactMode by remember { mutableStateOf(false) }
    val drafts = remember { DesktopDraftStore() }
    var confirmExit by remember { mutableStateOf(false) }
    val libraryTracks by repository.tracks.collectAsState()
    LaunchedEffect(libraryTracks) {
        libraryTracks.forEach { track -> if (drafts.notes[track.trackKey] == track.userNotes) drafts.notes.remove(track.trackKey) }
    }

    val windowState = rememberWindowState(size = DpSize(1280.dp, 820.dp))
    val playback by ingressProvider.currentPlayback.collectAsState()
    val audioLevels by audioMeterService.audioLevels.collectAsState()
    val lyricsState by lyricsService.lyricsState.collectAsState()
    val syncOffsetMs by com.lirix.app.desktop.ui.components.DesktopSyncOffsetStore.offsetMs.collectAsState()
    val latestPlayback by rememberUpdatedState(playback)
    val translationKey = playback?.let(repository::keyFor)
    val translationLanguage by automaticTranslations.language.collectAsState()
    LaunchedEffect(translationLanguage, translationKey, playback?.title, playback?.artist) {
        val track = playback
        if (track != null && translationKey != null) automaticTranslations.resolve(translationKey, track.title, track.artist)
    }
    val hotkeysManager = remember {
        fun action(block: () -> Unit): () -> Unit = { coroutineScope.launch { block() }; Unit }
        GlobalHotkeysManager(
            onToggleHud = action { isHudVisible = !isHudVisible },
            onTogglePlayPause = action { ingressProvider.playPause() },
            onSkipNext = action { ingressProvider.skipNext() },
            onSkipPrevious = action { ingressProvider.skipPrevious() },
            extraActions = mapOf(
                DesktopAction.HUD_EDIT to action { hudStore.update(hudStore.state.value.copy(locked = if (isHudVisible) !hudStore.state.value.locked else false)); isHudVisible = true },
                DesktopAction.SEEK_BACK to action { ingressProvider.seekTo(((latestPlayback?.currentPositionMs() ?: 0) - 5000).coerceAtLeast(0)) },
                DesktopAction.SEEK_FORWARD to action { ingressProvider.seekTo((latestPlayback?.currentPositionMs() ?: 0) + 5000) },
                DesktopAction.SHOW_MAIN to action { isMainWindowVisible = true; isCompactMode = false; windowState.isMinimized = false },
                DesktopAction.MINI_PLAYER to action { isMainWindowVisible = true; isCompactMode = !isCompactMode },
                DesktopAction.HELP to action { isMainWindowVisible = true; isCompactMode = false; windowState.isMinimized = false; helpVisible = true },
                DesktopAction.HUD_LARGER to action { hudStore.update(hudStore.state.value.copy(textScale = hudStore.state.value.textScale + .1f)) },
                DesktopAction.HUD_SMALLER to action { hudStore.update(hudStore.state.value.copy(textScale = hudStore.state.value.textScale - .1f)) },
                DesktopAction.HUD_RESET to action { hudStore.reset(); isHudVisible = true }
            )
        )
    }
    val hotkeyFailures by hotkeysManager.registrationFailures.collectAsState()
    LaunchedEffect(Unit) {
        while (isActive) {
            try { repository.record(ingressProvider.currentPlayback.value) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { com.lirix.app.platform.logError("DesktopLibrary", "Не удалось записать прослушивание", e) }
            delay(1000)
        }
    }

    // Trigger reactive lyrics and chords fetching whenever active track changes
    LaunchedEffect(playback?.title, playback?.artist, playback?.album) {
        lyricsService.onTrackChanged(playback?.title, playback?.artist, playback?.album)
    }

    // Initialize Background Services (Discord RPC, Global Hotkeys and Connect Server)
    DisposableEffect(Unit) {
        discordRpc.start()
        val connectServer = com.lirix.app.desktop.cast.DesktopConnectServer.getInstance().apply { start() }

        hotkeysManager.start()

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
        onExit = { if (drafts.hasUnsavedChanges) { confirmExit = true; isMainWindowVisible = true; isCompactMode = false } else exitApplication() },
        isPlaying = playback?.isPlaying == true,
        trackTitle = playback?.title
    )

    // Dynamically adjust window size when toggling between Studio Deck and Compact Widget
    LaunchedEffect(isCompactMode) {
        if (isCompactMode) {
            windowState.size = DpSize(480.dp, 145.dp)
        } else {
            windowState.size = DpSize(1280.dp, 820.dp)
        }
    }

    val desktopTypography = remember { DesktopFonts.createPrompterTypography() }
    val desktopPalette = remember(selectedTheme) { buildDesktopThemePalette(selectedTheme) }

    // Main Window (Studio Deck or Compact Player Widget)
    if (isMainWindowVisible) {
        Window(
            onCloseRequest = { isMainWindowVisible = false }, // Minimize to tray
            state = windowState,
            title = if (isCompactMode) "Lirix Mini Player" else "Lirix Music Hub — Studio Deck",
            resizable = !isCompactMode
        ) {
            // Apply native Win32 dark title bar based on current theme
            LaunchedEffect(isCompactMode) { window.minimumSize = if (isCompactMode) Dimension(420, 145) else Dimension(980, 680) }
            LaunchedEffect(selectedTheme) {
                WindowsTitleBarDarkTheme.apply(window, selectedTheme.isDark)
            }

            CompositionLocalProvider(
                LocalLirixColors provides desktopPalette.tokens,
                LocalLirixTypography provides desktopTypography
            ) {
                MaterialTheme(colorScheme = desktopPalette.material) {
                    if (isCompactMode) {
                        DesktopCompactWidget(
                            playback = playback,
                            ingressProvider = ingressProvider,
                            lyricsState = lyricsState,
                            onExpandToStudio = { isCompactMode = false }
                        )
                    } else {
                        DesktopWorkspace(repository, lyricsService, playback, ingressProvider, lyricsState,
                            selectedTheme, themeStore::setTheme, isHudVisible, { isHudVisible = !isHudVisible },
                            hudSettings, hudStore::update, { hudStore.update(hudSettings.copy(locked = false)); isHudVisible = true },
                            { isCompactMode = true }, audioLevels, helpVisible, { helpVisible = it }, hotkeyFailures, drafts, automaticTranslations)
                    }
                }
            }
        }
    }

    // Floating Click-Through Karaoke HUD Window
    CompositionLocalProvider(
        LocalLirixColors provides desktopPalette.tokens,
        LocalLirixTypography provides desktopTypography
    ) {
        MaterialTheme(colorScheme = desktopPalette.material) {
            if (confirmExit) com.lirix.app.desktop.ui.DesktopFeatureDialog("Несохранённые изменения", { confirmExit = false }) {
                Text("Остались несохранённые правки текста, разметки или заметок. Вернитесь в редактор или медиатеку, чтобы сохранить их.")
                TextButton(onClick = { confirmExit = false }) { Text("Вернуться к работе") }
                TextButton(onClick = ::exitApplication) { Text("Выйти без сохранения") }
            }
            DesktopHudWindow(
                visible = isHudVisible,
                onCloseRequest = { isHudVisible = false },
                playback = playback,
                lyricsLines = lyricsState.lrcLines,
                settings = hudSettings,
                onSettingsChange = hudStore::update,
                syncOffsetMs = syncOffsetMs,
                onHelp = { helpVisible = true; isMainWindowVisible = true }
            )
        }
    }
}
