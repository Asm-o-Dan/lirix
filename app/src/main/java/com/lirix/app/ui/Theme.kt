package com.lirix.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.lirix.app.ui.theme.AppColors

private val ObsidianAmoledColorScheme = darkColorScheme(
    primary = AppColors.HyperViolet,
    onPrimary = AppColors.AmoledBlack,
    primaryContainer = AppColors.SurfaceLevel3,
    onPrimaryContainer = AppColors.TextPrimary,
    secondary = AppColors.CyberCyan,
    onSecondary = AppColors.AmoledBlack,
    tertiary = AppColors.ElectricMint,
    background = AppColors.AmoledBlack,
    surface = AppColors.SurfaceLevel1,
    surfaceVariant = AppColors.SurfaceLevel2,
    onBackground = AppColors.TextPrimary,
    onSurface = AppColors.TextPrimary,
    onSurfaceVariant = AppColors.TextSecondary,
    outline = AppColors.BorderSubtle,
    outlineVariant = AppColors.BorderFocused
)

@Composable
fun LirixTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ObsidianAmoledColorScheme,
        content = content
    )
}

@Composable
fun EventEngineTheme(
    content: @Composable () -> Unit
) {
    LirixTheme(content = content)
}
