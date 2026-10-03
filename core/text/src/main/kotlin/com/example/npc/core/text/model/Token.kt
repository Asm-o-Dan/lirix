package com.example.npc.core.text.model

import com.example.npc.core.text.TextSpan

/**
 * Типы токенов, формируемые лексером.
 */
enum class TokenType {
    NUMBER,
    CURRENCY,
    CARD_MASK,
    KEYWORD,
    DATE,
    TIME,
    WORD,
    PUNCT,
    NEWLINE,
    URL,
    PERCENT,
    SIGN
}

/**
 * Категории семантических ключевых слов из версионируемого лексикона.
 */
enum class KeywordKind {
    DECLINED,
    REFUND,
    CREDIT,
    TRANSFER,
    DEBIT,
    BALANCE,
    OTP,
    PROMO,
    FEE
}

/**
 * Варианты интерпретации числовых кластеров (например, 1,234 -> 1234 или 1.234).
 */
data class NumberInterpretation(
    val rawValue: String,
    val integerPart: Long,
    val fractionPart: Int?, // minor units
    val decimalSeparator: Char?,
    val groupingSeparator: Char?
)

/**
 * Единичный токен из входного потока.
 */
data class Token(
    val type: TokenType,
    val text: String,
    val span: TextSpan,
    val keywordKind: KeywordKind? = null,
    val interpretations: List<NumberInterpretation> = emptyList(),
    val isCurrencyAmbiguous: Boolean = false
) {
    val currencyAmbiguous: Boolean get() = isCurrencyAmbiguous
}
