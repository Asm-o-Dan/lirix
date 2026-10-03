package com.example.npc.extract.finance

import com.example.npc.core.model.finance.CurrencyCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BankCurrencyResolverTest {

    @Test
    fun `resolves руб to RUP in PMR bank context (APB and Prisbank)`() {
        val pmrPackages = listOf("com.apb.mobile", "com.prisbank.app", "APB", "PRISBANK")
        val pmrTokens = listOf("руб", "руб.", "р.", "р", "RUP", "rup", "rub")

        for (pkg in pmrPackages) {
            for (token in pmrTokens) {
                val resolved = BankCurrencyResolver.resolve(token, pkg)
                assertEquals(
                    CurrencyCode.RUP,
                    resolved,
                    "Token '$token' in context '$pkg' must resolve to RUP, NOT RUB"
                )
                assertNotEquals(
                    CurrencyCode.RUB,
                    resolved,
                    "PMR currency must never be confused with Russian Ruble"
                )
            }
        }
    }

    @Test
    fun `resolves руб to RUB in Russian bank context (900 and Tinkoff)`() {
        val ruSenders = listOf("900", "SBERBANK", "Tinkoff", "T-Bank")
        val ruTokens = listOf("руб", "руб.", "р.", "₽", "RUB", "rub")

        for (sender in ruSenders) {
            for (token in ruTokens) {
                val resolved = BankCurrencyResolver.resolve(token, sender)
                assertEquals(
                    CurrencyCode.RUB,
                    resolved,
                    "Token '$token' in context '$sender' must resolve to RUB"
                )
            }
        }
    }

    @Test
    fun `resolves MDL, EUR and USD in MAIB context`() {
        val maibPkg = "md.maib.maibank"

        assertEquals(CurrencyCode.MDL, BankCurrencyResolver.resolve("MDL", maibPkg))
        assertEquals(CurrencyCode.MDL, BankCurrencyResolver.resolve("mdl", maibPkg))
        assertEquals(CurrencyCode.MDL, BankCurrencyResolver.resolve("лей", maibPkg))
        assertEquals(CurrencyCode.MDL, BankCurrencyResolver.resolve("lei", maibPkg))
        assertEquals(CurrencyCode.MDL, BankCurrencyResolver.resolve("L", maibPkg))

        assertEquals(CurrencyCode.EUR, BankCurrencyResolver.resolve("EUR", maibPkg))
        assertEquals(CurrencyCode.EUR, BankCurrencyResolver.resolve("€", maibPkg))
        assertEquals(CurrencyCode.EUR, BankCurrencyResolver.resolve("евро", maibPkg))

        assertEquals(CurrencyCode.USD, BankCurrencyResolver.resolve("USD", maibPkg))
        assertEquals(CurrencyCode.USD, BankCurrencyResolver.resolve("$", maibPkg))
        assertEquals(CurrencyCode.USD, BankCurrencyResolver.resolve("долларов", maibPkg))
    }

    @Test
    fun `resolves international currency symbols regardless of context`() {
        assertEquals(CurrencyCode.USD, BankCurrencyResolver.resolve("$", null))
        assertEquals(CurrencyCode.USD, BankCurrencyResolver.resolve("USD", null))
        assertEquals(CurrencyCode.EUR, BankCurrencyResolver.resolve("€", null))
        assertEquals(CurrencyCode.EUR, BankCurrencyResolver.resolve("EUR", null))
    }

    @Test
    fun `returns null for unsupported tokens`() {
        assertNull(BankCurrencyResolver.resolve("XYZ", "com.apb.mobile"))
        assertNull(BankCurrencyResolver.resolve("BTC", null))
        assertNull(BankCurrencyResolver.resolve("", null))
    }
}
