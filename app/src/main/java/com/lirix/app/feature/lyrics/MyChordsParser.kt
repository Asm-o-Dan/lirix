package com.lirix.app.feature.lyrics

import com.lirix.app.feature.MyChordsScrapeResult

/**
 * Parser for MyChords.net chords and song lyrics.
 * Extracts chords and lyrics from HTML, strips markup tags,
 * unescapes entities, and preserves original spacing and line alignment.
 *
 * Spec: TASK-MYCHORDS-01 / .sdd/specs/lyrics-engine/mychords_parser_spec.md
 */
object MyChordsParser {

    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")
    private val BR_TAG_REGEX = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
    private val SUBLINE_START_REGEX = Regex("""<span[^>]*class=["'][^"']*subline[^"']*["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val SUBLINE_END_REGEX = Regex("""</span>""", RegexOption.IGNORE_CASE)
    private val PLINE_START_REGEX = Regex("""<div[^>]*class=["'][^"']*(?:pline|single-line)[^"']*["'][^>]*>""", RegexOption.IGNORE_CASE)
    private val PLINE_END_REGEX = Regex("""</div>""", RegexOption.IGNORE_CASE)

    // Regex to locate the lyrics block container on mychords.net
    private val WORDS_TEXT_REGEXES = listOf(
        Regex("""<div[^>]*class=["'][^"']*w-words__text[^"']*["'][^>]*>([\s\S]*?)</div>\s*(?:<div[^>]*class=["'][^"']*b-words__text-actions|$)""", RegexOption.IGNORE_CASE),
        Regex("""<(?:div|p|span)[^>]*itemprop=["'](?:lyrics|text)["'][^>]*>([\s\S]*?)</(?:div|p|span)>""", RegexOption.IGNORE_CASE),
        Regex("""<div[^>]*class=["'][^"']*w-words__text[^"']*["'][^>]*>([\s\S]*?)(?:<div[^>]*class=["'][^"']*b-words__text-actions|<section|$)""", RegexOption.IGNORE_CASE),
        Regex("""<div[^>]*class=["'][^"']*b-words__centering[^"']*["'][^>]*>([\s\S]*?)</div>\s*</div>""", RegexOption.IGNORE_CASE)
    )

    private val CHORD_REGEX = Regex("""\b([A-H][#b]?)(m|maj|min|dim|aug|sus|add|\d)*\b""")

    /**
     * Extracts plain lyrics and chords with alignment from MyChords HTML.
     */
    fun parseMyChordsHtml(html: String?): MyChordsScrapeResult? {
        if (html.isNullOrBlank()) return null

        return runCatching {
            var rawContent: String? = null
            for (regex in WORDS_TEXT_REGEXES) {
                val match = regex.find(html)
                if (match != null && match.groupValues.size > 1) {
                    val candidate = match.groupValues[1]
                    if (candidate.isNotBlank()) {
                        rawContent = candidate
                        break
                    }
                }
            }

            if (rawContent == null && (html.contains("subline") || html.contains("w-words__text"))) {
                rawContent = html
            }

            if (rawContent == null || rawContent.isBlank()) return@runCatching null

            // 1. Structure conversion:
            // Convert subline/pline blocks to line breaks while preserving inner spaces
            var formatted = rawContent
                // Strip actions block if captured
                .replace(Regex("""(?s)<div[^>]*class=["'][^"']*b-words__text-actions[^"']*["'][^>]*>.*?</div>"""), "")
                // Replace sublines: start preserves indentation, end emits newline
                .let { SUBLINE_START_REGEX.replace(it, "") }
                .let { SUBLINE_END_REGEX.replace(it, "\n") }
                .let { PLINE_START_REGEX.replace(it, "") }
                .let { PLINE_END_REGEX.replace(it, "\n") }
                .let { BR_TAG_REGEX.replace(it, "\n") }

            // 2. Strip remaining HTML tags (like <a ...>, <b>, etc.)
            formatted = HTML_TAG_REGEX.replace(formatted, "")

            // 3. Unescape HTML entities
            formatted = decodeHtmlEntities(formatted)

            // 4. Clean leading/trailing spaces per line, but preserve leading space on chord lines
            val lines = formatted.lines()
            val chordsLines = mutableListOf<String>()
            val plainLyricsLines = mutableListOf<String>()

            for (line in lines) {
                val trimmedLine = line.trimEnd()
                chordsLines.add(trimmedLine)

                val strippedTrim = trimmedLine.trim()
                if (strippedTrim.isEmpty()) {
                    plainLyricsLines.add("")
                    continue
                }

                // Check if the line consists entirely of chords
                val words = strippedTrim.split(Regex("""\s+""")).filter { it.isNotBlank() }
                val isOnlyChords = words.isNotEmpty() && words.all { CHORD_REGEX.matches(it) }

                if (!isOnlyChords) {
                    plainLyricsLines.add(strippedTrim)
                }
            }

            val finalChords = collapseEmptyLines(chordsLines).trim()
            val finalPlain = collapseEmptyLines(plainLyricsLines).trim()

            if (finalPlain.isBlank() && finalChords.isBlank()) return@runCatching null

            MyChordsScrapeResult(
                plainLyrics = if (finalPlain.isNotBlank()) finalPlain else finalChords,
                chords = if (finalChords.isNotBlank()) finalChords else null
            )
        }.getOrNull()
    }

    private fun collapseEmptyLines(lines: List<String>): String {
        val result = mutableListOf<String>()
        var emptyCount = 0
        for (line in lines) {
            if (line.isBlank()) {
                emptyCount++
                if (emptyCount <= 2 && result.isNotEmpty()) {
                    result.add("")
                }
            } else {
                emptyCount = 0
                result.add(line)
            }
        }
        return result.joinToString("\n")
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("""&#(\d+);""")) { match ->
                val code = match.groupValues[1].toIntOrNull()
                if (code != null && code in 1..0x10FFFF) {
                    try {
                        String(Character.toChars(code))
                    } catch (e: Exception) {
                        match.value
                    }
                } else match.value
            }
            .replace(Regex("""&#x([0-9a-fA-F]+);""")) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                if (code != null && code in 1..0x10FFFF) {
                    try {
                        String(Character.toChars(code))
                    } catch (e: Exception) {
                        match.value
                    }
                } else match.value
            }
    }
}
