package com.lirix.app.desktop.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp
import com.lirix.app.ui.theme.FontFeature
import com.lirix.app.ui.theme.LirixTypography

/**
 * Desktop font loader providing physical Literata and Golos Text fonts
 * loaded from classpath resources.
 * Spec: TASK-UI-PROMPTER-FIX-01 §1
 */
object DesktopFonts {

    val Literata: FontFamily by lazy {
        try {
            FontFamily(
                Font("fonts/Literata-Variable.ttf", weight = FontWeight.Normal),
                Font("fonts/Literata-Variable.ttf", weight = FontWeight.Medium),
                Font("fonts/Literata-Variable.ttf", weight = FontWeight.SemiBold),
                Font("fonts/Literata-Variable.ttf", weight = FontWeight.Bold)
            )
        } catch (_: Throwable) {
            FontFamily.Serif
        }
    }

    val GolosText: FontFamily by lazy {
        try {
            FontFamily(
                Font("fonts/GolosText-Variable.ttf", weight = FontWeight.Normal),
                Font("fonts/GolosText-Variable.ttf", weight = FontWeight.Medium),
                Font("fonts/GolosText-Variable.ttf", weight = FontWeight.SemiBold),
                Font("fonts/GolosText-Variable.ttf", weight = FontWeight.Bold)
            )
        } catch (_: Throwable) {
            FontFamily.SansSerif
        }
    }

    /**
     * Builds the concrete desktop typography with loaded font families and tnum tabular figures.
     */
    fun createPrompterTypography(): LirixTypography {
        val literata = Literata
        val golos = GolosText

        return LirixTypography(
            lyricsActive = TextStyle(
                fontFamily = literata,
                fontWeight = FontWeight.Medium,
                fontSize = 30.sp,
                lineHeight = 39.sp
            ),
            lyricsNear = TextStyle(
                fontFamily = literata,
                fontWeight = FontWeight.Normal,
                fontSize = 22.sp,
                lineHeight = 31.sp
            ),
            lyricsFar = TextStyle(
                fontFamily = literata,
                fontWeight = FontWeight.Normal,
                fontSize = 18.sp,
                lineHeight = 25.sp
            ),
            trackTitle = TextStyle(
                fontFamily = golos,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 20.sp
            ),
            trackArtistAlbum = TextStyle(
                fontFamily = golos,
                fontWeight = FontWeight.Normal,
                fontSize = 13.sp,
                lineHeight = 16.sp
            ),
            timecodeAndBadges = TextStyle(
                fontFamily = golos,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 13.sp,
                fontFeatureSettings = FontFeature.tabularFigures()
            ),
            chordSymbols = TextStyle(
                fontFamily = golos,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 14.sp
            )
        )
    }
}
