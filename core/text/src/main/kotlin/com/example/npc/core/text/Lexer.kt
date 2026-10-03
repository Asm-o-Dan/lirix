package com.example.npc.core.text

import com.example.npc.core.text.lexicon.CurrencyMatchResult
import com.example.npc.core.text.lexicon.LexiconLoader
import com.example.npc.core.text.lexicon.LexiconRepository
import com.example.npc.core.text.model.KeywordKind
import com.example.npc.core.text.model.NumberInterpretation
import com.example.npc.core.text.model.Token
import com.example.npc.core.text.model.TokenType

typealias CurrencyMatchResult = CurrencyMatchResult
typealias LexiconRepository = LexiconRepository
typealias LexiconLoader = LexiconLoader

/**
 * Линейный детерминированный лексический анализатор за O(N) без регулярных выражений.
 */
interface Lexer {
    /**
     * Линейный лексический анализ нормализованного текста за O(N).
     * @throws IllegalArgumentException при превышении максимальной длины текста (> 4096 символов).
     */
    fun tokenize(text: NormalizedText, lexicon: LexiconRepository = LexiconLoader.default()): TokenStream

    companion object : Lexer {
        private val defaultInstance = FsmLexer()

        override fun tokenize(text: NormalizedText, lexicon: LexiconRepository): TokenStream =
            defaultInstance.tokenize(text, lexicon)

        fun create(): Lexer = FsmLexer()
    }
}

class FsmLexer : Lexer {

    override fun tokenize(text: NormalizedText, lexicon: LexiconRepository): TokenStream {
        val s = text.normalized
        val k = text.keyForm

        if (s.length > 4096) {
            throw IllegalArgumentException(
                "Text length (${s.length}) exceeds maximum allowed 4096 characters"
            )
        }

        if (s.isEmpty()) {
            return DefaultTokenStream(emptyList())
        }

        val tokens = ArrayList<Token>()
        var i = 0

        while (i < s.length) {
            val c = s[i]

            // 1. Whitespace separator
            if (c == ' ') {
                i++
                continue
            }

            // 2. Newline
            if (c == '\n') {
                tokens.add(Token(TokenType.NEWLINE, "\n", TextSpan(i, i + 1)))
                i++
                continue
            }

            // 3. URLs (http://, https://, www.)
            if (k.startsWith("http://", i) || k.startsWith("https://", i) || k.startsWith("www.", i)) {
                val start = i
                while (i < s.length && s[i] != ' ' && s[i] != '\n') {
                    i++
                }
                var end = i
                while (end > start && s[end - 1] in ".,);:!?\"'") {
                    end--
                }
                tokens.add(Token(TokenType.URL, s.substring(start, end), TextSpan(start, end)))
                i = end
                continue
            }

            // 3b. Prefix-less Domain Names (e.g. TEMU.COM, ALIEXPRESS.COM, MAIB.MD)
            val domainSpan = tryParseDomain(s, i)
            if (domainSpan != null) {
                tokens.add(Token(TokenType.URL, s.substring(domainSpan.start, domainSpan.end), domainSpan))
                i = domainSpan.end
                continue
            }

            // 4. Card Masks: "card ...1234", "*1234", "**1234", "**** 1234", "...1234"
            if (k.startsWith("card", i)) {
                val maskSpan = tryParseCardMaskAfterWord(s, i, 4)
                if (maskSpan != null) {
                    tokens.add(Token(TokenType.CARD_MASK, s.substring(maskSpan.start, maskSpan.end), maskSpan))
                    i = maskSpan.end
                    continue
                }
            }

            if (c == '*' || c == '•' || c == '·') {
                val maskSpan = tryParseCardMaskFromSymbols(s, i)
                if (maskSpan != null) {
                    tokens.add(Token(TokenType.CARD_MASK, s.substring(maskSpan.start, maskSpan.end), maskSpan))
                    i = maskSpan.end
                    continue
                }
            }

            if (c == '.' && i + 1 < s.length && s[i + 1] == '.') {
                val maskSpan = tryParseCardMaskFromDots(s, i)
                if (maskSpan != null) {
                    tokens.add(Token(TokenType.CARD_MASK, s.substring(maskSpan.start, maskSpan.end), maskSpan))
                    i = maskSpan.end
                    continue
                }
            }

            // 5. Percent
            if (c == '%') {
                tokens.add(Token(TokenType.PERCENT, "%", TextSpan(i, i + 1)))
                i++
                continue
            }

            // 6. Signs
            if (c == '+' || c == '-' || c == '±') {
                tokens.add(Token(TokenType.SIGN, s.substring(i, i + 1), TextSpan(i, i + 1)))
                i++
                continue
            }

            // 7. Currency symbols
            if (c in "$€£₽") {
                tokens.add(Token(TokenType.CURRENCY, s.substring(i, i + 1), TextSpan(i, i + 1)))
                i++
                continue
            }

            // 8. Digits: TIME, DATE, or NUMBER
            if (c in '0'..'9') {
                // Check TIME: HH:mm[:ss]
                val timeSpan = tryParseTime(s, i)
                if (timeSpan != null) {
                    tokens.add(Token(TokenType.TIME, s.substring(timeSpan.start, timeSpan.end), timeSpan))
                    i = timeSpan.end
                    continue
                }

                // Check DATE: DD.MM.YYYY, DD.MM.YY, DD/MM/YYYY, YYYY-MM-DD
                val dateSpan = tryParseDate(s, i)
                if (dateSpan != null) {
                    tokens.add(Token(TokenType.DATE, s.substring(dateSpan.start, dateSpan.end), dateSpan))
                    i = dateSpan.end
                    continue
                }

                // Parse NUMBER cluster
                val numberToken = parseNumberCluster(s, i)
                tokens.add(numberToken)
                i = numberToken.span.end
                continue
            }

            // 9. Words / Letters (Currencies, Keywords, Words)
            if (Character.isLetter(c)) {
                val start = i
                while (i < s.length && (Character.isLetter(s[i]) || s[i] in '0'..'9')) {
                    i++
                }

                // Check for trailing dot in currency abbreviation (e.g., "р.", "руб.")
                var end = i
                val candidateKey = k.substring(start, end)
                if ((candidateKey == "р" || candidateKey == "руб") &&
                    end < s.length && s[end] == '.' &&
                    (end + 1 == s.length || s[end + 1].isWhitespace() || s[end + 1] in ",;\n")
                ) {
                    end++
                    i = end
                }

                val wordNorm = s.substring(start, end)
                val wordKey = k.substring(start, end)

                val token = classifyWord(wordNorm, wordKey, start, end, lexicon)
                tokens.add(token)
                continue
            }

            // 10. General Punctuation
            tokens.add(Token(TokenType.PUNCT, s.substring(i, i + 1), TextSpan(i, i + 1)))
            i++
        }

        return DefaultTokenStream(tokens)
    }

