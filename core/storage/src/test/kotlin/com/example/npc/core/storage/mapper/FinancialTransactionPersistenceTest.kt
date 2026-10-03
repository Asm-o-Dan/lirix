package com.example.npc.core.storage.mapper

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.model.finance.TxStatus
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class FinancialTransactionPersistenceTest {
    private val transaction = FinancialTransaction(
        eventId = 12L,
        bank = "AnyBank",
        type = TransactionType.UNKNOWN,
        amount = Money(2500, CurrencyCode.USD),
        balance = null,
        merchant = null,
        accountMask = "*1234",
        status = TransactionStatus.DECLINED,
        occurredAt = Instant.ofEpochMilli(1000),
        extractorId = "universal",
        extractorVersion = 1,
        rawText = "Declined 25.00 USD",
        createdAt = Instant.ofEpochMilli(2000),
        txStatus = TxStatus.SUGGESTED,
        isRefund = true
    )

    @Test
    fun `decline unknown direction and every confirmation status survive storage roundtrip`() {
        for (confirmation in TxStatus.entries) {
            val original = transaction.copy(txStatus = confirmation)
            val entity = FinancialTransactionMapper.toEntity(original)
            entity.status shouldBe "DECLINED"
            entity.txStatus shouldBe confirmation.name
            entity.direction shouldBe "UNKNOWN"
            entity.isRefund shouldBe true
            FinancialTransactionMapper.toDomain(entity, original.rawText) shouldBe original
        }
    }

    @Test
    fun `invalid stored direction stays unknown instead of becoming an expense`() {
        val invalid = FinancialTransactionMapper.toEntity(transaction).copy(direction = "garbled")
        FinancialTransactionMapper.toDomain(invalid).type shouldBe TransactionType.UNKNOWN
    }

    @Test
    fun `legacy defaults remain completed and automatically confirmed`() {
        val entity = FinancialTransactionMapper.toEntity(transaction).copy(
            status = "COMPLETED",
            txStatus = "CONFIRMED_AUTO",
            direction = "INCOME"
        )
        val restored = FinancialTransactionMapper.toDomain(entity)
        restored.status shouldBe TransactionStatus.COMPLETED
        restored.txStatus shouldBe TxStatus.CONFIRMED_AUTO
        restored.type shouldBe TransactionType.CREDIT
    }
}
