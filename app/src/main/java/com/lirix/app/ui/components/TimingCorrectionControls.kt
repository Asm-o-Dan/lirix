package com.lirix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.lirix.app.R
import com.lirix.app.ingestion.SyncOffsetStore
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Stored offset: positive advances text. Users see the lyrics delay instead (= -offset), so
 * "−" moves text earlier, "+" later, and the shown number has the same sign as the buttons.
 */
internal fun lyricsDelayMs(offsetMs: Long): Long = -offsetMs

/** Stored offset for a lyrics delay chosen by the user; bounds are symmetric. */
internal fun offsetForLyricsDelay(delayMs: Long): Long =
    (-delayMs).coerceIn(SyncOffsetStore.MIN_OFFSET_MS, SyncOffsetStore.MAX_OFFSET_MS)

/** Signed number without unit: "+50", "0", "−50". */
internal fun formatLyricsDelay(delayMs: Long): String = when {
    delayMs > 0 -> "+$delayMs"
    delayMs < 0 -> "−${-delayMs}"
    else -> "0"
}

private const val TIMING_STEP_MS = 50L

@Composable
fun TimingCorrectionControls(offsetMs: Long, onChange: (Long) -> Unit, onDetails: () -> Unit, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(offsetMs)
    val change by rememberUpdatedState(onChange)
    val tokens = LirixThemeTokens.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RepeatTimingButton("−50", stringResource(R.string.timing_lyrics_earlier, TIMING_STEP_MS.toInt())) {
            change(offsetForLyricsDelay(lyricsDelayMs(current) - TIMING_STEP_MS))
        }
        TextButton(onClick = onDetails, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(
                text = stringResource(R.string.timing_delay_value, formatLyricsDelay(lyricsDelayMs(offsetMs))),
                color = tokens.textSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        RepeatTimingButton("+50", stringResource(R.string.timing_lyrics_later, TIMING_STEP_MS.toInt())) {
            change(offsetForLyricsDelay(lyricsDelayMs(current) + TIMING_STEP_MS))
        }
    }
}

@Composable
private fun RepeatTimingButton(label: String, description: String, onStep: () -> Unit) {
    val step by rememberUpdatedState(onStep)
    val tokens = LirixThemeTokens.colors
    Box(
        Modifier
            .size(48.dp)
            .background(tokens.surfaceElevated, RoundedCornerShape(8.dp))
            .semantics { role = Role.Button; contentDescription = description; onClick { step(); true } }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    step()
                    coroutineScope {
                        val repeating = launch { delay(400); while (true) { step(); delay(100) } }
                        try { tryAwaitRelease() } finally { repeating.cancel() }
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = tokens.textPrimary, style = MaterialTheme.typography.labelMedium)
    }
}
