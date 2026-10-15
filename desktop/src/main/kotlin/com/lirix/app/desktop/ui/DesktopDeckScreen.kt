package com.lirix.app.desktop.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.desktop.lyrics.DesktopLyricsState
import com.lirix.app.desktop.ui.components.AnalogVuMeter
import com.lirix.app.desktop.ui.components.DesktopTopBar
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.platform.MediaIngressProvider
import com.lirix.app.ui.components.AnalogTurntable
import com.lirix.app.ui.player.ChordsTabContent
import com.lirix.app.ui.player.LrcLine
import com.lirix.app.ui.player.LyricsScrollPanel
import com.lirix.app.ui.player.PlaybackControlsRow
import com.lirix.app.ui.player.TrackMetadataHeader
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.LirixThemeTokens
import java.io.File

/**
 * Lirix Studio Deck 2.0 (The Analog-Digital Hybrid).
 * Synthesizes analog audio engineering (AIMP VU-meters, turntable) with
 * modern kinetic streaming karaoke and guitar chords (Yandex Music / LrcLib).
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
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors
    var selectedRightTab by remember { mutableIntStateOf(0) } // 0: Lyrics, 1: Chords, 2: Info

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

    // Default lyrics demonstration fallback when idle
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

    val demoChords = remember {
        """
[Вступление]
Am  C  G  F

[Куплет 1]
Am                     C
Звёзды падают с небес на асфальт,
G                      F
Ночью город зажигает огни.
Am                     C
Если веришь ты в свою мечту,
G                      F
Значит, мы в этом мире не одни.

[Припев]
Dm          G
И звучит винил сквозь тишину,
C           Am
Lirix держит ритм в унисон.
Dm          G
Проплывают стрелки на часах,
F           E7          Am
Это наш с тобой любимый сон!
        """.trimIndent()
    }

    val currentPositionMs = playback?.currentPositionMs() ?: 0L
    val durationMs = playback?.durationMs ?: 0L

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.appBackground)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ==========================================
        // TOP CONTROL BAR: Brand, Source, Modes
        // ==========================================
        DesktopTopBar(
            sourceApp = playback?.packageName,
            isPlaying = playback?.isPlaying == true,
            lyricsSource = lyricsState.source,
            isHudVisible = isHudVisible,
            onToggleHud = onToggleHud,
            isCompactMode = isCompactMode,
            onToggleCompactMode = onToggleCompactMode
        )

        // ==========================================
        // MAIN TWO-PANE STUDIO DECK
        // ==========================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ==========================================
            // LEFT DECK: Vinyl, VU-Meter, Header, Controls, Themes
            // ==========================================
            Surface(
                modifier = Modifier
                    .width(420.dp)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top section: Vinyl Platter, VU Meter & Metadata
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AnalogTurntable(
                            isPlaying = playback?.isPlaying == true,
                            albumArtBitmap = albumArtBitmap,
                            isCollapsed = false,
                            onToggleCollapse = {},
                            onTogglePlayPause = { ingressProvider.playPause() },
                            modifier = Modifier.padding(bottom = 6.dp),
                            tokens = colors
                        )

                        // Analog Stereo LED VU-Meter (AIMP Inspired)
                        AnalogVuMeter(
                            isPlaying = playback?.isPlaying == true,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        val displayTitle = if (lyricsState.displayTitle.isNotBlank()) lyricsState.displayTitle else (playback?.title ?: "Ожидание трека")
                        val displayArtist = if (lyricsState.displayArtist.isNotBlank()) lyricsState.displayArtist else (playback?.artist ?: "Lirix Desktop Player")

                        TrackMetadataHeader(
                            title = displayTitle,
                            artist = displayArtist,
                            album = playback?.album,
                            packageName = playback?.packageName,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    // Middle section: Playback Controls Slider & Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PlaybackControlsRow(
                            positionMs = currentPositionMs,
                            durationMs = durationMs,
                            isPlaying = playback?.isPlaying == true,
                            onSeekTo = { pos -> ingressProvider.seekTo(pos) },
                            onTogglePlayPause = { ingressProvider.playPause() },
                            onSkipNext = { ingressProvider.skipNext() },
                            onSkipPrevious = { ingressProvider.skipPrevious() },
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // Bottom section: Quick Theme Switcher Pills
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ДИЗАЙН ТЕМА",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textMuted,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ThemePill(
                                label = "Spotify",
                                theme = AppThemeType.SPOTIFY_MOOD,
                                isSelected = selectedTheme == AppThemeType.SPOTIFY_MOOD,
                                onClick = { onThemeSelected(AppThemeType.SPOTIFY_MOOD) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemePill(
                                label = "Yandex",
                                theme = AppThemeType.YANDEX_MUSIC,
                                isSelected = selectedTheme == AppThemeType.YANDEX_MUSIC,
                                onClick = { onThemeSelected(AppThemeType.YANDEX_MUSIC) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemePill(
                                label = "Obsidian",
                                theme = AppThemeType.OBSIDIAN_PULSE,
                                isSelected = selectedTheme == AppThemeType.OBSIDIAN_PULSE,
                                onClick = { onThemeSelected(AppThemeType.OBSIDIAN_PULSE) },
                                modifier = Modifier.weight(1f)
                            )
                            ThemePill(
                                label = "Pastel",
                                theme = AppThemeType.PASTEL_SERENITY,
                                isSelected = selectedTheme == AppThemeType.PASTEL_SERENITY,
                                onClick = { onThemeSelected(AppThemeType.PASTEL_SERENITY) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ==========================================
            // RIGHT DECK: Karaoke (LRC), Chords, Info Tabs
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
                    // Tab Selection Bar: «Текст (LRC)», «Аккорды», «О треке»
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceElevated)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DeckTabButton(
                            label = "🎤 Текст (LRC)",
                            isSelected = selectedRightTab == 0,
                            onClick = { selectedRightTab = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        DeckTabButton(
                            label = "🎸 Аккорды",
                            isSelected = selectedRightTab == 1,
                            onClick = { selectedRightTab = 1 },
                            modifier = Modifier.weight(1f)
                        )
                        DeckTabButton(
                            label = "ℹ О треке",
                            isSelected = selectedRightTab == 2,
                            onClick = { selectedRightTab = 2 },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tab Content Pane
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (selectedRightTab) {
                            0 -> {
                                if (lyricsState.isLoading) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                text = "🔍",
                                                fontSize = 40.sp,
                                                modifier = Modifier.padding(bottom = 12.dp)
                                            )
                                            Text(
                                                text = "Поиск караоке и текста песни...",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colors.textPrimary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Источники: LrcLib, AmDm, MyChords, Genius",
                                                fontSize = 12.sp,
                                                color = colors.textMuted
                                            )
                                        }
                                    }
                                } else {
                                    val linesToDisplay = if (lyricsState.lrcLines.isNotEmpty()) {
                                        lyricsState.lrcLines
                                    } else if (playback == null) {
                                        demoLrcLines
                                    } else {
                                        emptyList()
                                    }

                                    LyricsScrollPanel(
                                        lines = linesToDisplay,
                                        currentPositionMs = currentPositionMs,
                                        onSeekTo = { pos -> ingressProvider.seekTo(pos) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            1 -> {
                                if (lyricsState.isLoading) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Поиск аккордов на AmDm и MyChords...",
                                            fontSize = 14.sp,
                                            color = colors.textMuted
                                        )
                                    }
                                } else {
                                    val chordsToDisplay = if (!lyricsState.chords.isNullOrBlank()) {
                                        lyricsState.chords
                                    } else if (playback == null) {
                                        demoChords
                                    } else {
                                        ""
                                    }

                                    ChordsTabContent(
                                        chordsText = chordsToDisplay,
                                        durationMs = durationMs,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            else -> {
                                // Info Tab: Audio Engineering & Metadata Specs
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
}

@Composable
private fun TrackSpecsPanel(
    playback: LivePlaybackSnapshot?,
    lyricsState: DesktopLyricsState
) {
    val colors = LirixThemeTokens.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "ИНЖЕНЕРНЫЕ ПАРАМЕТРЫ СЕССИИ",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textMuted,
            letterSpacing = 1.2.sp
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
private fun ThemePill(
    label: String,
    theme: AppThemeType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) colors.accentPrimary else colors.surfaceElevated)
            .border(
                width = 1.dp,
                color = if (isSelected) colors.accentPrimary else colors.borderSubtle,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) colors.appBackground else colors.textSecondary
        )
    }
}

@Composable
private fun DeckTabButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) colors.surfaceCard else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                width = if (isSelected) 1.dp else 0.dp,
                color = if (isSelected) colors.borderSubtle else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) colors.textPrimary else colors.textMuted
        )
    }
}
