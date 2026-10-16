package com.lirix.app

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.theme.FontFeature
import com.lirix.app.ui.theme.LirixTypography
import com.lirix.app.ui.theme.toSentenceCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TASK-UI-PROMPTER-02:
 * - Validates Literata Medium lyrics scale (30sp / 22sp / 18sp)
 * - Validates Golos Text interface scale and OpenType tnum tabular figures
 * - Validates Sentence case conversion for poetry
 */
class LirixTypographyTest {

    private val typography = LirixTypography()

    @Test
    fun test_lyrics_typography_scale() {
        // Active line: 30 sp, 1.3x (39 sp)
        assertEquals(30.sp, typography.lyricsActive.fontSize)
        assertEquals(39.sp, typography.lyricsActive.lineHeight)
        assertEquals(FontFamily.Serif, typography.lyricsActive.fontFamily)
        assertEquals(FontWeight.Medium, typography.lyricsActive.fontWeight)

        // Near lines: 22 sp, 1.4x (31 sp)
        assertEquals(22.sp, typography.lyricsNear.fontSize)
        assertEquals(31.sp, typography.lyricsNear.lineHeight)
        assertEquals(FontFamily.Serif, typography.lyricsNear.fontFamily)

        // Far lines: 18 sp, 1.4x (25 sp)
        assertEquals(18.sp, typography.lyricsFar.fontSize)
        assertEquals(25.sp, typography.lyricsFar.lineHeight)
        assertEquals(FontFamily.Serif, typography.lyricsFar.fontFamily)
    }

    @Test
    fun test_interface_typography_scale_and_tnum() {
        // Track title: 15 sp, SemiBold
        assertEquals(15.sp, typography.trackTitle.fontSize)
        assertEquals(FontWeight.SemiBold, typography.trackTitle.fontWeight)
        assertEquals(FontFamily.SansSerif, typography.trackTitle.fontFamily)

        // Artist and album: 13 sp, Regular
        assertEquals(13.sp, typography.trackArtistAlbum.fontSize)
        assertEquals(FontWeight.Normal, typography.trackArtistAlbum.fontWeight)
        assertEquals(FontFamily.SansSerif, typography.trackArtistAlbum.fontFamily)

        // Timecodes and badges: 13 sp, Medium, with OpenType "tnum" tabular figures
        assertEquals(13.sp, typography.timecodeAndBadges.fontSize)
        assertEquals(FontWeight.Medium, typography.timecodeAndBadges.fontWeight)
        assertEquals("tnum", typography.timecodeAndBadges.fontFeatureSettings)
        assertEquals("tnum", FontFeature.tabularFigures())

        // Chords: 14 sp, SemiBold
        assertEquals(14.sp, typography.chordSymbols.fontSize)
        assertEquals(FontWeight.SemiBold, typography.chordSymbols.fontWeight)
        assertEquals(FontFamily.SansSerif, typography.chordSymbols.fontFamily)
    }

    @Test
    fun test_toSentenceCase() {
        // All-caps lyrics strings from dirty sources
        val allCaps = "ТЕМ, КТО ЛОЖИТСЯ СПАТЬ, СПОКОЙНОГО СНА"
        assertEquals("Тем, кто ложится спать, спокойного сна", allCaps.toSentenceCase())

        val allCapsEnglish = "AND THE STARS FALL FROM THE SKY"
        assertEquals("And the stars fall from the sky", allCapsEnglish.toSentenceCase())

        // Lowercase line
        val lower = "спокойная ночь"
        assertEquals("Спокойная ночь", lower.toSentenceCase())

        // Already properly formatted
        val normal = "И город стреляет в ночь дробью огней"
        assertEquals("И город стреляет в ночь дробью огней", normal.toSentenceCase())

        // Blank
        assertEquals("", "".toSentenceCase())
        assertEquals("   ", "   ".toSentenceCase())
    }
}
