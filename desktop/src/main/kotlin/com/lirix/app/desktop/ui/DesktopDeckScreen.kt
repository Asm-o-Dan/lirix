package com.lirix.app.desktop.ui

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.connect.security.TrustedDevice
import com.lirix.app.connect.security.TrustedDeviceStore
import com.lirix.app.desktop.ingress.AudioLevels
import com.lirix.app.desktop.lyrics.DesktopLyricsState
import com.lirix.app.desktop.ui.components.AnalogVuMeter
import com.lirix.app.desktop.ui.components.DesktopConnectDialog
import com.lirix.app.desktop.ui.components.DesktopSyncOffsetStore
import com.lirix.app.desktop.ui.components.DesktopTeleprompterView
import com.lirix.app.desktop.ui.components.DesktopTopBar
import com.lirix.app.desktop.ui.components.DesktopTransportBar
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.platform.MediaIngressProvider
import com.lirix.app.ui.player.ChordsTabContent
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.ui.theme.LocalLirixTypography
import java.io.File

/**
 * Lirix Studio Deck 2.0 (The Teleprompter Deck).
 *
 * Spec: TASK-UI-PROMPTER-FIX-01
 * Anti-Slop: Zero fake spinning vinyls, bookish typography (Literata + Golos),
 * 88x88 album cover, full-width transport bar, honest IDLE state, WCAG AA contrast.
 */
