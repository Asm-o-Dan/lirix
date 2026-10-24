package com.lirix.app.feature.lyrics.romanization

/** Original UTF-16 indices; analysis normalization never mutates stored/displayed lyrics. */
data class ReadingRange(val start: Int, val endExclusive: Int)
enum class ReadingStatus { COMPLETE, PARTIAL, NEEDS_LANGUAGE, NOT_APPLICABLE, UNAVAILABLE }
data class ReadingOptions(val toneMarks: Boolean = true, val scheme: String = "hepburn-kana-v1")
data class ReadingResult(
    val originalText: String,
    val readingText: String?,
    val status: ReadingStatus,
    val language: RomanizationLanguage,
    val unresolvedRanges: List<ReadingRange> = emptyList(),
    val backendId: String = "none"
)
data class ReadingSourceLine(val occurrenceId: Int, val timestampMs: Long, val text: String)
data class ReadingDocumentLine(val source: ReadingSourceLine, val result: ReadingResult)
interface ReadingBackend {
    val version: String
    fun read(text: String, language: RomanizationLanguage, options: ReadingOptions): ReadingResult
}
expect fun createPlatformReadingBackend(): ReadingBackend

/** Han alone is ambiguous. NONE means explicit original-only, AUTO means a choice is required. */
fun resolveDocumentLanguage(lines: Collection<String>, override: RomanizationLanguage): RomanizationLanguage {
    if (override != RomanizationLanguage.AUTO) return override
    var hasHan = false
    for (line in lines) {
        var i = 0
        while (i < line.length) {
            val c = line[i]
            val cp = if (c.isHighSurrogate() && i + 1 < line.length && line[i + 1].isLowSurrogate()) {
                i++; 0x10000 + ((c.code - 0xD800) shl 10) + (line[i].code - 0xDC00)
            } else c.code
            if (cp in 0x3040..0x30FF || cp in 0xFF66..0xFF9F) return RomanizationLanguage.JAPANESE
            if (cp in 0x3400..0x9FFF || cp in 0xF900..0xFAFF || cp in 0x20000..0x323AF) hasHan = true
            i++
        }
    }
    return if (hasHan) RomanizationLanguage.AUTO else RomanizationLanguage.NONE
}

data class ReadingCacheKey(
    val original: String, val language: RomanizationLanguage, val options: ReadingOptions,
    val backendVersion: String, val normalizationVersion: String = "kana-nfkc-v1"
)
