package com.example.npc.classify.rules

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FingerprinterTest {

    @Test
    fun `createTemplate collapses numbers into single hash marker`() {
        val input = "Покупка по карте **5576 на сумму 15,15 RUP Баланс 406,86 RUP"
        val template = Fingerprinter.createTemplate(input)

        // Numbers collapsed into #
        assertTrue(template.contains("#"))
        // Template should be lowercase
        assertEquals(template, template.lowercase())
    }

    @Test
    fun `createTemplate unifies NBSP, Narrow NBSP and extra whitespaces to single space`() {
        val inputWithNbsp = "Покупка\u00A0по\u202Fкарте   **5576\nна\tсумму"
        val template = Fingerprinter.createTemplate(inputWithNbsp)

        assertFalse(template.contains("\u00A0"))
        assertFalse(template.contains("\u202F"))
        assertFalse(template.contains("  "))
        assertFalse(template.contains("\t"))
        assertFalse(template.contains("\n"))
    }

    @Test
    fun `calculateFingerprint generates valid 64-char lowercase hex SHA-256`() {
        val fp = Fingerprinter.calculateFingerprint(
            packageName = "com.apb.mobile",
            sender = null,
            text = "Покупка по карте **5576 на сумму 5,47 RUP"
        )

        assertEquals(64, fp.length)
        assertTrue(fp.matches(Regex("^[0-9a-f]{64}$")), "Fingerprint must be 64 lowercase hex characters")
    }

    @Test
    fun `calculateFingerprint is deterministic and produces identical hash for same template`() {
        val fp1 = Fingerprinter.calculateFingerprint(
            packageName = "com.apb.mobile",
            sender = null,
            text = "Покупка по карте **5576 на сумму 5,47 RUP Баланс 422,01 RUP"
        )
        val fp2 = Fingerprinter.calculateFingerprint(
            packageName = "com.apb.mobile",
            sender = null,
            text = "Покупка по карте **1234 на сумму 100,50 RUP Баланс 999,99 RUP"
        )

        // Different amounts and card masks should result in the exact same template and fingerprint!
        assertEquals(fp1, fp2, "Fingerprints must match across dynamic amounts and card numbers")
    }

    @Test
    fun `calculateFingerprint produces identical hash for InTour spam variations`() {
        val spam1 = "InTour Тур агентство ПМР: 499 евро, Турция из Кишинева, вылет 01.10"
        val spam2 = "InTour Тур агентство ПМР: 650 евро, Турция из Кишинева, вылет 15.10"

        val fp1 = Fingerprinter.calculateFingerprint("com.radolyn.ayugram", "InTour", spam1)
        val fp2 = Fingerprinter.calculateFingerprint("com.radolyn.ayugram", "InTour", spam2)

        assertEquals(fp1, fp2, "InTour spam template must be identical across varying prices/dates")
    }

    @Test
    fun `calculateFingerprint safely truncates inputs longer than 1024 characters without error`() {
        val hugeText = "Покупка " + "a".repeat(5000) + " 100 RUP"
        val fp = Fingerprinter.calculateFingerprint("com.apb.mobile", null, hugeText)

        assertEquals(64, fp.length)
        assertTrue(fp.matches(Regex("^[0-9a-f]{64}$")))
    }

    private fun assertFalse(condition: Boolean) = assertTrue(!condition)
}
