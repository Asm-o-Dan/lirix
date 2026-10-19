package com.lirix.app.translations

import java.text.Normalizer
import java.util.Locale

/** Only presentation suffixes are removed from exact matching; musical versions remain meaningful. */
object TranslationMetadata {
    private val numbers = mapOf("zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20,
        "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)
    private val presentation = Regex("""(?i)\s*[\(\[](?:official(?:\s+(?:music|animated|lyric))?\s*(?:video|audio)?|music video|lyric(?:s| video)?|audio|visuali[sz]er|hd|4k|\d{1,3}(?:th|st|nd|rd) anniversary edition(?:\s*[-–—]\s*official(?: animated)? video)?|(?:\d{4} )?remaster(?:ed)?(?: \d{4})?|(?:feat\.?|ft\.?|featuring)\s+[^)\]]+)[\)\]]""")
    private fun words(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    fun primaryArtist(value: String): String = value.trim()
        .replace(Regex("(?i)(?:\\s*-\\s*topic|\\s+official(?: channel)?|vevo)$"), "")
        .split(Regex("(?i)\\s+(?:feat\\.?|ft\\.?|featuring)\\s+|\\s+[&×]\\s+"), limit = 2).first().trim()
    fun artistKey(value: String): String {
        val base = primaryArtist(value).substringBefore('(').trim().replace(Regex("(?i),\\s*the$"), "")
        val parts = words(base).removePrefix("the ").split(' ')
        val number = numbers[parts.first()]
        return if (number == null) parts.joinToString("") else {
            val unit = parts.getOrNull(1)?.let(numbers::get)?.takeIf { it in 1..9 && number >= 20 && number % 10 == 0 }
            (number + (unit ?: 0)).toString() + parts.drop(if (unit == null) 1 else 2).joinToString("")
        }
    }
    fun title(value: String, artist: String = ""): String {
        var clean = value.trim().replace(presentation, " ")
            .replace(Regex("(?i)\\s+[-–—]\\s+(?:official(?: (?:music|animated|lyric))? (?:video|audio)|lyrics|youtube(?: music)?|spotify)\\s*$"), "")
        val split = clean.split(Regex("\\s+[-–—]\\s+"), limit = 2)
        if (artist.isNotBlank() && split.size == 2 && artistKey(split[0]) == artistKey(artist)) clean = split[1]
        return clean.trim()
    }
    fun titleKey(value: String, artist: String = "") = words(title(value, artist)).replace(" ", "")
    /** Only used after a unique exact catalog artist/title match, never for searching artists. */
    fun artistHeadingMatches(requested: String, heading: String): Boolean {
        val expected = artistKey(requested)
        val actual = artistKey(heading)
        return expected == actual || (expected.length >= 10 && expected.length == actual.length &&
            expected.indices.count { expected[it] != actual[it] } == 1)
    }
    fun baseTitleKey(value: String) = titleKey(value.replace(Regex("""[\(\[][^)\]]*[\)\]]"""), " "))
    fun bucket(artist: String): String? = words(primaryArtist(artist).replace(Regex("(?i),\\s*the$"), ""))
        .removePrefix("the ").firstOrNull()?.takeIf { it in 'a'..'z' || it in '0'..'9' }?.toString()
}
