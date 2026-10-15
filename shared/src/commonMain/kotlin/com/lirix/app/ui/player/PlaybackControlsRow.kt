package com.lirix.app.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.theme.LirixThemeTokens

/**
 * Isolated playback control row with timeline slider and transport buttons.
 * Spec: TASK-KMP-09 (.sdd/tasks/TASK-KMP-09.md)
 */
@Composable
fun PlaybackControlsRow(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(0L)
    val displayPosition = if (isDragging) {
        (dragFraction * safeDuration).toLong()
    } else {
        positionMs.coerceIn(0L, if (safeDuration > 0) safeDuration else Long.MAX_VALUE)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Timeline Slider
        val sliderValue = if (safeDuration > 0) {
            if (isDragging) dragFraction else (displayPosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
        } else 0f

        Slider(
            value = sliderValue,
            onValueChange = { frac ->
                isDragging = true
                dragFraction = frac
            },
            onValueChangeFinished = {
                val targetMs = (dragFraction * safeDuration).toLong()
                onSeekTo(targetMs)
                isDragging = false
            },
            enabled = safeDuration > 0,
            colors = SliderDefaults.colors(
                thumbColor = colors.accentPrimary,
                activeTrackColor = colors.accentPrimary,
                inactiveTrackColor = colors.borderSubtle
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Time labels: mm:ss / mm:ss
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDurationMs(displayPosition),
                color = colors.textSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = formatDurationMs(safeDuration),
                color = colors.textMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Transport Controls Row (Prev, Play/Pause, Next)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
            ) {
                Text(
                    text = "⏮",
                    fontSize = 20.sp,
                    color = colors.textPrimary
                )
            }

            Spacer(modifier = Modifier.width(18.dp))

            Button(
                onClick = onTogglePlayPause,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentPrimary,
                    contentColor = colors.appBackground
                ),
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(54.dp)
            ) {
                Text(
                    text = if (isPlaying) "⏸" else "▶",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(18.dp))

            IconButton(
                onClick = onSkipNext,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
            ) {
                Text(
                    text = "⏭",
                    fontSize = 20.sp,
                    color = colors.textPrimary
                )
            }
        }
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val mStr = if (minutes < 10) "0$minutes" else "$minutes"
    val sStr = if (seconds < 10) "0$seconds" else "$seconds"
    return "$mStr:$sStr"
}
