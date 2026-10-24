package com.lirix.app.desktop

import com.lirix.app.desktop.i18n.DesktopLanguage
import com.lirix.app.desktop.i18n.DesktopLanguagePreferences
import com.lirix.app.ui.i18n.InterfaceLanguage
import com.lirix.app.ui.i18n.LirixStrings
import org.junit.Test
import org.junit.After
import org.junit.Assert.assertEquals

class DesktopLanguagePreferencesTest {
    private val previousLanguage = LirixStrings.currentLanguage
    @After fun restoreLanguage() { LirixStrings.currentLanguage = previousLanguage }
    @Test fun persistedChoiceSurvivesFreshStoreAndSystemModeFollowsOs() {
        var stored = ""
        var system = "ru"
        fun store() = DesktopLanguagePreferences({ stored }, { stored = it }, { system })
        val first = store()
        assertEquals(InterfaceLanguage.RU, LirixStrings.currentLanguage)
        first.setLanguage(DesktopLanguage.ENGLISH)
        assertEquals("en", stored)
        assertEquals(DesktopLanguage.ENGLISH, store().selection)
        assertEquals("About track", LirixStrings.current.aboutTrack)
        assertEquals(listOf("All", "Favorites", "Karaoke", "With saved lyrics"),
            com.lirix.app.desktop.library.DesktopLibraryFilter.entries.map { it.label })
        first.setLanguage(DesktopLanguage.SYSTEM)
        assertEquals(InterfaceLanguage.RU, LirixStrings.currentLanguage)
        system = "en"
        first.refreshSystemLanguage()
        assertEquals(InterfaceLanguage.EN, LirixStrings.currentLanguage)
        first.setLanguage(DesktopLanguage.RUSSIAN)
        first.refreshSystemLanguage()
        assertEquals(InterfaceLanguage.RU, LirixStrings.currentLanguage)
    }
    @Test fun invalidPreferenceAndUnsupportedSystemFallBackWithoutOverwritingChoice() {
        var stored = "bad"
        val store = DesktopLanguagePreferences({ stored }, { stored = it }, { "fr" })
        assertEquals(DesktopLanguage.SYSTEM, store.selection)
        assertEquals(InterfaceLanguage.EN, LirixStrings.currentLanguage)
        assertEquals("bad", stored)
    }
}