    private fun tryParseCardMaskAfterWord(s: String, start: Int, wordLen: Int): TextSpan? {
        var p = start + wordLen
        while (p < s.length && s[p] == ' ') p++
        if (p >= s.length) return null

        val maskStart = p
        while (p < s.length && (s[p] == '.' || s[p] == '*' || s[p] == '•' || s[p] == '·' || s[p] == 'X' || s[p] == 'x' || s[p] == ' ')) {
            p++
        }
        if (p == maskStart) return null

        // Must be followed by exactly 4 digits
        if (p + 4 <= s.length &&
            s[p] in '0'..'9' && s[p + 1] in '0'..'9' && s[p + 2] in '0'..'9' && s[p + 3] in '0'..'9' &&
            (p + 4 == s.length || s[p + 4] !in '0'..'9')
        ) {
            return TextSpan(start, p + 4)
        }
        return null
    }

    private fun tryParseCardMaskFromSymbols(s: String, start: Int): TextSpan? {
        var p = start
        while (p < s.length && (s[p] == '*' || s[p] == '•' || s[p] == '·' || s[p] == ' ')) {
            p++
        }
        if (p + 4 <= s.length &&
            s[p] in '0'..'9' && s[p + 1] in '0'..'9' && s[p + 2] in '0'..'9' && s[p + 3] in '0'..'9' &&
            (p + 4 == s.length || s[p + 4] !in '0'..'9')
        ) {
            return TextSpan(start, p + 4)
        }
        return null
    }

    private fun tryParseCardMaskFromDots(s: String, start: Int): TextSpan? {
        var p = start
        while (p < s.length && (s[p] == '.' || s[p] == ' ')) {
            p++
        }
        if (p - start >= 2 && p + 4 <= s.length &&
            s[p] in '0'..'9' && s[p + 1] in '0'..'9' && s[p + 2] in '0'..'9' && s[p + 3] in '0'..'9' &&
            (p + 4 == s.length || s[p + 4] !in '0'..'9')
        ) {
            return TextSpan(start, p + 4)
        }
        return null
    }

