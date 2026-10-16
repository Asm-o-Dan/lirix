package com.lirix.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Mobile Typography standard for Lirix (SPEC-UI-MOBILE-2.0 / ADR-023).
 *
 * Pair:
 * - Literata (Serif): Screen-optimized serif for poetry and lyrics.
 * - Golos Text (SansSerif): Neutral high-legibility interface font with tabular figures (tnum)
 *   to eliminate layout jitter during high-precision timecode rendering.
 */
val LiterataFontFamily = FontFamily.Serif
val GolosTextFontFamily = FontFamily.SansSerif

val LirixMobileTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = GolosTextFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GolosTextFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleLarge = TextStyle(
        fontFamily = GolosTextFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = GolosTextFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 18.sp
    ),
    titleSmall = TextStyle(
        fontFamily = GolosTextFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 16.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = LiterataFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GolosTextFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = GolosTextFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GolosTextFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = GolosTextFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 15.sp,
        fontFeatureSettings = FontFeature.tabularFigures()
    ),
    labelSmall = TextStyle(
        fontFamily = GolosTextFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 13.sp,
        fontFeatureSettings = FontFeature.tabularFigures()
    )
)
