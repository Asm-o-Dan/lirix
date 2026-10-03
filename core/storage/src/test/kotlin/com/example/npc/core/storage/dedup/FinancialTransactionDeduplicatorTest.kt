package com.example.npc.core.storage.dedup

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TxStatus
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

    @Test
    fun `same event correction replaces wrong direction amount and bank status`() {
        val existing = createTxn(type = TransactionType.DEBIT, minor = 5000L)
        val incoming = createTxn(type = TransactionType.CREDIT, minor = 2500L).copy(
            status = TransactionStatus.DECLINED,
            txStatus = TxStatus.SUGGESTED
        )
        val result = deduplicator.merge(existing, incoming, replaceFinancialDetails = true)
        result.type shouldBe TransactionType.CREDIT
        result.amount.minor shouldBe 2500L
        result.status shouldBe TransactionStatus.DECLINED
        result.txStatus shouldBe TxStatus.SUGGESTED
        result.id shouldBe existing.id
        result.eventId shouldBe existing.eventId
    }

    @Test
    fun `unresolved replay can remove an earlier guessed direction`() {
        val existing = createTxn(type = TransactionType.DEBIT)
        val incoming = createTxn(type = TransactionType.UNKNOWN).copy(txStatus = TxStatus.SUGGESTED)
        val result = deduplicator.merge(existing, incoming, replaceFinancialDetails = true)
        result.type shouldBe TransactionType.UNKNOWN
        result.txStatus shouldBe TxStatus.SUGGESTED
    }

    @Test
    fun `replay preserves every field of a user confirmed or edited transaction`() {
        for (confirmation in listOf(TxStatus.USER_CONFIRMED, TxStatus.USER_EDITED)) {
            val existing = createTxn(merchant = "My correction", accountMask = "*1234")
                .copy(txStatus = confirmation)
            val incoming = createTxn(type = TransactionType.DEBIT, minor = 900L)
                .copy(status = TransactionStatus.DECLINED)
            deduplicator.merge(existing, incoming, replaceFinancialDetails = true) shouldBe existing
            deduplicator.merge(existing, incoming) shouldBe existing
        }
    }

    @Test
    fun `collapsing an automatic row with a manual row retains the manual values`() {
        val existing = createTxn(id = 1L, eventId = 100L)
        val manual = createTxn(id = 2L, eventId = 200L, merchant = "Edited merchant")
            .copy(txStatus = TxStatus.USER_EDITED)
        val result = deduplicator.merge(existing, manual)
        result.merchant shouldBe "Edited merchant"
        result.txStatus shouldBe TxStatus.USER_EDITED
        result.id shouldBe existing.id
        result.eventId shouldBe existing.eventId
    }

    @Test
    fun `corrected currency cannot retain an incompatible old balance`() {
        val existing = createTxn(balance = Money(9000L, CurrencyCode.RUP))
        val incoming = createTxn(currency = CurrencyCode.USD)
        val result = deduplicator.merge(existing, incoming, replaceFinancialDetails = true)
        result.amount.currency shouldBe CurrencyCode.USD
        result.balance shouldBe null
    }

    @Test
    fun `declined and completed payments are never semantic duplicates`() {
        val completed = createTxn()
        val declined = completed.copy(id = 2L, status = TransactionStatus.DECLINED)
        deduplicator.isDuplicate(completed, declined) shouldBe false
        deduplicator.isDuplicate(declined, completed) shouldBe false
    }

    @Test
    fun `unknown directions do not collapse unrelated equal amounts`() {
        val first = createTxn(type = TransactionType.UNKNOWN)
        val second = first.copy(id = 2L, eventId = 200L)
        deduplicator.isDuplicate(first, second) shouldBe false
    }

    @Test
    fun `same event correction adds and removes refund flag while protecting manual rows`() {
        val existing = createTxn(type = TransactionType.CREDIT)
        val refund = existing.copy(isRefund = true)
        deduplicator.merge(existing, refund, replaceFinancialDetails = true).isRefund shouldBe true
        deduplicator.merge(refund, existing, replaceFinancialDetails = true).isRefund shouldBe false
        val manual = refund.copy(txStatus = TxStatus.USER_EDITED)
        deduplicator.merge(manual, existing, replaceFinancialDetails = true) shouldBe manual
    }

    @Test
    fun `later explicit user edits replace earlier protected values including status and refund`() {
        val original = createTxn().copy(txStatus = TxStatus.USER_CONFIRMED)
        val firstEdit = original.copy(
            amount = Money(900L, CurrencyCode.RUP),
            merchant = "First edit",
            status = TransactionStatus.DECLINED,
            isRefund = true,
            txStatus = TxStatus.USER_EDITED
        )
        val firstSaved = deduplicator.merge(original, firstEdit, replaceFinancialDetails = true)
        firstSaved shouldBe firstEdit

        val secondEdit = firstEdit.copy(
            type = TransactionType.DEBIT,
            amount = Money(1200L, CurrencyCode.RUP),
            merchant = "Second edit",
            status = TransactionStatus.COMPLETED,
            isRefund = false
        )
        val secondSaved = deduplicator.merge(firstSaved, secondEdit, replaceFinancialDetails = true)
        secondSaved shouldBe secondEdit
        deduplicator.merge(secondSaved, createTxn(), replaceFinancialDetails = true) shouldBe secondSaved
        // A different protected notification is not an explicit correction of this event.
        deduplicator.merge(secondSaved, firstEdit) shouldBe secondSaved
    }
}
