package com.lirix.app.feature.share.animation

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.analytics.TimeOfDaySlot
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
class PulseBarAnimatorTest {

    private val palette = ThemeAnimationPalette.fromColors(ColorPalettes.SpotifyMood)
    private val distribution = mapOf(
        TimeOfDaySlot.NIGHT to 0.40f,
        TimeOfDaySlot.MORNING to 0.15f,
        TimeOfDaySlot.AFTERNOON to 0.25f,
        TimeOfDaySlot.EVENING to 0.20f
    )

    @Test
    fun springBounce_overshootsInMiddle_andConvergesAtEnd() {
        // DoD: springBounce overshoots > 1.0 в середине, converges к 1.0 в конце
        val overshoot = springBounce(0.25f)
        assertTrue("springBounce at 0.25 must overshoot > 1.0 (got $overshoot)", overshoot > 1.0f)

        val atEnd = springBounce(1.0f)
        assertEquals(1.0f, atEnd, 0.01f)
    }

    @Test
    fun barHeights_atT0_areAllZero() {
        // DoD: при t=0 высота всех столбиков = 0
        val animator = PulseBarAnimator(palette, distribution)
        val maxH = 1000f

        for (slot in TimeOfDaySlot.entries) {
            val h = animator.getBarHeight(slot, 0f, maxH)
            assertEquals("Bar height for $slot at t=0 must be 0", 0f, h, 0.0001f)
        }
    }

    @Test
    fun barHeights_atT1_equalFractionTimesMaxH() {
        // DoD: при t=1 высота = fraction * maxH
        val animator = PulseBarAnimator(palette, distribution)
        val maxH = 800f

        for (slot in TimeOfDaySlot.entries) {
            val expectedH = (distribution[slot] ?: 0f) * maxH
            val actualH = animator.getBarHeight(slot, 1f, maxH)
            assertEquals("Bar height for $slot at t=1 must be fraction * maxH", expectedH, actualH, 0.001f)
        }
    }

    @Test
    fun at_t0_nothingRenderedExceptBackground() {
        val animator = PulseBarAnimator(palette, distribution)
        val canvas = mock(Canvas::class.java)

        animator.renderFrame(canvas, 0f, 1080, 1080)

        // Only background is drawn
        verify(canvas).drawRect(anyFloat(), anyFloat(), anyFloat(), anyFloat(), any())

        // No texts drawn
        verify(canvas, never()).drawText(any(), anyFloat(), anyFloat(), any())
    }

    @Test
    fun at_t1_allElementsRendered() {
        val animator = PulseBarAnimator(palette, distribution)
        val bmp = Bitmap.createBitmap(500, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        animator.renderFrame(canvas, 1f, 500, 500)

        assertEquals(255, animator.getHeaderAlpha(1f))
        assertEquals(255, animator.getPercentAlpha(1f))
        assertEquals(1f, animator.getSweepProgress(1f), 0.0001f)

        bmp.recycle()
    }

    @Test
    fun staggeredGrowth_startsInSequentialOrder() {
        val animator = PulseBarAnimator(palette, distribution)
        val maxH = 500f

        // At t = 0.20: Night has started, others have not
        assertTrue(animator.getBarHeight(TimeOfDaySlot.NIGHT, 0.20f, maxH) > 0f)
        assertEquals(0f, animator.getBarHeight(TimeOfDaySlot.MORNING, 0.20f, maxH), 0.0001f)
        assertEquals(0f, animator.getBarHeight(TimeOfDaySlot.AFTERNOON, 0.20f, maxH), 0.0001f)
        assertEquals(0f, animator.getBarHeight(TimeOfDaySlot.EVENING, 0.20f, maxH), 0.0001f)

        // At t = 0.30: Night and Morning have started, Afternoon/Evening have not
        assertTrue(animator.getBarHeight(TimeOfDaySlot.NIGHT, 0.30f, maxH) > 0f)
        assertTrue(animator.getBarHeight(TimeOfDaySlot.MORNING, 0.30f, maxH) > 0f)
        assertEquals(0f, animator.getBarHeight(TimeOfDaySlot.AFTERNOON, 0.30f, maxH), 0.0001f)
        assertEquals(0f, animator.getBarHeight(TimeOfDaySlot.EVENING, 0.30f, maxH), 0.0001f)
    }
}
