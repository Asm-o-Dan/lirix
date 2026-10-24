package com.lirix.app.feature.lyrics.romanization

/**
 * Coordinator for lyrics romanization with automatic language detection,
 * engine delegation (JapaneseRomajiEngine / ChinesePinyinEngine),
 * and an in-memory LRU cache.
 *
 * Spec: SPEC-ROMAN-01 / Task: TASK-ROMAN-004
 */
class RomanizationCoordinator(
    private val maxCacheSize: Int = 1000
) : LyricsRomanizer {

    private val cache = LinkedHashMap<String, String>()
    var cacheHits: Long = 0
        private set
    var cacheMisses: Long = 0
        private set

    /**
     * Detects language:
     * - JAPANESE: contains Hiragana (\u3040-\u309F) or Katakana (\u30A0-\u30FF)
     * - CHINESE: ambiguous Han-only text; requires explicit source language
     * - NONE: otherwise (Latin, Cyrillic, etc.)
     */
    override fun detectLanguage(text: String): RomanizationLanguage {
        return resolveDocumentLanguage(listOf(text), RomanizationLanguage.AUTO)
    }

    /**
     * Romanizes a single line of text with caching and language auto-detection.
     * Non-Asian text (English, Russian, digits, symbols) is preserved as-is.
     */
    override fun romanizeLine(
        text: String,
        lang: RomanizationLanguage
    ): String {
        if (text.isBlank()) return text

        val targetLang = if (lang == RomanizationLanguage.AUTO) detectLanguage(text) else lang
        if (targetLang == RomanizationLanguage.NONE || targetLang == RomanizationLanguage.AUTO) {
            return text
        }

        val cacheKey = "${createPlatformReadingBackend().version}:$targetLang:$text"
        val cached = getFromCache(cacheKey)
        if (cached != null) {
            cacheHits++
            return cached
        }

        cacheMisses++
        val romanized = when (targetLang) {
            RomanizationLanguage.JAPANESE -> JapaneseRomajiEngine.toRomaji(text)
            RomanizationLanguage.CHINESE -> ChinesePinyinEngine.toPinyin(text, includeTones = true)
            RomanizationLanguage.NONE, RomanizationLanguage.AUTO -> text
        }

        putInCache(cacheKey, romanized)
        return romanized
    }

    /**
     * Batch conversion for synchronized LRC karaoke lines with timestamps.
     * When lang is AUTO, detects the dominant language across the entire lyrics stream
     * so that Kanji-only lines in a Japanese song are correctly romanized as Japanese Romaji.
     */
    override fun romanizeLrc(
        lines: List<Pair<Long, String>>,
        lang: RomanizationLanguage
    ): List<RomanizedLyricLine> {
        val effectiveSongLang = if (lang == RomanizationLanguage.AUTO) {
            detectDominantLanguage(lines.map { it.second })
        } else {
            lang
        }

        return lines.map { (timestampMs, text) ->
            RomanizedLyricLine(
                originalText = text,
                romanizedText = romanizeLine(text, effectiveSongLang),
                timestampMs = timestampMs
            )
        }
    }

    /**
     * Detects dominant language across a collection of lyrics lines.
     */
    fun detectDominantLanguage(lines: Collection<String>): RomanizationLanguage {
        return resolveDocumentLanguage(lines, RomanizationLanguage.AUTO)
    }

    fun getCacheSize(): Int = cache.size

    fun clearCache() {
        cache.clear()
        cacheHits = 0
        cacheMisses = 0
    }

    private fun getFromCache(key: String): String? {
        val value = cache[key] ?: return null
        cache.remove(key)
        cache[key] = value
        return value
    }

    private fun putInCache(key: String, value: String) {
        cache.remove(key)
        cache[key] = value
        if (cache.size > maxCacheSize) {
            val oldest = cache.keys.firstOrNull()
            if (oldest != null) {
                cache.remove(oldest)
            }
        }
    }

    companion object : LyricsRomanizer {
        val default = RomanizationCoordinator()

        override fun detectLanguage(text: String): RomanizationLanguage = default.detectLanguage(text)
        override fun romanizeLine(text: String, lang: RomanizationLanguage): String = default.romanizeLine(text, lang)
        override fun romanizeLrc(lines: List<Pair<Long, String>>, lang: RomanizationLanguage): List<RomanizedLyricLine> = default.romanizeLrc(lines, lang)
    }
}
