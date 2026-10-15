package com.lirix.app.feature.lyrics

/**
 * Parser for AmDm.ru chord tabs and lyrics.
 * Extracts chords and lyrics from HTML, strips markup tags,
 * unescapes entities, and preserves original spacing and line alignment.
 *
 * Spec: TASK-LYR-02 / .sdd/specs/lyrics-engine/overview.md#parseAmDmHtml (v1)
 */
object AmDmChordParser {

    private val CHORDS_BLOCK_REGEXES = listOf(
        Regex("""<pre[^>]*itemprop=["']chordsBlock["'][^>]*>([\s\S]*?)<\/pre>""", RegexOption.IGNORE_CASE),
        Regex("""<pre[^>]*>([\s\S]*?)<\/pre>""", RegexOption.IGNORE_CASE),
        Regex("""<div[^>]*class=["'][^"']*b-podbor__text[^"']*["'][^>]*>([\s\S]*?)<\/div>""", RegexOption.IGNORE_CASE)
    )

    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
    private val BR_TAG_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)

    /**
     * Extracts and cleans guitar chords and song lyrics from AmDm.ru HTML page.
     * Returns null if HTML is empty, invalid, or contains no chord block.
     */
    fun parseAmDmHtml(html: String?): String? {
        if (html.isNullOrBlank()) return null

        return runCatching {
            var rawContent: String? = null
            for (regex in CHORDS_BLOCK_REGEXES) {
                val match = regex.find(html)
                if (match != null && match.groupValues.size > 1) {
                    val candidate = match.groupValues[1]
                    if (candidate.isNotBlank()) {
                        rawContent = candidate
                        break
                    }
                }
            }

            if (rawContent == null && (html.contains("podbor__chord") || html.contains("podbor__text"))) {
                rawContent = html
            }

            if (rawContent == null || rawContent.isBlank()) return@runCatching null

            // 1. Convert <br> tags to standard newlines
            var text = BR_TAG_REGEX.replace(rawContent, "\n")

            // 2. Strip all remaining HTML tags (<a ...>, </a>, <span>, etc.)
            text = HTML_TAG_REGEX.replace(text, "")

            // 3. Unescape HTML entities
            text = text
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")

            // 4. Remove optional leading/trailing single newline introduced by <pre> tag layout
            text = text.removePrefix("\r\n").removePrefix("\n")
            text = text.removeSuffix("\r\n").removeSuffix("\n")

            if (text.isBlank()) null else text
        }.getOrNull()
    }

    /**
     * Extracts chords with lyrics from AmDm HTML page preserving chord elements.
     * Spec: TASK-BUG-06
     */
    fun extractChordsWithLyrics(html: String?): String? =
        parseAmDmHtml(html)

    private val CHORD_REGEX = Regex("""\b([A-H][#b]?)(m|maj|min|dim|aug|sus|add|\d)*\b""")

    /**
     * Detects if plain text has chord patterns (Am, Dm, E, etc.) and formats them.
     * Spec: TASK-BUG-06
     */
    fun detectAndFormatInlineChords(text: String): String? {
        if (text.isBlank()) return null
        val hasChords = text.lines().any { line ->
            val words = line.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
            words.isNotEmpty() && words.all { word ->
                CHORD_REGEX.matches(word)
            }
        }
        return if (hasChords) text.trim() else null
    }

    private val ROOT_NOTE_INDICES = mapOf(
        "C" to 0, "B#" to 0,
        "C#" to 1, "DB" to 1,
        "D" to 2,
        "D#" to 3, "EB" to 3,
        "E" to 4, "FB" to 4,
        "F" to 5, "E#" to 5,
        "F#" to 6, "GB" to 6,
        "G" to 7,
        "G#" to 8, "AB" to 8,
        "A" to 9,
        "A#" to 10, "BB" to 10,
        "B" to 11, "H" to 11, "CB" to 11
    )

    private val SHARP_NOTES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    private val FLAT_NOTES = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

    /**
     * Transposes chords in the given text by semitones (-11..+11).
     * Spec: TASK-BUG-06
     */
    fun transpose(chordsText: String, semitones: Int): String {
        if (semitones == 0 || chordsText.isBlank()) return chordsText

        val useFlats = semitones < 0

        return CHORD_REGEX.replace(chordsText) { matchResult ->
            val root = matchResult.groupValues[1]
            val suffix = matchResult.groupValues[2]

            val normalizedRoot = root.uppercase()
            val index = ROOT_NOTE_INDICES[normalizedRoot] ?: return@replace matchResult.value

            val transposedIndex = ((index + semitones) % 12 + 12) % 12
            val newRoot = if (useFlats) FLAT_NOTES[transposedIndex] else SHARP_NOTES[transposedIndex]

            newRoot + suffix
        }
    }
}

/**
 * Top-level signature per TASK-LYR-02 specification.
 */
fun parseAmDmHtml(html: String): String? =
    AmDmChordParser.parseAmDmHtml(html)
