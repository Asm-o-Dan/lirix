package com.lirix.app.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.ViewCompact
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.LirixThemeTokens
import com.lirix.app.ui.theme.LocalLirixTypography

/**
 * DesktopTopBar: Upper control bar for Lirix Desktop 2.0.
 *
 * Spec: TASK-UI-PROMPTER-FIX-01 §2 & §6, TASK-CON-UI-08 §1
 *
 * Features:
 * 1. Clean brand title: "Lirix Studio" (no caps, no emoji).
 * 2. Human-readable source status: "Нет активного плеера" / "Играет в Яндекс Музыке / Spotify".
 * 3. Vector Material Icons (no emojis).
 * 4. Theme selector for all six persisted themes.
 * 5. Lirix Connect (DevicesOther) button with active badge & tooltip.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DesktopTopBar(
    sourceApp: String?,
    isPlaying: Boolean,
    lyricsSource: String?,
    isHudVisible: Boolean,
    onToggleHud: () -> Unit,
    isCompactMode: Boolean,
    onToggleCompactMode: () -> Unit,
    selectedTheme: AppThemeType = AppThemeType.GRAPHITE_CHALK,
    onToggleTheme: () -> Unit = {},
    hasActiveConnectDevice: Boolean = false,
    activeConnectDeviceName: String? = null,
    onOpenConnect: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors
    val typography = LocalLirixTypography.current

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
                text = "Lirix Studio",
                style = typography.trackTitle,
                color = colors.accentPrimary
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(14.dp)
                    .background(colors.borderSubtle)
            )

            // Dynamic Source Badge with human-readable text
            val isIdle = sourceApp.isNullOrBlank()
            val sourceText = formatHumanSourceName(sourceApp)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfaceCard)
                    .border(1.dp, colors.borderSubtle, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                // Status LED
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isIdle -> colors.textMuted
                                isPlaying -> Color(0xFF00E676)
                                else -> Color(0xFFFFB300)
                            }
                        )
                )

                Text(
                    text = sourceText,
                    style = typography.trackArtistAlbum.copy(fontSize = 11.sp),
                    color = if (isIdle) colors.textMuted else colors.textPrimary,
                    fontWeight = FontWeight.Medium
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
                        text = lyricsSource,
                        style = typography.timecodeAndBadges.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accentSecondary
                    )
                }
            }
        }

        // Right: Mode Toggles and Theme Switcher
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Lirix Connect (DevicesOther) with Tooltip & Active Badge (§5.1, TASK-CON-UI-08)
            TopBarConnectButton(
                hasActiveDevice = hasActiveConnectDevice,
                activeDeviceName = activeConnectDeviceName,
                onClick = onOpenConnect
            )

            val themeLabel = "Тема оформления: ${selectedTheme.title}"
            TooltipArea(tooltip = {
                Surface(color = colors.surfaceElevated, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, colors.borderSubtle)) {
                    Text("$themeLabel\nВыбрать одну из шести тем", color = colors.textPrimary, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                }
            }, delayMillis = 300) {
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(colors.surfaceCard)
                        .border(1.dp, colors.borderSubtle, CircleShape)
                ) {
                    Icon(Icons.Filled.Palette, contentDescription = themeLabel,
                        tint = colors.textPrimary, modifier = Modifier.size(16.dp))
                }
            }

            // HUD Toggle Button
            TopBarActionButton(
                icon = Icons.Filled.PictureInPicture,
                label = if (isHudVisible) "Скрыть HUD" else "Караоке HUD",
                isActive = isHudVisible,
                onClick = onToggleHud
            )

            // Compact Mode Toggle Button
            TopBarActionButton(
                icon = Icons.Filled.ViewCompact,
                label = if (isCompactMode) "Студия" else "Мини-пульт",
                isActive = isCompactMode,
                onClick = onToggleCompactMode
            )
        }
    }
}

@Composable
private fun TopBarActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val colors = LirixThemeTokens.colors
    val contentColor = if (isActive) colors.appBackground else colors.textPrimary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) colors.accentPrimary else colors.surfaceCard)
            .border(
                width = 1.dp,
                color = if (isActive) colors.accentPrimary else colors.borderSubtle,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}

/**
 * Converts a raw process name (e.g. chrome.exe, yandexmusic.exe) to human-readable string.
 */
internal fun formatHumanSourceName(rawPackage: String?): String {
    if (rawPackage.isNullOrBlank()) return "Нет активного плеера"
    val lower = rawPackage.lowercase()
    return when {
        lower.contains("yandex") -> "Играет в Яндекс Музыке"
        lower.contains("spotify") -> "Играет в Spotify"
        lower.contains("chrome") || lower.contains("firefox") || lower.contains("msedge") || lower.contains("opera") || lower.contains("browser") -> "Играет в Браузере"
        lower.contains("aimp") -> "Играет в AIMP"
        lower.contains("vlc") -> "Играет в VLC"
        lower.contains("telegram") || lower.contains("ayugram") -> "Играет в Telegram"
        lower.contains("foobar") -> "Играет в foobar2000"
        else -> "Играет в " + rawPackage.removeSuffix(".exe").replaceFirstChar { it.uppercase() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TopBarConnectButton(
    hasActiveDevice: Boolean,
    activeDeviceName: String?,
    onClick: () -> Unit
) {
    val colors = LirixThemeTokens.colors
    val typography = LocalLirixTypography.current

    TooltipArea(
        tooltip = {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.borderSubtle),
                shadowElevation = 4.dp
            ) {
                Text(
                    text = if (hasActiveDevice && !activeDeviceName.isNullOrBlank()) {
                        "Воспроизведение: $activeDeviceName"
                    } else {
                        "Воспроизвести на устройстве"
                    },
                    style = typography.timecodeAndBadges.copy(fontSize = 11.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        delayMillis = 300
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .widthIn(min = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (hasActiveDevice) colors.accentPrimary.copy(alpha = 0.18f) else colors.surfaceCard)
                .border(
                    width = 1.dp,
                    color = if (hasActiveDevice) colors.accentPrimary else colors.borderSubtle,
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onClick() }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DevicesOther,
                    contentDescription = "Воспроизвести на устройстве",
                    tint = if (hasActiveDevice) colors.accentPrimary else colors.textPrimary,
                    modifier = Modifier.size(15.dp)
                )

                if (hasActiveDevice) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676))
                    )
                }
            }
        }
    }
}
