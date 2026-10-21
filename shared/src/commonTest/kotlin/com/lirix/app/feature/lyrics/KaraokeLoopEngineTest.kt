package com.lirix.app.feature.lyrics

import kotlin.test.*

class KaraokeLoopEngineTest {
    @Test fun rejectsTooShortNegativeAndOutOfBoundsRegions() {
        assertNull(KaraokeLoopEngine.start(-1, 1000, 3000, 0))
        assertNull(KaraokeLoopEngine.start(1000, 1499, 3000, 0))
        assertNull(KaraokeLoopEngine.start(2000, 3500, 3000, 0))
        assertNotNull(KaraokeLoopEngine.start(0, 500, 3000, 0))
    }
    @Test fun sendsExactlyOneSeekUntilActualSourceAcknowledges() {
        var loop = KaraokeLoop(1000, 2000)
        val first = KaraokeLoopEngine.tick(loop, 2010, true, true, true, 5000)
        assertEquals(1000, first.seekToMs)
        loop = assertNotNull(first.loop)
        repeat(10) { assertNull(KaraokeLoopEngine.tick(loop, 2100 + it.toLong(), true, true, true, 5100 + it * 100L).seekToMs) }
        val acknowledged = KaraokeLoopEngine.tick(loop, 1100, true, true, true, 6200)
        assertNull(acknowledged.loop?.awaitingSinceMs)
        assertEquals(1000, KaraokeLoopEngine.tick(assertNotNull(acknowledged.loop), 2000, true, true, true, 7200).seekToMs)
    }
    @Test fun failedSeekTimesOutWithoutRequestStorm() {
        val loop = KaraokeLoop(1000, 2000, 100)
        val result = KaraokeLoopEngine.tick(loop, 5000, true, true, true, 2601)
        assertNull(result.loop)
        assertNull(result.seekToMs)
        assertNotNull(result.error)
    }
    @Test fun pauseDoesNotSeekAndDifferentTrackDisablesLoop() {
        val loop = KaraokeLoop(1000, 2000)
        assertNull(KaraokeLoopEngine.tick(loop, 2500, false, true, true, 2000).seekToMs)
        assertNull(KaraokeLoopEngine.tick(loop, 2500, true, true, false, 2000).loop)
        assertNull(KaraokeLoopEngine.tick(loop, 2500, true, false, true, 2000).loop)
    }
}
