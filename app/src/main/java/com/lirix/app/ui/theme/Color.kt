package com.lirix.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Mobile Teleprompter design tokens for Lirix (SPEC-UI-MOBILE-2.0 / ADR-023).
 * Bridges common tokens from :shared with Android-specific color extensions.
 */
object LirixMobileColors {
    // Graphite & Chalk (Dark Teleprompter Monitor)
    val Graphite = LirixThemeTokens.Graphite
    val Slate = LirixThemeTokens.Slate
    val Hairline = LirixThemeTokens.Hairline
    val Chalk = LirixThemeTokens.Chalk
    val Mist = LirixThemeTokens.Mist
    val Clip = LirixThemeTokens.Clip
    val AccentFallbackDark = LirixThemeTokens.AccentFallbackDark

    // Paper & Ink (Light Bookish Teleprompter)
    val Parchment = LirixThemeTokens.Parchment
    val Vellum = LirixThemeTokens.Vellum
    val HairlineLight = LirixThemeTokens.HairlineLight
    val Ink = LirixThemeTokens.Ink
    val Pencil = LirixThemeTokens.Pencil
    val ClipLight = LirixThemeTokens.ClipLight
    val AccentFallbackLight = LirixThemeTokens.AccentFallbackLight

    /**
     * Clamps an arbitrary accent color (e.g., extracted from album art)
     * to guarantee WCAG AA contrast (>= 4.5:1) against the active theme canvas.
     */
    fun clampDynamicAccent(color: Color, isDark: Boolean): Color {
        return ContrastClampHelper.clampAccentContrast(color, isDark)
    }
}
