package com.lirix.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Domain semantic tokens for the Lirix music and lyrics experience.
 */
data class LirixColors(
    val appBackground: Color,
    val surfacePrimary: Color,
    val surfaceCard: Color,
    val surfaceElevated: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentGlow: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val borderSubtle: Color,
    val karaokeActive: Color,
    val karaokeInactive: Color,
    val chordBadgeBg: Color,
    val chordBadgeText: Color,
    val fretboardWood: Color,
    val cardCornerRadius: Dp,
    val pillCornerRadius: Dp,
    val isDark: Boolean
)

val LocalLirixColors = staticCompositionLocalOf<LirixColors> {
    error("LocalLirixColors not initialized! Wrap content in LirixTheme.")
}

/**
 * Convenient shorthand accessor for Composables:
 * val colors = LirixThemeTokens.colors
 */
object LirixThemeTokens {
    val colors: LirixColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLirixColors.current
}
