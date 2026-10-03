package com.example.npc.core.model

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class FinancialTransactionTest {

    private val now = Instant.parse("2026-09-27T10:00:00Z")
    private val validAmount = Money(1500L, CurrencyCode.RUP)
    private val validBalance = Money(50000L, CurrencyCode.RUP)

    @Test
    fun `FinancialTransaction instantiates with valid parameters`() {
        val tx = assertDoesNotThrow {
            FinancialTransaction(
                id = 1L,
                eventId = 42L,
                bank = "APB",
                type = TransactionType.DEBIT,
                amount = validAmount,
                balance = validBalance,
                merchant = "Sheriff",
                accountMask = "*1234",
                status = TransactionStatus.COMPLETED,
                occurredAt = now,
                extractorId = "apb.push",
                extractorVersion = 1,
                rawText = "Payment 15.00 RUP Sheriff"
            )
        }

        assertEquals(1L, tx.id)
        assertEquals(42L, tx.eventId)
        assertEquals("APB", tx.bank)
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals(validAmount, tx.amount)
        assertEquals(validBalance, tx.balance)
        assertEquals("Sheriff", tx.merchant)
        assertEquals("*1234", tx.accountMask)
        assertEquals(TransactionStatus.COMPLETED, tx.status)
        assertEquals(now, tx.occurredAt)
        assertEquals("apb.push", tx.extractorId)
        assertEquals(1, tx.extractorVersion)
        assertEquals("Payment 15.00 RUP Sheriff", tx.rawText)

        // Compatibility getters
        assertEquals(validAmount, tx.money)
        assertEquals("Sheriff", tx.counterparty)
        assertEquals(1, tx.parserVersion)
        assertEquals(now, tx.timestamp)
    }

    @Test
    fun `FinancialTransaction allows null eventId balance merchant accountMask`() {
        val tx = assertDoesNotThrow {
            FinancialTransaction(
                eventId = null,
                bank = "MAIB",
                type = TransactionType.CREDIT,
                amount = Money(1000L, CurrencyCode.MDL),
                balance = null,
                merchant = null,
                accountMask = null,
                status = TransactionStatus.COMPLETED,
                occurredAt = now,
                extractorId = "maib.push",
                extractorVersion = 1,
                rawText = "Incasare 10.00 MDL"
            )
        }

        assertEquals(0L, tx.id)
        assertNull(tx.eventId)
        assertNull(tx.balance)
        assertNull(tx.merchant)
        assertNull(tx.accountMask)
    }

    @Test
    fun `throws IllegalArgumentException on negative id`() {
        assertThrows<IllegalArgumentException> {
            FinancialTransaction(
                id = -1L,
                eventId = null,
                bank = "APB",
                type = TransactionType.DEBIT,
                amount = validAmount,
                balance = null,
                merchant = null,
                accountMask = null,
                status = TransactionStatus.COMPLETED,
                occurredAt = now,
                extractorId = "apb.push",
                extractorVersion = 1,
                rawText = "text"
            )
        }
    }

    @Test
    fun `throws IllegalArgumentException on non-positive eventId`() {
        assertThrows<IllegalArgumentException> {
            FinancialTransaction(
                eventId = 0L,
                bank = "APB",
                type = TransactionType.DEBIT,
                amount = validAmount,
                balance = null,
                merchant = null,
                accountMask = null,
                status = TransactionStatus.COMPLETED,
                occurredAt = now,
                extractorId = "apb.push",
                extractorVersion = 1,
                rawText = "text"
            )
        }

        assertThrows<IllegalArgumentException> {
            FinancialTransaction(
                eventId = -5L,
                bank = "APB",
                type = TransactionType.DEBIT,
                amount = validAmount,
                balance = null,
                merchant = null,
                accountMask = null,
                status = TransactionStatus.COMPLETED,
                occurredAt = now,
                extractorId = "apb.push",
                extractorVersion = 1,
                rawText = "text"
            )
        }
    }

    @Test
    fun `throws IllegalArgumentException on blank bank`() {
        assertThrows<IllegalArgumentException> {
            FinancialTransaction(
                bank = "   ",
                eventId = null,
                type = TransactionType.DEBIT,
                amount = validAmount,
                balance = null,
                merchant = null,
                accountMask = null,
                status = TransactionStatus.COMPLETED,
                occurredAt = now,
                extractorId = "apb.push",
                extractorVersion = 1,
                rawText = "text"
            )
        }
    }

    @Test
    fun `throws IllegalArgumentException on extractorVersion less than 1`() {
        assertThrows<IllegalArgumentException> {
            FinancialTransaction(
                bank = "APB",
                eventId = null,
                type = TransactionType.DEBIT,
                amount = validAmount,
                balance = null,
                merchant = null,
                accountMask = null,
                status = TransactionStatus.COMPLETED,
                occurredAt = now,
                extractorId = "apb.push",
                extractorVersion = 0,
                rawText = "text"
            )
        }
    }

    @Test
    fun `throws IllegalArgumentException on blank rawText`() {
        assertThrows<IllegalArgumentException> {
            FinancialTransaction(
                bank = "APB",
                eventId = null,
                type = TransactionType.DEBIT,
                amount = validAmount,
                balance = null,
                merchant = null,
                accountMask = null,
                status = TransactionStatus.COMPLETED,
                occurredAt = now,
                extractorId = "apb.push",
                extractorVersion = 1,
                rawText = "   "
            )
        }
    }

    @Test
    fun `throws IllegalArgumentException on blank merchant when specified`() {
        assertThrows<IllegalArgumentException> {
            FinancialTransaction(
                bank = "APB",
                eventId = null,
                type = TransactionType.DEBIT,
                amount = validAmount,
                balance = null,
                merchant = "   ",
                accountMask = null,
                status = TransactionStatus.COMPLETED,
                occurredAt = now,
                extractorId = "apb.push",
                extractorVersion = 1,
                rawText = "text"
            )
        }
    }

    @Test
    fun `throws IllegalArgumentException on currency mismatch between amount and balance`() {
        val ex = assertThrows<IllegalArgumentException> {
            FinancialTransaction(
                bank = "APB",
                eventId = null,
                type = TransactionType.DEBIT,
                amount = Money(100L, CurrencyCode.RUP),
                balance = Money(500L, CurrencyCode.MDL),
                merchant = null,
                accountMask = null,
                status = TransactionStatus.COMPLETED,
                occurredAt = now,
                extractorId = "apb.push",
                extractorVersion = 1,
                rawText = "text"
            )
        }
        assert(ex.message?.contains("Balance currency (MDL) must match transaction currency (RUP)") == true)
    }
}
