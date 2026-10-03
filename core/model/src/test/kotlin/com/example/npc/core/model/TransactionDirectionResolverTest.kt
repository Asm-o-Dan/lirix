package com.example.npc.core.model

import com.example.npc.core.model.finance.TransactionDirectionResolver
import com.example.npc.core.model.finance.TransactionType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TransactionDirectionResolverTest {
    @Test fun `incoming money from title and compact mixed-language cashback are credit`() {
        val cases = listOf(
            "🥳 Пришли деньги" to "2 000.00 RUP",
            "Банк" to "CashBack0,70RUP по карте **1234 от МАГАЗИН",
            "Банк" to "Кэшбэк зачислен 3.00 RUP",
            "" to "Кэшбэк начислен 0.70 RUP",
            "" to "Cashback paid 2 USD",
            "Money received" to "25.00 USD",
            "Перевод получен" to "100 RUP",
            "" to "Incoming transfer 100 EUR",
            "" to "Пополнение счета 158 RUP",
            "" to "Внесение 100 RUB",
            "" to "ÎNCASARE 10 MDL",
            "" to "Возврат за покупку 50 RUP",
            "" to "Restituire plata cu cardul 50 MDL"
        )
        for ((title, body) in cases) {
            assertEquals(TransactionType.CREDIT, TransactionDirectionResolver.resolve(title, body).type, "$title $body")
        }
    }

    @Test fun `purchases withdrawals and explicit outgoing transfers are debit`() {
        listOf("Покупка 5,47 RUP Баланс 290,46 RUP", "Покупка 100 RUP. Кэшбэк 1%", "Деньги списаны 20 RUP", "Оплата 12 MDL", "Outgoing transfer 20 EUR", "Перевод отправлен 10 USD")
            .forEach { assertEquals(TransactionType.DEBIT, TransactionDirectionResolver.resolve(body = it).type, it) }
    }

    @Test fun `ambiguous and conflicting signals remain unknown`() {
        listOf("100 RUP", "Cashback available", "Баланс -100 RUP", "Телефон +12345678", "Карта *1234", "Покупка 50 RUP. Зачислено 20 RUP", "Покупка +50 RUP")
            .forEach { assertEquals(TransactionType.UNKNOWN, TransactionDirectionResolver.resolve(body = it).type, it) }
        assertEquals(TransactionType.UNKNOWN, TransactionDirectionResolver.resolve("Пришли деньги", "Списано 100 RUP").type)
        assertEquals(TransactionType.UNKNOWN, TransactionDirectionResolver.resolve(body = "Зачислено 100 RUP", explicitType = TransactionType.DEBIT).type)
        assertEquals(TransactionType.UNKNOWN, TransactionDirectionResolver.resolve(body = "Перевод между своими счетами 100 RUP", explicitType = TransactionType.CREDIT).type)
    }

    @Test fun `transfers without direction are not assumed income or expense`() {
        listOf("Перевод 100 RUP", "Transfer 20 EUR", "Перевод между своими счетами 100 RUP")
            .forEach { assertEquals(TransactionType.TRANSFER, TransactionDirectionResolver.resolve(body = it).type, it) }
    }

    @Test fun `ads pending rewards negation and authentication never create income`() {
        listOf(
            "Получите cashback 100 RUP по карте **1234", "Cashback до 100 RUP", "Cashback 10% по карте **1234",
            "Кэшбэк 2 RUP будет зачислен", "Кэшбэк 2 RUP не зачислен", "Refund pending 20 EUR",
            "Cashback 10 RUP не начислен", "Cashback 10 RUP hasn't been credited", "Средства не списаны 100 RUP", "Payment not debited 10 USD",
            "Refund 10 EUR not processed", "Refund 10 EUR not executed", "Refund not received 20 EUR", "Ожидается возврат 20 RUP", "Пополнение 100 RUP в обработке",
            "Код подтверждения 123456. Покупка 10 RUP", "One-time code 1234 for payment 10 USD",
            "Cashback 10 RUP offer", "Запрос на возврат 10 RUP"
        ).forEach { assertTrue(TransactionDirectionResolver.resolve(body = it).isSuppressed, it) }
    }

    @Test fun `failed operations retain decline status without completed income`() {
        listOf("Cashback 10 RUP failed", "Refund declined 10 USD", "Оплата 10 MDL отклонена. Пополните карту и попробуйте снова.")
            .forEach {
                val result = TransactionDirectionResolver.resolve(body = it)
                assertTrue(result.isDeclined, it)
                assertFalse(result.isRefund, it)
                assertNotEquals(TransactionType.CREDIT, result.type, it)
            }
    }

    @Test fun `only currency-bound signed amounts provide fallback direction`() {
        assertEquals(TransactionType.CREDIT, TransactionDirectionResolver.resolve(body = "+100 MDL").type)
        assertEquals(TransactionType.DEBIT, TransactionDirectionResolver.resolve(body = "−50 USD").type)
        assertEquals(TransactionType.UNKNOWN, TransactionDirectionResolver.resolve(body = "balance: -100 EUR").type)
    }

    @Test fun `malformed long amounts have bounded processing cost`() {
        val text = "Cashback " + "1".repeat(4000) + " XYZ"
        TransactionDirectionResolver.resolve(body = text) // warm up expressions
        val start = System.nanoTime()
        repeat(10) { assertEquals(TransactionType.UNKNOWN, TransactionDirectionResolver.resolve(body = text).type) }
        assertTrue((System.nanoTime() - start) / 1_000_000 < 1000, "Malformed amount caused excessive backtracking")
    }

    @Test fun `explicit direction helps unsigned templates without defeating safety`() {
        assertEquals(TransactionType.CREDIT, TransactionDirectionResolver.resolve(body = "20 RUP", explicitType = TransactionType.CREDIT).type)
        assertTrue(TransactionDirectionResolver.resolve(body = "Earn 20 RUP cashback", explicitType = TransactionType.CREDIT).isSuppressed)
    }
}
