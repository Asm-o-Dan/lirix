package com.lirix.app.desktop

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.lirix.app.desktop.ui.theme.buildDesktopThemePalette
import com.lirix.app.desktop.ui.theme.desktopReadableForeground
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.ColorPalettes
import com.lirix.app.ui.theme.ContrastClampHelper
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class DesktopThemePaletteTest {
    private fun contrast(label: String, foreground: Color, background: Color, minimum: Double = 4.5) {
        assertEquals("$label foreground opaque", 1f, foreground.alpha, 0f)
        assertEquals("$label background opaque", 1f, background.alpha, 0f)
        val ratio = ContrastClampHelper.calculateContrastRatio(foreground, background)
        assertTrue("$label: $ratio < $minimum", ratio >= minimum)
    }

    private fun preservesQuantizedHueAndSaturation(label: String, original: Color, adjusted: Color) {
        val source = ContrastClampHelper.rgbToHsl(original)
        val observedLightness = ContrastClampHelper.rgbToHsl(adjusted)[2]
        val reference = ContrastClampHelper.hslToRgb(source[0], source[1], observedLightness)
        // sRGB Color packing rounds each channel to 8 bits. Observed HSL lightness
        // can therefore differ by half a channel step; HSL RGB slope in L is <=2.
        // Reconstructing original H/S at observed L permits at most one packed step,
        // unlike absolute hue tolerances which become unstable at low chroma.
        for ((name, pair) in listOf("red" to (reference.red to adjusted.red),
            "green" to (reference.green to adjusted.green), "blue" to (reference.blue to adjusted.blue))) {
            val expected = (pair.first * 255).roundToInt()
            val actual = (pair.second * 255).roundToInt()
            assertTrue("$label/$name: expected $expected, actual $actual", abs(expected - actual) <= 1)
        }
    }

    @Test fun validOpaqueForegroundIsUnchangedAndAlphaIsNormalizedBeforeContrast() {
        assertEquals(Color.White, desktopReadableForeground(Color.White, listOf(Color.Black)))
        assertEquals(Color.Black, desktopReadableForeground(Color.Black, listOf(Color.White), 21.0))
        assertEquals(Color.White, desktopReadableForeground(Color.White.copy(alpha = .1f), listOf(Color.Black)))
        assertEquals(Color.Black, desktopReadableForeground(Color.Black.copy(alpha = 0f), listOf(Color.White)))
        // The real translucent pixel is deliberately outside this solid-text helper's claim.
        assertTrue(ContrastClampHelper.calculateContrastRatio(Color.White.copy(alpha = .1f).compositeOver(Color.Black), Color.Black) < 4.5)
    }

    @Test fun invalidOrUnsatisfiableInputsThrowRatherThanClaimingContrast() {
        fun rejects(block: () -> Unit) {
            try { block(); fail("Expected IllegalArgumentException") } catch (_: IllegalArgumentException) { }
        }
        rejects { desktopReadableForeground(Color.Gray, emptyList()) }
        rejects { desktopReadableForeground(Color.White, listOf(Color.Black.copy(alpha = .5f))) }
        rejects { desktopReadableForeground(Color.Unspecified, listOf(Color.Black)) }
        rejects { desktopReadableForeground(Color.White, listOf(Color.Unspecified)) }
        for (minimum in listOf(.9, 21.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            rejects { desktopReadableForeground(Color.Gray, listOf(Color.White), minimum) }
        }
        rejects { desktopReadableForeground(Color.Gray, listOf(Color.White, Color.Black), 7.0) }
    }

    @Test fun adjustmentPreservesHueAndFindsThresholdOnActualLightAndDarkSurfaces() {
        for ((input, background) in listOf(Color(0xFFCA7777) to Color.White, Color(0xFF663333) to Color.Black)) {
            val adjusted = desktopReadableForeground(input, listOf(background))
            contrast("adjusted", adjusted, background)
            val old = ContrastClampHelper.rgbToHsl(input)
            val current = ContrastClampHelper.rgbToHsl(adjusted)
            preservesQuantizedHueAndSaturation("adjusted", input, adjusted)
            // A small step toward the original must fail: catches endpoint-only overcorrection.
            val towardOriginal = current[2] + if (old[2] > current[2]) .003f else -.003f
            val nearer = ContrastClampHelper.hslToRgb(current[0], current[1], towardOriginal)
            assertTrue(ContrastClampHelper.calculateContrastRatio(nearer, background) < 4.5)
        }
    }

    @Test fun sixPalettesPreserveSharedDataAndBrandSurfacesWhileMakingActualTextReadable() {
        val originals = AppThemeType.entries.associateWith { ColorPalettes.getTokens(it).copy() }
        for (theme in AppThemeType.entries) {
            val original = originals.getValue(theme)
            val tokens = buildDesktopThemePalette(theme).tokens
            assertEquals(original, ColorPalettes.getTokens(theme))
            assertEquals(original.appBackground, tokens.appBackground)
            assertEquals(original.surfacePrimary, tokens.surfacePrimary)
            assertEquals(original.surfaceCard, tokens.surfaceCard)
            assertEquals(original.surfaceElevated, tokens.surfaceElevated)
            assertEquals(original.chordBadgeBg, tokens.chordBadgeBg)
            assertEquals(original.accentGlow, tokens.accentGlow)
            assertEquals(original.fretboardWood, tokens.fretboardWood)
            assertEquals(original.cardCornerRadius, tokens.cardCornerRadius)
            assertEquals(original.pillCornerRadius, tokens.pillCornerRadius)
            assertEquals(original.isDark, tokens.isDark)
            val surfaces = listOf(tokens.appBackground, tokens.surfacePrimary, tokens.surfaceCard, tokens.surfaceElevated, tokens.chordBadgeBg)
            for ((role, color) in listOf("textPrimary" to tokens.textPrimary, "textSecondary" to tokens.textSecondary,
                "textMuted" to tokens.textMuted, "karaokeInactive" to tokens.karaokeInactive,
                "accentPrimary" to tokens.accentPrimary, "accentSecondary" to tokens.accentSecondary)) {
                surfaces.forEach { contrast("${theme.id}/$role", color, it) }
            }
            contrast("${theme.id}/karaokeActive/card", tokens.karaokeActive, tokens.surfaceCard)
            contrast("${theme.id}/karaokeActive/app", tokens.karaokeActive, tokens.appBackground)
            contrast("${theme.id}/chordBadge", tokens.chordBadgeText, tokens.chordBadgeBg)
            for ((before, after) in listOf(original.accentPrimary to tokens.accentPrimary, original.accentSecondary to tokens.accentSecondary)) {
                preservesQuantizedHueAndSaturation("${theme.id} brand", before, after)
            }
        }
        originals.forEach { (theme, original) -> assertEquals(original, ColorPalettes.getTokens(theme)) }
    }

    @Test fun allMaterialRolesUseBrandedMappingsAndOpaqueActualContainers() {
        for (theme in AppThemeType.entries) {
            val palette = buildDesktopThemePalette(theme)
            val t = palette.tokens; val m = palette.material
            assertEquals(t.accentPrimary, m.primary)
            assertEquals(t.accentSecondary, m.secondary)
            assertEquals(t.accentSecondary, m.tertiary)
            assertEquals(m.primary.copy(alpha = .18f).compositeOver(t.surfaceCard), m.primaryContainer)
            assertEquals(m.secondary.copy(alpha = .18f).compositeOver(t.surfaceCard), m.secondaryContainer)
            assertEquals(m.tertiary.copy(alpha = .18f).compositeOver(t.surfaceCard), m.tertiaryContainer)
            assertEquals(m.error.copy(alpha = .18f).compositeOver(t.surfaceCard), m.errorContainer)
            assertEquals(t.appBackground, m.background)
            assertEquals(t.surfaceCard, m.surface)
            assertEquals(t.textPrimary, m.onSurface)
            assertEquals(t.surfaceElevated, m.surfaceVariant)
            assertEquals(m.primary, m.surfaceTint)
            assertEquals(t.textPrimary, m.inverseSurface)
            assertEquals(t.borderSubtle, m.outlineVariant)
            assertEquals(Color.Black, m.scrim)
            val surfaceRoles = listOf(m.surfaceBright, m.surfaceDim, m.surfaceContainerLowest, m.surfaceContainerLow,
                m.surfaceContainer, m.surfaceContainerHigh, m.surfaceContainerHighest)
            val brandedSurfaces = listOf(t.appBackground, t.surfacePrimary, t.surfaceCard, t.surfaceElevated)
            surfaceRoles.forEach { assertTrue("${theme.id} unbranded surface role", it in brandedSurfaces) }
            val roles = roles(m)
            assertEquals(36, roles.size)
            roles.forEach { (name, color) -> assertEquals("${theme.id}/$name opaque", 1f, color.alpha, 0f) }
        }
    }

    @Test fun actualMaterialTextContainerInverseAndEssentialOutlinePairsPassContrast() {
        for (theme in AppThemeType.entries) {
            val palette = buildDesktopThemePalette(theme)
            val t = palette.tokens; val m = palette.material
            val pairs = listOf("primary" to (m.onPrimary to m.primary), "primaryContainer" to (m.onPrimaryContainer to m.primaryContainer),
                "secondary" to (m.onSecondary to m.secondary), "secondaryContainer" to (m.onSecondaryContainer to m.secondaryContainer),
                "tertiary" to (m.onTertiary to m.tertiary), "tertiaryContainer" to (m.onTertiaryContainer to m.tertiaryContainer),
                "background" to (m.onBackground to m.background), "surface" to (m.onSurface to m.surface),
                "surfaceVariant" to (m.onSurfaceVariant to m.surfaceVariant), "surfaceVariantOnCard" to (m.onSurfaceVariant to m.surface),
                "inverseSurface" to (m.inverseOnSurface to m.inverseSurface), "inversePrimary" to (m.inversePrimary to m.inverseSurface),
                "error" to (m.onError to m.error), "errorContainer" to (m.onErrorContainer to m.errorContainer))
            pairs.forEach { (name, pair) -> contrast("${theme.id}/$name", pair.first, pair.second) }
            listOf(m.surfaceBright, m.surfaceDim, m.surfaceContainerLowest, m.surfaceContainerLow, m.surfaceContainer,
                m.surfaceContainerHigh, m.surfaceContainerHighest).forEach { contrast("${theme.id}/onSurface/elevation", m.onSurface, it) }
            listOf(t.appBackground, t.surfacePrimary, t.surfaceCard, t.surfaceElevated, t.chordBadgeBg).forEach {
                contrast("${theme.id}/outline", m.outline, it, 3.0)
                contrast("${theme.id}/error", m.error, it)
            }
        }
    }

    // Exact installed Compose 1.7.3 API: no newer fixed roles.
    private fun roles(m: ColorScheme): Map<String, Color> = linkedMapOf(
        "primary" to m.primary, "onPrimary" to m.onPrimary, "primaryContainer" to m.primaryContainer, "onPrimaryContainer" to m.onPrimaryContainer,
        "inversePrimary" to m.inversePrimary, "secondary" to m.secondary, "onSecondary" to m.onSecondary,
        "secondaryContainer" to m.secondaryContainer, "onSecondaryContainer" to m.onSecondaryContainer,
        "tertiary" to m.tertiary, "onTertiary" to m.onTertiary, "tertiaryContainer" to m.tertiaryContainer, "onTertiaryContainer" to m.onTertiaryContainer,
        "background" to m.background, "onBackground" to m.onBackground, "surface" to m.surface, "onSurface" to m.onSurface,
        "surfaceVariant" to m.surfaceVariant, "onSurfaceVariant" to m.onSurfaceVariant, "surfaceTint" to m.surfaceTint,
        "inverseSurface" to m.inverseSurface, "inverseOnSurface" to m.inverseOnSurface,
        "error" to m.error, "onError" to m.onError, "errorContainer" to m.errorContainer, "onErrorContainer" to m.onErrorContainer,
        "outline" to m.outline, "outlineVariant" to m.outlineVariant, "scrim" to m.scrim,
        "surfaceBright" to m.surfaceBright, "surfaceDim" to m.surfaceDim, "surfaceContainer" to m.surfaceContainer,
        "surfaceContainerHigh" to m.surfaceContainerHigh, "surfaceContainerHighest" to m.surfaceContainerHighest,
        "surfaceContainerLow" to m.surfaceContainerLow, "surfaceContainerLowest" to m.surfaceContainerLowest
    )
}
