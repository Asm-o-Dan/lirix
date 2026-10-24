package com.lirix.app.feature.share.animation

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.ui.theme.ColorPalettes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyFloat
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class VinylSpinAnimatorTest {

    private val palette = ThemeAnimationPalette.fromColors(ColorPalettes.SpotifyMood)
    private val title = "Bohemian Rhapsody"
    private val artist = "Queen"
    private val lyric = "Is this the real life? Is this just fantasy?"

    @Test
    fun at_t0_nothingIsRenderedExceptBackground() {
        val animator = VinylSpinAnimator(palette, null, title, artist, lyric)

        // Mathematical properties check at t = 0
        assertEquals(0f, animator.getVinylScale(0f), 0.0001f)
        assertEquals(0f, animator.getVinylRotation(0f), 0.0001f)
        assertEquals("", animator.getTitleText(0f))
        assertEquals(0, animator.getArtistAlpha(0f))
        assertEquals("", animator.getLyricText(0f))

        // Canvas interactions check at t = 0
        val canvas = mock(Canvas::class.java)
        animator.renderFrame(canvas, 0f, 1080, 1080)

        // Background is drawn
        verify(canvas).drawRect(anyFloat(), anyFloat(), anyFloat(), anyFloat(), any())

        // No texts, tonearm, or bitmaps drawn at t = 0
        verify(canvas, never()).drawText(any(), anyFloat(), anyFloat(), any())
        verify(canvas, never()).drawLine(anyFloat(), anyFloat(), anyFloat(), anyFloat(), any())
    }

    @Test
    fun at_t1_allElementsAreFullyVisible() {
        val albumArt = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val animator = VinylSpinAnimator(palette, albumArt, title, artist, lyric)

        // Mathematical properties check at t = 1
        assertEquals(1f, animator.getVinylScale(1f), 0.0001f)
        assertEquals(360f, animator.getVinylRotation(1f), 0.0001f)
        assertEquals(title, animator.getTitleText(1f))
        assertEquals(255, animator.getArtistAlpha(1f))
        assertEquals(lyric, animator.getLyricText(1f))

        // Execution with real Canvas
        val outputBitmap = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        animator.renderFrame(canvas, 1f, 500, 500)

        albumArt.recycle()
        outputBitmap.recycle()
    }

    @Test
    fun vinylScale_overshootsInTransitionWindow() {
        val animator = VinylSpinAnimator(palette, null, title, artist, lyric)

        // Before 0.15 scale is 0
        assertEquals(0f, animator.getVinylScale(0.10f), 0.0001f)

        // Peak overshoot at middle of [0.15 .. 0.35] (t = 0.25)
        val midScale = animator.getVinylScale(0.25f)
        assertTrue("Vinyl scale should overshoot > 0.5 at midpoint (got $midScale)", midScale > 0.5f)

        // After 0.35 scale is stable 1.0
        assertEquals(1f, animator.getVinylScale(0.35f), 0.0001f)
        assertEquals(1f, animator.getVinylScale(0.80f), 0.0001f)
    }

    @Test
    fun typewriterEffects_progressGraduallyOnSpecifiedIntervals() {
        val animator = VinylSpinAnimator(palette, null, title, artist, lyric)

        // Title on [0.35 .. 0.55]
        assertEquals("", animator.getTitleText(0.34f))
        val midTitle = animator.getTitleText(0.45f)
        assertTrue(midTitle.isNotEmpty() && midTitle.length < title.length)
        assertEquals(title, animator.getTitleText(0.55f))

        // Artist alpha on [0.55 .. 0.70]
        assertEquals(0, animator.getArtistAlpha(0.54f))
        val midAlpha = animator.getArtistAlpha(0.625f)
        assertTrue(midAlpha in 100..180)
        assertEquals(255, animator.getArtistAlpha(0.70f))

        // Lyric on [0.70 .. 1.0]
        assertEquals("", animator.getLyricText(0.69f))
        val midLyric = animator.getLyricText(0.775f)
        assertTrue(midLyric.isNotEmpty() && midLyric.length < lyric.length)
        assertEquals(lyric, animator.getLyricText(1.0f))
    }

    @Test
    fun blinkingCursor_oscillatesOverTime() {
        val animator = VinylSpinAnimator(palette, null, title, artist, lyric)

        var hasVisible = false
        var hasHidden = false

        for (i in 0..100) {
            val t = i / 100f
            if (animator.isCursorVisible(t)) {
                hasVisible = true
            } else {
                hasHidden = true
            }
        }

        assertTrue("Cursor must blink on", hasVisible)
        assertTrue("Cursor must blink off", hasHidden)
    }
}
