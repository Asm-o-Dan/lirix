package com.example.npc.core.model

import com.example.npc.core.model.finance.CurrencyCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class CurrencyCodeTest {

    @Test
    fun `supported currencies can be created and have expected values`() {
        val rup = CurrencyCode(CurrencyCode.CODE_RUP)
        val mdl = CurrencyCode(CurrencyCode.CODE_MDL)
        val rub = CurrencyCode(CurrencyCode.CODE_RUB)
        val eur = CurrencyCode(CurrencyCode.CODE_EUR)
        val usd = CurrencyCode(CurrencyCode.CODE_USD)

        assertEquals("RUP", rup.value)
        assertEquals("MDL", mdl.value)
        assertEquals("RUB", rub.value)
        assertEquals("EUR", eur.value)
        assertEquals("USD", usd.value)
    }

    @Test
    fun `companion constants are correctly initialized`() {
        assertEquals(CurrencyCode("RUP"), CurrencyCode.RUP)
        assertEquals(CurrencyCode("MDL"), CurrencyCode.MDL)
        assertEquals(CurrencyCode("RUB"), CurrencyCode.RUB)
        assertEquals(CurrencyCode("EUR"), CurrencyCode.EUR)
        assertEquals(CurrencyCode("USD"), CurrencyCode.USD)

        assertEquals("RUP", CurrencyCode.CODE_RUP)
        assertEquals("MDL", CurrencyCode.CODE_MDL)
        assertEquals("RUB", CurrencyCode.CODE_RUB)
        assertEquals("EUR", CurrencyCode.CODE_EUR)
        assertEquals("USD", CurrencyCode.CODE_USD)

        assertEquals(
            setOf("RUP", "MDL", "RUB", "EUR", "USD"),
            CurrencyCode.SUPPORTED_CODES
        )
    }

    @Test
    fun `all supported currencies have minorDigits equal to 2`() {
        for (code in CurrencyCode.SUPPORTED_CODES) {
            val currency = CurrencyCode(code)
            assertEquals(2, currency.minorDigits, "minorDigits for $code should be 2")
        }
    }

    @Test
    fun `symbols match regional requirements`() {
        assertEquals("р.", CurrencyCode.RUP.symbol)
        assertEquals("L", CurrencyCode.MDL.symbol)
        assertEquals("₽", CurrencyCode.RUB.symbol)
        assertEquals("€", CurrencyCode.EUR.symbol)
        assertEquals("$", CurrencyCode.USD.symbol)
    }

    @Test
    fun `isIso4217 is false for RUP and true for other currencies`() {
        assertFalse(CurrencyCode.RUP.isIso4217, "RUP is not an ISO-4217 currency")
        assertTrue(CurrencyCode.MDL.isIso4217, "MDL is an ISO-4217 currency")
        assertTrue(CurrencyCode.RUB.isIso4217, "RUB is an ISO-4217 currency")
        assertTrue(CurrencyCode.EUR.isIso4217, "EUR is an ISO-4217 currency")
        assertTrue(CurrencyCode.USD.isIso4217, "USD is an ISO-4217 currency")
    }

    @Test
    fun `of factory is case-insensitive and trims whitespace`() {
        assertEquals(CurrencyCode.RUP, CurrencyCode.of("rup"))
        assertEquals(CurrencyCode.RUP, CurrencyCode.of("  RUP  "))
        assertEquals(CurrencyCode.MDL, CurrencyCode.of("mdl"))
        assertEquals(CurrencyCode.RUB, CurrencyCode.of("Rub"))
        assertEquals(CurrencyCode.EUR, CurrencyCode.of("eur"))
        assertEquals(CurrencyCode.USD, CurrencyCode.of("uSd"))
    }

    @Test
    fun `of factory throws IllegalArgumentException for unsupported codes`() {
        val invalidCodes = listOf("GBP", "JPY", "BTC", "XYZ", "", "   ", "rup1")
        for (code in invalidCodes) {
            val ex = assertThrows<IllegalArgumentException>("Expected IllegalArgumentException for '$code'") {
                CurrencyCode.of(code)
            }
            assertTrue(ex.message?.contains("Unsupported currency code") == true)
        }
    }

    @Test
    fun `constructor directly throws IllegalArgumentException for unsupported codes`() {
        val invalidCodes = listOf("GBP", "JPY", "BTC", "XYZ", "", "rup", "mdl")
        for (code in invalidCodes) {
            assertThrows<IllegalArgumentException>("Direct constructor should throw for '$code'") {
                CurrencyCode(code)
            }
        }
    }

    @Test
    fun `ofOrNull returns CurrencyCode for valid codes and null for invalid or blank`() {
        assertEquals(CurrencyCode.RUP, CurrencyCode.ofOrNull("rup"))
        assertEquals(CurrencyCode.MDL, CurrencyCode.ofOrNull("MDL"))
        assertEquals(CurrencyCode.USD, CurrencyCode.ofOrNull("  usd  "))

        assertNull(CurrencyCode.ofOrNull(null))
        assertNull(CurrencyCode.ofOrNull(""))
        assertNull(CurrencyCode.ofOrNull("   "))
        assertNull(CurrencyCode.ofOrNull("BTC"))
        assertNull(CurrencyCode.ofOrNull("INVALID"))
    }

    @Test
    fun `toString returns currency code value`() {
        assertEquals("RUP", CurrencyCode.RUP.toString())
        assertEquals("MDL", CurrencyCode.MDL.toString())
        assertEquals("USD", CurrencyCode.USD.toString())
    }
}
