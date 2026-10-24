package com.lirix.app.feature.lyrics.romanization

/** Legacy synchronous API for non-UI callers; rendering uses ReadingDocumentService off the main thread. */
object JapaneseRomajiEngine : LyricsRomanizer {
    fun isHiragana(c: Char) = c in '\u3040'..'\u309F'
    fun isKatakana(c: Char) = c in '\u30A0'..'\u30FF' || c in '\uFF66'..'\uFF9F'
    fun isCjkUnifiedIdeograph(c: Char) = c in '\u3400'..'\u9FFF'
    fun containsJapanese(text: String) = text.any { isHiragana(it) || isKatakana(it) }
    override fun detectLanguage(text: String) = resolveDocumentLanguage(listOf(text), RomanizationLanguage.AUTO)
    override fun romanizeLine(text: String, lang: RomanizationLanguage) = toRomaji(text)
    fun toRomaji(text: String): String = createPlatformReadingBackend().read(text, RomanizationLanguage.JAPANESE, ReadingOptions()).readingText ?: text
}
