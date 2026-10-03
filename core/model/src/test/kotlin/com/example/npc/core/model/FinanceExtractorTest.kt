package com.example.npc.core.model

import com.example.npc.core.model.extract.CurrencyResolver
import com.example.npc.core.model.extract.ExtractorInput
import com.example.npc.core.model.extract.FinanceExtractor
import com.example.npc.core.model.extract.ParsedFinanceResult
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class FinanceExtractorTest {

    private val now = Instant.parse("2026-09-27T10:00:00Z")
    private val dummyResolver = object : CurrencyResolver {
        override fun resolve(token: String, contextPackage: String?): CurrencyCode? =
            CurrencyCode.ofOrNull(token)
    }

    @Test
    fun `ParsedFinanceResult Success holds expected values`() {
        val success = ParsedFinanceResult.Success(
            type = TransactionType.DEBIT,
            amount = Money(1500L, CurrencyCode.RUP),
            balance = Money(50000L, CurrencyCode.RUP),
            merchant = "Sheriff",
            accountMask = "*1234",
            status = TransactionStatus.COMPLETED
        )

        assertEquals(TransactionType.DEBIT, success.type)
        assertEquals(Money(1500L, CurrencyCode.RUP), success.amount)
        assertEquals(Money(50000L, CurrencyCode.RUP), success.balance)
        assertEquals("Sheriff", success.merchant)
        assertEquals("*1234", success.accountMask)
        assertEquals(TransactionStatus.COMPLETED, success.status)
    }

    @Test
    fun `ParsedFinanceResult Declined holds expected values`() {
        val declined = ParsedFinanceResult.Declined(
            reason = "Insufficient funds",
            type = TransactionType.DEBIT,
            amount = Money(2000L, CurrencyCode.MDL),
            merchant = "Linella",
            accountMask = "*5678"
        )

        assertEquals("Insufficient funds", declined.reason)
        assertEquals(TransactionType.DEBIT, declined.type)
        assertEquals(Money(2000L, CurrencyCode.MDL), declined.amount)
        assertEquals("Linella", declined.merchant)
        assertEquals("*5678", declined.accountMask)
    }

    @Test
    fun `ParsedFinanceResult NotApplicable and Failed behave correctly`() {
        val na: Any = ParsedFinanceResult.NotApplicable
        assertTrue(na is ParsedFinanceResult)

        val failed = ParsedFinanceResult.Failed("Parser syntax error")
        assertEquals("Parser syntax error", failed.reason)
    }

    @Test
    fun `FinanceExtractor dummy implementation executes extract method`() {
        val extractor = object : FinanceExtractor {
            override val id: String = "test.extractor"
            override val version: Int = 1
            override val supportedBank: String = "TEST"

            override fun extract(input: ExtractorInput): ParsedFinanceResult {
                return ParsedFinanceResult.NotApplicable
            }
        }

        val input = ExtractorInput(
            text = "Test notification",
            senderOrTitle = "Bank",
            postedAt = now,
            currencyResolver = dummyResolver
        )

        val result = extractor.extract(input)
        assertEquals(ParsedFinanceResult.NotApplicable, result)
        assertEquals("test.extractor", extractor.id)
        assertEquals(1, extractor.version)
        assertEquals("TEST", extractor.supportedBank)
    }
}
