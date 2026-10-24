package com.lirix.app.feature.share.animation

import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.lirix.app.ui.theme.ColorPalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class ThemeAnimationPaletteTest {

    @Test
    fun animationSpec_calculatesTotalFramesCorrectly() {
        // SPEC-ANIM-POSTER-01 §3.1 / DoD: (4000 * 24 / 1000) = 96
        val defaultSpec = AnimationSpec()
        assertEquals(4000L, defaultSpec.durationMs)
        assertEquals(24, defaultSpec.fps)
        assertEquals(1080, defaultSpec.width)
        assertEquals(1080, defaultSpec.height)
        assertTrue(defaultSpec.loopable)
        assertEquals(96, defaultSpec.totalFrames)

        // Custom specs
        val customSpec1 = AnimationSpec(durationMs = 5000L, fps = 30)
        assertEquals(150, customSpec1.totalFrames)

        val customSpec2 = AnimationSpec(durationMs = 1000L, fps = 60)
        assertEquals(60, customSpec2.totalFrames)

        // Presets
        assertEquals(1080, AnimationSpec.SQUARE.width)
        assertEquals(1080, AnimationSpec.SQUARE.height)
        assertEquals(1080, AnimationSpec.STORIES.width)
        assertEquals(1920, AnimationSpec.STORIES.height)
    }

    @Test
    fun springBounce_satisfiesMathematicalBoundaryAndOvershootConditions() {
        // DoD: springBounce(0)=0
        assertEquals(0f, springBounce(0f), 0.0001f)
        assertEquals(0f, springBounce(-0.1f), 0.0001f)

        // DoD: springBounce(1) ≈ 1
        val atEnd = springBounce(1f)
        assertEquals(1f, atEnd, 0.01f)
        // Analytical value: 1 - e^-6 * cos(4π) ≈ 1 - 0.00247875 ≈ 0.99752
        assertTrue(atEnd in 0.99f..1.01f)

        // DoD: overshoot > 1.0 в середине
        // Peak overshoot at t = 0.25: 1 - e^-1.5 * cos(π) = 1 + e^-1.5 ≈ 1.223
        val atQuarter = springBounce(0.25f)
        assertTrue("Expected overshoot > 1.0 at t=0.25, but was $atQuarter", atQuarter > 1.0f)
        assertEquals(1.223f, atQuarter, 0.01f)

        // Find max overshoot in middle [0.1 .. 0.5]
        var maxOvershoot = 0f
        var step = 0.01f
        while (step <= 0.60f) {
            val v = springBounce(step)
            if (v > maxOvershoot) {
                maxOvershoot = v
            }
            step += 0.01f
        }
        assertTrue("Max overshoot should exceed 1.15 (~15% overshoot), got $maxOvershoot", maxOvershoot > 1.15f)
    }

    @Test
    fun themeAnimationPalette_glowColorHasFortyPercentAlpha() {
        // DoD: ThemeAnimationPalette.glowColor имеет alpha ~40%
        val spotifyPalette = ThemeAnimationPalette.fromColors(ColorPalettes.SpotifyMood)
        val alphaByte = (spotifyPalette.glowColor ushr 24) and 0xFF
        val alphaRatio = alphaByte / 255f

        // 102 / 255 = 0.40
        assertEquals(102, alphaByte)
        assertEquals(0.40f, alphaRatio, 0.01f)

        // Verify RGB matches accentPrimary
        val accentRgb = spotifyPalette.accent and 0x00FFFFFF
        val glowRgb = spotifyPalette.glowColor and 0x00FFFFFF
        assertEquals(accentRgb, glowRgb)
    }

    @Test
    fun themeAnimationPalette_correctlyMapsSemanticTokensForAllThemes() {
        val testThemes = listOf(
            ColorPalettes.SpotifyMood,
            ColorPalettes.YandexMusic,
            ColorPalettes.GraphiteChalk,
            ColorPalettes.PaperInk
        )

        for (theme in testThemes) {
            val palette = ThemeAnimationPalette.fromColors(theme)

            assertEquals(theme.surfacePrimary.toArgb(), palette.background)
            assertEquals(theme.surfaceCard.toArgb(), palette.surface)
            assertEquals(theme.accentPrimary.toArgb(), palette.accent)
            assertEquals(theme.textPrimary.toArgb(), palette.textPrimary)
            assertEquals(theme.textSecondary.toArgb(), palette.textSecondary)
            assertEquals(theme.borderSubtle.toArgb(), palette.border)

            // Extension function check
            val fromExtension = theme.toThemeAnimationPalette()
            assertEquals(palette, fromExtension)

            // Glow alpha must consistently be 40%
            val alphaByte = (palette.glowColor ushr 24) and 0xFF
            assertEquals(102, alphaByte)
        }
    }

    @Test
    fun toSafeArgb_handlesUnspecifiedAndValidColorsGracefully() {
        val fallback = 0x12345678
        assertEquals(fallback, Color.Unspecified.toSafeArgb(fallback))

        val redArgb = Color.Red.toSafeArgb()
        assertEquals(Color.Red.toArgb(), redArgb)
    }

    @Test
    fun posterAnimator_delegatesRenderFrameWithSpec() {
        val palette = ThemeAnimationPalette.fromColors(ColorPalettes.SpotifyMood)
        val canvas = mock(Canvas::class.java)

        var renderedT = -1f
        var renderedW = -1
        var renderedH = -1

        val animator = object : PosterAnimator(palette) {
            override fun renderFrame(canvas: Canvas, t: Float, width: Int, height: Int) {
                renderedT = t
                renderedW = width
                renderedH = height
            }
        }

        assertEquals(palette, animator.palette)

        val spec = AnimationSpec.STORIES
        animator.renderFrame(canvas, 0.75f, spec)

        assertEquals(0.75f, renderedT, 0.0001f)
        assertEquals(1080, renderedW)
        assertEquals(1920, renderedH)
    }
}
