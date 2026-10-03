package com.example.npc.core.model.finance

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Неотрицательная денежная сумма в минимальных неделимых единицах (копейки, центы).
 *
 * Инвариант: minor >= 0L. Знак и направление операции задаются строго через TransactionType.
 * Арифметика защищена от переполнения через Math.addExact / Math.subtractExact.
 */
data class Money(
    val minor: Long,
    val currency: CurrencyCode
) : Comparable<Money> {

    init {
        require(minor >= 0L) {
            "Money minor must be non-negative (got $minor)"
        }
    }

    /** Синоним для совместимости со спеками. */
    val amountMinor: Long get() = minor

    operator fun plus(other: Money): Money {
        checkSameCurrency(other)
        return Money(Math.addExact(minor, other.minor), currency)
    }

    operator fun minus(other: Money): Money {
        checkSameCurrency(other)
        val result = Math.subtractExact(minor, other.minor)
        require(result >= 0L) {
            "Subtraction would result in negative Money amount: $minor - ${other.minor} = $result"
        }
        return Money(result, currency)
    }

    override fun compareTo(other: Money): Int {
        checkSameCurrency(other)
        return minor.compareTo(other.minor)
    }

    fun toMajorBigDecimal(): BigDecimal =
        BigDecimal.valueOf(minor, currency.minorDigits)

    fun formatDisplay(): String {
        val majorStr = toMajorBigDecimal()
            .setScale(currency.minorDigits, RoundingMode.UNNECESSARY)
            .toPlainString()
        return "$majorStr ${currency.symbol}"
    }

    private fun checkSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: expected $currency, got ${other.currency}"
        }
    }

    companion object {
        val ZERO_RUP = Money(0L, CurrencyCode.RUP)
        val ZERO_MDL = Money(0L, CurrencyCode.MDL)
        val ZERO_RUB = Money(0L, CurrencyCode.RUB)
        val ZERO_EUR = Money(0L, CurrencyCode.EUR)
        val ZERO_USD = Money(0L, CurrencyCode.USD)

        fun ofMajor(major: BigDecimal, currency: CurrencyCode): Money {
            require(major.signum() >= 0) { "Major amount must be non-negative: $major" }
            val scaled = major.setScale(currency.minorDigits, RoundingMode.UNNECESSARY)
            return Money(scaled.unscaledValue().longValueExact(), currency)
        }

        fun ofMinor(minor: Long, currency: CurrencyCode): Money = Money(minor, currency)
    }
}
