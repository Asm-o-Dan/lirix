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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lirix.app.feature.lyrics.LrcSyncEngine
import com.lirix.app.feature.lyrics.SyncLineItem
import com.lirix.app.ingestion.MediaSessionCollector
import com.lirix.app.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Fullscreen interactive AMOLED dialog for syncing plain song lyrics into standard LRC
 * караоке timestamps in real-time ("Tap-to-Sync Studio").
 *
 * Spec: TASK-LRC-02 / .sdd/specs/media-ui/lrc_tap_sync_studio.md
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
    val haptic = LocalHapticFeedback.current
    val livePlayback by MediaSessionCollector.livePlaybackFlow.collectAsStateWithLifecycle()

    var lines by remember(initialPlainText) {
        mutableStateOf(LrcSyncEngine.parsePlainToSyncLines(initialPlainText))
    }
    var currentIndex by remember { mutableIntStateOf(0) }
    var isPreviewOpen by remember { mutableStateOf(false) }

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
        targetValue = 1.03f,
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
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = AppColors.AmoledBlack
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
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
                            tint = AppColors.TextSecondary
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
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            ),
                            color = AppColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = artist.ifBlank { "Tap-to-Sync" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = AppColors.HyperViolet,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Top Preview / Save button
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
                            tint = if (hasAnyMarked) AppColors.CyberCyan else AppColors.TextDisabled
                        )
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
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        color = AppColors.CyberCyan
                    )

                    Surface(
                        color = AppColors.SurfaceLevel2,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderSubtle)
                    ) {
                        Text(
                            text = "Размечено: $markedCount / ${lines.size}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            ),
                            color = if (markedCount == lines.size && lines.isNotEmpty()) AppColors.ElectricMint else AppColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = AppColors.CyberCyan,
                    trackColor = AppColors.SurfaceLevel2
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Central Teleprompter Focus Area
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = AppColors.SurfaceLevel1,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                if (prevItem.timestampMs != null) {
                                    Text(
                                        text = LrcSyncEngine.formatTimestamp(prevItem.timestampMs),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp
                                        ),
                                        color = AppColors.ElectricMint.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                                Text(
                                    text = prevItem.text,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal
                                    ),
                                    color = AppColors.TextSecondary.copy(alpha = 0.55f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Text(
                                text = "— Начало песни —",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextTertiary.copy(alpha = 0.35f)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // ACTIVE / FOCUS LINE (Line 0)
                        val currentItem = lines.getOrNull(currentIndex)
                        if (currentItem != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(pulseScale)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AppColors.SurfaceLevel2)
                                    .border(
                                        width = 1.5.dp,
                                        brush = Brush.horizontalGradient(
                                            listOf(AppColors.HyperViolet, AppColors.CyberCyan)
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = currentItem.text,
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            lineHeight = 26.sp
                                        ),
                                        color = AppColors.CyberCyan,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "ТАПАЙТЕ В НАЧАЛО ЭТОЙ СТРОКИ",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            letterSpacing = 1.sp
                                        ),
                                        color = AppColors.HyperViolet
                                    )
                                }
                            }
                        } else {
                            // Completion Banner
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(AppColors.SurfaceLevel2)
                                    .border(
                                        width = 1.5.dp,
                                        color = AppColors.ElectricMint,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = AppColors.ElectricMint,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Все строки синхронизированы!",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        ),
                                        color = AppColors.ElectricMint,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Нажмите «Сохранить караоке» ниже для записи в медиатеку",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AppColors.TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Next Line (Line + 1)
                        val nextItem = lines.getOrNull(currentIndex + 1)
                        if (nextItem != null) {
                            Text(
                                text = nextItem.text,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = AppColors.TextSecondary.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else if (currentIndex < lines.size) {
                            Text(
                                text = "— Конец текста —",
                                style = MaterialTheme.typography.labelSmall,
                                color = AppColors.TextTertiary.copy(alpha = 0.35f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Compact Media Player Controller (Play/Pause & Seek)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = AppColors.SurfaceLevel2,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderSubtle)
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
                                tint = AppColors.TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Play / Pause Button
                        val isPlaying = livePlayback?.isPlaying == true
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(AppColors.HyperViolet)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    MediaSessionCollector.togglePlayPause()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Пауза" else "Воспроизведение",
                                tint = Color.Black,
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
                                tint = AppColors.TextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Position / Duration
                        val durationMs = livePlayback?.durationMs ?: 0L
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = LrcSyncEngine.formatTimeLabel(currentPlaybackPositionMs),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                color = AppColors.CyberCyan
                            )
                            if (durationMs > 0L) {
                                Text(
                                    text = "/ ${LrcSyncEngine.formatTimeLabel(durationMs)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    ),
                                    color = AppColors.TextTertiary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. Massive Ergonomic Action Button (TAP TO SYNC)
                val isAllFinished = currentIndex >= lines.size && lines.isNotEmpty()
                val buttonBrush = if (isAllFinished) {
                    Brush.horizontalGradient(listOf(AppColors.ElectricMint, AppColors.CyberCyan))
                } else {
                    Brush.horizontalGradient(listOf(AppColors.HyperViolet, AppColors.CyberCyan))
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(buttonBrush)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (currentIndex < lines.size) {
                                val currentMs = livePlayback?.currentPositionMs() ?: currentPlaybackPositionMs
                                lines = LrcSyncEngine.recordTimestamp(lines, currentIndex, currentMs)
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
                            tint = Color.Black,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isAllFinished) "СОХРАНИТЬ КАРАОКЕ" else "ТАП • СЛЕДУЮЩАЯ СТРОКА",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                letterSpacing = 0.5.sp
                            ),
                            color = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 6. Bottom Navigation Controls: [ ↺ Шаг назад ] | [ 🔄 Сбросить ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            .defaultMinSize(minHeight = 40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AppColors.TextPrimary,
                            disabledContentColor = AppColors.TextDisabled
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (canUndo) AppColors.BorderFocused else AppColors.BorderSubtle
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Шаг назад",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
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
                            .defaultMinSize(minHeight = 40.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AppColors.ExpenseRed,
                            disabledContentColor = AppColors.TextDisabled
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (markedCount > 0) AppColors.ExpenseRed.copy(alpha = 0.5f) else AppColors.BorderSubtle
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Сбросить",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }
    }

    // 7. Preview & Final Save Modal Dialog
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
                color = AppColors.SurfaceLevel2,
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderSubtle)
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
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 17.sp
                                ),
                                color = AppColors.TextPrimary
                            )
                            Text(
                                text = "Синхронизировано строк: ${LrcSyncEngine.markedCount(lines)} из ${lines.size}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.HyperViolet
                            )
                        }

                        if (isMonotonic) {
                            Surface(
                                color = AppColors.IncomeContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "✓ Хронология OK",
                                    color = AppColors.IncomeGreen,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Monospace text field with generated LRC content
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = AppColors.SurfaceLevel1,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderSubtle)
                    ) {
                        Text(
                            text = lrcContent.ifBlank { "(Нет размеченных строк)" },
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            ),
                            color = AppColors.TextPrimary,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
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
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.BorderSubtle)
                        ) {
                            Text(text = "Назад к записи", color = AppColors.TextSecondary)
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
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AppColors.ElectricMint,
                                contentColor = Color.Black
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
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
