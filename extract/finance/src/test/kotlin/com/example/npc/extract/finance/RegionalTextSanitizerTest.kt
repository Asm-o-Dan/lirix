package com.example.npc.extract.finance

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegionalTextSanitizerTest {

    @Test
    fun `sanitize replaces non-breaking space and narrow non-breaking space with regular space`() {
        val input = "Покупка\u00A0на\u202Fсумму 100 RUP"
        val result = RegionalTextSanitizer.sanitize(input)

        assertFalse(result.contains("\u00A0"))
        assertFalse(result.contains("\u202F"))
        assertEquals("Покупка на сумму 100 RUP", result)
    }

    @Test
    fun `sanitize removes invisible zero-width and control characters`() {
        val input = "Банк\u200B\u200C\u200DСообщение\uFEFF\u00AD"
        val result = RegionalTextSanitizer.sanitize(input)

        assertFalse(result.contains("\u200B"))
        assertFalse(result.contains("\u200C"))
        assertFalse(result.contains("\u200D"))
        assertFalse(result.contains("\uFEFF"))
        assertFalse(result.contains("\u00AD"))
        assertEquals("БанкСообщение", result)
    }

    @Test
    fun `sanitize normalizes Romanian cedillas to commas-below`() {
        // Turkish/old Unicode cedillas: ş (U+015F) and ţ (U+0163)
        // Romanian standard comma-below: ș (U+0219) and ț (U+021B)
        val input = "Plată cu cardul în sumă de 100 lei. Refuzată"
        val inputWithCedilla = "Plat\u0103 cu cardul \u00een sum\u0103 de 100 lei. Refuza\u0163\u0103" // with ţ
        val result = RegionalTextSanitizer.sanitize(inputWithCedilla)

        assertFalse(result.contains('\u0163'), "Cedilla ţ should be converted to comma-below ț")
        assertTrue(result.contains('\u021B'), "Should contain comma-below ț")
    }

    @Test
    fun `sanitize truncates input exceeding 1024 characters`() {
        val huge = "A".repeat(5000)
        val result = RegionalTextSanitizer.sanitize(huge)

        assertEquals(1024, result.length, "Sanitizer must strictly cap input length to 1024 characters")
    }

    @Test
    fun `sanitize collapses multiple whitespace into single space and trims edges`() {
        val input = "   Покупка   по   карте   "
        val result = RegionalTextSanitizer.sanitize(input)

        assertEquals("Покупка по карте", result)
    }

    @Test
    fun `sanitize safely handles null or empty input`() {
        assertEquals("", RegionalTextSanitizer.sanitize(null))
        assertEquals("", RegionalTextSanitizer.sanitize(""))
        assertEquals("", RegionalTextSanitizer.sanitize("   "))
    }
}
