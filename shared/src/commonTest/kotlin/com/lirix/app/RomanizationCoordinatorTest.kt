package com.lirix.app

import com.lirix.app.feature.lyrics.romanization.RomanizationCoordinator
import com.lirix.app.feature.lyrics.romanization.RomanizationLanguage
import com.lirix.app.feature.lyrics.romanization.RomanizedLyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for RomanizationCoordinator.
 * Spec: SPEC-ROMAN-01 / Task: TASK-ROMAN-004
 */
class RomanizationCoordinatorTest {

    @Test
    fun testLanguageDetectionJapanese() {
        val coordinator = RomanizationCoordinator()
        // Hiragana
        assertEquals(RomanizationLanguage.JAPANESE, coordinator.detectLanguage("さくら"))
        // Katakana
        assertEquals(RomanizationLanguage.JAPANESE, coordinator.detectLanguage("カタカナ"))
        // Mixed Kanji + Kana
        assertEquals(RomanizationLanguage.JAPANESE, coordinator.detectLanguage("君の瞳"))
    }

    @Test
    fun testLanguageDetectionChinese() {
        val coordinator = RomanizationCoordinator()
        // Pure CJK without Kana
        assertEquals(RomanizationLanguage.AUTO, coordinator.detectLanguage("你好世界"))
        assertEquals(RomanizationLanguage.AUTO, coordinator.detectLanguage("我爱你"))
    }

    @Test
    fun testLanguageDetectionNonAsian() {
        val coordinator = RomanizationCoordinator()
        // English
        assertEquals(RomanizationLanguage.NONE, coordinator.detectLanguage("In the end it doesn't even matter"))
        // Russian
        assertEquals(RomanizationLanguage.NONE, coordinator.detectLanguage("Группа крови на рукаве"))
        // Punctuation and numbers
        assertEquals(RomanizationLanguage.NONE, coordinator.detectLanguage("12345 !? #$%"))
    }

    @Test
    fun testNonAsianTextPreservedWithoutDistortion() {
        val coordinator = RomanizationCoordinator()
        val english = "Bohemian Rhapsody - Queen"
        val russian = "Кукушка - Виктор Цой"

        assertEquals(english, coordinator.romanizeLine(english))
        assertEquals(russian, coordinator.romanizeLine(russian))
    }

    @Test
    fun testCacheHitVerification() {
        val coordinator = RomanizationCoordinator()
        val input = "さくら"

        assertEquals(0L, coordinator.cacheHits)
        assertEquals(0L, coordinator.cacheMisses)

        // First call: Cache miss, computes result
        val firstResult = coordinator.romanizeLine(input)
        assertEquals("sakura", firstResult)
        assertEquals(0L, coordinator.cacheHits)
        assertEquals(1L, coordinator.cacheMisses)

        // Second call: Cache hit, returns from cache
        val secondResult = coordinator.romanizeLine(input)
        assertEquals("sakura", secondResult)
        assertEquals(1L, coordinator.cacheHits)
        assertEquals(1L, coordinator.cacheMisses)

        // Third call: Another cache hit
        coordinator.romanizeLine(input)
        assertEquals(2L, coordinator.cacheHits)
    }

    @Test
    fun testLruEviction() {
        // Small cache with max size 2
        val coordinator = RomanizationCoordinator(maxCacheSize = 2)

        coordinator.romanizeLine("さくら") // key 1
        coordinator.romanizeLine("ずっと") // key 2
        assertEquals(2, coordinator.getCacheSize())

        coordinator.romanizeLine("とうきょう") // key 3: should evict "さくら"
        assertEquals(2, coordinator.getCacheSize())

        val hitsBefore = coordinator.cacheHits
        coordinator.romanizeLine("さくら") // should be a miss again!
        assertEquals(hitsBefore, coordinator.cacheHits)
    }

    @Test
    fun testRomanizeLrcSynchronizedLines() {
        val coordinator = RomanizationCoordinator()
        val lines = listOf(
            1000L to "さくら",
            3500L to "ずっと忘れない",
            7000L to "Forever with you"
        )

        val romanized = coordinator.romanizeLrc(lines)

        assertEquals(3, romanized.size)
        assertEquals(RomanizedLyricLine("さくら", "sakura", 1000L), romanized[0])
        assertEquals(1000L, romanized[0].timestampMs)
        assertEquals(3500L, romanized[1].timestampMs)
        assertEquals("Forever with you", romanized[2].romanizedText)
        assertEquals(7000L, romanized[2].timestampMs)
    }

    @Test
    fun testDominantLanguageDetectionInLrc() {
        val coordinator = RomanizationCoordinator()
        val japaneseSongLines = listOf(
            "Hello intro",
            "東京", // Pure kanji line, but belongs to Japanese song!
            "ずっと一緒" // Contains kana
        )

        val detected = coordinator.detectDominantLanguage(japaneseSongLines)
        assertEquals(RomanizationLanguage.JAPANESE, detected)
    }
}
