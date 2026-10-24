package com.lirix.app.feature.lyrics.romanization

import java.text.Normalizer
import com.atilika.kuromoji.ipadic.Tokenizer
import dev.esnault.wanakana.core.Wanakana
import com.github.houbb.pinyin.bs.PinyinBs
import com.github.houbb.nlp.common.format.impl.ChineseTsCharFormat
import java.io.ByteArrayInputStream

actual fun createPlatformReadingBackend(): ReadingBackend = JvmReadingBackend

internal data class AnalysisText(val text: String, val sourceRanges: List<ReadingRange>) {
    fun originalRange(start: Int, end: Int): ReadingRange = ReadingRange(
        sourceRanges.getOrNull(start)?.start ?: 0,
        sourceRanges.getOrNull(end - 1)?.endExclusive ?: 0
    )
}

internal fun isHan(cp: Int) = Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN || cp in 0x3400..0x4DBF || cp in 0x20000..0x323AF
internal fun isKana(cp: Int) = Character.UnicodeScript.of(cp) in setOf(Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA) || cp in 0xFF66..0xFF9F || cp == 0x3099 || cp == 0x309A || cp == 0x30FC
internal fun isTarget(cp: Int) = isHan(cp) || isKana(cp)

/** NFKC only on target-script runs; Latin case/width, emoji and all original bytes are retained. */
internal fun normalizeAnalysis(source: String): AnalysisText {
    val out = StringBuilder()
    val ranges = mutableListOf<ReadingRange>()
    var i = 0
    while (i < source.length) {
        val start = i
        val target = isTarget(source.codePointAt(i))
        i += Character.charCount(source.codePointAt(i))
        if (target) while (i < source.length && isTarget(source.codePointAt(i))) i += Character.charCount(source.codePointAt(i))
        val normalized = if (target) Normalizer.normalize(source.substring(start, i), Normalizer.Form.NFKC) else source.substring(start, i)
        out.append(normalized)
        repeat(normalized.length) { ranges += ReadingRange(start, i) }
    }
    return AnalysisText(out.toString(), ranges)
}

/** Versioned lexical supplement to IPADIC; original entries authored for Lirix (GPL-3.0). */
private val lexicalReadings = mapOf(
    "失くす" to "ナクス", "失くして" to "ナクシテ", "失くした" to "ナクシタ",
    "失くさない" to "ナクサナイ", "失くせば" to "ナクセバ", "失くそう" to "ナクソウ",
    "失くし" to "ナクシ", "失くさ" to "ナクサ", "失くせ" to "ナクセ",
    "彷徨う" to "サマヨウ", "彷徨って" to "サマヨッテ", "彷徨った" to "サマヨッタ",
    "彷徨わない" to "サマヨワナイ", "彷徨えば" to "サマヨエバ", "彷徨おう" to "サマヨオウ",
    "彷徨い" to "サマヨイ", "彷徨わ" to "サマヨワ", "彷徨え" to "サマヨエ"
)

/** One lazy dictionary per language. Calls are synchronized for legacy non-service users as well. */
internal object JvmReadingBackend : ReadingBackend {
    override val version = "kuromoji-ipadic-0.9.0+lexicon1/wanakana-1.1.1/houbb-pinyin-0.4.0/normalization2"
    private val japanese by lazy {
        val csv = lexicalReadings.entries.joinToString("\n") { (surface, reading) -> "$surface,$surface,$reading,カスタム動詞" }
        Tokenizer.Builder().userDictionary(ByteArrayInputStream(csv.toByteArray(Charsets.UTF_8))).build() as Tokenizer
    }
    private val chinese by lazy { PinyinBs.newInstance().init() }
    private val chineseSimplifier by lazy { ChineseTsCharFormat() }

