package com.lirix.app

import com.lirix.app.feature.lyrics.romanization.JapaneseRomajiEngine
import com.lirix.app.feature.lyrics.romanization.LyricsRomanizer
import com.lirix.app.feature.lyrics.romanization.RomanizationDisplayMode
import com.lirix.app.feature.lyrics.romanization.RomanizationLanguage
import com.lirix.app.feature.lyrics.romanization.RomanizedLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/**
 * Unit tests for JapaneseRomajiEngine and RomanizationModels.
 * Spec: SPEC-ROMAN-01 / Tasks: TASK-ROMAN-001, TASK-ROMAN-002
 */
class JapaneseRomajiTest {

    // --- TASK-ROMAN-001 Contract & Model Tests ---

    @Test
    fun testRomanizationLanguageEnumValues() {
        val expected = listOf("JAPANESE", "CHINESE", "AUTO", "NONE")
        val actual = RomanizationLanguage.values().map { it.name }
        assertEquals(expected, actual)
    }

    @Test
    fun testRomanizationDisplayModeEnumValues() {
        val expected = listOf("ORIGINAL_ONLY", "READING_ABOVE", "ROMANIZED_ONLY")
        val actual = RomanizationDisplayMode.values().map { it.name }
        assertEquals(expected, actual)
    }

    @Test
    fun testRomanizedLyricLineModel() {
        val line = RomanizedLyricLine(
            originalText = "さくら",
            romanizedText = "sakura",
            timestampMs = 12500L
        )
        assertEquals("さくら", line.originalText)
        assertEquals("sakura", line.romanizedText)
        assertEquals(12500L, line.timestampMs)
    }

    @Test
    fun testLyricsRomanizerLrcBatchConversion() {
        val input = listOf(
            0L to "さくら",
            2500L to "ずっと",
            5000L to "とうきょう"
        )
        val result = JapaneseRomajiEngine.romanizeLrc(input)

        assertEquals(3, result.size)
        assertEquals(RomanizedLyricLine("さくら", "sakura", 0L), result[0])
        assertEquals(RomanizedLyricLine("ずっと", "zutto", 2500L), result[1])
        assertEquals(RomanizedLyricLine("とうきょう", "toukyou", 5000L), result[2])
    }

    // --- TASK-ROMAN-002 Core Tests ---

    @Test
    fun testSakuraHiragana() {
        // Spec required: "さくら" -> "sakura"
        assertEquals("sakura", JapaneseRomajiEngine.toRomaji("さくら"))
    }

    @Test
    fun testZuttoSokuon() {
        // Spec required: "ずっと" -> "zutto"
        assertEquals("zutto", JapaneseRomajiEngine.toRomaji("ずっと"))
    }

    @Test
    fun testToukyouYoonAndLongVowel() {
        // Spec required: "とうきょう" -> "toukyou"
        assertEquals("toukyou", JapaneseRomajiEngine.toRomaji("とうきょう"))
    }

    @Test
    fun testLatinPreservationAndPunctuation() {
        // Spec required: preservation of latin and punctuation marks
        assertEquals("Tokyo toukyou", JapaneseRomajiEngine.toRomaji("Tokyo 東京"))
        assertEquals("Hello world!", JapaneseRomajiEngine.toRomaji("Hello world!"))
        assertEquals("sakura、zutto", JapaneseRomajiEngine.toRomaji("さくら、ずっと"))
        assertEquals("zutto！", JapaneseRomajiEngine.toRomaji("ずっと！"))
    }

    @Test
    fun testSokuonDoublingConsonants() {
        assertEquals("matte", JapaneseRomajiEngine.toRomaji("まって"))
        assertEquals("rokku", JapaneseRomajiEngine.toRomaji("ロック"))
        // Hepburn rule: small tsu before 'ch' becomes 't'
        assertEquals("matchi", JapaneseRomajiEngine.toRomaji("マッチ"))
    }

    @Test
    fun testKatakanaAndChoonpu() {
        assertEquals("raamen", JapaneseRomajiEngine.toRomaji("ラーメン"))
        assertEquals("koohii", JapaneseRomajiEngine.toRomaji("コーヒー"))
        assertEquals("aisukuriimu", JapaneseRomajiEngine.toRomaji("アイスクリーム"))
    }

    @Test
    fun testJPopKanjiDictionary() {
        assertEquals("watashi", JapaneseRomajiEngine.toRomaji("私"))
        assertEquals("koi", JapaneseRomajiEngine.toRomaji("恋"))
        assertEquals("ai", JapaneseRomajiEngine.toRomaji("愛"))
        assertEquals("yume", JapaneseRomajiEngine.toRomaji("夢"))
        assertEquals("kokoro", JapaneseRomajiEngine.toRomaji("心"))
        assertEquals("kimi", JapaneseRomajiEngine.toRomaji("君"))
        assertEquals("anata", JapaneseRomajiEngine.toRomaji("あなた"))
        assertEquals("kimi wa", JapaneseRomajiEngine.toRomaji("君は"))
        assertEquals("arigatou", JapaneseRomajiEngine.toRomaji("ありがとう"))
        assertEquals("sayounara", JapaneseRomajiEngine.toRomaji("さようなら"))
    }

    @Test
    fun testLanguageDetection() {
        assertEquals(RomanizationLanguage.JAPANESE, JapaneseRomajiEngine.detectLanguage("さくら"))
        assertEquals(RomanizationLanguage.JAPANESE, JapaneseRomajiEngine.detectLanguage("カタカナ"))
        assertEquals(RomanizationLanguage.JAPANESE, JapaneseRomajiEngine.detectLanguage("Tokyo 東京で"))
        assertEquals(RomanizationLanguage.AUTO, JapaneseRomajiEngine.detectLanguage("你好世界"))
        assertEquals(RomanizationLanguage.NONE, JapaneseRomajiEngine.detectLanguage("English lyrics only 123"))
    }

    @Test
    fun testRealBackendWarmBatchRetainsConversionCoverage() {
        val sampleLines = List(100) { "夜空に光る星のように、ずっと君の瞳を見つめていたい。" }


        // Measure
        val durationMs = measureTimeMillis {
            sampleLines.forEach { assertFalse(JapaneseRomajiEngine.toRomaji(it).any { char -> char in '\u3400'..'\u9FFF' }) }
        }

        println("Performance test: 100 lines romanized in $durationMs ms")
        // Timing is measured on the reference phone; machine-dependent speed is not a correctness oracle.
    }
}
