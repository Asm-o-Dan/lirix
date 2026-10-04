package com.eventengine.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.runtime.setValue
import com.eventengine.app.storage.TrackEntity
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import com.eventengine.app.classifier.SharedMediaPayload
import com.eventengine.app.feature.lyrics.AmDmChordParser
import com.eventengine.app.feature.music.ParsedTrackInfo
import com.eventengine.app.storage.AppDatabase
import com.eventengine.app.storage.LyricsCacheEntity
import com.eventengine.app.ui.components.ShareLyricsAttachDialog
import com.eventengine.app.ui.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 4-Tab navigation enum for Music & Lyrics Hub.
 * Spec: TASK-UI-01 / .sdd/specs/media-ui/overview.md (v1)
 */
enum class AppTab(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    NOW_PLAYING("now_playing", "Плеер", Icons.Default.PlayArrow),
    HISTORY("history", "История", Icons.Default.History),
    LIBRARY("library", "Библиотека", Icons.Default.MusicNote),
    WRAPPED("wrapped", "Итоги", Icons.Default.EmojiEvents)
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

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.AmoledBlack),
        containerColor = AppColors.AmoledBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                NavigationBar(
                    containerColor = AppColors.SurfaceLevel1.copy(alpha = 0.94f),
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .border(
                            BorderStroke(
                                1.5.dp,
                                Brush.horizontalGradient(
                                    listOf(
                                        AppColors.HyperViolet.copy(alpha = 0.6f),
                                        AppColors.CyberCyan.copy(alpha = 0.6f)
                                    )
                                )
                            ),
                            RoundedCornerShape(26.dp)
                        )
                ) {
                    AppTab.entries.forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentTab != tab) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    currentTab = tab
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    tint = if (isSelected) AppColors.CyberCyan else AppColors.TextTertiary
                                )
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) AppColors.TextPrimary else AppColors.TextTertiary,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.ExtraBold else androidx.compose.ui.text.font.FontWeight.Medium
                                    )
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = AppColors.CyberCyanGlow.copy(alpha = 0.25f)
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(
                targetState = currentTab,
                animationSpec = tween(200),
                label = "TabSwitch"
            ) { target ->
                when (target) {
                    AppTab.NOW_PLAYING -> NowPlayingScreen(
                        initialTrack = initialLyricsTrack,
                        selectedTrack = viewingTrack,
                        onTrackConsumed = {
                            onLyricsDialogConsumed()
                            viewingTrack = null
                        },
                        onOpenTeachMode = {
                            activeTeachUrl = null
                            isTeachModeOpen = true
                        }
                    )
                    AppTab.HISTORY -> HistoryScreen(
                        onTrackSelected = { track ->
                            viewingTrack = track
                            currentTab = AppTab.NOW_PLAYING
                        }
                    )
                    AppTab.LIBRARY -> LibraryScreen(
                        onTrackSelected = { track ->
                            viewingTrack = track
                            currentTab = AppTab.NOW_PLAYING
                        }
                    )
                    AppTab.WRAPPED -> WrappedScreen()
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
            if (isTeachModeOpen) {
                TeachModeScreen(
                    initialUrl = activeTeachUrl,
                    targetTrack = viewingTrack,
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
        }
    }
}
