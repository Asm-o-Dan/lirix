package com.lirix.app.desktop.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlin.random.Random

/**
 * Analog-style stereo LED VU Meter inspired by classic AIMP and studio mixing consoles.
 * Displays Left and Right channels with dynamic peak falloff and decibel tick marks.
 */
@Composable
fun AnalogVuMeter(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    totalSegments: Int = 18
) {
    val colors = LirixThemeTokens.colors

    // Oscillating animated pulse when music is actively playing
    val infiniteTransition = rememberInfiniteTransition(label = "VuPulse")
    val waveA by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WaveA"
    )
    val waveB by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(310, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "WaveB"
    )

    // Smooth physics damping animatables for Left and Right channels
    val animLeft = remember { Animatable(0f) }
    val animRight = remember { Animatable(0f) }

    LaunchedEffect(isPlaying, waveA, waveB) {
        if (isPlaying) {
            val targetL = (waveA * 0.7f + Random.nextFloat() * 0.28f).coerceIn(0.15f, 0.98f)
            val targetR = (waveB * 0.7f + Random.nextFloat() * 0.28f).coerceIn(0.15f, 0.98f)
            animLeft.animateTo(targetL, tween(60, easing = LinearEasing))
            animRight.animateTo(targetR, tween(70, easing = LinearEasing))
        } else {
            animLeft.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
            animRight.animateTo(0f, tween(400, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.85f))
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Channel L
            VuChannelRow(
                channelLabel = "L",
                level = animLeft.value,
                totalSegments = totalSegments,
                accentColor = colors.accentPrimary
            )

            // Channel R
            VuChannelRow(
                channelLabel = "R",
                level = animRight.value,
                totalSegments = totalSegments,
                accentColor = colors.accentPrimary
            )

            // Decibel scale markers (-24, -12, -6, -3, 0 dB, PEAK)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "-24", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = colors.textMuted)
                Text(text = "-12", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = colors.textMuted)
                Text(text = "-6", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = colors.textMuted)
                Text(text = "-3", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = colors.textMuted)
                Text(text = "0", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = colors.accentSecondary, fontWeight = FontWeight.Bold)
                Text(text = "PK", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VuChannelRow(
    channelLabel: String,
    level: Float,
    totalSegments: Int,
    accentColor: Color
) {
    val colors = LirixThemeTokens.colors
    val activeCount = (level * totalSegments).toInt().coerceIn(0, totalSegments)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = channelLabel,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (level > 0.05f) colors.textPrimary else colors.textMuted,
            modifier = Modifier.width(16.dp)
        )

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until totalSegments) {
                val isActive = i < activeCount
                val segmentColor = when {
                    i >= totalSegments - 2 -> Color(0xFFFF5252) // Red peak warning (0 to +3 dB)
                    i >= totalSegments - 5 -> Color(0xFFFFB300) // Amber warm zone (-3 to 0 dB)
                    else -> accentColor // Normal green/cyan/theme active color
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(7.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            if (isActive) segmentColor else colors.borderSubtle.copy(alpha = 0.25f)
                        )
                )
            }
        }
    }
}
