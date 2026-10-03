package com.example.npc.extract.finance

import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.extract.finance.apb.ApbNotificationExtractor
import com.example.npc.extract.finance.maib.MaibNotificationExtractor
import com.example.npc.extract.finance.prisbank.PrisbankNotificationExtractor
import com.example.npc.extract.finance.sms.BankSmsExtractor
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class IsolatedExtractorRunnerTest {

    private lateinit var runner: IsolatedExtractorRunner
    private lateinit var circuitBreaker: CircuitBreaker
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    @BeforeEach
    fun setUp() {
        circuitBreaker = CircuitBreaker(failureThreshold = 3, cooldownDurationMs = 600_000L)
        val extractors = listOf(
            ApbNotificationExtractor(),
            PrisbankNotificationExtractor(),
            MaibNotificationExtractor(),
            BankSmsExtractor()
        )
        runner = IsolatedExtractorRunner(extractors, circuitBreaker)
    }

    private fun createEvent(
        id: Long = 100L,
        title: String,
        text: String
    ): Event {
        return Event(
            id = id,
            rawId = 1L,
            ts = now,
            title = title,
            text = text,
            normalizedText = text,
            lang = Lang.RU
        )
    }

    @Test
    fun `runExtraction successfully extracts APB transaction`() {
        val event = createEvent(
            title = "Агропромбанк",
            text = "Покупка по карте **5576 на сумму 5,47 RUP Баланс 422,01 RUP"
        )
        val transaction = runner.runExtraction(event, "com.apb.mobile")

        assertNotNull(transaction)
        assertEquals("APB", transaction!!.bank)
        assertEquals(TransactionType.DEBIT, transaction.type)
        assertEquals(547L, transaction.amount.minor)
        assertEquals(CurrencyCode.RUP, transaction.amount.currency)
        assertEquals(42201L, transaction.balance?.minor)
        assertEquals("**5576", transaction.accountMask)
        assertEquals(TransactionStatus.COMPLETED, transaction.status)
        assertEquals(100L, transaction.eventId)
    }

    @Test
    fun `runExtraction successfully handles declined transaction from MAIB`() {
        val event = createEvent(
            title = "Транзакция отклонена",
            text = "Платеж с карты ***6159 на сумму 664 MDL в Temu.com ОТКЛОНЕН из-за недостаточности средств. Пополните карту и попробуйте снова."
        )
        val transaction = runner.runExtraction(event, "md.maib.maibank")

        assertNotNull(transaction)
        assertEquals("MAIB", transaction!!.bank)
        assertEquals(TransactionStatus.DECLINED, transaction.status)
        assertEquals(66400L, transaction.amount.minor)
        assertEquals(CurrencyCode.MDL, transaction.amount.currency)
        assertNull(transaction.balance, "Declined transaction must have null balance")
        assertEquals("Temu.com", transaction.merchant)
        assertEquals("***6159", transaction.accountMask)
    }

    @Test
    fun `runExtraction returns null for non-financial notifications`() {
        val otpEvent = createEvent(
            title = "900",
            text = "Сбербанк Онлайн. Пароль для входа в приложение: 8492. Никому не сообщайте!"
        )
        val transaction = runner.runExtraction(otpEvent, "com.google.android.apps.messaging")
        assertNull(transaction)
    }

    @Test
    fun `runExtraction returns null when circuit breaker is tripped`() {
        // Force circuit breaker to open
        val tripTime = System.currentTimeMillis()
        repeat(3) { circuitBreaker.recordFailure(tripTime) }

        val event = createEvent(
            title = "Агропромбанк",
            text = "Покупка по карте **5576 на сумму 5,47 RUP Баланс 422,01 RUP"
        )
        val transaction = runner.runExtraction(event, "com.apb.mobile")
        assertNull(transaction, "Should reject execution when circuit breaker is OPEN")
    }

    @Test
    fun `coroutine run method executes within timeout budget`() = runBlocking {
        val extractor = ApbNotificationExtractor()
        val resolver = BankCurrencyResolver
        val input = com.example.npc.core.model.extract.ExtractorInput(
            text = "Покупка по карте **5576 на сумму 5,47 RUP Баланс 422,01 RUP",
            senderOrTitle = "Агропромбанк",
            postedAt = now,
            currencyResolver = resolver
        )

        val result = runner.run(extractor, input)
        assertTrue(result is com.example.npc.core.model.extract.ParsedFinanceResult.Success)
    }
}
