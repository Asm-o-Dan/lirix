package com.lirix.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.theme.AppColors

@Composable
fun AnalogTurntable(
    isPlaying: Boolean,
    albumArtBitmap: ImageBitmap?,
    isCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Collapsible Vinyl Turntable Unit
        AnimatedVisibility(
            visible = !isCollapsed,
            enter = expandVertically(animationSpec = tween(320, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(220)),
            exit = shrinkVertically(animationSpec = tween(320, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(200))
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            ) {
                // Turntable Platter Container (Disc + Tonearm overlay)
                Box(
                    modifier = Modifier
                        .size(width = 240.dp, height = 200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Infinite rotation animation for the vinyl disc when playing
                    val infiniteTransition = rememberInfiniteTransition(label = "VinylSpin")
                    val spinAngle by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 16000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "VinylSpinAngle"
                    )

                    // The Spinning Vinyl Record
                    Box(
                        modifier = Modifier
                            .size(184.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTogglePlayPause()
                            }
                            .background(
                                Brush.radialGradient(
                                    listOf(
                                        AppColors.SurfaceLevel3,
                                        AppColors.SurfaceLevel1,
                                        AppColors.AmoledBlack
                                    )
                                )
                            )
                            .border(
                                1.5.dp,
                                Brush.sweepGradient(
                                    listOf(
                                        AppColors.HyperViolet,
                                        AppColors.CyberCyan,
                                        AppColors.HyperVioletDark,
                                        AppColors.HyperViolet
                                    )
                                ),
                                CircleShape
                            )
                            .rotate(if (isPlaying) spinAngle else 0f),
                        contentAlignment = Alignment.Center
                    ) {
                        // Vinyl Grooves
                        Box(
                            modifier = Modifier
                                .size(142.dp)
                                .clip(CircleShape)
                                .border(1.dp, AppColors.BorderSubtle.copy(alpha = 0.6f), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(102.dp)
                                .clip(CircleShape)
                                .border(1.dp, AppColors.BorderSubtle.copy(alpha = 0.5f), CircleShape)
                        )

                        // Center Label
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, AppColors.HyperViolet, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (albumArtBitmap != null) {
                                Image(
                                    bitmap = albumArtBitmap,
                                    contentDescription = "Album Art Label",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Center Spindle Hole
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(AppColors.AmoledBlack)
                                        .border(1.dp, AppColors.CyberCyan.copy(alpha = 0.6f), CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.radialGradient(
                                                listOf(AppColors.HyperViolet, AppColors.HyperVioletDark, AppColors.SurfaceLevel1)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = AppColors.AmoledBlack,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Kinetic Analog Tonearm (Звукосниматель)
                    // Rotates onto record groove when playing, lifts and parks away when paused
                    val tonearmAngle by animateFloatAsState(
                        targetValue = if (isPlaying) 0f else -26f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "TonearmAngle"
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                // Pivot origin at top-right corner of the turntable
                                transformOrigin = TransformOrigin(0.86f, 0.16f)
                                rotationZ = tonearmAngle
                            }
                    ) {
                        val pivotX = size.width * 0.86f
                        val pivotY = size.height * 0.16f

                        // 1. Pivot Base (Concentric metallic bearing)
                        drawCircle(
                            color = Color(0xFF1E2028),
                            radius = 16.dp.toPx(),
                            center = Offset(pivotX, pivotY)
                        )
                        drawCircle(
                            color = Color(0xFF475569),
                            radius = 16.dp.toPx(),
                            center = Offset(pivotX, pivotY),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0xFF94A3B8),
                            radius = 6.dp.toPx(),
                            center = Offset(pivotX, pivotY)
                        )

                        // 2. Tonearm Metallic Shaft (Extending towards the record)
                        val endX = pivotX - 86.dp.toPx()
                        val endY = pivotY + 98.dp.toPx()

                        // Counterweight behind the pivot
                        drawLine(
                            color = Color(0xFF334155),
                            start = Offset(pivotX, pivotY),
                            end = Offset(pivotX + 16.dp.toPx(), pivotY - 14.dp.toPx()),
                            strokeWidth = 6.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                        drawCircle(
                            color = Color(0xFFCBD5E1),
                            radius = 7.dp.toPx(),
                            center = Offset(pivotX + 16.dp.toPx(), pivotY - 14.dp.toPx())
                        )

                        // Main arm shaft (Chrome gradient effect)
                        drawLine(
                            brush = Brush.linearGradient(
                                listOf(Color(0xFFF1F5F9), Color(0xFF94A3B8), Color(0xFF64748B))
                            ),
                            start = Offset(pivotX, pivotY),
                            end = Offset(endX, endY),
                            strokeWidth = 3.2.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )

                        // 3. Cartridge / Headshell (Головка звукоснимателя)
                        val headWidth = 14.dp.toPx()
                        val headHeight = 24.dp.toPx()
                        drawRoundRect(
                            color = Color(0xFF0F172A),
                            topLeft = Offset(endX - headWidth / 2, endY - 2.dp.toPx()),
                            size = Size(headWidth, headHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                        drawRoundRect(
                            color = Color(0xFFA855F7),
                            topLeft = Offset(endX - headWidth / 2, endY - 2.dp.toPx()),
                            size = Size(headWidth, headHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )

                        // 4. Stylus Needle Light (Игла)
                        val needleX = endX
                        val needleY = endY + headHeight - 2.dp.toPx()
                        drawCircle(
                            color = if (isPlaying) Color(0xFF00E5FF) else Color(0xFF64748B),
                            radius = if (isPlaying) 3.5.dp.toPx() else 2.dp.toPx(),
                            center = Offset(needleX, needleY)
                        )
                        if (isPlaying) {
                            drawCircle(
                                color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                                radius = 7.dp.toPx(),
                                center = Offset(needleX, needleY)
                            )
                        }
                    }
                }
            }
        }

        // Ergonomic Collapse / Expand Pill Indicator (>=48dp Touch Target)
        Box(
            modifier = Modifier
                .padding(vertical = 4.dp)
                .defaultMinSize(minHeight = 44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.SurfaceLevel1)
                .border(1.dp, AppColors.BorderSubtle, RoundedCornerShape(12.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggleCollapse()
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = if (isCollapsed) "Показать винил" else "Скрыть винил",
                    tint = if (isCollapsed) AppColors.CyberCyan else AppColors.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isCollapsed) "Показать винил" else "Скрыть винил",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.3.sp
                    ),
                    color = if (isCollapsed) AppColors.CyberCyan else AppColors.TextSecondary
                )
            }
        }
    }
}
