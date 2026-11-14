package com.lirix.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Stored offset convention (unchanged): positive offset shows lyrics earlier, because the lyric
 * clock is media position + offset (see karaokeMediaSeekPosition). Users see the lyrics delay.
 */
class TimingCorrectionSignTest {
    @Test fun minusMovesLyricsEarlierAndPlusLater() {
        // "−50" from zero: lyrics earlier, so the stored offset grows.
        val earlier = offsetForLyricsDelay(lyricsDelayMs(0) - 50)
        assertEquals(50L, earlier)
        assertEquals(-50L, lyricsDelayMs(earlier))
        // Lyric clock runs ahead of the media: lyric time 10 050 ms is reached at media 10 000 ms.
        assertEquals(10_000L, karaokeMediaSeekPosition(10_050, earlier, 0))
        // "+50" from zero: lyrics later.
        val later = offsetForLyricsDelay(lyricsDelayMs(0) + 50)
        assertEquals(-50L, later)
        assertEquals(10_100L, karaokeMediaSeekPosition(10_050, later, 0))
    }

    @Test fun shownNumberHasTheSameSignAsTheButtonThatProducedIt() {
        assertEquals("−50", formatLyricsDelay(lyricsDelayMs(50)))
        assertEquals("+50", formatLyricsDelay(lyricsDelayMs(-50)))
        assertEquals("0", formatLyricsDelay(lyricsDelayMs(0)))
    }

    @Test fun delayIsBoundedLikeTheStoredOffset() {
        assertEquals(3000L, offsetForLyricsDelay(-10_000))
        assertEquals(-3000L, offsetForLyricsDelay(10_000))
        assertEquals(-3000L, offsetForLyricsDelay(3000))
        // Existing stored values round-trip unchanged.
        listOf(-3000L, -120L, 0L, 75L, 3000L).forEach { assertEquals(it, offsetForLyricsDelay(lyricsDelayMs(it))) }
    }
}
