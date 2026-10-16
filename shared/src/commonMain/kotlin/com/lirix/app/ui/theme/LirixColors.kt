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
    val isDark: Boolean,
    val hairline: Color = borderSubtle,
    val clipIndicator: Color = if (isDark) Color(0xFFE5484D) else Color(0xFFC92A2A)
)

val LocalLirixColors = staticCompositionLocalOf<LirixColors> {
    error("LocalLirixColors not initialized! Wrap content in LirixTheme.")
}

/**
 * Convenient shorthand accessor and token registry:
 * val colors = LirixThemeTokens.colors
 *
 * Spec: TASK-UI-PROMPTER-01 / ADR-022
 */
object LirixThemeTokens {

    // =========================================================================
    // Dark Theme: Graphite & Chalk (#1B1E24 / #F2EFE9)
    // =========================================================================
    val Graphite: Color = Color(0xFF1B1E24)
    val Slate: Color = Color(0xFF242830)
    val Hairline: Color = Color(0xFF3A404B)
    val Chalk: Color = Color(0xFFF2EFE9)
    val Mist: Color = Color(0xFF8B93A1)
    val Clip: Color = Color(0xFFE5484D)
    val AccentFallbackDark: Color = Color(0xFF7FA6F0)

    // =========================================================================
    // Light Theme: Paper & Ink (#F5F2EB / #181B20)
    // =========================================================================
    val Parchment: Color = Color(0xFFF5F2EB)
    val Vellum: Color = Color(0xFFEAE6DC)
    val HairlineLight: Color = Color(0xFFD6D0C2)
    val Ink: Color = Color(0xFF181B20)
    val Pencil: Color = Color(0xFF5C6370)
    val ClipLight: Color = Color(0xFFC92A2A)
    val AccentFallbackLight: Color = Color(0xFF29549E)

    val colors: LirixColors
        @Composable
        @ReadOnlyComposable
        get() = LocalLirixColors.current

    /**
     * Adapts an accent color dynamically to guarantee >= 4.5:1 contrast.
     */
    fun clampAccent(color: Color, isDark: Boolean): Color {
        return ContrastClampHelper.clampAccentContrast(color, isDark)
    }
}