    @Synchronized override fun read(text: String, language: RomanizationLanguage, options: ReadingOptions): ReadingResult {
        if (language == RomanizationLanguage.AUTO) return ReadingResult(text, null, ReadingStatus.NEEDS_LANGUAGE, language)
        if (language == RomanizationLanguage.NONE || text.isBlank()) return ReadingResult(text, null, ReadingStatus.NOT_APPLICABLE, language)
        return try {
            val analysis = normalizeAnalysis(text)
            val out = StringBuilder()
            val unresolved = mutableListOf<ReadingRange>()
            var anyTarget = false
            var i = 0
            while (i < analysis.text.length) {
                val start = i
                val cp = analysis.text.codePointAt(i)
                val target = if (language == RomanizationLanguage.JAPANESE) isTarget(cp) else isHan(cp)
                i += Character.charCount(cp)
                if (target) {
                    anyTarget = true
                    while (i < analysis.text.length) {
                        val next = analysis.text.codePointAt(i)
                        if (!(if (language == RomanizationLanguage.JAPANESE) isTarget(next) else isHan(next))) break
                        i += Character.charCount(next)
                    }
                    val run = analysis.text.substring(start, i)
                    val japaneseReading = if (language == RomanizationLanguage.JAPANESE) readJapanesePhrase(run) else null
                    val value = japaneseReading?.first ?: chinese.toPinyin(run.map { chineseSimplifier.format(it) }.joinToString(""))
                    japaneseReading?.second?.forEach { unresolved += analysis.originalRange(start + it.start, start + it.endExclusive) }
                    if (value.codePoints().anyMatch { isHan(it) || isKana(it) }) unresolved += analysis.originalRange(start, i)
                    out.append(if (options.toneMarks || language != RomanizationLanguage.CHINESE) value else ChinesePinyinEngine.removeTones(value))
                } else {
                    // Japanese kana in an explicitly Chinese document is not Mandarin reading.
                    if (language == RomanizationLanguage.CHINESE && isKana(cp)) unresolved += analysis.originalRange(start, i)
                    out.append(analysis.text.substring(start, i))
                }
            }
            ReadingResult(text, out.toString(), when {
                unresolved.isNotEmpty() -> ReadingStatus.PARTIAL
                anyTarget -> ReadingStatus.COMPLETE
                else -> ReadingStatus.NOT_APPLICABLE
            }, language, unresolved.distinct(), version)
        } catch (error: Exception) {
            ReadingResult(text, null, ReadingStatus.UNAVAILABLE, language, backendId = version)
        } catch (error: LinkageError) {
            ReadingResult(text, null, ReadingStatus.UNAVAILABLE, language, backendId = version)
        }
    }

    private fun readJapanesePhrase(text: String): Pair<String, List<ReadingRange>> {
        val tokens = japanese.tokenize(text)
        val unknown = mutableListOf<ReadingRange>()
        val result = StringBuilder()
        var pendingKana = StringBuilder()
        fun flush() {
            if (pendingKana.isNotEmpty()) {
                if (result.isNotEmpty() && !result.endsWith(' ')) result.append(' ')
                result.append(Wanakana.toRomaji(pendingKana.toString()).lowercase())
                pendingKana = StringBuilder()
            }
        }
        for (token in tokens) {
            val reading = token.reading
            if (!token.isKnown && !token.isUser && token.surface.codePoints().anyMatch { isHan(it) }) {
                unknown += ReadingRange(token.position, token.position + token.surface.length)
            }
            if (token.partOfSpeechLevel1 == "助詞" && token.surface in setOf("は", "へ", "を")) {
                flush()
                if (result.isNotEmpty()) result.append(' ')
                result.append(when(token.surface) { "は" -> "wa"; "へ" -> "e"; else -> "o" })
            } else if (reading != "*" && reading.isNotBlank()) {
                // Keep sokuon with the following sound across tokenizer boundaries.
                pendingKana.append(reading)
            } else if (token.surface.codePoints().allMatch { isKana(it) }) {
                pendingKana.append(token.surface)
            } else {
                flush()
                if (result.isNotEmpty()) result.append(' ')
                result.append(token.surface) // unresolved Han remains intact, and classifies PARTIAL above.
            }
        }
        flush()
        return result.toString() to unknown
    }
}
