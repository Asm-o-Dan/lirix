package com.example.npc.ui.timeline

import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.ui.timeline.mapper.EventUiMapper
import com.example.npc.ui.timeline.model.TransactionStatusUi
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Instant

class EventUiMapperTest {

    private val now = Instant.parse("2026-09-27T12:00:00Z")

    private fun createBaseEvent(
        id: Long = 1L,
        category: Category = Category.FINANCE,
        confidence: Double = 0.95,
        engine: Engine = Engine.RULES,
        isUserCorrected: Boolean = false,
        fingerprint: String? = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    ): Event = Event(
        id = id,
        rawId = 100L,
        ts = now,
        title = "Агропромбанк",
        text = "Покупка по карте **5576 на сумму 15,15 RUP Баланс 406,86 RUP",
        normalizedText = "покупка по карте **5576 на сумму 15,15 rup баланс 406,86 rup",
        lang = Lang.RU,
        threadKey = null,
        isUpdateOf = null,
        category = category,
        confidence = confidence.toFloat(),
        engineUsed = engine,
        isUserCorrected = isUserCorrected,
        contentFingerprint = fingerprint
    )

    private fun createBaseTransaction(
        id: Long = 10L,
        eventId: Long? = 1L,
        type: TransactionType = TransactionType.DEBIT,
        minor: Long = 1515L,
        currency: CurrencyCode = CurrencyCode.RUP,
        balanceMinor: Long? = 40686L,
        status: TransactionStatus = TransactionStatus.COMPLETED,
        merchant: String? = "Аптека",
        accountMask: String? = "**5576"
    ): FinancialTransaction = FinancialTransaction(
        id = id,
        eventId = eventId,
        bank = "APB",
        type = type,
        amount = Money(minor, currency),
        balance = balanceMinor?.let { Money(it, currency) },
        merchant = merchant,
        accountMask = accountMask,
        status = status,
        occurredAt = now,
        extractorId = "apb.notification",
        extractorVersion = 1,
        rawText = "Покупка по карте **5576 на сумму 15,15 RUP",
        createdAt = now
    )

    @Test
    fun `toTransactionUiModel formats DEBIT expense with minus sign and RUP symbol`() {
        val txn = createBaseTransaction(
            type = TransactionType.DEBIT,
            minor = 1515L,
            currency = CurrencyCode.RUP,
            balanceMinor = 40686L
        )

        val uiModel = EventUiMapper.toTransactionUiModel(txn)

        uiModel.isExpense shouldBe true
        uiModel.isIncome shouldBe false
        uiModel.isTransfer shouldBe false
        uiModel.formattedAmount shouldBe "-15.15"
        uiModel.currencyCode shouldBe "RUP"
        uiModel.currencySymbol shouldBe "р."
        uiModel.formattedBalance shouldBe "Остаток: 406.86 р."
        uiModel.merchant shouldBe "Аптека"
        uiModel.accountMask shouldBe "**5576"
        uiModel.status shouldBe TransactionStatusUi.COMPLETED
        uiModel.isDeclined shouldBe false
    }

    @Test
    fun `toTransactionUiModel formats CREDIT income with plus sign and MDL symbol`() {
        val txn = createBaseTransaction(
            type = TransactionType.CREDIT,
            minor = 50000L,
            currency = CurrencyCode.MDL,
            balanceMinor = null,
            merchant = "Зарплата"
        )

        val uiModel = EventUiMapper.toTransactionUiModel(txn)

        uiModel.isIncome shouldBe true
        uiModel.isExpense shouldBe false
        uiModel.formattedAmount shouldBe "+500.00"
        uiModel.currencyCode shouldBe "MDL"
        uiModel.currencySymbol shouldBe "L"
        uiModel.formattedBalance shouldBe null
    }

