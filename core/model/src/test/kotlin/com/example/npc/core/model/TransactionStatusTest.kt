package com.example.npc.core.model

import com.example.npc.core.model.finance.TransactionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TransactionStatusTest {

    @Test
    fun `enum contains COMPLETED and DECLINED`() {
        val names = TransactionStatus.entries.map { it.name }.toSet()
        assertEquals(setOf("COMPLETED", "DECLINED"), names)
    }

    @Test
    fun `synonym SUCCESS matches COMPLETED`() {
        assertEquals(TransactionStatus.COMPLETED, TransactionStatus.SUCCESS)
    }

    @Test
    fun `fromStringOrDefault maps success variants to COMPLETED`() {
        assertEquals(TransactionStatus.COMPLETED, TransactionStatus.fromStringOrDefault("COMPLETED"))
        assertEquals(TransactionStatus.COMPLETED, TransactionStatus.fromStringOrDefault("completed"))
        assertEquals(TransactionStatus.COMPLETED, TransactionStatus.fromStringOrDefault("SUCCESS"))
        assertEquals(TransactionStatus.COMPLETED, TransactionStatus.fromStringOrDefault("success"))
    }

    @Test
    fun `fromStringOrDefault maps decline markers to DECLINED including regional terms`() {
        assertEquals(TransactionStatus.DECLINED, TransactionStatus.fromStringOrDefault("DECLINED"))
        assertEquals(TransactionStatus.DECLINED, TransactionStatus.fromStringOrDefault("declined"))
        assertEquals(TransactionStatus.DECLINED, TransactionStatus.fromStringOrDefault("REFUZATA"))
        assertEquals(TransactionStatus.DECLINED, TransactionStatus.fromStringOrDefault("refuzata"))
        assertEquals(TransactionStatus.DECLINED, TransactionStatus.fromStringOrDefault("REJECTED"))
        assertEquals(TransactionStatus.DECLINED, TransactionStatus.fromStringOrDefault("rejected"))
        assertEquals(TransactionStatus.DECLINED, TransactionStatus.fromStringOrDefault("FAILED"))
        assertEquals(TransactionStatus.DECLINED, TransactionStatus.fromStringOrDefault("failed"))
    }

    @Test
    fun `fromStringOrDefault returns default for null, blank, or unrecognized strings`() {
        assertEquals(TransactionStatus.COMPLETED, TransactionStatus.fromStringOrDefault(null))
        assertEquals(TransactionStatus.COMPLETED, TransactionStatus.fromStringOrDefault(""))
        assertEquals(TransactionStatus.COMPLETED, TransactionStatus.fromStringOrDefault("   "))
        assertEquals(TransactionStatus.COMPLETED, TransactionStatus.fromStringOrDefault("UNKNOWN_VALUE"))

        assertEquals(
            TransactionStatus.DECLINED,
            TransactionStatus.fromStringOrDefault(null, default = TransactionStatus.DECLINED)
        )
        assertEquals(
            TransactionStatus.DECLINED,
            TransactionStatus.fromStringOrDefault("UNKNOWN", default = TransactionStatus.DECLINED)
        )
    }
}
