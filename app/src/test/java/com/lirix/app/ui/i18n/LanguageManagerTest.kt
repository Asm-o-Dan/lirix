package com.lirix.app.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageManagerTest {

    @Test
    fun fromCode_emptyOrNull_returnsSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("   "))
    }

    @Test
    fun fromCode_ru_returnsRussian() {
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromCode("ru"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromCode("RU"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromCode("Ru"))
    }

    @Test
    fun fromCode_en_returnsEnglish() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("EN"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("En"))
    }

    @Test
    fun fromCode_invalid_returnsSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("invalid"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("fr"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("de"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("123"))
    }

    @Test
    fun appLanguage_properties_areCorrect() {
        assertEquals("", AppLanguage.SYSTEM.code)
        assertEquals("ru", AppLanguage.RUSSIAN.code)
        assertEquals("en", AppLanguage.ENGLISH.code)

        assertEquals("Русский", AppLanguage.RUSSIAN.nativeName)
        assertEquals("English", AppLanguage.ENGLISH.nativeName)
    }
}
