package com.lirix.app

import com.lirix.app.feature.lyrics.romanization.*
import org.junit.Assert.*
import org.junit.Test

/** Regression witnesses against the original public engine APIs, added before their replacement. */
class RomanizationRepairRegressionTest {
    @Test fun unknownJapaneseDictionaryWordsMustBeRead() {
        for (text in listOf("失くす", "彷徨う", "立ち止まる")) {
            val reading = JapaneseRomajiEngine.toRomaji(text)
            assertFalse("Residual Han: $text -> $reading", reading.any { it in '\u4E00'..'\u9FFF' })
        }
    }
    @Test fun japaneseParticleUsesGrammar() { assertEquals("kimi e", JapaneseRomajiEngine.toRomaji("君へ")) }
    @Test fun decomposedAndHalfwidthKanaAreEquivalent() {
        assertEquals(JapaneseRomajiEngine.toRomaji("カタカナ"), JapaneseRomajiEngine.toRomaji("ｶﾀｶﾅ"))
        assertEquals(JapaneseRomajiEngine.toRomaji("が"), JapaneseRomajiEngine.toRomaji("か\u3099"))
    }
    @Test fun chineseTraditionalFormsAreRead() { assertEquals(ChinesePinyinEngine.toPinyin("爱你"), ChinesePinyinEngine.toPinyin("愛你")) }
    @Test fun chinesePolyphonesUsePhraseContext() {
        assertEquals("yín háng", ChinesePinyinEngine.toPinyin("银行"))
        assertEquals("shǎo le", ChinesePinyinEngine.toPinyin("少了"))
        assertEquals("chóng qìng", ChinesePinyinEngine.toPinyin("重庆"))
    }
}
