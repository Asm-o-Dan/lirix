package com.lirix.app.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.theme.LirixThemeTokens

@Composable
fun DesktopTopBar(
    sourceApp: String?,
    isPlaying: Boolean,
    lyricsSource: String?,
    isHudVisible: Boolean,
    onToggleHud: () -> Unit,
    isCompactMode: Boolean,
    onToggleCompactMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.9f))
            .border(1.dp, colors.borderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Studio Brand & Active Audio Source Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "⚡ LIRIX STUDIO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                color = colors.accentPrimary
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(colors.borderSubtle)
            )

            // Dynamic Source Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                // Pulsing status LED
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) Color(0xFF00E676) else Color(0xFFFFB300))
                )

                Text(
                    text = sourceApp ?: "idle.session",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "• GSMTC",
                    fontSize = 10.sp,
                    color = colors.textMuted
                )
            }

            if (!lyricsSource.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.accentSecondary.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "✓ $lyricsSource",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accentSecondary
                    )
                }
            }
        }

        // Right: Mode Toggles (Compact Player, Floating HUD)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // HUD Toggle Button
            TopBarActionButton(
                label = if (isHudVisible) "🪟 Скрыть HUD" else "🪟 Караоке HUD",
                isActive = isHudVisible,
                onClick = onToggleHud
            )

            // Compact Mode Toggle Button
            TopBarActionButton(
                label = if (isCompactMode) "🎛 Студия" else "🗗 Мини-пульт",
                isActive = isCompactMode,
                onClick = onToggleCompactMode
            )
        }
    }
}

@Composable
private fun TopBarActionButton(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val colors = LirixThemeTokens.colors

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) colors.accentPrimary else colors.surfaceCard)
            .border(
                width = 1.dp,
                color = if (isActive) colors.accentPrimary else colors.borderSubtle,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
            color = if (isActive) colors.appBackground else colors.textPrimary
        )
    }
}