    @Test
    fun `toTransactionUiModel formats TRANSFER without sign`() {
        val txn = createBaseTransaction(
            type = TransactionType.TRANSFER,
            minor = 4000L,
            currency = CurrencyCode.RUP
        )

        val uiModel = EventUiMapper.toTransactionUiModel(txn)

        uiModel.isTransfer shouldBe true
        uiModel.formattedAmount shouldBe "40.00"
    }

    @Test
    fun `toTransactionUiModel formats all supported currency symbols correctly`() {
        val rubTxn = createBaseTransaction(currency = CurrencyCode.RUB)
        EventUiMapper.toTransactionUiModel(rubTxn).currencySymbol shouldBe "₽"

        val eurTxn = createBaseTransaction(currency = CurrencyCode.EUR)
        EventUiMapper.toTransactionUiModel(eurTxn).currencySymbol shouldBe "€"

        val usdTxn = createBaseTransaction(currency = CurrencyCode.USD)
        EventUiMapper.toTransactionUiModel(usdTxn).currencySymbol shouldBe "$"
    }

    @Test
    fun `toTransactionUiModel correctly marks DECLINED transactions and isDeclined flag`() {
        val declinedTxn = createBaseTransaction(
            status = TransactionStatus.DECLINED,
            merchant = "DECLINED: Temu.com"
        )

        val uiModel = EventUiMapper.toTransactionUiModel(declinedTxn)

        uiModel.status shouldBe TransactionStatusUi.DECLINED
        uiModel.isDeclined shouldBe true
    }

    @Test
    fun `toUiModel maps Phase 1 semantic classification and binds transaction`() {
        val event = createBaseEvent(
            id = 1L,
            category = Category.FINANCE,
            confidence = 1.0,
            engine = Engine.PROTOTYPE,
            isUserCorrected = true
        )
        val txn = createBaseTransaction(eventId = 1L)

        val uiModel = EventUiMapper.toUiModel(event = event, transaction = txn)

        uiModel.id shouldBe 1L
        uiModel.category shouldBe Category.FINANCE
        uiModel.confidence shouldBe 1.0f
        uiModel.engineUsed shouldBe Engine.PROTOTYPE
        uiModel.isUserCorrected shouldBe true
        uiModel.isPrototypeDerived shouldBe true
        uiModel.hasFinancialData shouldBe true
        uiModel.financialData shouldNotBe null
        uiModel.financialData?.formattedAmount shouldBe "-15.15"
    }

    @Test
    fun `toUiModel preserves domain category and pipelineRevisionId without heuristics`() {
        val event = Event(
            id = 42L,
            rawId = 100L,
            ts = now,
            title = "Банк", // Previously would have triggered FINANCE via regex heuristic!
            text = "Списание 500р",
            normalizedText = "списание 500р",
            lang = Lang.RU,
            category = Category.ADVERTISEMENT, // Deliberately set to ADVERTISEMENT
            confidence = 0.85f,
            engineUsed = Engine.RULES,
            isUserCorrected = false,
            contentFingerprint = "deadbeef1234",
            pipelineRevisionId = 77L
        )

        val uiModel = EventUiMapper.toUiModel(event = event)

        uiModel.category shouldBe Category.ADVERTISEMENT
        uiModel.confidence shouldBe 0.85f
        uiModel.engineUsed shouldBe Engine.RULES
        uiModel.isUserCorrected shouldBe false
        uiModel.contentFingerprint shouldBe "deadbeef1234"
        uiModel.pipelineRevisionId shouldBe 77L
    }

    @Test
    fun `unknown direction remains unsigned and is neither expense income nor transfer`() {
        val uiModel = EventUiMapper.toTransactionUiModel(createBaseTransaction(type = TransactionType.UNKNOWN))
        uiModel.formattedAmount shouldBe "15.15"
        uiModel.isUnknown shouldBe true
        uiModel.isIncome shouldBe false
        uiModel.isExpense shouldBe false
        uiModel.isTransfer shouldBe false
    }
}
