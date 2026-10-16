package com.lirix.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lirix.app.feature.lyrics.LrcSyncEngine
import com.lirix.app.feature.lyrics.SyncLineItem
import com.lirix.app.ingestion.MediaSessionCollector
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.ui.theme.LiterataFontFamily
import com.lirix.app.ui.theme.GolosTextFontFamily
import com.lirix.app.ui.theme.FontFeature
import com.lirix.app.ui.theme.toSentenceCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Fullscreen interactive dialog for syncing plain song lyrics into standard LRC
 * караоке timestamps in real-time ("Tap-to-Sync Studio").
 *
 * Spec: TASK-UI-MOB-FIX-04 / TASK-LRC-02
 *
 * @param trackTitle Name of song.
 * @param artist Artist of song.
 * @param initialPlainText Unsynchronized static song lyrics text.
 * @param onDismissRequest Called when the user exits the studio without saving.
 * @param onSaveLrc Called with the formatted LRC string when the user finishes and confirms saving.
 */
@Composable
fun LrcTapSyncStudioDialog(
    trackTitle: String,
    artist: String,
    initialPlainText: String,
    onDismissRequest: () -> Unit,
    onSaveLrc: (String) -> Unit
) {
    val tokens = LirixThemeTokens.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = LocalHapticFeedback.current
    val livePlayback by MediaSessionCollector.livePlaybackFlow.collectAsStateWithLifecycle()
    val syncPrefs = remember { context.getSharedPreferences("lirix_sync_prefs", android.content.Context.MODE_PRIVATE) }
    var reactionLagMs by remember { mutableLongStateOf(syncPrefs.getLong("reaction_lag_ms", 200L)) }

    var lines by remember(initialPlainText) {
        mutableStateOf(LrcSyncEngine.parsePlainToSyncLines(initialPlainText))
    }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPreviewOpen by remember { mutableStateOf(false) }
    var isEditingLyrics by remember { mutableStateOf(false) }

    // Realtime playback position tracker (50ms ticker)
    var currentPlaybackPositionMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(livePlayback?.isPlaying, livePlayback?.lastPositionUpdateTimeMs) {
        while (isActive) {
            val snapshot = livePlayback
            if (snapshot != null) {
                currentPlaybackPositionMs = snapshot.currentPositionMs()
            }
            delay(50L)
        }
    }

    // Pulsing animation for the active line cue
    val infiniteTransition = rememberInfiniteTransition(label = "PromptPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.025f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
            }
            onDispose {}
        }

        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val safeBottomPadding = maxOf(navBarBottom + 16.dp, 56.dp)

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            color = tokens.appBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = safeBottomPadding)
            ) {
                // 1. Top Bar: Header, Song Info, Close & Save Shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismissRequest()
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть студию",
                            tint = tokens.textSecondary
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = trackTitle.ifBlank { "Студия караоке" },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = GolosTextFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            ),
                            color = tokens.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist.ifBlank { "Tap-to-Sync" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = GolosTextFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 12.sp
                            ),
                            color = tokens.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Top Action buttons: Edit plain text & Preview LRC
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isEditingLyrics = true
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Редактировать текст",
                                tint = tokens.textSecondary
                            )
                        }

                        val hasAnyMarked = remember(lines) { LrcSyncEngine.markedCount(lines) > 0 }
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isPreviewOpen = true
                            },
                            enabled = hasAnyMarked,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = "Предпросмотр LRC",
                                tint = if (hasAnyMarked) tokens.accentPrimary else tokens.textMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. Progress Indicator & Step Count Badge
                val progress = remember(lines) { LrcSyncEngine.progress(lines) }
                val markedCount = remember(lines) { LrcSyncEngine.markedCount(lines) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Строка ${minOf(currentIndex + 1, lines.size)} из ${lines.size}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = GolosTextFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        color = tokens.accentPrimary
                    )

                    Surface(
                        color = tokens.surfaceElevated,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle)
                    ) {
                        Text(
                            text = "Размечено: $markedCount / ${lines.size}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = GolosTextFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                fontFeatureSettings = FontFeature.tabularFigures()
                            ),
                            color = if (markedCount == lines.size && lines.isNotEmpty()) tokens.accentPrimary else tokens.textSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = tokens.accentPrimary,
                    trackColor = tokens.surfaceElevated
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Central Teleprompter Focus Area
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = tokens.surfacePrimary,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Previous Line (Line - 1)
                        val prevItem = lines.getOrNull(currentIndex - 1)
                        if (prevItem != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val prevTimestampMs = prevItem.timestampMs
                                if (prevTimestampMs != null) {
                                    Text(
                                        text = LrcSyncEngine.formatTimestamp(prevTimestampMs),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = GolosTextFontFamily,
                                            fontFeatureSettings = FontFeature.tabularFigures(),
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 11.sp
                                        ),
                                        color = tokens.accentPrimary.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                                Text(
                                    text = prevItem.text.toSentenceCase(),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = LiterataFontFamily,
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 14.sp,
                                        lineHeight = 20.sp
                                    ),
                                    color = tokens.textSecondary.copy(alpha = 0.65f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Text(
                                text = "— Начало песни —",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GolosTextFontFamily,
                                    fontSize = 12.sp
                                ),
                                color = tokens.textMuted.copy(alpha = 0.5f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // ACTIVE / FOCUS LINE (Line 0)
                        val currentItem = lines.getOrNull(currentIndex)
                        if (currentItem != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(pulseScale)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(tokens.surfaceCard)
                                    .border(
                                        width = 1.5.dp,
                                        color = tokens.accentPrimary,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = currentItem.text.toSentenceCase(),
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontFamily = LiterataFontFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 20.sp,
                                            lineHeight = 28.sp
                                        ),
                                        color = tokens.textPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Нажмите в начале этой строки",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = GolosTextFontFamily,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 12.sp
                                        ),
                                        color = tokens.accentPrimary
                                    )
                                }
                            }
                        } else {
                            // Completion Banner
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(tokens.surfaceCard)
                                    .border(
                                        width = 1.5.dp,
                                        color = tokens.accentPrimary,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = tokens.accentPrimary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Все строки синхронизированы!",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontFamily = GolosTextFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp
                                        ),
                                        color = tokens.accentPrimary,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Нажмите «Сохранить караоке» ниже для записи в медиатеку",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = GolosTextFontFamily,
                                            fontSize = 12.sp
                                        ),
                                        color = tokens.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Next Line (Line + 1)
                        val nextItem = lines.getOrNull(currentIndex + 1)
                        if (nextItem != null) {
                            Text(
                                text = nextItem.text.toSentenceCase(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = LiterataFontFamily,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                ),
                                color = tokens.textSecondary.copy(alpha = 0.65f),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (currentIndex < lines.size) {
                            Text(
                                text = "— Конец текста —",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GolosTextFontFamily,
                                    fontSize = 12.sp
                                ),
                                color = tokens.textMuted.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Compact Media Player Controller (Play/Pause & Seek)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = tokens.surfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Rewind -5s
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                MediaSessionCollector.seekRelative(-5000L)
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay5,
                                contentDescription = "Назад 5с",
                                tint = tokens.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Play / Pause Button
                        val isPlaying = livePlayback?.isPlaying == true
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(tokens.accentPrimary)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    MediaSessionCollector.togglePlayPause()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Пауза" else "Воспроизведение",
                                tint = tokens.appBackground,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Forward +5s
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                MediaSessionCollector.seekRelative(5000L)
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward5,
                                contentDescription = "Вперед 5с",
                                tint = tokens.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Position / Duration
                        val durationMs = livePlayback?.durationMs ?: 0L
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = LrcSyncEngine.formatTimeLabel(currentPlaybackPositionMs),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = GolosTextFontFamily,
                                    fontFeatureSettings = FontFeature.tabularFigures(),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = tokens.textPrimary
                            )
                            if (durationMs > 0L) {
                                Text(
                                    text = "/ ${LrcSyncEngine.formatTimeLabel(durationMs)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = GolosTextFontFamily,
                                        fontFeatureSettings = FontFeature.tabularFigures(),
                                        fontWeight = FontWeight.Normal,
                                        fontSize = 11.sp
                                    ),
                                    color = tokens.textSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4b. Reaction Lag Controller (Motor latency compensation)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Задержка реакции: ${reactionLagMs} мс",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = GolosTextFontFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp
                        ),
                        color = tokens.textSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(tokens.surfaceElevated)
                                .border(1.dp, tokens.borderSubtle, RoundedCornerShape(8.dp))
                                .clickable {
                                    val newLag = (reactionLagMs - 50L).coerceAtLeast(0L)
                                    reactionLagMs = newLag
                                    syncPrefs.edit().putLong("reaction_lag_ms", newLag).apply()
                                }
                                .defaultMinSize(minWidth = 52.dp, minHeight = 44.dp)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "−50 мс",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GolosTextFontFamily,
                                    fontFeatureSettings = FontFeature.tabularFigures(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = tokens.textPrimary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(tokens.surfaceElevated)
                                .border(1.dp, tokens.borderSubtle, RoundedCornerShape(8.dp))
                                .clickable {
                                    val newLag = (reactionLagMs + 50L).coerceAtMost(500L)
                                    reactionLagMs = newLag
                                    syncPrefs.edit().putLong("reaction_lag_ms", newLag).apply()
                                }
                                .defaultMinSize(minWidth = 52.dp, minHeight = 44.dp)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+50 мс",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = GolosTextFontFamily,
                                    fontFeatureSettings = FontFeature.tabularFigures(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                color = tokens.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 5. Massive Ergonomic Action Button (TAP TO SYNC)
                val isAllFinished = currentIndex >= lines.size && lines.isNotEmpty()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.accentPrimary)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (currentIndex < lines.size) {
                                val currentMs = livePlayback?.currentPositionMs() ?: currentPlaybackPositionMs
                                lines = LrcSyncEngine.recordTimestamp(lines, currentIndex, currentMs, reactionLagMs)
                                currentIndex++
                                if (currentIndex >= lines.size) {
                                    // Finished all lines! Open preview for review
                                    isPreviewOpen = true
                                }
                            } else {
                                // Completed state -> Open preview
                                isPreviewOpen = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isAllFinished) Icons.Default.Check else Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = tokens.appBackground,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAllFinished) "Сохранить караоке" else "Тап — следующая строка",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = GolosTextFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            ),
                            color = tokens.appBackground
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 6. Bottom Navigation Controls: [ ↶ Отменить шаг ] | [ 🔄 Сбросить всё ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Undo Button
                    val canUndo = currentIndex > 0 || LrcSyncEngine.markedCount(lines) > 0
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val (undoneLines, newIdx) = LrcSyncEngine.undoTimestamp(lines, currentIndex)
                            lines = undoneLines
                            currentIndex = newIdx
                            // Smart audio cue seek: rewind 2 seconds to allow user to hear cue again
                            val rewindTarget = maxOf(0L, currentPlaybackPositionMs - 2000L)
                            MediaSessionCollector.seekTo(rewindTarget)
                        },
                        enabled = canUndo,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = tokens.textPrimary,
                            disabledContentColor = tokens.textMuted
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (canUndo) tokens.accentPrimary else tokens.borderSubtle
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Отменить шаг",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = GolosTextFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }

                    // Reset Button
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            lines = LrcSyncEngine.parsePlainToSyncLines(initialPlainText)
                            currentIndex = 0
                        },
                        enabled = markedCount > 0,
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = tokens.textSecondary,
                            disabledContentColor = tokens.textMuted
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (markedCount > 0) tokens.accentPrimary.copy(alpha = 0.5f) else tokens.borderSubtle
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Сбросить всё",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = GolosTextFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }
            }
        }
    }

    // 7. Manual Lyrics Editing Modal Dialog
    if (isEditingLyrics) {
        val currentPlainText = remember(lines) { lines.joinToString("\n") { it.text } }
        EditLyricsDialog(
            trackTitle = trackTitle,
            artist = artist,
            initialLyrics = currentPlainText.ifBlank { initialPlainText },
            onDismissRequest = { isEditingLyrics = false },
            onSaveLyrics = { updatedText ->
                isEditingLyrics = false
                lines = LrcSyncEngine.parsePlainToSyncLines(updatedText)
                currentIndex = 0
            }
        )
    }

    // 8. Preview & Final Save Modal Dialog
    if (isPreviewOpen) {
        val lrcContent = remember(lines) {
            val sanitized = LrcSyncEngine.validateAndSanitize(lines)
            LrcSyncEngine.buildLrcString(sanitized)
        }
        val isMonotonic = remember(lines) { LrcSyncEngine.isMonotonic(lines) }

        Dialog(onDismissRequest = { isPreviewOpen = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                shape = RoundedCornerShape(24.dp),
                color = tokens.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Предпросмотр LRC",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = GolosTextFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 17.sp
                                ),
                                color = tokens.textPrimary
                            )
                            Text(
                                text = "Синхронизировано строк: ${LrcSyncEngine.markedCount(lines)} из ${lines.size}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = GolosTextFontFamily,
                                    fontSize = 12.sp
                                ),
                                color = tokens.textSecondary
                            )
                        }

                        if (isMonotonic) {
                            Surface(
                                color = tokens.surfacePrimary,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "✓ Хронология OK",
                                    color = tokens.accentPrimary,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = GolosTextFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Interactive fine-tuning list with [−0,1 с] and [+0,1 с] buttons
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = tokens.surfacePrimary,
                        border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(lines) { idx, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(tokens.surfaceCard)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.timestampMs?.let { LrcSyncEngine.formatTimestamp(it) } ?: "[--:--.--]",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = GolosTextFontFamily,
                                            fontFeatureSettings = FontFeature.tabularFigures(),
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 12.sp
                                        ),
                                        color = if (item.timestampMs != null) tokens.accentPrimary else tokens.textMuted,
                                        modifier = Modifier.width(78.dp)
                                    )
                                    Text(
                                        text = item.text.toSentenceCase(),
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = LiterataFontFamily,
                                            fontWeight = FontWeight.Normal,
                                            fontSize = 13.sp
                                        ),
                                        color = tokens.textPrimary,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (item.timestampMs != null) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(tokens.surfaceElevated)
                                                    .border(1.dp, tokens.borderSubtle, RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        lines = LrcSyncEngine.adjustTimestamp(lines, idx, -100L)
                                                    }
                                                    .defaultMinSize(minWidth = 44.dp, minHeight = 32.dp)
                                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "−0,1 с",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = GolosTextFontFamily,
                                                        fontFeatureSettings = FontFeature.tabularFigures(),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    color = tokens.textPrimary
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(tokens.surfaceElevated)
                                                    .border(1.dp, tokens.borderSubtle, RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        lines = LrcSyncEngine.adjustTimestamp(lines, idx, +100L)
                                                    }
                                                    .defaultMinSize(minWidth = 44.dp, minHeight = 32.dp)
                                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "+0,1 с",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = GolosTextFontFamily,
                                                        fontFeatureSettings = FontFeature.tabularFigures(),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    color = tokens.textPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isPreviewOpen = false },
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, tokens.borderSubtle)
                        ) {
                            Text(
                                text = "Назад к записи",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = GolosTextFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                ),
                                color = tokens.textSecondary
                            )
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isPreviewOpen = false
                                onSaveLrc(lrcContent)
                            },
                            enabled = lrcContent.isNotBlank(),
                            modifier = Modifier
                                .weight(1.2f)
                                .defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = tokens.accentPrimary,
                                contentColor = tokens.appBackground
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Сохранить",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = GolosTextFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
