package com.example.npc.core.model.finance

/**
 * Закрытый реестр поддерживаемых валют.
 * Приднестровский рубль (RUP) изолирован от java.util.Currency во избежание сбоев в Android рантайме.
 */
@JvmInline
value class CurrencyCode(val value: String) {

    init {
        require(value in SUPPORTED_CODES) {
            "Unsupported currency code: '$value'. Supported codes are: $SUPPORTED_CODES"
        }
    }

    /** Количество знаков дробной части (копейки, центы). Равно 2 для всех валют системы. */
    val minorDigits: Int get() = 2

    /** Общепринятый символ валюты для форматирования в UI. */
    val symbol: String
        get() = when (value) {
            CODE_RUP -> "р."
            CODE_MDL -> "L"
            CODE_RUB -> "₽"
            CODE_EUR -> "€"
            CODE_USD -> "$"
            else -> value
        }

    /** Признак соответствия ISO-4217 (RUP не входит в ISO-4217). */
    val isIso4217: Boolean get() = value != CODE_RUP

    override fun toString(): String = value

    companion object {
        const val CODE_RUP = "RUP" // Приднестровский рубль (ПМР)
        const val CODE_MDL = "MDL" // Молдавский лей
        const val CODE_RUB = "RUB" // Российский рубль
        const val CODE_EUR = "EUR" // Евро
        const val CODE_USD = "USD" // Доллар США

        val SUPPORTED_CODES: Set<String> = setOf(CODE_RUP, CODE_MDL, CODE_RUB, CODE_EUR, CODE_USD)

        val RUP = CurrencyCode(CODE_RUP)
        val MDL = CurrencyCode(CODE_MDL)
        val RUB = CurrencyCode(CODE_RUB)
        val EUR = CurrencyCode(CODE_EUR)
        val USD = CurrencyCode(CODE_USD)

        fun of(code: String): CurrencyCode = CurrencyCode(code.trim().uppercase())

        fun ofOrNull(code: String?): CurrencyCode? {
            if (code.isNullOrBlank()) return null
            val upper = code.trim().uppercase()
            return if (upper in SUPPORTED_CODES) CurrencyCode(upper) else null
        }
    }
}
