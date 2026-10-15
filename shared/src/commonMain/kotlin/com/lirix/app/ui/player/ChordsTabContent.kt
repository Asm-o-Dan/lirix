package com.lirix.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.feature.lyrics.ChordAutoscrollCalculator
import com.lirix.app.feature.lyrics.ChordTransposer
import com.lirix.app.feature.lyrics.GuitarChordDialog
import com.lirix.app.feature.lyrics.GuitarChordDictionary
import com.lirix.app.platform.currentTimeMillis
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Interactive guitar chords tab with transposition, adaptive autoscroll,
 * and chord fingering diagram dialogs.
 * Spec: TASK-KMP-09 (.sdd/tasks/TASK-KMP-09.md)
 */
@Composable
fun ChordsTabContent(
    chordsText: String,
    durationMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors

    if (chordsText.isBlank()) {
        Box(
            modifier = modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "🎸", fontSize = 44.sp)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Аккорды для трека не найдены",
                    color = colors.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Поддерживается база AmDm и MyChords",
                    color = colors.textMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    var transposeSemitones by remember { mutableIntStateOf(0) }
    var isAutoscrollRunning by remember { mutableStateOf(false) }
    var autoscrollMultiplier by remember { mutableFloatStateOf(1.0f) }
    var selectedChordForDialog by remember { mutableStateOf<String?>(null) }

    val transposedText = remember(chordsText, transposeSemitones) {
        if (transposeSemitones == 0) chordsText else ChordTransposer.transposeText(chordsText, transposeSemitones)
    }

    val songChords = remember(transposedText) {
        GuitarChordDictionary.extractChordsFromText(transposedText)
    }

    val scrollState = rememberScrollState()

    // Adaptive Autoscroll Loop
    LaunchedEffect(isAutoscrollRunning, autoscrollMultiplier, durationMs, chordsText) {
        if (!isAutoscrollRunning) return@LaunchedEffect
        var lastTime = currentTimeMillis()
        while (isActive && isAutoscrollRunning) {
            delay(16L) // ~60 FPS
            val now = currentTimeMillis()
            val frameDelta = (now - lastTime).coerceAtLeast(0L)
            lastTime = now

            val baseSpeed = ChordAutoscrollCalculator.calculateBaseSpeedPxPerSec(
                durationMs = durationMs,
                linesCount = chordsText.lines().size,
                totalScrollPx = scrollState.maxValue.toFloat()
            ).coerceAtLeast(35f)

            val step = ChordAutoscrollCalculator.calculateStepDelta(baseSpeed * autoscrollMultiplier, frameDelta)
            if (step > 0f) {
                scrollState.scrollBy(step)
            }
            if (scrollState.value >= scrollState.maxValue && scrollState.maxValue > 0) {
                isAutoscrollRunning = false
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Toolbar Container: Transpose + Autoscroll Controls
        Surface(
            color = colors.surfaceCard,
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Transpose controls and Autoscroll button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Transpose controls [ ♭ -1 ] [ Тон: 0 ] [ ♯ +1 ]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceElevated)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "♭ -1",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (transposeSemitones > -11) colors.textPrimary else colors.textMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = transposeSemitones > -11) {
                                    transposeSemitones--
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        val toneLabel = when {
                            transposeSemitones == 0 -> "Тон: 0"
                            transposeSemitones > 0 -> "+$transposeSemitones"
                            else -> "$transposeSemitones"
                        }
                        Text(
                            text = toneLabel,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (transposeSemitones != 0) colors.accentPrimary else colors.textSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { transposeSemitones = 0 }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )

                        Text(
                            text = "♯ +1",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (transposeSemitones < 11) colors.textPrimary else colors.textMuted,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = transposeSemitones < 11) {
                                    transposeSemitones++
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    // Autoscroll Start/Pause and Speed
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Speed multiplier button (0.75x, 1.0x, 1.25x, 1.5x)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceElevated)
                                .clickable {
                                    autoscrollMultiplier = when {
                                        autoscrollMultiplier < 1.0f -> 1.0f
                                        autoscrollMultiplier < 1.25f -> 1.25f
                                        autoscrollMultiplier < 1.5f -> 1.5f
                                        autoscrollMultiplier < 2.0f -> 2.0f
                                        else -> 0.75f
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${autoscrollMultiplier}x",
                                color = colors.accentSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Play/Pause Autoscroll
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isAutoscrollRunning) colors.accentPrimary.copy(alpha = 0.2f) else colors.surfaceElevated)
                                .border(
                                    width = 1.dp,
                                    color = if (isAutoscrollRunning) colors.accentPrimary else colors.borderSubtle,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    isAutoscrollRunning = !isAutoscrollRunning
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isAutoscrollRunning) "⏸ Автоскролл" else "▶ Автоскролл",
                                color = if (isAutoscrollRunning) colors.accentPrimary else colors.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Row 2: Interactive Song Chords Ribbon (Fingerings)
                if (songChords.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Аппликатуры:",
                            fontSize = 11.sp,
                            color = colors.textMuted,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(songChords) { chord ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.chordBadgeBg)
                                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(6.dp))
                                        .clickable {
                                            selectedChordForDialog = chord
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = chord,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.chordBadgeText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Chords Content View
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfacePrimary)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            Text(
                text = transposedText,
                color = colors.textPrimary,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 22.sp
            )
        }
    }

    // Modal dialog for chord fingerings
    if (selectedChordForDialog != null) {
        GuitarChordDialog(
            initialChordName = selectedChordForDialog!!,
            availableChords = songChords,
            onDismissRequest = { selectedChordForDialog = null }
        )
    }
}
