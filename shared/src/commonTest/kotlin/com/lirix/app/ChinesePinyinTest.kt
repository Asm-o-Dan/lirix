package com.lirix.app

import com.lirix.app.feature.lyrics.romanization.ChinesePinyinEngine
import com.lirix.app.feature.lyrics.romanization.RomanizationLanguage
import com.lirix.app.feature.lyrics.romanization.RomanizedLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Unit tests for ChinesePinyinEngine (Chinese Hanzi to Pinyin Engine).
 * Spec: SPEC-ROMAN-01 / Task: TASK-ROMAN-003
 */
class ChinesePinyinTest {

    @Test
    fun testNiHaoWithAndWithoutTones() {
        // Spec required: "你好" -> "nǐ hǎo" (or "ni hao" without tones)
        assertEquals("nǐ hǎo", ChinesePinyinEngine.toPinyin("你好", includeTones = true))
        assertEquals("ni hao", ChinesePinyinEngine.toPinyin("你好", includeTones = false))
    }

    @Test
    fun testWoAiNiWithAndWithoutTones() {
        // Spec required: "我爱你" -> "wǒ ài nǐ"
        assertEquals("wǒ ài nǐ", ChinesePinyinEngine.toPinyin("我爱你", includeTones = true))
        assertEquals("wo ai ni", ChinesePinyinEngine.toPinyin("我爱你", includeTones = false))
    }

    @Test
    fun testLatinAndPunctuationPreservation() {
        assertEquals("Hello nǐ hǎo!", ChinesePinyinEngine.toPinyin("Hello 你好!"))
        assertEquals("wǒ ài nǐ，Forever", ChinesePinyinEngine.toPinyin("我爱你，Forever"))
        assertEquals("nǐ hǎo……", ChinesePinyinEngine.toPinyin("你好……"))
    }

    @Test
    fun testToneRemovalHelper() {
        assertEquals("a e i o u ü", ChinesePinyinEngine.removeTones("ā é ǐ ò ū ǘ"))
        assertEquals("wo ai ni", ChinesePinyinEngine.removeTones("wǒ ài nǐ"))
    }

    // Dictionary lexical tones; spoken tone sandhi/neutralization is not inferred from lyrics.
    @Test
    fun testMultiCharacterPhrases() {
        assertEquals("xiè xiè", ChinesePinyinEngine.toPinyin("谢谢"))
        assertEquals("zài jiàn", ChinesePinyinEngine.toPinyin("再见"))
        assertEquals("duì bù qǐ", ChinesePinyinEngine.toPinyin("对不起"))
        assertEquals("méi guān xì", ChinesePinyinEngine.toPinyin("没关系"))
        assertEquals("wèi shén me", ChinesePinyinEngine.toPinyin("为什么"))
        assertEquals("yǒng yuǎn", ChinesePinyinEngine.toPinyin("永远"))
    }

    @Test
    fun testLanguageDetection() {
        assertEquals(RomanizationLanguage.AUTO, ChinesePinyinEngine.detectLanguage("你好世界"))
        assertEquals(RomanizationLanguage.AUTO, ChinesePinyinEngine.detectLanguage("我爱你"))
        // Kana present means Japanese
        assertEquals(RomanizationLanguage.JAPANESE, ChinesePinyinEngine.detectLanguage("你好世界 さくら"))
        assertEquals(RomanizationLanguage.NONE, ChinesePinyinEngine.detectLanguage("Only English Text 123"))
    }

    @Test
    fun testRomanizeLrcBatch() {
        val lines = listOf(
            0L to "你好",
            3000L to "我爱你",
            6000L to "谢谢"
        )
        val result = ChinesePinyinEngine.romanizeLrc(lines)

        assertEquals(3, result.size)
        assertEquals(RomanizedLyricLine("你好", "nǐ hǎo", 0L), result[0])
        assertEquals(RomanizedLyricLine("我爱你", "wǒ ài nǐ", 3000L), result[1])
        assertEquals(RomanizedLyricLine("谢谢", "xiè xiè", 6000L), result[2])
    }

    @Test
    fun testRealBackendWarmBatchRetainsConversionCoverage() {
        val sampleLines = List(100) { "夜空中最亮的星，能否听清，那仰望的人心底的孤独和叹息。" }


        // Measure
        val durationMs = measureTimeMillis {
            sampleLines.forEach { assertTrue(ChinesePinyinEngine.toPinyin(it).none { char -> char in '\u3400'..'\u9FFF' }) }
        }

        println("Performance test: 100 lines of Chinese lyrics romanized in $durationMs ms")
        // Timing is measured on the reference phone; machine-dependent speed is not a correctness oracle.
    }
}
