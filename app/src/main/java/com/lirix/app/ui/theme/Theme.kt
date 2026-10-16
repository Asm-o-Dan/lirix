package com.lirix.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Mobile Teleprompter Compose Theme (SPEC-UI-MOBILE-2.0 / ADR-023).
 *
 * Guarantees:
 * - Proper tokens for Graphite & Chalk (dark #1B1E24) and Paper & Ink (light #F5F2EB).
 * - Dynamic Accent extraction from album art clamped to WCAG AA (>= 4.5:1) via [ContrastClampHelper].
 * - Tabular figures (tnum) for timecodes and Literata Medium for lyrics.
 */
@Composable
fun LirixTheme(
    themeType: AppThemeType = AppThemeType.DEFAULT,
    dynamicAccent: Color? = null,
    content: @Composable () -> Unit
) {
    var lirixColors = ColorPalettes.getTokens(themeType)

    // Dynamic accent extraction from album art with strict contrast clamping
    if (dynamicAccent != null) {
        val clampedAccent = ContrastClampHelper.clampAccentContrast(dynamicAccent, themeType.isDark)
        lirixColors = lirixColors.copy(
            accentPrimary = clampedAccent,
            accentSecondary = clampedAccent,
            karaokeActive = clampedAccent
        )
    }

    val colorScheme = if (themeType.isDark) {
        darkColorScheme(
            primary = lirixColors.accentPrimary,
            onPrimary = if (themeType == AppThemeType.YANDEX_MUSIC) Color.Black else lirixColors.appBackground,
            primaryContainer = lirixColors.surfaceElevated,
            onPrimaryContainer = lirixColors.textPrimary,
            secondary = lirixColors.accentSecondary,
            onSecondary = lirixColors.appBackground,
            background = lirixColors.appBackground,
            surface = lirixColors.surfacePrimary,
            surfaceVariant = lirixColors.surfaceCard,
            onBackground = lirixColors.textPrimary,
            onSurface = lirixColors.textPrimary,
            onSurfaceVariant = lirixColors.textSecondary,
            outline = lirixColors.borderSubtle
        )
    } else {
        lightColorScheme(
            primary = lirixColors.accentPrimary,
            onPrimary = Color.White,
            primaryContainer = lirixColors.surfaceElevated,
            onPrimaryContainer = lirixColors.textPrimary,
            secondary = lirixColors.accentSecondary,
            onSecondary = Color.White,
            background = lirixColors.appBackground,
            surface = lirixColors.surfacePrimary,
            surfaceVariant = lirixColors.surfaceCard,
            onBackground = lirixColors.textPrimary,
            onSurface = lirixColors.textPrimary,
            onSurfaceVariant = lirixColors.textSecondary,
            outline = lirixColors.borderSubtle
        )
    }

    CompositionLocalProvider(
        LocalLirixColors provides lirixColors,
        LocalLirixTypography provides LirixTypography()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LirixMobileTypography,
            content = content
        )
    }
}
