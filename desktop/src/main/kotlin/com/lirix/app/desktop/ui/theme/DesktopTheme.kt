package com.lirix.app.desktop.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.ColorPalettes
import com.lirix.app.ui.theme.ContrastClampHelper
import com.lirix.app.ui.theme.LirixColors
import kotlin.math.abs

data class DesktopThemePalette(val tokens: LirixColors, val material: ColorScheme)

/** Normalizes solid text, not translucent state layers or text over unknown images. */
internal fun desktopReadableForeground(
    foreground: Color,
    backgrounds: List<Color>,
    minimumContrast: Double = 4.5
): Color {
    fun valid(color: Color) = color != Color.Unspecified &&
        listOf(color.red, color.green, color.blue, color.alpha).all { it.isFinite() }
    require(valid(foreground)) { "A specified finite foreground is required" }
    require(backgrounds.isNotEmpty() && backgrounds.all { valid(it) && it.alpha == 1f }) {
        "Contrast backgrounds must be specified, finite and opaque"
    }
    require(minimumContrast.isFinite() && minimumContrast in 1.0..21.0)
    val solid = foreground.copy(alpha = 1f)
    fun passes(color: Color) = backgrounds.all {
        ContrastClampHelper.calculateContrastRatio(color, it) >= minimumContrast
    }
    if (passes(solid)) return solid
    val (hue, saturation, lightness) = ContrastClampHelper.rgbToHsl(solid)
    val candidates = listOf(0f, 1f).mapNotNull { endpoint ->
        var passingLightness = endpoint
        var failingLightness = lightness
        var candidate = ContrastClampHelper.hslToRgb(hue, saturation, endpoint)
        if (!passes(candidate)) return@mapNotNull null
        repeat(16) {
            val middle = (passingLightness + failingLightness) / 2f
            val color = ContrastClampHelper.hslToRgb(hue, saturation, middle)
            if (passes(color)) {
                passingLightness = middle
                candidate = color
            } else failingLightness = middle
        }
        candidate to abs(passingLightness - lightness)
    }
    val result = candidates.minByOrNull { it.second }?.first
    require(result != null && passes(result)) { "No readable HSL endpoint for the requested backgrounds" }
    return result
}

/** Desktop-only derivation; shared/Android palette instances are never mutated. */
fun buildDesktopThemePalette(theme: AppThemeType): DesktopThemePalette {
    val original = ColorPalettes.getTokens(theme)
    val surfaces = listOf(original.appBackground, original.surfacePrimary, original.surfaceCard,
        original.surfaceElevated, original.chordBadgeBg)
    fun readable(color: Color, backgrounds: List<Color> = surfaces, minimum: Double = 4.5) =
        desktopReadableForeground(color, backgrounds, minimum)
    val tokens = original.copy(
        textPrimary = readable(original.textPrimary),
        textSecondary = readable(original.textSecondary),
        textMuted = readable(original.textMuted),
        karaokeInactive = readable(original.karaokeInactive),
        karaokeActive = readable(original.karaokeActive, listOf(original.surfaceCard, original.appBackground)),
        chordBadgeText = readable(original.chordBadgeText, listOf(original.chordBadgeBg)),
        accentPrimary = readable(original.accentPrimary),
        accentSecondary = readable(original.accentSecondary)
    )
    val primaryContainer = tokens.accentPrimary.copy(alpha = .18f).compositeOver(tokens.surfaceCard)
    val secondaryContainer = tokens.accentSecondary.copy(alpha = .18f).compositeOver(tokens.surfaceCard)
    val error = readable(original.clipIndicator)
    val errorContainer = error.copy(alpha = .18f).compositeOver(tokens.surfaceCard)
    val base = if (tokens.isDark) darkColorScheme() else lightColorScheme()
    // Increasing elevation lightens dark palettes and darkens light palettes. Raw light
    // surfaceElevated is white, so sort by luminance rather than assuming token-name order.
    // Lowest/Low/Container/High/Highest use the four ordered surfaces as 0/1/2/3/3.
    val ordered = listOf(tokens.appBackground, tokens.surfacePrimary, tokens.surfaceCard, tokens.surfaceElevated)
        .sortedBy { ContrastClampHelper.calculateLuminance(it) }
        .let { if (tokens.isDark) it else it.reversed() }
    val material = base.copy(
        primary = tokens.accentPrimary,
        onPrimary = readable(tokens.textPrimary, listOf(tokens.accentPrimary)),
        primaryContainer = primaryContainer,
        onPrimaryContainer = readable(tokens.accentPrimary, listOf(primaryContainer)),
        inversePrimary = readable(tokens.accentPrimary, listOf(tokens.textPrimary)),
        secondary = tokens.accentSecondary,
        onSecondary = readable(tokens.textPrimary, listOf(tokens.accentSecondary)),
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = readable(tokens.accentSecondary, listOf(secondaryContainer)),
        tertiary = tokens.accentSecondary,
        onTertiary = readable(tokens.textPrimary, listOf(tokens.accentSecondary)),
        tertiaryContainer = secondaryContainer,
        onTertiaryContainer = readable(tokens.accentSecondary, listOf(secondaryContainer)),
        background = tokens.appBackground,
        onBackground = readable(tokens.textPrimary, listOf(tokens.appBackground)),
        surface = tokens.surfaceCard,
        onSurface = tokens.textPrimary,
        surfaceVariant = tokens.surfaceElevated,
        onSurfaceVariant = readable(tokens.textSecondary, listOf(tokens.surfaceElevated, tokens.surfaceCard)),
        surfaceTint = tokens.accentPrimary,
        inverseSurface = tokens.textPrimary,
        inverseOnSurface = readable(tokens.appBackground, listOf(tokens.textPrimary)),
        error = error,
        onError = readable(tokens.textPrimary, listOf(error)),
        errorContainer = errorContainer,
        onErrorContainer = readable(error, listOf(errorContainer)),
        outline = readable(tokens.borderSubtle, minimum = 3.0),
        outlineVariant = tokens.borderSubtle,
        scrim = Color.Black,
        surfaceBright = ordered.maxBy { ContrastClampHelper.calculateLuminance(it) },
        surfaceDim = ordered.minBy { ContrastClampHelper.calculateLuminance(it) },
        surfaceContainerLowest = ordered[0],
        surfaceContainerLow = ordered[1],
        surfaceContainer = ordered[2],
        surfaceContainerHigh = ordered[3],
        surfaceContainerHighest = ordered[3]
    )
    return DesktopThemePalette(tokens, material)
}
