package com.lirix.app.feature.lyrics.romanization

/** Legacy synchronous API; dictionary-backed whole phrases, never a single-reading character map. */
object ChinesePinyinEngine : LyricsRomanizer {
    fun isChineseCharacter(c: Char) = c in '\u3400'..'\u9FFF'
    fun containsChinese(text: String) = text.any(::isChineseCharacter)
    override fun detectLanguage(text: String) = resolveDocumentLanguage(listOf(text), RomanizationLanguage.AUTO)
    override fun romanizeLine(text: String, lang: RomanizationLanguage) = toPinyin(text)
    fun toPinyin(text: String, includeTones: Boolean = true): String = createPlatformReadingBackend().read(text, RomanizationLanguage.CHINESE, ReadingOptions(toneMarks = includeTones)).readingText ?: text
    fun removeTones(pinyin: String): String {
        val marked = "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜĀÁǍÀĒÉĚÈĪÍǏÌŌÓǑÒŪÚǓÙǕǗǙǛ"
        val plain = "aaaaeeeeiiiioooouuuuüüüüAAAAEEEEIIIIOOOOUUUUÜÜÜÜ"
        return pinyin.map { c -> marked.indexOf(c).let { if (it >= 0) plain[it] else c } }.joinToString("")
    }
}
