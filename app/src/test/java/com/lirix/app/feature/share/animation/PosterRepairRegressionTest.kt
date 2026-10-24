package com.lirix.app.feature.share.animation

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.ui.theme.ColorPalettes
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config
import android.graphics.Paint
import android.graphics.RectF

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class PosterRepairRegressionTest {
    private val palette = ThemeAnimationPalette.fromColors(ColorPalettes.SpotifyMood)

    @Test fun finalLyricIsCompleteAndHeldForLastSixTenthsOfSecond() {
        val text = "Long text ".repeat(20)
        val animator = VinylSpinAnimator(palette, null, "Title", "Artist", text)
        for (frame in 82 until 96) assertEquals(text, animator.getLyricText(frame / 96f))
    }

    @Test fun typewriterNeverSeparatesEmojiOrCombiningCluster() {
        val text = "👨‍👩‍👧‍👦e\u0301🇯🇵"
        val validPrefixes = setOf("", "👨‍👩‍👧‍👦", "👨‍👩‍👧‍👦e\u0301", text)
        val animator = VinylSpinAnimator(palette, null, text, "Artist", text)
        repeat(101) { step ->
            assertTrue("Broken title cluster at $step", animator.getTitleText(step / 100f) in validPrefixes)
            assertTrue("Broken lyric cluster at $step", animator.getLyricText(step / 100f) in validPrefixes)
        }
    }

    @Test fun fullLongTextFitsSafeRectangleWithoutEllipsis() {
        val text = "Unusually long title 日本語 👨‍👩‍👧‍👦 ".repeat(18)
        val fit = PosterTextLayout(text, Paint().apply { textSize = 56f }, RectF(86f, 800f, 994f, 940f))
        assertTrue(fit.layout.height <= fit.bounds.height() + 1)
        assertEquals(text.length, fit.layout.getLineEnd(fit.layout.lineCount - 1))
        repeat(fit.layout.lineCount) { line ->
            assertTrue(fit.layout.getLineWidth(line) <= fit.bounds.width() + 1)
            assertEquals(0, fit.layout.getEllipsisCount(line))
        }
    }
}
