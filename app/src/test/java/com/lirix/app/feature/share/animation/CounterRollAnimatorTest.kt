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
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class CounterRollAnimatorTest {

    private val palette = ThemeAnimationPalette.fromColors(ColorPalettes.SpotifyMood)
    private val topTracks = listOf(
        Triple("Starboy", "The Weeknd", 45),
        Triple("Blinding Lights", "The Weeknd", 32),
        Triple("Save Your Tears", "The Weeknd", 28)
    )

    @Test
    fun odometer_digitOffsetFor42AtT1_shows4And2() {
        // DoD: odometer digit offset для числа 42 при t=1.0 → показывает «4» и «2»
        val animator = CounterRollAnimator(
            palette = palette,
            totalMinutes = 42,
            uniqueTracks = 15,
            uniqueArtists = 8,
            periodLabel = "Октябрь 2026",
            topTracks = topTracks
        )

        // At t = 1.0, progress is 1.0
        val digits = animator.getOdometerDigits(42, progress = 1.0f)

        assertEquals("Should have 2 digits for 42", 2, digits.size)

        val firstDigit = digits[0]
        assertEquals("First digit must be 4", 4, firstDigit.first)
        assertEquals("First digit fractional offset must be 0 at t=1.0", 0f, firstDigit.second, 0.0001f)

        val secondDigit = digits[1]
        assertEquals("Second digit must be 2", 2, secondDigit.first)
        assertEquals("Second digit fractional offset must be 0 at t=1.0", 0f, secondDigit.second, 0.0001f)
    }

    @Test
    fun at_t0_nothingRenderedExceptBackground() {
        // DoD: при t=0 — пустой фон
        val animator = CounterRollAnimator(
            palette = palette,
            totalMinutes = 120,
            uniqueTracks = 25,
            uniqueArtists = 10,
            periodLabel = "2026 год",
            topTracks = topTracks
        )

        val canvas = mock(Canvas::class.java)
        animator.renderFrame(canvas, 0f, 1080, 1080)

        // Background is drawn
        verify(canvas).drawRect(anyFloat(), anyFloat(), anyFloat(), anyFloat(), any())

        // No texts or cards drawn at t=0
        verify(canvas, never()).drawText(any(), anyFloat(), anyFloat(), any())
        verify(canvas, never()).drawRoundRect(any(), anyFloat(), anyFloat(), any())
    }

    @Test
    fun at_t1_allElementsAreRendered() {
        // DoD: при t=1 — все элементы видны
        val animator = CounterRollAnimator(
            palette = palette,
            totalMinutes = 540,
            uniqueTracks = 72,
            uniqueArtists = 30,
            periodLabel = "Октябрь 2026",
            topTracks = topTracks
        )

        val bmp = Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        animator.renderFrame(canvas, 1f, 800, 800)

        // Progress values at t=1.0 must be 1.0
        assertEquals(1f, animator.getMinutesProgress(1f), 0.0001f)
        assertEquals(1f, animator.getTracksProgress(1f), 0.0001f)
        assertEquals(1f, animator.getArtistsProgress(1f), 0.0001f)
        assertEquals(255, animator.getHeaderAlpha(1f))
        assertEquals(0f, animator.getPeriodSlideOffset(1f, 800f), 0.0001f)

        val (translateY, alpha) = animator.getTopTrackCascade(1f, 0)
        assertEquals(0f, translateY, 0.0001f)
        assertEquals(255, alpha)

        bmp.recycle()
    }

    @Test
    fun easingSpeeds_differAppropriatelyDuringOdometerRoll() {
        val animator = CounterRollAnimator(palette, 100, 50, 20, "2026", topTracks)

        // In the middle of roll interval t = 0.50 (localT = 0.5)
        val minP = animator.getMinutesProgress(0.50f)
        val trkP = animator.getTracksProgress(0.50f)
        val artP = animator.getArtistsProgress(0.50f)

        // Ease-out cubic > ease-out quad > ease-out sine at midpoint
        assertTrue("Minutes ease-out cubic ($minP) should be fastest", minP > trkP)
        assertTrue("Tracks ease-out quad ($trkP) should be faster than artists ($artP)", trkP > artP)
    }
}
