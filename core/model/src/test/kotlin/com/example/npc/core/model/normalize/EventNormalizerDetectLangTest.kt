package com.example.npc.core.model.normalize

import com.example.npc.core.model.Lang
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EventNormalizerDetectLangTest {

    @Test
    fun `detectLang returns Lang UNK for fewer than 3 total letters`() {
        assertEquals(Lang.UNK, EventNormalizer.detectLang(""))
        assertEquals(Lang.UNK, EventNormalizer.detectLang("A"))
        assertEquals(Lang.UNK, EventNormalizer.detectLang("Hi"))
        assertEquals(Lang.UNK, EventNormalizer.detectLang("Да"))
        assertEquals(Lang.UNK, EventNormalizer.detectLang("12"))
    }

    @Test
    fun `detectLang returns Lang UNK for digits and special characters only`() {
        assertEquals(Lang.UNK, EventNormalizer.detectLang("12345 67890"))
        assertEquals(Lang.UNK, EventNormalizer.detectLang("!@#$%^&*()_+=-~`{}[]:;'\"<>,.?/|\\"))
        assertEquals(Lang.UNK, EventNormalizer.detectLang(" \t \r\n "))
        assertEquals(Lang.UNK, EventNormalizer.detectLang("123.456 + 789 - 000"))
    }

    @Test
    fun `detectLang returns Lang RU when Cyrillic ratio is greater than or equal to 70 percent`() {
        // Exactly 3 cyrillic letters (100%)
        assertEquals(Lang.RU, EventNormalizer.detectLang("Код"))

        // Pure Russian banking notification with numbers
        val russianText = "Зачисление зарплаты 50 000 рублей на карту *1234"
        assertEquals(Lang.RU, EventNormalizer.detectLang(russianText))

        // Russian with uppercase, lowercase, and ё/Ё
        val yoText = "Съешь же ещё этих мягких французских булок, да выпей чаю."
        assertEquals(Lang.RU, EventNormalizer.detectLang(yoText))

        // 7 Cyrillic letters and 3 Latin letters (7/10 = 70.0% -> RU)
        val boundaryRu = "АБВГДЕЖxyz"
        assertEquals(Lang.RU, EventNormalizer.detectLang(boundaryRu))
    }

    @Test
    fun `detectLang returns Lang EN when Latin ratio is greater than or equal to 70 percent`() {
        // Exactly 3 latin letters (100%)
        assertEquals(Lang.EN, EventNormalizer.detectLang("Yes"))

        // Pure English notification
        val englishText = "Your verification code is 492019. Do not share it."
        assertEquals(Lang.EN, EventNormalizer.detectLang(englishText))

        // English text with numbers and symbols
        val serviceText = "SMS: 481-01"
        assertEquals(Lang.EN, EventNormalizer.detectLang(serviceText))

        // 7 Latin letters and 3 Cyrillic letters (7/10 = 70.0% -> EN)
        val boundaryEn = "abcdefgАБВ"
        assertEquals(Lang.EN, EventNormalizer.detectLang(boundaryEn))
    }

    @Test
    fun `detectLang returns Lang UNK for mixed language text without 70 percent dominance`() {
        // Cyrillic = 3 ("Код"), Latin = 4 ("ABCD"), total = 7 (42.8% and 57.1%)
        assertEquals(Lang.UNK, EventNormalizer.detectLang("Код: 1234 ABCD"))

        // 50% Cyrillic and 50% Latin (5 Cyrillic, 5 Latin)
        assertEquals(Lang.UNK, EventNormalizer.detectLang("Привет Hello"))

        // 6 Cyrillic, 4 Latin = 60% Cyrillic (< 70%)
        assertEquals(Lang.UNK, EventNormalizer.detectLang("Москва City"))

        // 6 Latin, 4 Cyrillic = 60% Latin (< 70%)
        assertEquals(Lang.UNK, EventNormalizer.detectLang("Online Банк"))
    }
}