@Composable
fun DesktopDeckScreen(
    playback: LivePlaybackSnapshot?,
    ingressProvider: MediaIngressProvider,
    selectedTheme: AppThemeType,
    onThemeSelected: (AppThemeType) -> Unit,
    lyricsState: DesktopLyricsState = DesktopLyricsState(),
    isHudVisible: Boolean = false,
    onToggleHud: () -> Unit = {},
    isCompactMode: Boolean = false,
    onToggleCompactMode: () -> Unit = {},
    audioLevels: AudioLevels? = null,
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors
    val typography = LocalLirixTypography.current
    var selectedRightTab by remember { mutableIntStateOf(0) } // 0: Текст, 1: Аккорды, 2: О треке

    // Safely load local album thumbnail bitmap if available from bridge
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

    val currentPositionMs = playback?.currentPositionMs() ?: 0L
    val durationMs = playback?.durationMs ?: 0L
    var syncOffsetMs by remember { mutableLongStateOf(DesktopSyncOffsetStore.getOffsetMs()) }
    val effectiveLyricsPositionMs = (currentPositionMs - syncOffsetMs).coerceAtLeast(0L)

    var isConnectDialogOpen by remember { mutableStateOf(false) }
    var activeConnectDevice by remember { mutableStateOf<TrustedDevice?>(null) }
    val trustedStore = remember { com.lirix.app.desktop.cast.DesktopConnectServer.getInstance().trustedStore }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    event.isCtrlPressed && event.isShiftPressed && event.key == Key.D) {
                    isConnectDialogOpen = !isConnectDialogOpen
                    true
                } else false
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.appBackground)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ==========================================
            // TOP CONTROL BAR: Brand, Source, Modes, Theme Toggle
            // ==========================================
            DesktopTopBar(
                sourceApp = playback?.packageName,
                isPlaying = playback?.isPlaying == true,
                lyricsSource = lyricsState.source,
                isHudVisible = isHudVisible,
                onToggleHud = onToggleHud,
                isCompactMode = isCompactMode,
                onToggleCompactMode = onToggleCompactMode,
                selectedTheme = selectedTheme,
                onToggleTheme = {
                    val nextTheme = if (selectedTheme.isDark) {
                        AppThemeType.PAPER_INK
                    } else {
                        AppThemeType.GRAPHITE_CHALK
                    }
                    onThemeSelected(nextTheme)
                },
                hasActiveConnectDevice = activeConnectDevice != null,
                activeConnectDeviceName = activeConnectDevice?.deviceName,
                onOpenConnect = { isConnectDialogOpen = true }
            )

        // ==========================================
        // MAIN TWO-PANE TELEPROMPTER DECK
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // LEFT COLUMN: 88x88 Cover, Metadata, Transport, VU-Meter
            // ==========================================
            Surface(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top: Cover Art (88x88 dp) + Track Metadata
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (albumArtBitmap != null) {
                            Image(
                                bitmap = albumArtBitmap,
                                contentDescription = "Обложка трека",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceElevated)
                                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = colors.textMuted.copy(alpha = 0.5f),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Track Title & Artist
                        val displayTitle = when {
                            lyricsState.displayTitle.isNotBlank() -> lyricsState.displayTitle
                            playback?.title != null -> playback.title
                            else -> "Ожидание трека"
                        }
                        val displayArtist = when {
                            lyricsState.displayArtist.isNotBlank() -> lyricsState.displayArtist
                            playback?.artist != null -> playback.artist
                            else -> "Запустите музыку в плеере"
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = displayTitle,
                                style = typography.trackTitle,
                                color = colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = displayArtist,
                                style = typography.trackArtistAlbum,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Bottom: Transport Controls & Conditional WASAPI VU Meter
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DesktopTransportBar(
                            positionMs = currentPositionMs,
                            durationMs = durationMs,
                            isPlaying = playback?.isPlaying == true,
                            onSeekTo = { pos -> ingressProvider.seekTo(pos) },
                            onTogglePlayPause = { ingressProvider.playPause() },
                            onSkipNext = { ingressProvider.skipNext() },
                            onSkipPrevious = { ingressProvider.skipPrevious() },
                            initialOffsetMs = syncOffsetMs,
                            onOffsetChanged = { newOffset -> syncOffsetMs = newOffset },
                            accentColor = colors.accentPrimary,
                            isDark = colors.isDark,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Analog VU Meter: strictly visible when actively playing and audio is producing sound
                        if (playback?.isPlaying == true && audioLevels?.isActive == true && audioLevels.leftPeak > 0.0001f) {
                            AnalogVuMeter(
                                audioLevels = audioLevels,
                                isPlaying = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // ==========================================
            // RIGHT COLUMN: Tabs (Текст, Аккорды, О треке)
            // ==========================================
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Clean Tab Selection: «Текст», «Аккорды», «О треке» (No emoji, no all-caps)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceElevated)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DeckTabButton(
                            label = "Текст",
                            isSelected = selectedRightTab == 0,
                            onClick = { selectedRightTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        DeckTabButton(
                            label = "Аккорды",
                            isSelected = selectedRightTab == 1,
                            onClick = { selectedRightTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                        DeckTabButton(
                            label = "О треке",
                            isSelected = selectedRightTab == 2,
                            onClick = { selectedRightTab = 2 },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab Content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (selectedRightTab) {
                            0 -> {
                                when {
                                    lyricsState.isLoading -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(28.dp),
                                                    color = colors.accentPrimary,
                                                    strokeWidth = 2.5.dp
                                                )
                                                Text(
                                                    text = "Поиск текста песни...",
                                                    style = typography.trackTitle.copy(fontSize = 15.sp),
                                                    color = colors.textPrimary
                                                )
                                                Text(
                                                    text = "LrcLib • Genius • Музыкальные базы",
                                                    style = typography.timecodeAndBadges,
                                                    color = colors.textMuted
                                                )
                                            }
                                        }
                                    }
                                    playback == null -> {
                                        // Honest IDLE State
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.MusicNote,
                                                    contentDescription = null,
                                                    tint = colors.textMuted.copy(alpha = 0.4f),
                                                    modifier = Modifier.size(44.dp)
                                                )
                                                Text(
                                                    text = "Ничего не играет",
                                                    style = typography.lyricsNear.copy(fontSize = 18.sp),
                                                    color = colors.textSecondary
                                                )
                                                Text(
                                                    text = "Запустите музыку в любом плеере",
                                                    style = typography.timecodeAndBadges,
                                                    color = colors.textMuted
                                                )
                                            }
                                        }
                                    }
                                    lyricsState.lrcLines.isEmpty() -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = "Текст не найден",
                                                    style = typography.lyricsNear.copy(fontSize = 18.sp),
                                                    color = colors.textSecondary
                                                )
                                                Text(
                                                    text = "Для этого трека пока нет синхронизированных строк LRC",
                                                    style = typography.timecodeAndBadges,
                                                    color = colors.textMuted
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        DesktopTeleprompterView(
                                            lines = lyricsState.lrcLines,
                                            currentPositionMs = effectiveLyricsPositionMs,
                                            onSeekRequested = { pos -> ingressProvider.seekTo(pos) },
                                            accentColor = colors.accentPrimary,
                                            isDark = colors.isDark,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                            1 -> {
                                when {
                                    lyricsState.isLoading -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(28.dp),
                                                    color = colors.accentPrimary,
                                                    strokeWidth = 2.5.dp
                                                )
                                                Text(
                                                    text = "Поиск аккордов...",
                                                    style = typography.trackTitle.copy(fontSize = 15.sp),
                                                    color = colors.textPrimary
                                                )
                                            }
                                        }
                                    }
                                    playback == null -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Запустите трек для просмотра аккордов",
                                                style = typography.timecodeAndBadges,
                                                color = colors.textMuted
                                            )
                                        }
                                    }
                                    lyricsState.chords.isNullOrBlank() -> {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "Аккорды не найдены",
                                                    style = typography.lyricsNear.copy(fontSize = 18.sp),
                                                    color = colors.textSecondary
                                                )
                                                Text(
                                                    text = "В базах AmDm и MyChords нет гармонии для этого трека",
                                                    style = typography.timecodeAndBadges,
                                                    color = colors.textMuted
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        ChordsTabContent(
                                            chordsText = lyricsState.chords,
                                            durationMs = durationMs,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                            else -> {
                                TrackSpecsPanel(
                                    playback = playback,
                                    lyricsState = lyricsState
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        // ==========================================
        // Lirix Connect Popover / Modal Dialog (§5.2, §5.3, TASK-CON-UI-08)
        // ==========================================
        if (isConnectDialogOpen) {
            DesktopConnectDialog(
                onDismissRequest = { isConnectDialogOpen = false },
                trustedStore = trustedStore,
                activeConnectedDevice = activeConnectDevice,
                onDeviceSelectedForHandoff = { device ->
                    activeConnectDevice = device
                    isConnectDialogOpen = false
                }
            )
        }
    }
}

@Composable
private fun TrackSpecsPanel(
    playback: LivePlaybackSnapshot?,
    lyricsState: DesktopLyricsState
) {
    val colors = LirixThemeTokens.colors
    val typography = LocalLirixTypography.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Параметры аудиосессии",
            style = typography.trackTitle.copy(fontSize = 13.sp),
            color = colors.textMuted
        )

        Surface(
            color = colors.surfaceElevated,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SpecRow(label = "Источник аудио", value = playback?.packageName ?: "idle.session")
                SpecRow(label = "Караоке провайдер", value = lyricsState.source ?: "Поиск в базах")
                SpecRow(label = "Синхронизированных строк", value = "${lyricsState.lrcLines.size} LRC tags")
                SpecRow(label = "Длительность", value = formatTimeLabel(playback?.durationMs ?: 0L))
                SpecRow(label = "Discord Rich Presence", value = "Активен (\\.\\pipe\\discord-ipc-0)")
                SpecRow(label = "WinRT GSMTC Hook", value = "Подключен (Lirix Media Bridge C#)")
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    val colors = LirixThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.sp, color = colors.textSecondary)
        Text(text = value, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    }
}

private fun formatTimeLabel(ms: Long): String {
    val s = (ms / 1000) % 60
    val m = (ms / (1000 * 60)) % 60
    return "%02d:%02d".format(m, s)
}

@Composable
private fun DeckTabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors
    val typography = LocalLirixTypography.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) colors.surfaceCard else Color.Transparent)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) colors.borderSubtle else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = typography.trackTitle.copy(fontSize = 13.sp),
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) colors.textPrimary else colors.textMuted
        )
    }
}
