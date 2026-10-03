package com.example.npc.extract.finance

object RegionalTextSanitizer {

    private const val MAX_INPUT_LENGTH = 1024

    /**
     * Выполняет ReDoS-безопасную предобработку банковского текста:
     * - Замена неразрывных пробелов NBSP (\u00A0) и Narrow NBSP (\u202F) на стандартные пробелы.
     * - Удаление невидимых служебных символов Unicode (Zero-Width Space, Joiners, BOM, Soft Hyphen).
     * - Нормализация румынских диакритик (приведение cedilla ş/ţ к comma-below ș/ț).
     * - Схлопывание множественных пробелов и обрезка по краям.
     * - Жесткое усечение длины до 1024 символов (ReDoS guard).
     */
    fun sanitize(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""

        val input = if (raw.length > MAX_INPUT_LENGTH) raw.substring(0, MAX_INPUT_LENGTH) else raw
        val sb = StringBuilder(input.length)
        var lastWasSpace = false

        for (i in 0 until input.length) {
            val ch = input[i]

            // 1. Фильтрация невидимых и мусорных символов Unicode
            if (isZeroWidthOrGarbage(ch)) {
                continue
            }

            // 2. Унификация пробельных символов и схлопывание
            if (isWhitespace(ch)) {
                if (!lastWasSpace && sb.isNotEmpty()) {
                    sb.append(' ')
                    lastWasSpace = true
                }
                continue
            }

            // 3. Нормализация румынских диакритик (Cedilla -> Comma-below)
            val normalizedChar = normalizeRomanianDiacritic(ch)
            sb.append(normalizedChar)
            lastWasSpace = false
        }

        // Удаление хвостового пробела
        while (sb.isNotEmpty() && sb[sb.length - 1] == ' ') {
            sb.setLength(sb.length - 1)
        }

        return sb.toString()
    }

    private fun isZeroWidthOrGarbage(ch: Char): Boolean = when (ch) {
        '\u200B', // Zero-Width Space
        '\u200C', // Zero-Width Non-Joiner
        '\u200D', // Zero-Width Joiner
        '\uFEFF', // Zero-Width No-Break Space (BOM)
        '\u00AD'  // Soft Hyphen
        -> true
        else -> false
    }

    private fun isWhitespace(ch: Char): Boolean = when (ch) {
        ' ',
        '\t',
        '\r',
        '\n',
        '\u00A0', // Non-Breaking Space (NBSP)
        '\u202F', // Narrow No-Break Space (NNBSP)
        '\u2007', // Figure Space
        '\u2009', // Thin Space
        '\u2002', // En Space
        '\u2003', // Em Space
        '\u3000'  // Ideographic Space
        -> true
        else -> false
    }

    private fun normalizeRomanianDiacritic(ch: Char): Char = when (ch) {
        '\u015E' -> '\u0218' // 'Ş' -> 'Ș'
        '\u015F' -> '\u0219' // 'ş' -> 'ș'
        '\u0162' -> '\u021A' // 'Ţ' -> 'Ț'
        '\u0163' -> '\u021B' // 'ţ' -> 'ț'
        else -> ch
    }
}
