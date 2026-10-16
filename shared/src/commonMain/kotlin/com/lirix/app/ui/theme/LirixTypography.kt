package com.lirix.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * OpenType Font Features helper for Lirix typography.
 */
object FontFeature {
    /**
     * OpenType 'tnum' feature: tabular (monospaced) figures for timers, timecodes, and numbers.
     */
    fun tabularFigures(): String = "tnum"
}

/**
 * Typography system for the Lirix Teleprompter Deck (TASK-UI-PROMPTER-02 / ADR-022).
 *
 * Font pair:
 * - Lyrics: Literata (Serif) with Sentence case
 * - Interface & Chords: Golos Text (SansSerif) with OpenType tnum for jitter-free timecodes
 */
@Immutable
data class LirixTypography(
    // -------------------------------------------------------------------------
    // 1. Lyrics / Poetry Typography (Literata Medium - Serif)
    // -------------------------------------------------------------------------
    /**
     * Active sung lyric line: 30 sp, 1.3x leading (39 sp).
     */
    val lyricsActive: TextStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 39.sp
    ),

    /**
     * Adjacent (+/- 1) lyric lines: 22 sp, 1.4x leading (31 sp).
     */
    val lyricsNear: TextStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 31.sp
    ),

    /**
     * Distant (+/- 2..N) lyric lines: 18 sp, 1.4x leading (25 sp).
     */
    val lyricsFar: TextStyle = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 25.sp
    ),

    // -------------------------------------------------------------------------
    // 2. Interface Typography (Golos Text - SansSerif)
    // -------------------------------------------------------------------------
    /**
     * Track title: 15 sp, SemiBold, 1.2x leading (18 sp).
     */
    val trackTitle: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 18.sp
    ),

    /**
     * Artist and album name: 13 sp, Regular, 1.2x leading (16 sp).
     */
    val trackArtistAlbum: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 16.sp
    ),

    /**
     * Timecodes and badges: 13 sp, Medium, with OpenType 'tnum' tabular figures
     * ensuring that changing milliseconds and seconds do not cause layout jitter.
     */
    val timecodeAndBadges: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 13.sp,
        fontFeatureSettings = FontFeature.tabularFigures()
    ),

    // -------------------------------------------------------------------------
    // 3. Musical Chords Typography (Golos Text SemiBold)
    // -------------------------------------------------------------------------
    /**
     * Chord symbols (Am, C#m7, etc.): 14 sp, SemiBold.
     */
    val chordSymbols: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 14.sp
    )
)

val LocalLirixTypography = staticCompositionLocalOf { LirixTypography() }

object LirixThemeTypography {
    val typography: LirixTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalLirixTypography.current
}

/**
 * Formats dirty all-caps or mixed lyrics strings into proper Sentence case
 * for bookish poetic typesetting.
 */
fun String.toSentenceCase(): String {
    if (isBlank()) return this
    val isAllCaps = length > 3 && this == uppercase() && this != lowercase()
    val base = if (isAllCaps) lowercase() else this
    return base.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