    private fun tryParseTime(s: String, start: Int): TextSpan? {
        var p = start
        while (p < s.length && s[p] in '0'..'9') p++
        val digits1 = p - start
        if (digits1 !in 1..2 || p >= s.length || s[p] != ':') return null
        p++ // skip ':'
        val mStart = p
        while (p < s.length && s[p] in '0'..'9') p++
        if (p - mStart != 2) return null

        // Optional seconds: :ss
        if (p + 3 <= s.length && s[p] == ':' && s[p + 1] in '0'..'9' && s[p + 2] in '0'..'9' &&
            (p + 3 == s.length || s[p + 3] !in '0'..'9')
        ) {
            p += 3
        }

        // Boundary check: time should not be followed immediately by a letter
        if (p < s.length && Character.isLetter(s[p])) return null

        return TextSpan(start, p)
    }

    private fun tryParseDate(s: String, start: Int): TextSpan? {
        var p = start
        while (p < s.length && s[p] in '0'..'9') p++
        val firstDigits = p - start

        if (p >= s.length) return null
        val sep = s[p]

        // YYYY-MM-DD
        if (firstDigits == 4 && sep == '-') {
            p++
            val mStart = p
            while (p < s.length && s[p] in '0'..'9') p++
            if (p - mStart != 2 || p >= s.length || s[p] != '-') return null
            p++
            val dStart = p
            while (p < s.length && s[p] in '0'..'9') p++
            if (p - dStart != 2) return null
            return TextSpan(start, p)
        }

        // DD.MM.YYYY or DD/MM/YYYY or DD.MM.YY
        if (firstDigits in 1..2 && (sep == '.' || sep == '/')) {
            val sep1 = sep
            p++
            val mStart = p
            while (p < s.length && s[p] in '0'..'9') p++
            val mDigits = p - mStart
            if (mDigits !in 1..2 || p >= s.length || s[p] != sep1) return null
            p++
            val yStart = p
            while (p < s.length && s[p] in '0'..'9') p++
            val yDigits = p - yStart
            if (yDigits == 4 || yDigits == 2) {
                // Not followed by another separator or digit
                if (p < s.length && (s[p] == sep1 || s[p] in '0'..'9')) return null
                return TextSpan(start, p)
            }
        }

        return null
    }

    private fun parseNumberCluster(s: String, start: Int): Token {
        var p = start
        while (p < s.length && s[p] in '0'..'9') p++

        var hasDecimal = false
        var groupingChar: Char? = null

        while (p < s.length) {
            // Space grouping: only valid in integer part
            if (!hasDecimal && (groupingChar == null || groupingChar == ' ') && s[p] == ' ') {
                if (p + 3 < s.length &&
                    s[p + 1] in '0'..'9' && s[p + 2] in '0'..'9' && s[p + 3] in '0'..'9' &&
                    (p + 4 >= s.length || s[p + 4] !in '0'..'9')
                ) {
                    groupingChar = ' '
                    p += 4
                    continue
                }
            }

            // Dot or comma separator
            if (!hasDecimal && (s[p] == '.' || s[p] == ',') && p + 1 < s.length && s[p + 1] in '0'..'9') {
                val sep = s[p]

                if (groupingChar == ' ') {
                    // Space-grouped number with decimal part: "12 345,67"
                    hasDecimal = true
                    p++
                    while (p < s.length && s[p] in '0'..'9') p++
                    break
                }

                // Check if this separator is followed by another separator: e.g. "1.234,56" or "1.234.567"
                var look = p + 1
                while (look < s.length && s[look] in '0'..'9') look++

                if (look < s.length && (s[look] == '.' || s[look] == ',') && look + 1 < s.length && s[look + 1] in '0'..'9') {
                    groupingChar = sep
                    val nextSep = s[look]
                    if (nextSep != sep) {
                        hasDecimal = true
                    }
                    p = look + 1
                    while (p < s.length && s[p] in '0'..'9') p++
                    break
                } else {
                    // Single separator: e.g. "245.9", "245,90", "1,234"
                    hasDecimal = true
                    p = look
                    break
                }
            }

            break
        }

        val raw = s.substring(start, p)
        val interpretations = generateNumberInterpretations(raw)

        return Token(
            type = TokenType.NUMBER,
            text = raw,
            span = TextSpan(start, p),
            interpretations = interpretations
        )
    }

