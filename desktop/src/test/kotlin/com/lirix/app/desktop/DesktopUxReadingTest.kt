package com.lirix.app.desktop

import com.lirix.app.desktop.ui.desktopLibraryLyrics
import com.lirix.app.desktop.ui.components.playbackSourceLabel
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import com.lirix.app.ui.i18n.LirixStrings
import com.lirix.app.ui.i18n.InterfaceLanguage

class DesktopUxReadingTest {
    private val previousLanguage = LirixStrings.currentLanguage
    @Before fun useRussianForLegacyExpectations() { LirixStrings.currentLanguage = InterfaceLanguage.RU }
    @After fun restoreLanguage() { LirixStrings.currentLanguage = previousLanguage }
    @Test fun sourceLabelsDistinguishPausePlaybackAndNoSession() {
        assertEquals("Играет в Spotify", playbackSourceLabel("spotify.exe", true))
        assertEquals("На паузе · Spotify", playbackSourceLabel("spotify.exe", false))
        assertEquals("Нет активного плеера", playbackSourceLabel(null, false))
    }

    @Test fun libraryReadsSyncedOnlyDocumentsWithoutTimingOrMetadata() {
        val lrc = "[ar:Artist]\n[ti:Example]\n[00:01.20]First line\n[00:02.40]Second line"
        assertEquals("First line\nSecond line", desktopLibraryLyrics(null, lrc))
        assertEquals("First line\nSecond line", desktopLibraryLyrics("  ", lrc))
        assertEquals("User's original", desktopLibraryLyrics("User's original", lrc))
        assertEquals("Текст пока не сохранён", desktopLibraryLyrics(null, "[ar:Artist]"))
    }
}
