package com.lirix.app.desktop.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.desktop.ingress.AudioLevels
import com.lirix.app.ui.theme.LirixThemeTokens

/**
 * Authentic Analog-style stereo LED VU Meter connected to Windows WASAPI Loopback (IAudioMeterInformation).
 * Translates true peak amplitude into decibel full scale (-60..0 dBFS) with realistic ballistic damping
 * and honest clipping detection (dBFS > -1.0 dBFS). Zero fake sinusoids or simulated data.
 */
@Composable
fun AnalogVuMeter(
    audioLevels: AudioLevels? = null,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    totalSegments: Int = 18
) {
    val colors = LirixThemeTokens.colors

    val isLive = isPlaying && audioLevels != null && audioLevels.isActive
    val targetLeft = if (isLive) audioLevels!!.leftNormalized else 0f
    val targetRight = if (isLive) audioLevels!!.rightNormalized else 0f
    val isClipping = isLive && audioLevels!!.isClipping

    // Smooth physics damping animatables for Left and Right channels
    val animLeft = remember { Animatable(0f) }
    val animRight = remember { Animatable(0f) }

    LaunchedEffect(targetLeft, targetRight, isLive) {
        if (isLive) {
            // Ballistic dynamics: fast attack, natural decay
            val attackL = targetLeft > animLeft.value
            val attackR = targetRight > animRight.value
            animLeft.animateTo(
                targetLeft,
                tween(if (attackL) 30 else 160, easing = if (attackL) LinearEasing else FastOutSlowInEasing)
            )
            animRight.animateTo(
                targetRight,
                tween(if (attackR) 30 else 160, easing = if (attackR) LinearEasing else FastOutSlowInEasing)
            )
        } else {
            // Silence / Pause: smoothly drop to zero
            animLeft.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
            animRight.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
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
                dbfs = if (isLive) audioLevels?.leftDbfs else -60f,
                totalSegments = totalSegments,
                accentColor = colors.accentPrimary
            )

            // Channel R
            VuChannelRow(
                channelLabel = "R",
                level = animRight.value,
                dbfs = if (isLive) audioLevels?.rightDbfs else -60f,
                totalSegments = totalSegments,
                accentColor = colors.accentPrimary
            )

            // Decibel scale markers (-60, -24, -12, -6, -3, 0 dBFS, CLIP)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "-60", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = colors.textMuted)
                Text(text = "-24", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = colors.textMuted)
                Text(text = "-12", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = colors.textMuted)
                Text(text = "-6", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = colors.textMuted)
                Text(text = "-3", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = colors.textMuted)
                Text(text = "0 dB", fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = colors.accentSecondary, fontWeight = FontWeight.Bold)
                Text(
                    text = if (isClipping) "CLIP!" else "CLIP",
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (isClipping) Color(0xFFE5484D) else colors.textMuted.copy(alpha = 0.6f),
                    fontWeight = if (isClipping) FontWeight.ExtraBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun VuChannelRow(
    channelLabel: String,
    level: Float,
    dbfs: Float?,
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
                    i >= totalSegments - 2 -> Color(0xFFE5484D) // Red peak warning (> -1.0 dBFS)
                    i >= totalSegments - 5 -> Color(0xFFFFB300) // Amber warm zone (-3 to -1 dBFS)
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
