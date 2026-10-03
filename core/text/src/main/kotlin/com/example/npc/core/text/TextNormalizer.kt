package com.example.npc.core.text

import java.text.Normalizer

interface TextNormalizer {
    /**
     * Нормализует строку, удаляет шумные управляющие символы и строит OffsetMap.
     */
    fun normalize(rawText: String): NormalizedText

    companion object : TextNormalizer {
        private val defaultInstance = DefaultTextNormalizer()

        override fun normalize(rawText: String): NormalizedText =
            defaultInstance.normalize(rawText)

        fun create(): TextNormalizer = DefaultTextNormalizer()
    }
}

class DefaultTextNormalizer : TextNormalizer {

    override fun normalize(rawText: String): NormalizedText {
        if (rawText.isEmpty()) {
            return NormalizedText(
                original = "",
                normalized = "",
                keyForm = "",
                offsetMap = ArrayOffsetMap(
                    originalLength = 0,
                    normalizedLength = 0,
                    charOrigStart = IntArray(0),
                    charOrigEnd = IntArray(0),
                    origToNorm = IntArray(1) { 0 }
                )
            )
        }

        val normalizedBuilder = StringBuilder(rawText.length)
        val charOrigStart = ArrayList<Int>(rawText.length)
        val charOrigEnd = ArrayList<Int>(rawText.length)
        val origToNorm = IntArray(rawText.length + 1)

        var i = 0
        while (i < rawText.length) {
            val origStart = i
            val normChunk: String
            val origEnd: Int

            val c = rawText[i]

            // 1. Line endings: \r\n -> \n, isolated \r -> \n, \n -> \n
            if (c == '\r') {
                if (i + 1 < rawText.length && rawText[i + 1] == '\n') {
                    origEnd = i + 2
                } else {
                    origEnd = i + 1
                }
                normChunk = "\n"
            } else if (c == '\n') {
                origEnd = i + 1
                normChunk = "\n"
            } else if (isSpaceChar(c)) {
                // 2. Unify all types of whitespace (\u00A0, \u202F, \u2009, \t, etc.) to \u0020
                origEnd = i + 1
                normChunk = " "
            } else if (isInvisibleChar(c)) {
                // 3. Remove zero-width & invisible control characters
                origEnd = i + 1
                normChunk = ""
            } else {
                // 4. Unicode grapheme / combining sequence + NFKC normalization
                val cp = rawText.codePointAt(i)
                val count = Character.charCount(cp)
                var next = i + count
                while (next < rawText.length) {
                    val nextCp = rawText.codePointAt(next)
                    val type = Character.getType(nextCp)
                    if (type == Character.NON_SPACING_MARK.toInt() ||
                        type == Character.COMBINING_SPACING_MARK.toInt() ||
                        type == Character.ENCLOSING_MARK.toInt()
                    ) {
                        next += Character.charCount(nextCp)
                    } else {
                        break
                    }
                }
                origEnd = next
                val slice = rawText.substring(origStart, origEnd)
                val nfkc = Normalizer.normalize(slice, Normalizer.Form.NFKC)
                normChunk = sanitizeNfkcChunk(nfkc)
            }

            val currentNormStart = normalizedBuilder.length
            val deltaOrig = origEnd - origStart

            if (normChunk.isEmpty()) {
                for (idx in origStart until origEnd) {
                    origToNorm[idx] = currentNormStart
                }
            } else {
                normalizedBuilder.append(normChunk)
                val currentNormEnd = normalizedBuilder.length
                val deltaNorm = currentNormEnd - currentNormStart

                if (deltaNorm == 1) {
                    charOrigStart.add(origStart)
                    charOrigEnd.add(origEnd)
                } else {
                    for (k in 0 until deltaNorm) {
                        val s = origStart + (k * deltaOrig) / deltaNorm
                        val e = origStart + ((k + 1) * deltaOrig + deltaNorm - 1) / deltaNorm
                        charOrigStart.add(s.coerceIn(origStart, origEnd))
                        charOrigEnd.add(maxOf(s + 1, e.coerceIn(origStart, origEnd)))
                    }
                }

                for (idx in 0 until deltaOrig) {
                    val normPos = currentNormStart + (idx * deltaNorm) / deltaOrig
                    origToNorm[origStart + idx] = normPos.coerceIn(currentNormStart, currentNormEnd)
                }
            }

            i = origEnd
        }

        origToNorm[rawText.length] = normalizedBuilder.length

        val normalized = normalizedBuilder.toString()
        val keyForm = buildKeyForm(normalized)

        val offsetMap = ArrayOffsetMap(
            originalLength = rawText.length,
            normalizedLength = normalized.length,
            charOrigStart = charOrigStart.toIntArray(),
            charOrigEnd = charOrigEnd.toIntArray(),
            origToNorm = origToNorm
        )

        return NormalizedText(
            original = rawText,
            normalized = normalized,
            keyForm = keyForm,
            offsetMap = offsetMap
        )
    }

