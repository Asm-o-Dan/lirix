package com.lirix.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.annotation.StringRes
import com.lirix.app.R
import com.lirix.app.ui.components.NavigationCapsule

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.lirix.app.storage.TrackEntity
import com.lirix.app.ui.blacklist.BlacklistSettingsScreen
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.lirix.app.classifier.SharedMediaPayload
import com.lirix.app.feature.lyrics.AmDmChordParser
import com.lirix.app.feature.music.ParsedTrackInfo
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.ui.components.ShareLyricsAttachDialog
import com.lirix.app.ui.theme.AppColors
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 3-Tab navigation enum for Music & Lyrics Hub.
 * Spec: [ Плеер ] [ Медиатека ] [ Итоги ]
 */
enum class AppTab(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    NOW_PLAYING("now_playing", R.string.tab_now_playing, Icons.Default.PlayArrow),
    LIBRARY("library", R.string.tab_library, Icons.Default.MusicNote),
    WRAPPED("wrapped", R.string.tab_wrapped, Icons.Default.EmojiEvents)
}

/**
 * Root AMOLED navigation scaffold for Music & Lyrics Hub.
 * Implements Obsidian Pulse pure black standard (#000000) with 120Hz smooth transitions.
 */
@Composable
fun AppScaffold(
    initialLyricsTrack: ParsedTrackInfo? = null,
    onLyricsDialogConsumed: () -> Unit = {},
    initialSharedPayload: SharedMediaPayload? = null,
    onSharedPayloadConsumed: () -> Unit = {}
) {
    var currentTab by rememberSaveable { mutableStateOf(AppTab.NOW_PLAYING) }
    var viewingTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var activeTeachTrack by remember { mutableStateOf<TrackEntity?>(null) }
    var activeTeachUrl by remember { mutableStateOf<String?>(null) }
    var isTeachModeOpen by remember { mutableStateOf(false) }
    var pendingSharedLyricsText by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current

    // Auto-focus NowPlaying tab when lyrics intent arrives from status bar notification
    LaunchedEffect(initialLyricsTrack) {
        if (initialLyricsTrack != null && initialLyricsTrack.title.isNotBlank()) {
            currentTab = AppTab.NOW_PLAYING
        }
    }

    // Handle shared payload from Android Share Intent
    LaunchedEffect(initialSharedPayload) {
        when (val payload = initialSharedPayload) {
            is SharedMediaPayload.WebUrl -> {
                activeTeachUrl = payload.url
                isTeachModeOpen = true
                onSharedPayloadConsumed()
            }
            is SharedMediaPayload.LyricsText -> {
                if (payload.text.isNotBlank()) {
                    pendingSharedLyricsText = payload.text
                }
                onSharedPayloadConsumed()
            }
            null -> Unit
        }
    }

    var isCapsuleVisible by rememberSaveable { mutableStateOf(true) }
    var isBlacklistSettingsOpen by rememberSaveable { mutableStateOf(false) }

    val lirixColors = LirixThemeTokens.colors
    val tabStateHolder = rememberSaveableStateHolder()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(lirixColors.appBackground),
        containerColor = lirixColors.appBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            AnimatedVisibility(
                visible = currentTab != AppTab.NOW_PLAYING && isCapsuleVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(280)
                ) + fadeIn(animationSpec = tween(240)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(240)
                ) + fadeOut(animationSpec = tween(200))
            ) {
                NavigationCapsule(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            AnimatedContent(
                targetState = currentTab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (fadeIn(tween(300, delayMillis = 60)) +
                        slideInHorizontally(tween(360)) { width -> direction * width / 16 })
                        .togetherWith(fadeOut(tween(180)))
                        .using(null)
                },
                label = "TabSwitch"
            ) { target ->
                tabStateHolder.SaveableStateProvider(target.route) {
                when (target) {
                    AppTab.NOW_PLAYING -> NowPlayingScreen(
                        initialTrack = initialLyricsTrack,
                        selectedTrack = viewingTrack,
                        onTrackConsumed = {
                            onLyricsDialogConsumed()
                            viewingTrack = null
                        },
                        onOpenTeachMode = { track ->
                            activeTeachTrack = track
                            activeTeachUrl = null
                            isTeachModeOpen = true
                        },
                        onOpenBlacklistSettings = {
                            isBlacklistSettingsOpen = true
                        },
                        onBack = {
                            currentTab = AppTab.LIBRARY
                        }
                    )
                    AppTab.LIBRARY -> LibraryScreen(
                        onTrackSelected = { track ->
                            viewingTrack = track
                            currentTab = AppTab.NOW_PLAYING
                        },
                        onOpenBlacklistSettings = {
                            isBlacklistSettingsOpen = true
                        }
                    )
                    AppTab.WRAPPED -> WrappedScreen()
                }
                }
            }

            // Share Lyrics Attach Dialog overlay
            pendingSharedLyricsText?.let { lyricsText ->
                val scope = rememberCoroutineScope()
                val context = LocalContext.current
                val db = remember { AppDatabase.getInstance(context) }

                ShareLyricsAttachDialog(
                    lyricsText = lyricsText,
                    onDismissRequest = { pendingSharedLyricsText = null },
                    onTrackSelected = { selectedTrack ->
                        val targetTrackKey = selectedTrack.trackKey
                        scope.launch(Dispatchers.IO) {
                            db.musicDao().updateLyrics(targetTrackKey, lyricsText, null)
                            db.lyricsDao().saveLyrics(
                                LyricsCacheEntity(
                                    trackKey = targetTrackKey,
                                    plainLyrics = lyricsText,
                                    syncedLyricsLrc = null,
                                    chordsAmDm = AmDmChordParser.parseAmDmHtml(lyricsText),
                                    userNotes = selectedTrack.userNotes,
                                    provider = "share:manual"
                                )
                            )
                        }
                        viewingTrack = selectedTrack
                        currentTab = AppTab.NOW_PLAYING
                        pendingSharedLyricsText = null
                    }
                )
            }

            // Teach Mode WebView overlay
            AnimatedVisibility(
                visible = isTeachModeOpen,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 24 },
                exit = fadeOut(tween(180))
            ) {
                TeachModeScreen(
                    initialUrl = activeTeachUrl,
                    targetTrack = activeTeachTrack ?: viewingTrack,
                    onClose = {
                        isTeachModeOpen = false
                        activeTeachUrl = null
                    },
                    onRuleSaved = {
                        // Rule saved, keep open or close based on user preference
                    },
                    onLyricsAttached = {
                        currentTab = AppTab.NOW_PLAYING
                    }
                )
            }

            // Blacklist Settings overlay
            AnimatedVisibility(
                visible = isBlacklistSettingsOpen,
                enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 24 },
                exit = fadeOut(tween(180))
            ) {
                BlacklistSettingsScreen(
                    onBack = {
                        isBlacklistSettingsOpen = false
                    }
                )
            }
        }
    }
}
