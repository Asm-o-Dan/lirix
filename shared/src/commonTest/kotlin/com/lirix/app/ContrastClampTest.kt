package com.lirix.app

import androidx.compose.ui.graphics.Color
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.ColorPalettes
import com.lirix.app.ui.theme.ContrastClampHelper
import com.lirix.app.ui.theme.LirixThemeTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TASK-UI-PROMPTER-01:
 * - Validates Graphite & Chalk (#1B1E24/#F2EFE9) and Paper & Ink (#F5F2EB/#181B20) palettes
 * - Validates WCAG relative luminance and contrast ratio calculations
 * - Validates Contrast Clamp algorithm guaranteeing contrast >= 4.5:1 (WCAG AA)
 */
class ContrastClampTest {

    @Test
    fun test_tokens_graphite_and_chalk() {
        assertEquals(Color(0xFF1B1E24), LirixThemeTokens.Graphite)
        assertEquals(Color(0xFF242830), LirixThemeTokens.Slate)
        assertEquals(Color(0xFF3A404B), LirixThemeTokens.Hairline)
        assertEquals(Color(0xFFF2EFE9), LirixThemeTokens.Chalk)
        assertEquals(Color(0xFF8B93A1), LirixThemeTokens.Mist)
        assertEquals(Color(0xFFE5484D), LirixThemeTokens.Clip)
        assertEquals(Color(0xFF7FA6F0), LirixThemeTokens.AccentFallbackDark)

        val chalkContrast = ContrastClampHelper.calculateContrastRatio(
            LirixThemeTokens.Chalk,
            LirixThemeTokens.Graphite
        )
        // Spec: Chalk on Graphite has contrast ~13.4:1 (>= 4.5 WCAG AA and >= 7.0 AAA)
        assertTrue("Chalk contrast should be >= 13.0:1, was $chalkContrast", chalkContrast >= 13.0)

        val mistContrast = ContrastClampHelper.calculateContrastRatio(
            LirixThemeTokens.Mist,
            LirixThemeTokens.Graphite
        )
        // Spec: Mist on Graphite has contrast ~5.4:1 (>= 4.5 WCAG AA)
        assertTrue("Mist contrast should be >= 5.0:1, was $mistContrast", mistContrast >= 5.0)
    }

    @Test
    fun test_tokens_paper_and_ink() {
        assertEquals(Color(0xFFF5F2EB), LirixThemeTokens.Parchment)
        assertEquals(Color(0xFFEAE6DC), LirixThemeTokens.Vellum)
        assertEquals(Color(0xFFD6D0C2), LirixThemeTokens.HairlineLight)
        assertEquals(Color(0xFF181B20), LirixThemeTokens.Ink)
        assertEquals(Color(0xFF5C6370), LirixThemeTokens.Pencil)
        assertEquals(Color(0xFFC92A2A), LirixThemeTokens.ClipLight)
        assertEquals(Color(0xFF29549E), LirixThemeTokens.AccentFallbackLight)

        val inkContrast = ContrastClampHelper.calculateContrastRatio(
            LirixThemeTokens.Ink,
            LirixThemeTokens.Parchment
        )
        // Spec: Ink on Parchment has contrast ~13.8:1 (AAA)
        assertTrue("Ink contrast should be >= 13.0:1, was $inkContrast", inkContrast >= 13.0)

        val pencilParchmentContrast = ContrastClampHelper.calculateContrastRatio(
            LirixThemeTokens.Pencil,
            LirixThemeTokens.Parchment
        )
        // Spec: Pencil (#5C6370) on Parchment has contrast >= 4.5:1 (WCAG AA)
        assertTrue("Pencil contrast on Parchment should be >= 4.5:1, was $pencilParchmentContrast", pencilParchmentContrast >= 4.5)

        val pencilVellumContrast = ContrastClampHelper.calculateContrastRatio(
            LirixThemeTokens.Pencil,
            LirixThemeTokens.Vellum
        )
        // Spec: Pencil (#5C6370) on Vellum has contrast >= 4.5:1 (WCAG AA)
        assertTrue("Pencil contrast on Vellum should be >= 4.5:1, was $pencilVellumContrast", pencilVellumContrast >= 4.5)

        val accentVellumContrast = ContrastClampHelper.calculateContrastRatio(
            LirixThemeTokens.AccentFallbackLight,
            LirixThemeTokens.Vellum
        )
        // Spec: AccentFallbackLight (#29549E) on Vellum has contrast >= 4.5:1 (WCAG AA)
        assertTrue("AccentFallbackLight contrast on Vellum should be >= 4.5:1, was $accentVellumContrast", accentVellumContrast >= 4.5)
    }

    @Test
    fun test_clampAccentContrast_darkTheme_alwaysGuarantees_minimum_4_5_contrast() {
        val darkBg = LirixThemeTokens.Graphite
        val testColors = listOf(
            Color(0xFF000000), // Pure black
            Color(0xFF0A1128), // Very dark navy
            Color(0xFF300000), // Very dark red
            Color(0xFF112211), // Very dark green
            Color(0xFF1B1E24), // Exact same color as background
            Color(0xFF555555), // Mid gray
            Color(0xFF7FA6F0), // Default accent
            Color(0xFFFFFFFF)  // Pure white
        )

        for (color in testColors) {
            val clamped = ContrastClampHelper.clampAccentContrast(color, isDark = true)
            val contrast = ContrastClampHelper.calculateContrastRatio(clamped, darkBg)
            assertTrue(
                "Color $color clamped to $clamped should have contrast >= 4.5 on Graphite, but was $contrast",
                contrast >= 4.5
            )
        }
    }

    @Test
    fun test_clampAccentContrast_lightTheme_alwaysGuarantees_minimum_4_5_contrast() {
        val lightBg = LirixThemeTokens.Parchment
        val testColors = listOf(
            Color(0xFFFFFFFF), // Pure white
            Color(0xFFFFFF00), // Bright yellow
            Color(0xFFE0FFFF), // Light cyan
            Color(0xFFF5F2EB), // Exact same color as background
            Color(0xFFE8E4D8), // Near background
            Color(0xFFB0C4DE), // Light steel blue
            Color(0xFF3A68B8), // Default light accent
            Color(0xFF000000)  // Pure black
        )

        for (color in testColors) {
            val clamped = ContrastClampHelper.clampAccentContrast(color, isDark = false)
            val contrast = ContrastClampHelper.calculateContrastRatio(clamped, lightBg)
            assertTrue(
                "Color $color clamped to $clamped should have contrast >= 4.5 on Parchment, but was $contrast",
                contrast >= 4.5
            )
        }
    }

    @Test
    fun test_palettes_graphiteChalk_and_paperInk_in_ColorPalettes() {
        val graphiteColors = ColorPalettes.getTokens(AppThemeType.GRAPHITE_CHALK)
        assertEquals(LirixThemeTokens.Graphite, graphiteColors.appBackground)
        assertEquals(LirixThemeTokens.Chalk, graphiteColors.textPrimary)
        assertTrue(graphiteColors.isDark)

        val paperColors = ColorPalettes.getTokens(AppThemeType.PAPER_INK)
        assertEquals(LirixThemeTokens.Parchment, paperColors.appBackground)
        assertEquals(LirixThemeTokens.Ink, paperColors.textPrimary)
        assertTrue(!paperColors.isDark)
    }
}
