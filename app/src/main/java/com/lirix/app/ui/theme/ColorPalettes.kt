package com.lirix.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Concrete palette token specifications for all 4 Lirix themes.
 */
object ColorPalettes {

    val SpotifyMood = LirixColors(
        appBackground = Color(0xFF121212),
        surfacePrimary = Color(0xFF181818),
        surfaceCard = Color(0xFF242424),
        surfaceElevated = Color(0xFF2A2A2A),
        accentPrimary = Color(0xFF1DB954),
        accentSecondary = Color(0xFF1ED760),
        accentGlow = Color(0x261DB954),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFB3B3B3),
        textMuted = Color(0xFF727272),
        borderSubtle = Color(0xFF282828),
        karaokeActive = Color(0xFF1DB954),
        karaokeInactive = Color(0xFF5E5E5E),
        chordBadgeBg = Color(0xFF282828),
        chordBadgeText = Color(0xFF1DB954),
        fretboardWood = Color(0xFF1A1A1A),
        cardCornerRadius = 6.dp,
        pillCornerRadius = 100.dp,
        isDark = true
    )

    val YandexMusic = LirixColors(
        appBackground = Color(0xFF0E0F12),
        surfacePrimary = Color(0xFF18191E),
        surfaceCard = Color(0xFF22242B),
        surfaceElevated = Color(0xFF2B2E38),
        accentPrimary = Color(0xFFFFCC00),
        accentSecondary = Color(0xFFFF5500),
        accentGlow = Color(0x33FFCC00),
        textPrimary = Color(0xFFF5F6F8),
        textSecondary = Color(0xFF9EA3B0),
        textMuted = Color(0xFF656A78),
        borderSubtle = Color(0xFF262933),
        karaokeActive = Color(0xFFFFCC00),
        karaokeInactive = Color(0xFF707584),
        chordBadgeBg = Color(0xFF2B2E38),
        chordBadgeText = Color(0xFFFFCC00),
        fretboardWood = Color(0xFF16171B),
        cardCornerRadius = 14.dp,
        pillCornerRadius = 18.dp,
        isDark = true
    )

    val ObsidianPulse = LirixColors(
        appBackground = Color(0xFF000000),
        surfacePrimary = Color(0xFF0D0E12),
        surfaceCard = Color(0xFF161820),
        surfaceElevated = Color(0xFF1E212D),
        accentPrimary = Color(0xFFB388FF),
        accentSecondary = Color(0xFF00E5FF),
        accentGlow = Color(0x33B388FF),
        textPrimary = Color(0xFFF2F2F7),
        textSecondary = Color(0xFFA0A0B2),
        textMuted = Color(0xFF5A5A6E),
        borderSubtle = Color(0xFF222530),
        karaokeActive = Color(0xFFB388FF),
        karaokeInactive = Color(0xFF6E6E85),
        chordBadgeBg = Color(0xFF1E212D),
        chordBadgeText = Color(0xFF00E5FF),
        fretboardWood = Color(0xFF08090C),
        cardCornerRadius = 14.dp,
        pillCornerRadius = 16.dp,
        isDark = true
    )

    val PastelSerenity = LirixColors(
        appBackground = Color(0xFFFBF9F5),
        surfacePrimary = Color(0xFFF3EFE6),
        surfaceCard = Color(0xFFE9E3D8),
        surfaceElevated = Color(0xFFFFFFFF),
        accentPrimary = Color(0xFFD9777F),
        accentSecondary = Color(0xFF7A9A7B),
        accentGlow = Color(0x20D9777F),
        textPrimary = Color(0xFF2C2D30),
        textSecondary = Color(0xFF6E6F74),
        textMuted = Color(0xFF9C9B9F),
        borderSubtle = Color(0xFFE4DDD2),
        karaokeActive = Color(0xFFD9777F),
        karaokeInactive = Color(0xFFA5A4A8),
        chordBadgeBg = Color(0xFFE9E3D8),
        chordBadgeText = Color(0xFF7A9A7B),
        fretboardWood = Color(0xFFDFD5C4),
        cardCornerRadius = 20.dp,
        pillCornerRadius = 24.dp,
        isDark = false
    )

    fun getTokens(theme: AppThemeType): LirixColors {
        return when (theme) {
            AppThemeType.SPOTIFY_MOOD -> SpotifyMood
            AppThemeType.YANDEX_MUSIC -> YandexMusic
            AppThemeType.OBSIDIAN_PULSE -> ObsidianPulse
            AppThemeType.PASTEL_SERENITY -> PastelSerenity
        }
    }
}