    private fun sanitizeNfkcChunk(chunk: String): String {
        var hasSpecial = false
        for (idx in chunk.indices) {
            val ch = chunk[idx]
            if (isSpaceChar(ch) || isInvisibleChar(ch)) {
                hasSpecial = true
                break
            }
        }
        if (!hasSpecial) return chunk

        return buildString(chunk.length) {
            for (idx in chunk.indices) {
                val ch = chunk[idx]
                if (isInvisibleChar(ch)) continue
                if (isSpaceChar(ch)) append(' ')
                else append(ch)
            }
        }
    }

    private fun isSpaceChar(c: Char): Boolean {
        return when (c) {
            '\u0020', // standard space
            '\t',     // horizontal tab
            '\u00A0', // no-break space
            '\u202F', // narrow no-break space
            '\u2009', // thin space
            in '\u2000'..'\u200A', // en quad .. hair space
            '\u1680', // ogham space mark
            '\u205F', // medium mathematical space
            '\u3000'  // ideographic space
            -> true
            else -> false
        }
    }

    private fun isInvisibleChar(c: Char): Boolean {
        return when (c) {
            '\u200B', // ZERO WIDTH SPACE
            '\u200C', // ZERO WIDTH NON-JOINER
            '\u200D', // ZERO WIDTH JOINER
            '\uFEFF', // ZERO WIDTH NO-BREAK SPACE / BOM
            '\u00AD', // SOFT HYPHEN
            '\u200E', // LEFT-TO-RIGHT MARK
            '\u200F', // RIGHT-TO-LEFT MARK
            '\u2060'  // WORD JOINER
            -> true
            in '\u202A'..'\u202E' -> true // Bidi embedding / override
            in '\u2066'..'\u2069' -> true // Bidi isolate
            in '\u0000'..'\u0008', '\u000B', '\u000C', in '\u000E'..'\u001F', '\u007F' -> true // ASCII controls
            else -> false
        }
    }

    private fun buildKeyForm(normalized: String): String {
        if (normalized.isEmpty()) return ""
        val chars = CharArray(normalized.length)
        for (idx in normalized.indices) {
            val ch = normalized[idx].lowercaseChar()
            chars[idx] = when (ch) {
                'ă', 'â' -> 'a'
                'î' -> 'i'
                'ș', 'ş' -> 's'
                'ț', 'ţ' -> 't'
                'ё' -> 'е' // Russian Cyrillic \u0435
                else -> ch
            }
        }

        // Homoglyphs: Cyrillic 'М'/'м' in 'MDL'/'mdl'
        for (idx in chars.indices) {
            if (chars[idx] == '\u043C') { // Cyrillic 'м'
                if (idx + 2 < chars.size && chars[idx + 1] == 'd' && chars[idx + 2] == 'l') {
                    chars[idx] = 'm' // Latin 'm'
                }
            } else if (chars[idx] == '\u0440') { // Cyrillic 'р' in 'rub' / 'rup'
                if (idx + 2 < chars.size && chars[idx + 1] == 'u' &&
                    (chars[idx + 2] == 'b' || chars[idx + 2] == 'p')
                ) {
                    chars[idx] = 'r' // Latin 'r'
                }
            }
        }

        return String(chars)
    }
}
