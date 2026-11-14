package com.lirix.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lirix.app.analytics.ProfileAvatar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Background pair and foreground of each palette; index = [ProfileAvatar.palette]. */
object ProfileAvatarPalettes {
    val all: List<Triple<Color, Color, Color>> = listOf(
        Triple(Color(0xFF7A1F2B), Color(0xFFD9483B), Color(0xFFFFE3D6)), // rock: crimson
        Triple(Color(0xFF5A3A0A), Color(0xFFE39A1F), Color(0xFFFFF1CF)), // hip-hop: amber
        Triple(Color(0xFF0B3C5D), Color(0xFF1FB6C9), Color(0xFFD9FBFF)), // electronic: cyan
        Triple(Color(0xFF7B2460), Color(0xFFE86AA6), Color(0xFFFFE4F1)), // pop: pink
        Triple(Color(0xFF2B2A6B), Color(0xFF6D6AE0), Color(0xFFE6E5FF)), // jazz and soul: indigo
        Triple(Color(0xFF3F3A2E), Color(0xFFB79B5B), Color(0xFFFFF6DC)), // classical: gold
        Triple(Color(0xFF1F4D33), Color(0xFF4FB06D), Color(0xFFE1F7E7)), // folk: green
        Triple(Color(0xFF2E3440), Color(0xFF6B7A90), Color(0xFFE9EEF5))  // neutral: slate
    )
}

/** Purely decorative drawing; the caller supplies a description when the avatar is a control. */
@Composable
fun ProfileAvatarView(avatar: ProfileAvatar, size: Dp = 72.dp, modifier: Modifier = Modifier) {
    val (dark, light, ink) = ProfileAvatarPalettes.all[avatar.palette]
    Canvas(modifier.size(size)) {
        val outline = shapePath(avatar.shape)
        clipPath(outline) {
            drawRect(Brush.linearGradient(listOf(light, dark), Offset.Zero, Offset(this.size.width, this.size.height)))
            drawPattern(avatar.pattern, ink.copy(alpha = 0.55f))
            drawAccent(avatar.accent, ink)
        }
    }
}

private fun DrawScope.shapePath(shape: Int): Path {
    val w = size.width
    val h = size.height
    fun polygon(sides: Int, rotation: Double) = Path().apply {
        repeat(sides) { i ->
            val angle = rotation + 2 * PI * i / sides
            val x = w / 2 + (w / 2) * cos(angle).toFloat()
            val y = h / 2 + (h / 2) * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    return when (shape) {
        1 -> Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, CornerRadius(w * 0.24f))) }
        2 -> polygon(6, PI / 6)
        3 -> polygon(4, PI / 2)
        4 -> polygon(8, PI / 8)
        else -> Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, w, h)) }
    }
}

private fun DrawScope.drawPattern(pattern: Int, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.035f
    when (pattern) {
        0 -> for (i in 1..4) drawCircle(color, radius = w * 0.11f * i, center = Offset(w / 2, h / 2), style = Stroke(stroke))
        1 -> for (row in 0..4) {
            val y = h * (0.2f + row * 0.15f)
            val path = Path().apply {
                moveTo(0f, y)
                for (step in 0..3) quadraticBezierTo(w * (step * 0.25f + 0.125f), y + (if (step % 2 == 0) -1 else 1) * h * 0.07f, w * (step + 1) * 0.25f, y)
            }
            drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        2 -> repeat(12) { i ->
            val angle = 2 * PI * i / 12
            drawLine(color, Offset(w / 2 + w * 0.16f * cos(angle).toFloat(), h / 2 + h * 0.16f * sin(angle).toFloat()),
                Offset(w / 2 + w * 0.6f * cos(angle).toFloat(), h / 2 + h * 0.6f * sin(angle).toFloat()), stroke, StrokeCap.Round)
        }
        3 -> listOf(0.45f, 0.75f, 0.55f, 0.9f, 0.6f, 0.4f, 0.7f).forEachIndexed { i, level ->
            val barWidth = w * 0.07f
            val x = w * (0.14f + i * 0.115f)
            drawRoundRect(color, Offset(x, h * (1f - level) * 0.9f), Size(barWidth, h * level), CornerRadius(barWidth / 2))
        }
        else -> for (row in 0..5) for (column in 0..5) {
            if ((row + column) % 2 == 0) drawCircle(color, w * 0.035f, Offset(w * (0.12f + column * 0.152f), h * (0.12f + row * 0.152f)))
        }
    }
}

private fun DrawScope.drawAccent(accent: Int, color: Color) {
    val w = size.width
    val h = size.height
    val center = Offset(w / 2, h / 2)
    when (accent) {
        1 -> { // note
            drawCircle(color, w * 0.085f, Offset(w * 0.44f, h * 0.6f))
            drawLine(color, Offset(w * 0.52f, h * 0.6f), Offset(w * 0.52f, h * 0.34f), w * 0.04f, StrokeCap.Round)
            drawLine(color, Offset(w * 0.52f, h * 0.34f), Offset(w * 0.64f, h * 0.4f), w * 0.04f, StrokeCap.Round)
        }
        2 -> drawCircle(color, w * 0.13f, center) // sun
        3 -> { // moon: a disc with an offset bite
            val moon = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, w * 0.15f)) }
            val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(center.x + w * 0.07f, center.y - h * 0.04f), w * 0.13f)) }
            drawPath(Path().apply { op(moon, bite, androidx.compose.ui.graphics.PathOperation.Difference) }, color)
        }
    }
}