    private fun generateNumberInterpretations(raw: String): List<NumberInterpretation> {
        val hasSpace = raw.contains(' ')
        val dotCount = raw.count { it == '.' }
        val commaCount = raw.count { it == ',' }

        val interpretations = ArrayList<NumberInterpretation>(2)

        if (hasSpace) {
            // Space is grouping separator. E.g. "12 345,67" or "12 345.67" or "12 345"
            val decSep = if (commaCount == 1) ',' else if (dotCount == 1) '.' else null
            if (decSep != null) {
                val parts = raw.split(decSep)
                val intStr = parts[0].replace(" ", "")
                val fracStr = parts[1]
                val intVal = intStr.toLongOrNull() ?: 0L
                val fracVal = parseFraction(fracStr)
                interpretations.add(
                    NumberInterpretation(raw, intVal, fracVal, decSep, ' ')
                )
            } else {
                val intVal = raw.replace(" ", "").toLongOrNull() ?: 0L
                interpretations.add(
                    NumberInterpretation(raw, intVal, null, null, ' ')
                )
            }
            return interpretations
        }

        if (dotCount == 1 && commaCount == 1) {
            val dotIdx = raw.indexOf('.')
            val commaIdx = raw.indexOf(',')
            if (dotIdx < commaIdx) {
                // "1.234,56" -> Grouping '.', Decimal ','
                val intStr = raw.substring(0, commaIdx).replace(".", "")
                val fracStr = raw.substring(commaIdx + 1)
                val intVal = intStr.toLongOrNull() ?: 0L
                val fracVal = parseFraction(fracStr)
                interpretations.add(
                    NumberInterpretation(raw, intVal, fracVal, ',', '.')
                )
            } else {
                // "1,234.56" -> Grouping ',', Decimal '.'
                val intStr = raw.substring(0, dotIdx).replace(",", "")
                val fracStr = raw.substring(dotIdx + 1)
                val intVal = intStr.toLongOrNull() ?: 0L
                val fracVal = parseFraction(fracStr)
                interpretations.add(
                    NumberInterpretation(raw, intVal, fracVal, '.', ',')
                )
            }
            return interpretations
        }

        if (commaCount == 1 && dotCount == 0) {
            val parts = raw.split(',')
            val intPart = parts[0].toLongOrNull() ?: 0L
            val fracStr = parts[1]

            // If exactly 3 digits, could be grouping (1,234) or decimal (1.234)
            if (fracStr.length == 3) {
                // Hypothesis 1: Grouping comma -> 1234
                val fullInt = (parts[0] + parts[1]).toLongOrNull() ?: 0L
                interpretations.add(
                    NumberInterpretation(raw, fullInt, null, null, ',')
                )
                // Hypothesis 2: Decimal comma -> 1.234
                interpretations.add(
                    NumberInterpretation(raw, intPart, fracStr.toIntOrNull(), ',', null)
                )
            } else {
                // Decimal comma
                val fracVal = parseFraction(fracStr)
                interpretations.add(
                    NumberInterpretation(raw, intPart, fracVal, ',', null)
                )
            }
            return interpretations
        }

        if (dotCount == 1 && commaCount == 0) {
            val parts = raw.split('.')
            val intPart = parts[0].toLongOrNull() ?: 0L
            val fracStr = parts[1]

            if (fracStr.length == 3) {
                // Hypothesis 1: Grouping dot -> 1234
                val fullInt = (parts[0] + parts[1]).toLongOrNull() ?: 0L
                interpretations.add(
                    NumberInterpretation(raw, fullInt, null, null, '.')
                )
                // Hypothesis 2: Decimal dot -> 1.234
                interpretations.add(
                    NumberInterpretation(raw, intPart, fracStr.toIntOrNull(), '.', null)
                )
            } else {
                // Decimal dot
                val fracVal = parseFraction(fracStr)
                interpretations.add(
                    NumberInterpretation(raw, intPart, fracVal, '.', null)
                )
            }
            return interpretations
        }

        if (dotCount > 1 && commaCount == 0) {
            // "1.234.567" -> Grouping dot
            val intVal = raw.replace(".", "").toLongOrNull() ?: 0L
            interpretations.add(
                NumberInterpretation(raw, intVal, null, null, '.')
            )
            return interpretations
        }

        if (commaCount > 1 && dotCount == 0) {
            // "1,234,567" -> Grouping comma
            val intVal = raw.replace(",", "").toLongOrNull() ?: 0L
            interpretations.add(
                NumberInterpretation(raw, intVal, null, null, ',')
            )
            return interpretations
        }

        // Pure integer: "100"
        val intVal = raw.toLongOrNull() ?: 0L
        interpretations.add(
            NumberInterpretation(raw, intVal, null, null, null)
        )
        return interpretations
    }

