package com.lirix.app.desktop

import com.lirix.app.desktop.lyrics.DesktopLyricsState
import com.lirix.app.desktop.ui.components.*
import com.lirix.app.ui.player.LrcLine
import org.junit.Assert.*
import org.junit.Test

class DesktopKaraokePresentationTest {
    private val timed = DesktopLyricsState(lrcLines = listOf(LrcLine(1000, "First"), LrcLine(2500, "Second")))

    @Test fun `text and karaoke remain separate for timed only songs`() {
        assertEquals(DesktopLyricsContent.TEXT, resolveDesktopLyricsContent(DesktopLyricsMode.TEXT, true, timed))
        assertEquals("First\nSecond", desktopPlainLyrics(timed))
        assertEquals(DesktopLyricsContent.KARAOKE, resolveDesktopLyricsContent(DesktopLyricsMode.KARAOKE, true, timed))
    }

    @Test fun `plain lyrics stay readable and report missing synchronization`() {
        val plain = DesktopLyricsState(plainLyrics = "Saved words")
        assertEquals(DesktopLyricsContent.TEXT, resolveDesktopLyricsContent(DesktopLyricsMode.TEXT, true, plain))
        assertEquals("Saved words", desktopPlainLyrics(plain))
        assertEquals(DesktopLyricsContent.UNSYNCED, resolveDesktopLyricsContent(DesktopLyricsMode.KARAOKE, true, plain))
        assertEquals(DesktopLyricsContent.UNSYNCED, resolveDesktopLyricsContent(DesktopLyricsMode.KARAOKE, true, DesktopLyricsState(chords = "Am Words")))
        assertEquals("Preferred plain", desktopPlainLyrics(timed.copy(plainLyrics = "Preferred plain")))
        assertEquals("First\nSecond", desktopPlainLyrics(timed.copy(plainLyrics = "  ")))
    }

    @Test fun `idle loading and missing content are distinct`() {
        for (mode in listOf(DesktopLyricsMode.TEXT, DesktopLyricsMode.KARAOKE)) {
            assertEquals(DesktopLyricsContent.IDLE, resolveDesktopLyricsContent(mode, false, timed.copy(isLoading = true)))
            assertEquals(DesktopLyricsContent.LOADING, resolveDesktopLyricsContent(mode, true, timed.copy(isLoading = true)))
            assertEquals(DesktopLyricsContent.UNAVAILABLE, resolveDesktopLyricsContent(mode, true, DesktopLyricsState(errorMessage = "Unavailable")))
        }
        assertEquals(DesktopLyricsContent.CHORDS, resolveDesktopLyricsContent(DesktopLyricsMode.CHORDS, false, DesktopLyricsState()))
        assertEquals(DesktopLyricsContent.ABOUT, resolveDesktopLyricsContent(DesktopLyricsMode.ABOUT, false, DesktopLyricsState()))
    }

    @Test fun `first lyric is not active before its timestamp and seek can move backward`() {
        assertEquals(-1, activeDesktopLyricIndex(timed.lrcLines, 999))
        assertEquals(0, activeDesktopLyricIndex(timed.lrcLines, 1000))
        assertEquals(1, activeDesktopLyricIndex(timed.lrcLines, 2500))
        assertEquals(0, activeDesktopLyricIndex(timed.lrcLines, 1600))
        assertEquals(-1, activeDesktopLyricIndex(emptyList(), 10000))
    }

    @Test fun `line seek inverts the displayed offset and clamps to playable bounds`() {
        for (offset in listOf(-300L, 0L, 700L)) {
            val seek = desktopLyricSeekPosition(2500, offset, 10000)
            assertEquals(2500L, seek - offset)
        }
        assertEquals(0L, desktopLyricSeekPosition(100, -500, 10000))
        assertEquals(3000L, desktopLyricSeekPosition(2500, 700, 3000))
        assertEquals(3200L, desktopLyricSeekPosition(2500, 700, 0))
        assertEquals(Long.MAX_VALUE, desktopLyricSeekPosition(Long.MAX_VALUE - 5, 100, 0))
        assertEquals(0L, desktopLyricSeekPosition(5, Long.MIN_VALUE, 0))
    }
}
