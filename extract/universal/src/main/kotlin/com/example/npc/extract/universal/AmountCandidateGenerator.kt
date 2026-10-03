package com.example.npc.extract.universal

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.text.TextSpan
import com.example.npc.core.text.TokenStream
import com.example.npc.core.text.model.KeywordKind
import com.example.npc.core.text.model.Token
import com.example.npc.core.text.model.TokenType
import com.example.npc.extract.universal.model.AmountCandidate
import com.example.npc.extract.universal.model.Candidate
import kotlin.math.max
import kotlin.math.min

typealias AmountCandidate = AmountCandidate
typealias Candidate = Candidate

/**
 * Генератор кандидатов на денежную сумму в соответствии с правилом связки с валютой (ADR-302).
 */
interface AmountCandidateGenerator {

    /**
     * Извлекает список кандидатов на сумму из потока токенов с дефолтным разрешением амбивалентных валют.
     */
    fun generate(tokenStream: TokenStream): List<AmountCandidate>

    /**
     * Извлекает список кандидатов на сумму с явным указанием валюты для разрешения амбивалентного "руб".
     */
    fun generate(tokenStream: TokenStream, defaultAmbiguousCurrency: CurrencyCode): List<AmountCandidate>

    /**
     * Извлекает список кандидатов на сумму с учетом пакета приложения-источника.
     */
    fun generate(tokenStream: TokenStream, sourcePackage: String?): List<AmountCandidate>

    companion object : AmountCandidateGenerator by DefaultAmountCandidateGenerator() {
        fun create(): AmountCandidateGenerator = DefaultAmountCandidateGenerator()
    }
}

/**
 * Детерминированная реализация AmountCandidateGenerator за линейное время O(N).
 */
class DefaultAmountCandidateGenerator : AmountCandidateGenerator {

    override fun generate(tokenStream: TokenStream): List<AmountCandidate> =
        generate(tokenStream, defaultAmbiguousCurrency = CurrencyCode.RUP)

    override fun generate(
        tokenStream: TokenStream,
        defaultAmbiguousCurrency: CurrencyCode
    ): List<AmountCandidate> =
        generateInternal(tokenStream, defaultAmbiguousCurrency)

    override fun generate(tokenStream: TokenStream, sourcePackage: String?): List<AmountCandidate> {
        val ambiguousCurrency = resolveAmbiguousBySource(sourcePackage)
        return generateInternal(tokenStream, ambiguousCurrency)
    }

    private fun generateInternal(
        tokenStream: TokenStream,
        defaultAmbiguousCurrency: CurrencyCode
    ): List<AmountCandidate> {
        if (tokenStream.size == 0) return emptyList()

        val candidates = ArrayList<AmountCandidate>()
        val size = tokenStream.size

        for (i in 0 until size) {
            val token = tokenStream[i]
            if (token.type != TokenType.NUMBER) continue

            // Защита от карточных номеров и телефонных номеров:
            // Длинные 12-19 значные числа без десятичной части не являются денежными суммами
            if (isLikelyCardOrPhone(token)) continue

            // Защита от чисел, явно маркированных как OTP-коды (например, "Код: 1234")
            if (isPrecededByOtpMarker(tokenStream, i)) continue

            var currencyTokenIndex = -1
            var distance = -1
            var isPrefix = false

            // 1. Поиск валюты справа (суффиксная форма, дистанция 1 или 2)
            if (i + 1 < size && tokenStream[i + 1].type == TokenType.CURRENCY) {
                currencyTokenIndex = i + 1
                distance = 1
                isPrefix = false
            } else if (i + 2 < size &&
                isPermittedInterveningToken(tokenStream[i + 1]) &&
                tokenStream[i + 2].type == TokenType.CURRENCY
            ) {
                currencyTokenIndex = i + 2
                distance = 2
                isPrefix = false
            }

            // 2. Поиск валюты слева (префиксная форма, если справа не найдено)
            if (currencyTokenIndex == -1) {
                if (i - 1 >= 0 && tokenStream[i - 1].type == TokenType.CURRENCY) {
                    currencyTokenIndex = i - 1
                    distance = 1
                    isPrefix = true
                } else if (i - 2 >= 0 &&
                    isPermittedInterveningToken(tokenStream[i - 1]) &&
                    tokenStream[i - 2].type == TokenType.CURRENCY
                ) {
                    currencyTokenIndex = i - 2
                    distance = 2
                    isPrefix = true
                }
            }

            // Currency-bound constraint (ADR-302): только числа с валютой в пределах 2 токенов
            if (currencyTokenIndex != -1) {
                val currToken = tokenStream[currencyTokenIndex]
                val currencyCode = resolveCurrencyCode(currToken, defaultAmbiguousCurrency) ?: continue
                val minorUnits = computeMinorUnits(token) ?: continue

                val spanStart = min(token.span.start, currToken.span.start)
                val spanEnd = max(token.span.end, currToken.span.end)
                val candidateSpan = TextSpan(spanStart, spanEnd)

                candidates.add(
                    AmountCandidate(
                        id = candidates.size,
                        tokenIndex = i,
                        currencyTokenIndex = currencyTokenIndex,
                        minorUnits = minorUnits,
                        currencyCode = currencyCode,
                        isPrefixCurrency = isPrefix,
                        distanceTokens = distance,
                        span = candidateSpan
                    )
                )
            }
        }

        return candidates
    }

