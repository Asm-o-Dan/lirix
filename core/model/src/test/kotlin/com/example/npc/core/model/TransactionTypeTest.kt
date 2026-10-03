package com.example.npc.core.model

import com.example.npc.core.model.finance.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class TransactionTypeTest {

    @Test
    fun `enum contains DEBIT, CREDIT, and TRANSFER`() {
        val names = TransactionType.entries.map { it.name }.toSet()
        assertEquals(setOf("DEBIT", "CREDIT", "TRANSFER"), names)
    }

    @Test
    fun `synonyms EXPENSE and INCOME match DEBIT and CREDIT`() {
        assertEquals(TransactionType.DEBIT, TransactionType.EXPENSE)
        assertEquals(TransactionType.CREDIT, TransactionType.INCOME)
    }

    @Test
    fun `fromStringOrNull maps DEBIT and EXPENSE case-insensitively`() {
        assertEquals(TransactionType.DEBIT, TransactionType.fromStringOrNull("DEBIT"))
        assertEquals(TransactionType.DEBIT, TransactionType.fromStringOrNull("debit"))
        assertEquals(TransactionType.DEBIT, TransactionType.fromStringOrNull("Debit"))
        assertEquals(TransactionType.DEBIT, TransactionType.fromStringOrNull("EXPENSE"))
        assertEquals(TransactionType.DEBIT, TransactionType.fromStringOrNull("expense"))
        assertEquals(TransactionType.DEBIT, TransactionType.fromStringOrNull("Expense"))
    }

    @Test
    fun `fromStringOrNull maps CREDIT and INCOME case-insensitively`() {
        assertEquals(TransactionType.CREDIT, TransactionType.fromStringOrNull("CREDIT"))
        assertEquals(TransactionType.CREDIT, TransactionType.fromStringOrNull("credit"))
        assertEquals(TransactionType.CREDIT, TransactionType.fromStringOrNull("INCOME"))
        assertEquals(TransactionType.CREDIT, TransactionType.fromStringOrNull("income"))
    }

    @Test
    fun `fromStringOrNull maps TRANSFER case-insensitively`() {
        assertEquals(TransactionType.TRANSFER, TransactionType.fromStringOrNull("TRANSFER"))
        assertEquals(TransactionType.TRANSFER, TransactionType.fromStringOrNull("transfer"))
        assertEquals(TransactionType.TRANSFER, TransactionType.fromStringOrNull("Transfer"))
    }

    @Test
    fun `fromStringOrNull returns null for invalid or blank strings`() {
        assertNull(TransactionType.fromStringOrNull(null))
        assertNull(TransactionType.fromStringOrNull(""))
        assertNull(TransactionType.fromStringOrNull("   "))
        assertNull(TransactionType.fromStringOrNull("UNKNOWN"))
        assertNull(TransactionType.fromStringOrNull("WITHDRAW"))
    }
}
