package com.example.npc.core.model

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `Money allows valid non-negative minor amounts`() {
        val zero = assertDoesNotThrow { Money(0L, CurrencyCode.RUP) }
        assertEquals(0L, zero.minor)
        assertEquals(CurrencyCode.RUP, zero.currency)

        val amount = assertDoesNotThrow { Money(1500L, CurrencyCode.RUP) }
        assertEquals(1500L, amount.minor)
        assertEquals(CurrencyCode.RUP, amount.currency)
    }

    @Test
    fun `Money throws IllegalArgumentException on negative minor amount`() {
        val ex = assertThrows<IllegalArgumentException> {
            Money(-1L, CurrencyCode.RUP)
        }
        assertTrue(ex.message?.contains("non-negative") == true)

        assertThrows<IllegalArgumentException> {
            Money(Long.MIN_VALUE, CurrencyCode.MDL)
        }
    }

    @Test
    fun `plus adds minor amounts for same currency`() {
        val a = Money(100L, CurrencyCode.RUP)
        val b = Money(200L, CurrencyCode.RUP)
        val sum = a + b

        assertEquals(300L, sum.minor)
        assertEquals(CurrencyCode.RUP, sum.currency)
    }

    @Test
    fun `plus throws IllegalArgumentException on currency mismatch`() {
        val rup = Money(100L, CurrencyCode.RUP)
        val mdl = Money(100L, CurrencyCode.MDL)

        val ex = assertThrows<IllegalArgumentException> {
            rup + mdl
        }
        assertTrue(ex.message?.contains("Currency mismatch") == true)
    }

    @Test
    fun `plus throws ArithmeticException on Long overflow`() {
        val huge = Money(Long.MAX_VALUE, CurrencyCode.RUP)
        val one = Money(1L, CurrencyCode.RUP)

        assertThrows<ArithmeticException> {
            huge + one
        }
    }

    @Test
    fun `minus subtracts minor amounts for same currency`() {
        val a = Money(500L, CurrencyCode.RUB)
        val b = Money(200L, CurrencyCode.RUB)
        val diff = a - b

        assertEquals(300L, diff.minor)
        assertEquals(CurrencyCode.RUB, diff.currency)

        val exactZero = b - b
        assertEquals(0L, exactZero.minor)
        assertEquals(CurrencyCode.RUB, exactZero.currency)
    }

    @Test
    fun `minus throws IllegalArgumentException when result would be negative`() {
        val a = Money(100L, CurrencyCode.RUB)
        val b = Money(200L, CurrencyCode.RUB)

        val ex = assertThrows<IllegalArgumentException> {
            a - b
        }
        assertTrue(ex.message?.contains("negative") == true)
    }

    @Test
    fun `minus throws IllegalArgumentException on currency mismatch`() {
        val rub = Money(500L, CurrencyCode.RUB)
        val eur = Money(200L, CurrencyCode.EUR)

        assertThrows<IllegalArgumentException> {
            rub - eur
        }
    }

    @Test
    fun `compareTo correctly orders Money of same currency`() {
        val small = Money(100L, CurrencyCode.USD)
        val large = Money(200L, CurrencyCode.USD)
        val equal = Money(100L, CurrencyCode.USD)

        assertTrue(small < large)
        assertTrue(large > small)
        assertTrue(small <= equal)
        assertTrue(small >= equal)
        assertEquals(0, small.compareTo(equal))
    }

    @Test
    fun `compareTo throws IllegalArgumentException on currency mismatch`() {
        val usd = Money(100L, CurrencyCode.USD)
        val eur = Money(100L, CurrencyCode.EUR)

        assertThrows<IllegalArgumentException> {
            usd.compareTo(eur)
        }
    }

    @Test
    fun `toMajorBigDecimal formats major units correctly`() {
        assertEquals(BigDecimal("15.50"), Money(1550L, CurrencyCode.RUP).toMajorBigDecimal())
        assertEquals(BigDecimal("0.05"), Money(5L, CurrencyCode.MDL).toMajorBigDecimal())
        assertEquals(BigDecimal("0.00"), Money(0L, CurrencyCode.USD).toMajorBigDecimal())
        assertEquals(BigDecimal("1234.56"), Money(123456L, CurrencyCode.EUR).toMajorBigDecimal())
    }

    @Test
    fun `formatDisplay returns string with two decimal places and currency symbol`() {
        assertEquals("15.50 р.", Money(1550L, CurrencyCode.RUP).formatDisplay())
        assertEquals("49.00 $", Money(4900L, CurrencyCode.USD).formatDisplay())
        assertEquals("10.00 L", Money(1000L, CurrencyCode.MDL).formatDisplay())
        assertEquals("2.50 ₽", Money(250L, CurrencyCode.RUB).formatDisplay())
        assertEquals("9.99 €", Money(999L, CurrencyCode.EUR).formatDisplay())
        assertEquals("0.00 р.", Money(0L, CurrencyCode.RUP).formatDisplay())
    }

    @Test
    fun `ofMajor factory converts BigDecimal to Money in minor units`() {
        val rup = Money.ofMajor(BigDecimal("15.50"), CurrencyCode.RUP)
        assertEquals(1550L, rup.minor)
        assertEquals(CurrencyCode.RUP, rup.currency)

        val usd = Money.ofMajor(BigDecimal("10.50"), CurrencyCode.USD)
        assertEquals(1050L, usd.minor)
        assertEquals(CurrencyCode.USD, usd.currency)

        val zero = Money.ofMajor(BigDecimal("0.00"), CurrencyCode.EUR)
        assertEquals(0L, zero.minor)
        assertEquals(CurrencyCode.EUR, zero.currency)
    }

    @Test
    fun `ofMajor throws IllegalArgumentException for negative BigDecimal`() {
        assertThrows<IllegalArgumentException> {
            Money.ofMajor(BigDecimal("-0.01"), CurrencyCode.RUB)
        }
    }

    @Test
    fun `ofMinor factory returns Money with exact minor units`() {
        val money = Money.ofMinor(2500L, CurrencyCode.MDL)
        assertEquals(2500L, money.minor)
        assertEquals(CurrencyCode.MDL, money.currency)
    }

    @Test
    fun `zero constants in companion object have 0 minor and respective currency`() {
        assertEquals(Money(0L, CurrencyCode.RUP), Money.ZERO_RUP)
        assertEquals(Money(0L, CurrencyCode.MDL), Money.ZERO_MDL)
        assertEquals(Money(0L, CurrencyCode.RUB), Money.ZERO_RUB)
        assertEquals(Money(0L, CurrencyCode.EUR), Money.ZERO_EUR)
        assertEquals(Money(0L, CurrencyCode.USD), Money.ZERO_USD)
    }
}
