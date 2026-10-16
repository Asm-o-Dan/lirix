package com.lirix.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.lirix.app.ui.theme.AppThemeType
import com.lirix.app.ui.theme.ColorPalettes
import com.lirix.app.ui.theme.ContrastClampHelper
import com.lirix.app.ui.theme.LocalLirixColors
import com.lirix.app.ui.theme.LocalLirixTypography
import com.lirix.app.ui.theme.LirixTypography

@Composable
fun LirixTheme(
    themeType: AppThemeType = AppThemeType.DEFAULT,
    dynamicAccent: Color? = null,
    content: @Composable () -> Unit
) {
    var lirixColors = ColorPalettes.getTokens(themeType)
    if (dynamicAccent != null) {
        val clamped = ContrastClampHelper.clampAccentContrast(dynamicAccent, themeType.isDark)
        lirixColors = lirixColors.copy(
            accentPrimary = clamped,
            accentSecondary = clamped,
            karaokeActive = clamped
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
            content = content
        )
    }
}

@Composable
fun EventEngineTheme(
    themeType: AppThemeType = AppThemeType.DEFAULT,
    content: @Composable () -> Unit
) {
    LirixTheme(themeType = themeType, content = content)
}
