package com.example.npc.core.storage.dedup

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Instant

class FinancialTransactionDeduplicatorTest {

    private val deduplicator = FinancialTransactionDeduplicator(windowSeconds = 300L)
    private val baseTime = Instant.parse("2026-09-30T10:00:00Z")

    private fun createTxn(
        id: Long = 1L,
        eventId: Long = 101L,
        bank: String = "APB",
        type: TransactionType = TransactionType.CREDIT,
        minor: Long = 22200L,
        currency: CurrencyCode = CurrencyCode.RUP,
        merchant: String? = "Виталий С.",
        accountMask: String? = "*5576",
        occurredAt: Instant = baseTime,
        balance: Money? = null
    ): FinancialTransaction {
        return FinancialTransaction(
            id = id,
            eventId = eventId,
            bank = bank,
            type = type,
            amount = Money(minor, currency),
            balance = balance,
            merchant = merchant,
            accountMask = accountMask,
            status = TransactionStatus.COMPLETED,
            occurredAt = occurredAt,
            extractorId = "apb.push",
            extractorVersion = 1,
            rawText = "Тестовый пуш",
            createdAt = occurredAt
        )
    }

    @Test
    fun `isDuplicate returns true for APB P2P push and processing push within 4 minutes`() {
        val p2pTxn = createTxn(
            id = 1L,
            merchant = "Виталий С.",
            accountMask = "*5576",
            occurredAt = baseTime
        )
        val processingTxn = createTxn(
            id = 2L,
            merchant = null,
            accountMask = "910401******5576",
            occurredAt = baseTime.plusSeconds(240) // 4 minutes later
        )

        deduplicator.isDuplicate(processingTxn, p2pTxn) shouldBe true
        deduplicator.isDuplicate(p2pTxn, processingTxn) shouldBe true
    }

    @Test
    fun `isDuplicate returns false when occurredAt is outside 5-minute window`() {
        val tx1 = createTxn(occurredAt = baseTime)
        val tx2 = createTxn(occurredAt = baseTime.plusSeconds(301))

        deduplicator.isDuplicate(tx1, tx2) shouldBe false
    }

    @Test
    fun `isDuplicate returns false when card mask suffix does not match`() {
        val tx1 = createTxn(accountMask = "*5576")
        val tx2 = createTxn(accountMask = "*1234")

        deduplicator.isDuplicate(tx1, tx2) shouldBe false
    }

    @Test
    fun `isDuplicate returns false when amounts differ`() {
        val tx1 = createTxn(minor = 22200L)
        val tx2 = createTxn(minor = 1515L)

        deduplicator.isDuplicate(tx1, tx2) shouldBe false
    }

    @Test
    fun `isDuplicate returns false when currencies differ`() {
        val tx1 = createTxn(minor = 22200L, currency = CurrencyCode.RUP)
        val tx2 = createTxn(minor = 22200L, currency = CurrencyCode.MDL)

        deduplicator.isDuplicate(tx1, tx2) shouldBe false
    }

    @Test
    fun `isDuplicate returns false when transaction directions differ`() {
        val tx1 = createTxn(type = TransactionType.CREDIT)
        val tx2 = createTxn(type = TransactionType.DEBIT)

        deduplicator.isDuplicate(tx1, tx2) shouldBe false
    }

    @Test
    fun `isDuplicate returns false when banks differ`() {
        val tx1 = createTxn(bank = "APB")
        val tx2 = createTxn(bank = "PRISBANK")

        deduplicator.isDuplicate(tx1, tx2) shouldBe false
    }

    @Test
    fun `isDuplicate matches bank with packageName com apb mobile`() {
        val tx1 = createTxn(bank = "APB")
        val tx2 = createTxn(bank = "com.apb.mobile")

        deduplicator.isDuplicate(tx1, tx2) shouldBe true
    }

    @Test
    fun `merge preserves sender from P2P and detailed mask from processing without doubling amount`() {
        val p2pTxn = createTxn(
            id = 10L,
            eventId = 100L,
            minor = 22200L,
            merchant = "Виталий С.",
            accountMask = "*5576"
        )
        val processingTxn = createTxn(
            id = 11L,
            eventId = 101L,
            minor = 22200L,
            merchant = null,
            accountMask = "910401******5576"
        )

        // Case A: P2P is existing, Processing is incoming
        val mergedA = deduplicator.merge(existing = p2pTxn, incoming = processingTxn)
        mergedA.id shouldBe 10L
        mergedA.eventId shouldBe 100L
        mergedA.amount.minor shouldBe 22200L // not doubled!
        mergedA.merchant shouldBe "Виталий С."
        mergedA.accountMask shouldBe "910401******5576"

        // Case B: Processing is existing, P2P is incoming
        val mergedB = deduplicator.merge(existing = processingTxn, incoming = p2pTxn)
        mergedB.id shouldBe 11L
        mergedB.eventId shouldBe 101L
        mergedB.amount.minor shouldBe 22200L
        mergedB.merchant shouldBe "Виталий С."
        mergedB.accountMask shouldBe "910401******5576"
    }

    @Test
    fun `findDuplicate returns matching duplicate from list`() {
        val existing1 = createTxn(id = 1L, minor = 5000L, accountMask = "*1111")
        val existing2 = createTxn(id = 2L, minor = 22200L, accountMask = "*5576")
        val candidate = createTxn(id = 3L, minor = 22200L, accountMask = "910401******5576")

        val found = deduplicator.findDuplicate(candidate, listOf(existing1, existing2))
        found shouldNotBe null
        found?.id shouldBe 2L
    }

    @Test
    fun `isDuplicate returns true when one transaction has null mask such as SMS and other has card mask`() {
        val smsTxn = createTxn(id = 1L, accountMask = null)
        val pushTxn = createTxn(id = 2L, accountMask = "*5576")

        deduplicator.isDuplicate(smsTxn, pushTxn) shouldBe true
        deduplicator.isDuplicate(pushTxn, smsTxn) shouldBe true
    }

    @Test
    fun `merge preserves template metadata when incoming is extracted from dynamic template`() {
        val staticTxn = createTxn(
            id = 1L,
            merchant = "Sheriff"
        )
        val templateTxn = createTxn(
            id = 2L,
            merchant = "Sheriff-15"
        ).copy(
            extractorKind = com.example.npc.core.model.finance.ExtractorKind.TEMPLATE,
            templateId = "tmpl-apb-sheriff-01",
            extractorId = "template:tmpl-apb-sheriff-01"
        )

        val merged = deduplicator.merge(existing = staticTxn, incoming = templateTxn)
        merged.extractorKind shouldBe com.example.npc.core.model.finance.ExtractorKind.TEMPLATE
        merged.templateId shouldBe "tmpl-apb-sheriff-01"
        merged.extractorId shouldBe "template:tmpl-apb-sheriff-01"
        merged.merchant shouldBe "Sheriff"
    }
}
