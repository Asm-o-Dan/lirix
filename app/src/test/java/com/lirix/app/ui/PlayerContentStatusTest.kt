package com.lirix.app.ui

import com.lirix.app.R
import org.junit.Assert.*
import org.junit.Test

class PlayerContentStatusTest {
    @Test fun absentContentLeavesFeedbackToTheRetryArea() {
        NowPlayingMode.entries.forEach { assertNull(playerHeaderStatus(it, false, false, false, false)) }
    }
    @Test fun chordsViewDoesNotClaimThatAvailableLyricsAreChords() {
        assertNull(playerHeaderStatus(NowPlayingMode.CHORDS, true, true, false, false))
        assertEquals(R.string.player_chords_available, playerHeaderStatus(NowPlayingMode.CHORDS, false, true, true, false))
    }
    @Test fun timedKaraokeHasOneAccurateStatus() {
        assertEquals(R.string.lyrics_status_synced, playerHeaderStatus(NowPlayingMode.KARAOKE, true, true, false, true))
        assertNull(playerHeaderStatus(NowPlayingMode.KARAOKE, false, true, false, true))
    }
    @Test fun notesHaveNoLyricsStatusAndTextDoesNotClaimKaraokeTiming() {
        assertNull(playerHeaderStatus(NowPlayingMode.NOTES, true, true, true, false))
        assertEquals(R.string.lyrics_status_static, playerHeaderStatus(NowPlayingMode.LYRICS, true, true, false, false))
    }
}
