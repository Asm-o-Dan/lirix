package com.lirix.app

import com.lirix.app.ui.i18n.*
import kotlin.test.*

class UiMessageCatalogTest {
    @Test fun everyMessageHasEnglishAndMatchingArgumentPositions() {
        assertTrue(UiMessageCatalog.english.size >= 300)
        val placeholders = Regex("\\{[0-9]+\\}")
        UiMessageCatalog.english.forEach { (russian, english) ->
            assertTrue(english.isNotBlank(), russian)
            if (russian != "Русский") assertFalse(Regex("[А-Яа-яЁё]").containsMatchIn(english), "Untranslated UI caption: $russian")
            assertEquals(placeholders.findAll(russian).map { it.value }.toSet(),
                placeholders.findAll(english).map { it.value }.toSet(), russian)
        }
    }
    @Test fun changingInterfaceLanguageChangesExistingCatalogAndFormattedMessages() {
        val previous = LirixStrings.currentLanguage
        try {
            LirixStrings.currentLanguage = InterfaceLanguage.EN
            assertEquals("About track", LirixStrings.current.aboutTrack)
            assertEquals("Tracks: 12", LirixStrings.message("Треков: {0}", 12))
            LirixStrings.currentLanguage = InterfaceLanguage.RU
            assertEquals("О треке", LirixStrings.current.aboutTrack)
            assertEquals("Треков: 12", LirixStrings.message("Треков: {0}", 12))
        } finally { LirixStrings.currentLanguage = previous }
    }
    @Test fun argumentValuesAreNotReinterpretedAsFormattingOrTranslations() {
        assertEquals("Lyrics — {1} 日本語", UiMessageCatalog.text("Текст — {0}", InterfaceLanguage.EN, "{1} 日本語"))
        assertEquals("Optional search URL with {query}", UiMessageCatalog.text("Адрес поиска с {query}, необязательно", InterfaceLanguage.EN))
    }
}
