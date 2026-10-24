package com.lirix.app.feature.lyrics.romanization

/**
 * Target language for lyrics romanization/reading transcription.
 */
enum class RomanizationLanguage {
    JAPANESE,
    CHINESE,
    AUTO,
    NONE
}

/**
 * Visual display mode for lyrics romanization in the player/karaoke UI.
 */
enum class RomanizationDisplayMode {
    ORIGINAL_ONLY,
    READING_ABOVE, // Ruby / furigana-style annotation above the original line
    ROMANIZED_ONLY
}

/**
 * Line model representing a synchronized lyric line with its romanized reading.
 */
data class RomanizedLyricLine(
    val originalText: String,
    val romanizedText: String,
    val timestampMs: Long
)

/**
 * Romanizer engine contract for lyrics transcription.
 */
interface LyricsRomanizer {
    /**
     * Detects if the given text contains Japanese, Chinese, or neither.
     */
    fun detectLanguage(text: String): RomanizationLanguage

    /**
     * Converts a single line of text into romanized representation (Romaji/Pinyin).
     */
    fun romanizeLine(text: String, lang: RomanizationLanguage = RomanizationLanguage.AUTO): String

    /**
     * Converts a list of timestamped lyrics lines into [RomanizedLyricLine] objects.
     */
    fun romanizeLrc(
        lines: List<Pair<Long, String>>,
        lang: RomanizationLanguage = RomanizationLanguage.AUTO
    ): List<RomanizedLyricLine> {
        return lines.map { (timestampMs, text) ->
            RomanizedLyricLine(
                originalText = text,
                romanizedText = romanizeLine(text, lang),
                timestampMs = timestampMs
            )
        }
    }
}