    private fun parseFraction(fracStr: String): Int {
        return when (fracStr.length) {
            1 -> (fracStr.toIntOrNull() ?: 0) * 10
            2 -> fracStr.toIntOrNull() ?: 0
            else -> fracStr.take(2).toIntOrNull() ?: 0
        }
    }

    private fun classifyWord(
        wordNorm: String,
        wordKey: String,
        start: Int,
        end: Int,
        lexicon: LexiconRepository
    ): Token {
        val span = TextSpan(start, end)

        // 1. Currency lookup via LexiconRepository
        val currencyResult = lexicon.matchCurrency(wordKey)
        if (currencyResult != null) {
            return when (currencyResult) {
                is CurrencyMatchResult.Exact -> Token(type = TokenType.CURRENCY, text = wordNorm, span = span)
                is CurrencyMatchResult.Ambiguous -> Token(
                    type = TokenType.CURRENCY,
                    text = wordNorm,
                    span = span,
                    isCurrencyAmbiguous = true
                )
            }
        }

        // 2. Keyword lookup via LexiconRepository
        val keywordKind = lexicon.matchKeyword(wordKey)
        if (keywordKind != null) {
            return Token(
                type = TokenType.KEYWORD,
                text = wordNorm,
                span = span,
                keywordKind = keywordKind
            )
        }

        // 3. General Word
        return Token(type = TokenType.WORD, text = wordNorm, span = span)
    }

    private fun isAsciiAlphaNumeric(c: Char): Boolean =
        (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9')

    private fun isAsciiAlpha(c: Char): Boolean =
        (c in 'a'..'z') || (c in 'A'..'Z')

    private fun isKnownTld(tld: String): Boolean {
        if (tld.length !in 2..20) return false
        if (tld in EXCLUDED_EXTENSIONS) return false
        if (tld.length in 2..4 && tld.all { it in 'a'..'z' }) return true
        return tld in KNOWN_LONG_TLDS
    }

    private fun tryParseDomain(s: String, start: Int): TextSpan? {
        if (start >= s.length || !isAsciiAlphaNumeric(s[start])) return null

        var p = start
        // First label
        while (p < s.length && (isAsciiAlphaNumeric(s[p]) || s[p] == '-')) {
            p++
        }
        if (p == start || s[p - 1] == '-') return null
        if (p >= s.length || s[p] != '.') return null

        var lastLabelStart = -1
        var lastLabelEnd = -1

        while (p < s.length && s[p] == '.') {
            if (p + 1 >= s.length || s[p + 1] == '.' || s[p + 1].isWhitespace()) {
                break
            }
            if (!isAsciiAlphaNumeric(s[p + 1])) {
                break
            }

            p++ // consume '.'
            val labelStart = p
            while (p < s.length && (isAsciiAlphaNumeric(s[p]) || s[p] == '-')) {
                p++
            }
            if (s[p - 1] == '-') return null

            lastLabelStart = labelStart
            lastLabelEnd = p
        }

        if (lastLabelStart == -1 || lastLabelEnd == -1) return null

        // TLD must consist strictly of ASCII letters
        for (idx in lastLabelStart until lastLabelEnd) {
            if (!isAsciiAlpha(s[idx])) return null
        }

        val tld = s.substring(lastLabelStart, lastLabelEnd).lowercase()
        if (!isKnownTld(tld)) return null

        val domainEnd = lastLabelEnd
        if (domainEnd < s.length) {
            val nextChar = s[domainEnd]
            if (nextChar == '/') {
                // Optional URL path
                var end = domainEnd
                while (end < s.length && s[end] != ' ' && s[end] != '\n') {
                    end++
                }
                while (end > domainEnd && s[end - 1] in ".,);:!?\"'") {
                    end--
                }
                return TextSpan(start, end)
            } else {
                val isValidFollower = nextChar.isWhitespace() || nextChar in ".,);:!?\"'[]{}<>\n\r\t"
                if (!isValidFollower) return null
            }
        }

        return TextSpan(start, domainEnd)
    }

    companion object {
        private val EXCLUDED_EXTENSIONS = hashSetOf(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
            "png", "jpg", "jpeg", "gif", "svg", "webp",
            "txt", "csv", "xml", "json", "html", "htm",
            "zip", "rar", "tar", "gz", "7z", "apk"
        )

        private val KNOWN_LONG_TLDS = hashSetOf(
            "online", "store", "market", "group", "money", "finance",
            "digital", "global", "world", "today", "agency", "center",
            "credit", "direct", "express", "services", "company", "network",
            "systems", "capital", "holdings", "payment", "solutions", "travel"
        )
    }
}
