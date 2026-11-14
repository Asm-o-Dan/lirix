package com.lirix.app.metadata

import kotlin.math.abs

/**
 * Decides whether a catalogue entry is the song the player reported.
 * Spec: .sdd/specs/media-core/track_metadata_and_avatar_v1.md, section "Сопоставление".
 *
 * Player metadata is imprecise, catalogues transliterate names and keep many versions of one
 * song. An entry is accepted only when the title is the same, an artist agrees and the version
 * (live, remix, acoustic...) is the same. Anything weaker is not a match (AGENTS 7.4).
 */
object TrackMatch {
    const val DURATION_TOLERANCE_MS = 3_000L

    /** Words that make a recording a different version of the song. */
    private val variantWords = setOf(
        "live", "remix", "mix", "acoustic", "instrumental", "karaoke", "cover", "demo", "edit", "unplugged",
        "nightcore", "slowed", "sped", "reverb", "orchestral", "piano", "rework", "bootleg", "mashup",
        "концерт", "концертная", "ремикс", "акустика", "акустическая", "инструментал", "караоке", "кавер", "демо", "live."
    )

    /** Decorations that do not change which recording it is. */
    // Android's ICU regex rejects the Unicode character class flag and \b there is not portable for Cyrillic, so word
    // edges are spelled out as "no letter or digit next to it" (DEFECT-153-01).
    private const val START = """(?<![\p{L}\p{N}])"""
    private const val END = """(?![\p{L}\p{N}])"""

    private val neutralNote = Regex(
        START + """(feat\.?|ft\.?|featuring|with|при уч\.?|при участии|remaster(ed)?|album version|single version|""" +
            """original( version| mix)?|explicit|clean|mono|stereo|bonus track|deluxe|из (к/ф|фильма|сериала|мультфильма)|from|ost|""" +
            """official( video| audio| music video)?|lyrics?( video)?|audio|video|hd|hq|\d{4})""" + END,
        RegexOption.IGNORE_CASE
    )
    private val bracketed = Regex("""[(\[{]([^)\]}]*)[)\]}]""")
    private val dashSuffix = Regex("""\s+[-–—]\s+(.+)$""")
    private val nonWord = Regex("""[^\p{L}\p{Nd}]+""")
    private val artistSplit = Regex(
        """\s*(?:,|;|&|/|\+|""" + START + """feat\.?|""" + START + """ft\.?|""" + START + """featuring""" + END + "|" +
            START + "x" + END + "|" + START + """vs\.?|""" + START + "и" + END + "|" + START + "and" + END + "|" +
            START + """при уч\.?)\s*""",
        RegexOption.IGNORE_CASE
    )

    data class Title(val core: String, val variants: Set<String>)

    fun title(raw: String): Title {
        val variants = mutableSetOf<String>()
        fun note(text: String) { words(text).filterTo(variants) { it in variantWords } }
        var value = raw
        bracketed.findAll(raw).forEach { note(it.groupValues[1]) }
        value = bracketed.replace(value, " ")
        dashSuffix.find(value)?.let { suffix ->
            val text = suffix.groupValues[1]
            // "Song - Live at Wembley", "Song - Remastered 2011": a note; "Artist - Song" is not handled here.
            if (words(text).any { it in variantWords } || neutralNote.containsMatchIn(text)) {
                note(text); value = value.removeRange(suffix.range)
            }
        }
        return Title(words(value).joinToString(" "), variants)
    }

    fun sameTitle(a: String, b: String): Boolean {
        val x = title(a)
        val y = title(b)
        return x.core.isNotEmpty() && x.variants == y.variants && (x.core == y.core || fold(x.core) == fold(y.core))
    }

    fun artists(raw: String): List<String> =
        bracketed.replace(raw, " ").split(artistSplit).map { words(it).joinToString(" ") }.filter { it.isNotEmpty() }

    /** At least one named artist is the same, in the same script or transliterated. */
    fun sameArtist(a: String, b: String): Boolean {
        val x = artists(a)
        val y = artists(b)
        if (x.isEmpty() || y.isEmpty()) return false
        val whole = words(a).joinToString(" ") to words(b).joinToString(" ")
        if (whole.first == whole.second || fold(whole.first) == fold(whole.second)) return true
        return x.any { left -> y.any { right -> left == right || fold(left) == fold(right) } }
    }

    fun sameDuration(aMs: Long?, bMs: Long?): Boolean =
        aMs != null && bMs != null && aMs > 0 && bMs > 0 && abs(aMs - bMs) <= DURATION_TOLERANCE_MS

    private fun words(value: String): List<String> =
        value.lowercase().replace('ё', 'е').split(nonWord).filter { it.isNotEmpty() }

    private val cyrillic = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
        'й' to "i", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s",
        'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch", 'ш' to "sh", 'щ' to "sch", 'ъ' to "",
        'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "u", 'я' to "a", 'і' to "i", 'ї' to "i", 'є' to "e", 'ґ' to "g"
    )
    private val latinFolds = listOf(
        "shch" to "sch", "kh" to "h", "ts" to "c", "tz" to "c", "yu" to "u", "iu" to "u", "ju" to "u", "ya" to "a",
        "ia" to "a", "ja" to "a", "yo" to "e", "jo" to "e", "ye" to "e", "je" to "e", "y" to "i", "j" to "i", "w" to "v",
        "'" to "", "ck" to "k", "x" to "ks"
    )

    /** Script-independent key: Cyrillic is romanized, then common romanization variants are merged. */
    internal fun fold(value: String): String {
        var text = buildString { value.lowercase().forEach { append(cyrillic[it] ?: it.toString()) } }
        latinFolds.forEach { (from, to) -> text = text.replace(from, to) }
        return buildString { text.forEach { if (it != ' ' && (isEmpty() || last() != it)) append(it) } }
    }
}