    private fun isPermittedInterveningToken(token: Token): Boolean {
        // Разрешены знаки препинания (двоеточие, тире, слеш) или пробелы/знаки, но строго НЕ переносы строк
        return token.type == TokenType.PUNCT || token.type == TokenType.SIGN
    }

    private fun isLikelyCardOrPhone(token: Token): Boolean {
        val cleanDigits = token.text.filter { it in '0'..'9' }
        // PAN карт (12-19 цифр) или телефонные номера (10-12 цифр без дробной части)
        if (cleanDigits.length >= 10) {
            val hasFraction = token.interpretations.any { it.fractionPart != null }
            if (!hasFraction) return true
        }
        return false
    }

    private fun isPrecededByOtpMarker(tokenStream: TokenStream, index: Int): Boolean {
        // Проверяем 1 или 2 токена назад на маркер OTP
        if (index - 1 >= 0) {
            val prev1 = tokenStream[index - 1]
            if (prev1.keywordKind == KeywordKind.OTP) return true
        }
        if (index - 2 >= 0) {
            val prev1 = tokenStream[index - 1]
            val prev2 = tokenStream[index - 2]
            if (prev2.keywordKind == KeywordKind.OTP && (prev1.type == TokenType.PUNCT || prev1.type == TokenType.SIGN)) {
                return true
            }
        }
        return false
    }

    private fun computeMinorUnits(numberToken: Token): Long? {
        val interpretation = numberToken.interpretations.firstOrNull()
        if (interpretation != null) {
            val intPart = interpretation.integerPart
            val fracPart = interpretation.fractionPart ?: 0
            return try {
                Math.addExact(Math.multiplyExact(intPart, 100L), fracPart.toLong())
            } catch (_: ArithmeticException) {
                null
            }
        }

        // Фоллбэк: если interpretations пустой, чистим число вручную
        val raw = numberToken.text.replace(" ", "").replace(",", ".")
        val doubleVal = raw.toDoubleOrNull() ?: return null
        if (doubleVal < 0.0) return null
        return (doubleVal * 100.0 + 0.5).toLong()
    }

    private fun resolveCurrencyCode(
        currToken: Token,
        defaultAmbiguous: CurrencyCode
    ): CurrencyCode? {
        val raw = currToken.text.trim()
        val lower = raw.lowercase()

        // 1. Однозначные валюты
        when (lower) {
            "mdl", "lei", "leu", "лей", "лея", "леев", "l" -> return CurrencyCode.MDL
            "rup" -> return CurrencyCode.RUP
            "usd", "$", "долл", "долл." -> return CurrencyCode.USD
            "eur", "€" -> return CurrencyCode.EUR
            "rub", "₽" -> return CurrencyCode.RUB
        }

        // 2. Амбивалентные формы ("руб", "руб.", "рубля", "р.")
        if (currToken.isCurrencyAmbiguous || lower in AMBIGUOUS_RUBLE_FORMS) {
            return defaultAmbiguous
        }

        // 3. Прямая проверка по реестру ISO кодов
        return CurrencyCode.ofOrNull(raw.uppercase())
    }

    private fun resolveAmbiguousBySource(sourcePackage: String?): CurrencyCode {
        if (sourcePackage == null) return CurrencyCode.RUP
        return when (sourcePackage) {
            "com.apb.mobile", "com.prisbank.app" -> CurrencyCode.RUP
            else -> CurrencyCode.RUB
        }
    }

    companion object {
        private val AMBIGUOUS_RUBLE_FORMS = setOf("руб", "руб.", "рубля", "рублей", "р.", "р")
    }
}
