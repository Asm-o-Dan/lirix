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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import com.lirix.app.feature.lyrics.ChordAutoscrollCalculator
import com.lirix.app.feature.lyrics.ChordTransposer
import com.lirix.app.feature.lyrics.GuitarChordDialog
import com.lirix.app.feature.lyrics.GuitarChordDictionary
import com.lirix.app.platform.currentTimeMillis
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val CHORD_MATCH_REGEX = Regex(
    """^([A-H][#b]?)(m|maj|min|dim|aug|sus2|sus4|add9|5|6|7|maj7|m7|mM7)?(?:/([A-H][#b]?))?$""",
    RegexOption.IGNORE_CASE
)

private fun isChordToken(token: String): Boolean {
    val clean = token.trim().removePrefix("[").removeSuffix("]").removePrefix("(").removeSuffix(")")
    return CHORD_MATCH_REGEX.matches(clean) || GuitarChordDictionary.getChord(clean) != null
}

private fun isChordLine(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    val words = trimmed.split(Regex("""\s+""")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return false
    val chordCount = words.count { isChordToken(it) }
    return chordCount.toFloat() / words.size >= 0.5f
}

/**
 * Parses a line containing inline bracketed chords (e.g. "[Am]Вчера я [C]видел [Dm]свет")
 * and converts it into a monospaced column-aligned pair of (ChordLine, LyricLine)
 * where each chord sits directly over its corresponding syllable.
 */
private fun formatInlineChordsToSyllabicPair(line: String): Pair<String, String>? {
    if (!line.contains("[") || !line.contains("]")) return null
    val chordSb = StringBuilder()
    val lyricSb = StringBuilder()
    var i = 0
    var hasChords = false

    while (i < line.length) {
        if (line[i] == '[') {
            val closeIdx = line.indexOf(']', i)
            if (closeIdx != -1) {
                val candidate = line.substring(i + 1, closeIdx).trim()
                if (isChordToken(candidate)) {
                    hasChords = true
                    // Pad chord line with spaces to match current character position in lyric line
                    while (chordSb.length < lyricSb.length) {
                        chordSb.append(' ')
                    }
                    // If chord line is already at or past lyric line, ensure at least 1 separator space
                    if (chordSb.isNotEmpty() && !chordSb.last().isWhitespace()) {
                        chordSb.append(' ')
                    }
                    // Keep lyrics line aligned with chord insertion point
                    while (lyricSb.length < chordSb.length) {
                        lyricSb.append(' ')
                    }
                    chordSb.append(candidate)
                    i = closeIdx + 1
                    continue
                }
            }
        }
        lyricSb.append(line[i])
        i++
    }

    if (!hasChords) return null
    return Pair(chordSb.toString(), lyricSb.toString())
}

private fun AnnotatedString.Builder.appendChordLine(
    line: String,
    accentColor: Color,
    textColor: Color
) {
    var i = 0
    while (i < line.length) {
        if (line[i].isWhitespace()) {
            append(line[i])
            i++
        } else {
            val start = i
            while (i < line.length && !line[i].isWhitespace()) {
                i++
            }
            val word = line.substring(start, i)
            if (isChordToken(word)) {
                withStyle(
                    SpanStyle(
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                ) {
                    append(word)
                }
            } else {
                withStyle(
                    SpanStyle(
                        color = textColor,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                ) {
                    append(word)
                }
            }
        }
    }
}

private fun buildChordAnnotatedString(
    text: String,
    accentColor: Color,
    textColor: Color
): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.lines()
        lines.forEachIndexed { index, line ->
            val inlinePair = formatInlineChordsToSyllabicPair(line)
            if (inlinePair != null) {
                appendChordLine(inlinePair.first, accentColor, textColor)
                append("\n")
                withStyle(
                    SpanStyle(
                        color = textColor,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                ) {
                    append(inlinePair.second)
                }
            } else if (isChordLine(line)) {
                appendChordLine(line, accentColor, textColor)
            } else {
                withStyle(
                    SpanStyle(
                        color = textColor,
                        fontWeight = FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                ) {
                    append(line)
                }
            }
            if (index < lines.size - 1) {
                append("\n")
            }
        }
    }
}

/**
 * Interactive guitar chords tab with transposition, adaptive autoscroll,
 * and chord fingering diagram dialogs.
 * Spec: TASK-KMP-09 / TASK-UI-MOB-FIX-03
 */
@Composable
fun ChordsTabContent(
    chordsText: String,
    durationMs: Long = 0L,
    initialTransposeSemitones: Int = 0,
    onTransposeChange: ((Int) -> Unit)? = null,
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

    var transposeSemitones by remember(chordsText, initialTransposeSemitones) { mutableIntStateOf(initialTransposeSemitones) }
    var isAutoscrollRunning by remember { mutableStateOf(false) }
    var autoscrollMultiplier by remember { mutableFloatStateOf(1.0f) }
    var selectedChordForDialog by remember { mutableStateOf<String?>(null) }

    val transposedText = remember(chordsText, transposeSemitones) {
        if (transposeSemitones == 0) chordsText else ChordTransposer.transposeText(chordsText, transposeSemitones)
    }

    val chordAnnotatedText = remember(transposedText, colors.accentPrimary, colors.textPrimary) {
        buildChordAnnotatedString(transposedText, colors.accentPrimary, colors.textPrimary)
    }

    val songChords = remember(transposedText) {
        GuitarChordDictionary.extractChordsFromText(transposedText)
    }

    val originalBaseChord = remember(chordsText) {
        GuitarChordDictionary.extractChordsFromText(chordsText).firstOrNull()
    }

    val currentKey = remember(originalBaseChord, transposeSemitones) {
        if (originalBaseChord != null) {
            ChordTransposer.transposeChord(originalBaseChord, transposeSemitones)
        } else {
            null
        }
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Ultra-compact chords toolbar: Transpose stepper | Inline chord chips | Autoscroll trigger
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Compact Key Stepper: [ ♭ ] [ Key ] [ ♯ ]
            val keyLabel = when {
                currentKey != null && transposeSemitones == 0 -> currentKey
                currentKey != null && transposeSemitones > 0 -> "$currentKey (+$transposeSemitones)"
                currentKey != null && transposeSemitones < 0 -> "$currentKey ($transposeSemitones)"
                transposeSemitones == 0 -> "Orig"
                transposeSemitones > 0 -> "+$transposeSemitones"
                else -> "$transposeSemitones"
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(enabled = transposeSemitones > -11) {
                            val newT = transposeSemitones - 1
                            transposeSemitones = newT
                            onTransposeChange?.invoke(newT)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "♭",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (transposeSemitones > -11) colors.textPrimary else colors.textMuted
                    )
                }

                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clickable {
                            transposeSemitones = 0
                            onTransposeChange?.invoke(0)
                        }
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = keyLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (transposeSemitones != 0) colors.accentPrimary else colors.textSecondary
                    )
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clickable(enabled = transposeSemitones < 11) {
                            val newT = transposeSemitones + 1
                            transposeSemitones = newT
                            onTransposeChange?.invoke(newT)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "♯",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (transposeSemitones < 11) colors.textPrimary else colors.textMuted
                    )
                }
            }

            // 2. Inline interactive chord fingerings ribbon
            if (songChords.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(songChords) { chord ->
                        Box(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.chordBadgeBg)
                                .border(1.dp, colors.borderSubtle, RoundedCornerShape(6.dp))
                                .clickable {
                                    selectedChordForDialog = chord
                                }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
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
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // 3. Compact Autoscroll Controls: [ 1.0x ] [ ▶ / ⏸ ]
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Speed multiplier chip
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceCard)
                        .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
                        .clickable {
                            autoscrollMultiplier = when {
                                autoscrollMultiplier < 1.0f -> 1.0f
                                autoscrollMultiplier < 1.25f -> 1.25f
                                autoscrollMultiplier < 1.5f -> 1.5f
                                autoscrollMultiplier < 2.0f -> 2.0f
                                else -> 0.75f
                            }
                        }
                        .padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${autoscrollMultiplier}x",
                        color = colors.accentSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Autoscroll Start/Pause
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isAutoscrollRunning) colors.accentPrimary.copy(alpha = 0.2f) else colors.surfaceCard)
                        .border(
                            width = 1.dp,
                            color = if (isAutoscrollRunning) colors.accentPrimary else colors.borderSubtle,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            isAutoscrollRunning = !isAutoscrollRunning
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isAutoscrollRunning) "⏸" else "▶",
                        color = if (isAutoscrollRunning) colors.accentPrimary else colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        val horizontalScrollState = rememberScrollState()

        // 4. Maximized Chords Content View (>85% vertical screen space)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfacePrimary)
                .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
                .verticalScroll(scrollState)
                .horizontalScroll(horizontalScrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = chordAnnotatedText,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 22.sp,
                softWrap = false
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
