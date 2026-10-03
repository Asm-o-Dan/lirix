package com.example.npc.extract.finance

import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.finance.*
import com.example.npc.extract.finance.apb.ApbNotificationExtractor
import com.example.npc.extract.finance.prisbank.PrisbankNotificationExtractor
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

/** Synthetic fixtures; no real card/account identifiers or private screenshots. */
class UniversalDirectionRegressionTest {
    private val runner = IsolatedExtractorRunner(listOf(ApbNotificationExtractor(), PrisbankNotificationExtractor()))
    private fun extract(title: String, body: String, pkg: String = "com.apb.mobile"): FinancialTransaction? =
        runner.runExtraction(Event(id = 1, rawId = 1, ts = Instant.EPOCH, title = title, text = body,
            normalizedText = body, lang = Lang.RU), pkg)

    @Test fun `live Prisbank title identifies incoming compact transfer`() {
        val tx = extract("🥳 Пришли деньги", "2 000.00 RUP", "com.prisbank.app")!!
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals(200000L, tx.amount.minor)
    }

    @Test fun `live APB cashback falls through to universal amount parser`() {
        val tx = extract("Агропромбанк", "CashBack0,70RUP по карте **1234 от МАГАЗИН")!!
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals(70L, tx.amount.minor)
        assertEquals(CurrencyCode.RUP, tx.amount.currency)
        assertEquals("**1234", tx.accountMask)
    }

    @Test fun `static topup and purchase preserve correct amounts and balance`() {
        val topup = extract("Банк", "Пополнение счета по карте Клевер **1234, 158.00 RUP")!!
        assertEquals(TransactionType.CREDIT, topup.type)
        assertEquals(15800L, topup.amount.minor)
        val purchase = extract("Банк", "Покупка по карте **1234 на сумму 5,47 RUP Баланс 290,46 RUP")!!
        assertEquals(TransactionType.DEBIT, purchase.type)
        assertEquals(547L, purchase.amount.minor)
        assertEquals(29046L, purchase.balance!!.minor)
    }

    @Test fun `new bank format uses shared fallback without bank-specific parser`() {
        val tx = extract("Incoming transfer", "25.00 EUR card *1234", "example.authorized.bank")!!
        assertEquals(TransactionType.CREDIT, tx.type)
        assertEquals(2500L, tx.amount.minor)
    }

    @Test fun `unknown or contradictory compact amount stays suggested`() {
        val unknown = extract("Банк", "50 RUP", "com.prisbank.app")!!
        assertEquals(TransactionType.UNKNOWN, unknown.type)
        assertEquals(TxStatus.SUGGESTED, unknown.txStatus)
        val conflicting = extract("Пришли деньги", "Списание: 50 RUP", "com.prisbank.app")!!
        assertEquals(TransactionType.UNKNOWN, conflicting.type)
    }

    @Test fun `completed purchase survives cashback percentage footer`() {
        val tx = extract("Банк", "Покупка 100 RUP. Кэшбэк 1%")!!
        assertEquals(TransactionType.DEBIT, tx.type)
        assertEquals(10000L, tx.amount.minor)
    }

    @Test fun `negated debit and contracted negation never become completed movements`() {
        listOf("Средства не списаны 100 RUP", "Cashback 10 RUP не начислен",
            "Cashback 10 RUP hasn't been credited", "Refund 10 EUR not processed")
            .forEach { assertNull(extract("Банк", it), it) }
    }

    @Test fun `balance only cashback promotion and negation do not become credits`() {
        listOf("CashBack 100 RUP будет зачислен", "Получите cashback 100 RUP по карте **1234", "Cashback 10% по карте **1234", "Кэшбэк не зачислен 1 RUP", "Баланс: 100 RUP")
            .forEach { assertNull(extract("Банк", it), it) }
    }
}
