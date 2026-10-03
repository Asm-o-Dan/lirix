package com.example.npc.feature.replay.engine

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.pipeline.compiler.Signal
import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.effect.EffectKindId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class VirtualEffectEvaluatorTest {

    private val evaluator = VirtualEffectEvaluator()

    @Test
    fun `evaluate on Signal DROP returns UNCLASSIFIED with dropped flag`() {
        val buffer = EffectBuffer(10, 20, 100)
        buffer.begin(EffectKindId.SET_CATEGORY)
        buffer.putLong(Category.FINANCE.ordinal.toLong())
        buffer.putLong(java.lang.Double.doubleToRawLongBits(0.95))
        buffer.end()

        val outcome = evaluator.evaluate(Signal.DROP, buffer, 5000L)

        outcome.isDropped shouldBe true
        outcome.isPassed shouldBe false
        outcome.isFault shouldBe false
        outcome.signal shouldBe Signal.DROP
        outcome.category shouldBe Category.UNCLASSIFIED
        outcome.confidence shouldBe 0.0
        outcome.transaction shouldBe null
        outcome.executionDurationNanos shouldBe 5000L
    }

    @Test
    fun `evaluate extracts SET_CATEGORY and CREATE_FINANCIAL_TRANSACTION effects`() {
        val buffer = EffectBuffer(10, 20, 100)
        val tx = FinancialTransaction(
            id = 1L,
            eventId = 5L,
            bank = "APB",
            type = TransactionType.DEBIT,
            amount = Money(1500L, CurrencyCode.MDL),
            balance = null,
            merchant = "Store",
            accountMask = "*1234",
            status = TransactionStatus.COMPLETED,
            occurredAt = Instant.now(),
            extractorId = "rule",
            extractorVersion = 1,
            rawText = "Spent 15 MDL"
        )

        buffer.begin(EffectKindId.SET_CATEGORY)
        buffer.putLong(Category.FINANCE.ordinal.toLong())
        buffer.putLong(java.lang.Double.doubleToRawLongBits(0.99))
        buffer.end()

        buffer.begin(EffectKindId.CREATE_FINANCIAL_TRANSACTION)
        buffer.putRef(tx)
        buffer.end()

        val outcome = evaluator.evaluate(Signal.PASS, buffer, 12000L)

        outcome.isPassed shouldBe true
        outcome.isDropped shouldBe false
        outcome.isFault shouldBe false
        outcome.category shouldBe Category.FINANCE
        outcome.confidence shouldBe 0.99
        outcome.transaction shouldBe tx
        outcome.executionDurationNanos shouldBe 12000L
    }

    @Test
    fun `evaluate handles empty buffer and invalid ordinal gracefully`() {
        val buffer = EffectBuffer(10, 20, 100)
        val outcomeEmpty = evaluator.evaluate(Signal.PASS, buffer, 1000L)

        outcomeEmpty.category shouldBe Category.UNCLASSIFIED
        outcomeEmpty.confidence shouldBe 0.0
        outcomeEmpty.transaction shouldBe null

        buffer.begin(EffectKindId.SET_CATEGORY)
        buffer.putLong(9999L) // out of bounds
        buffer.putLong(java.lang.Double.doubleToRawLongBits(0.5))
        buffer.end()

        val outcomeInvalid = evaluator.evaluate(Signal.PASS, buffer, 1000L)
        outcomeInvalid.category shouldBe Category.UNCLASSIFIED
    }
}
