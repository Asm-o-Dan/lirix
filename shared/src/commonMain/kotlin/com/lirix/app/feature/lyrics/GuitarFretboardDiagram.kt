package com.lirix.app.feature.lyrics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.theme.AppColors

@Composable
fun GuitarFretboardDiagram(
    chord: GuitarChord,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.SurfaceLevel2)
            .border(1.dp, AppColors.BorderSubtle, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Chord Title
        Text(
            text = chord.name,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                fontFamily = FontFamily.Monospace
            ),
            color = AppColors.HyperViolet
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Fretboard Canvas
        val stringCount = 6
        val fretCount = 5

        Box(
            modifier = Modifier
                .width(170.dp)
                .height(210.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                val paddingLeft = 32f
                val paddingRight = 18f
                val paddingTop = 30f
                val paddingBottom = 16f

                val boardWidth = canvasWidth - paddingLeft - paddingRight
                val boardHeight = canvasHeight - paddingTop - paddingBottom

                val stringSpacing = boardWidth / (stringCount - 1)
                val fretSpacing = boardHeight / fretCount

                // 1. Draw Nut or Base Fret Marker
                if (chord.baseFret == 1) {
                    // Thick Nut Bar
                    drawRoundRect(
                        color = Color(0xFFE2E8F0),
                        topLeft = Offset(paddingLeft, paddingTop - 4f),
                        size = Size(boardWidth, 6f),
                        cornerRadius = CornerRadius(2f, 2f)
                    )
                } else {
                    // Regular fret wire for top
                    drawLine(
                        color = Color(0xFF475569),
                        start = Offset(paddingLeft, paddingTop),
                        end = Offset(paddingLeft + boardWidth, paddingTop),
                        strokeWidth = 2f
                    )
                }

                // 2. Draw Frets (horizontal wires)
                for (fret in 1..fretCount) {
                    val y = paddingTop + fret * fretSpacing
                    drawLine(
                        color = Color(0xFF475569),
                        start = Offset(paddingLeft, y),
                        end = Offset(paddingLeft + boardWidth, y),
                        strokeWidth = 2f
                    )
                }

                // 3. Draw Strings (vertical lines with variable gauge thickness)
                for (s in 0 until stringCount) {
                    val x = paddingLeft + s * stringSpacing
                    // Low E (index 0) thicker, High e (index 5) thinner
                    val strokeWidth = 3.2f - (s * 0.4f)
                    drawLine(
                        color = Color(0xFF94A3B8),
                        start = Offset(x, paddingTop),
                        end = Offset(x, paddingTop + boardHeight),
                        strokeWidth = strokeWidth
                    )
                }

                // 4. Draw X and O markers above the fretboard
                for (s in 0 until stringCount) {
                    val fretValue = chord.frets.getOrElse(s) { 0 }
                    val x = paddingLeft + s * stringSpacing
                    val y = paddingTop - 12f

                    when (fretValue) {
                        -1 -> {
                            val measured = textMeasurer.measure(
                                text = "×",
                                style = TextStyle(
                                    color = Color(0xFFEF4444),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            )
                            drawText(measured, topLeft = Offset(x - measured.size.width / 2f, y - measured.size.height / 2f))
                        }
                        0 -> {
                            val measured = textMeasurer.measure(
                                text = "○",
                                style = TextStyle(
                                    color = Color(0xFF10B981),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            )
                            drawText(measured, topLeft = Offset(x - measured.size.width / 2f, y - measured.size.height / 2f))
                        }
                    }
                }

                // 5. Draw Base Fret Label if > 1
                if (chord.baseFret > 1) {
                    val baseFretText = textMeasurer.measure(
                        text = "${chord.baseFret} fr",
                        style = TextStyle(
                            color = Color(0xFFA78BFA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    drawText(
                        baseFretText,
                        topLeft = Offset(paddingLeft - 8f - baseFretText.size.width, paddingTop + fretSpacing * 0.7f - baseFretText.size.height / 2f)
                    )
                }

                // 6. Draw Barre if present
                val barre = chord.barre
                if (barre != null) {
                    val barreRelFret = barre - chord.baseFret + 1
                    if (barreRelFret in 1..fretCount) {
                        val barreY = paddingTop + (barreRelFret - 0.5f) * fretSpacing
                        val startX = paddingLeft
                        val endX = paddingLeft + boardWidth
                        drawRoundRect(
                            color = Color(0xFF7C3AED).copy(alpha = 0.85f),
                            topLeft = Offset(startX - 6f, barreY - 11f),
                            size = Size(endX - startX + 12f, 22f),
                            cornerRadius = CornerRadius(11f, 11f)
                        )
                    }
                }

                // 7. Draw Pressed Frets & Finger numbers
                for (s in 0 until stringCount) {
                    val rawFret = chord.frets.getOrElse(s) { -1 }
                    if (rawFret > 0) {
                        val relFret = rawFret - chord.baseFret + 1
                        if (relFret in 1..fretCount) {
                            val x = paddingLeft + s * stringSpacing
                            val y = paddingTop + (relFret - 0.5f) * fretSpacing

                            // Pressed dot
                            drawCircle(
                                color = Color(0xFFA855F7),
                                radius = 13f,
                                center = Offset(x, y)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 13f,
                                center = Offset(x, y),
                                style = Stroke(width = 2f)
                            )

                            // Finger number
                            val finger = chord.fingers.getOrNull(s)
                            if (finger != null && finger > 0) {
                                val fingerText = textMeasurer.measure(
                                    text = finger.toString(),
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center
                                    )
                                )
                                drawText(
                                    fingerText,
                                    topLeft = Offset(x - fingerText.size.width / 2f, y - fingerText.size.height / 2f)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Finger Legend
        Text(
            text = "1: Указательный  2: Средний  3: Безымянный  4: Мизинец",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = AppColors.TextTertiary
        )
    }
}
