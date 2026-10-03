package com.example.npc.extract.finance

import com.example.npc.core.model.extract.CurrencyResolver
import com.example.npc.core.model.extract.ExtractorInput
import com.example.npc.core.model.extract.ParsedFinanceResult
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.extract.finance.maib.MaibNotificationExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class MaibExtractorDeclinedTest {

    private lateinit var extractor: MaibNotificationExtractor
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    private val resolver = object : CurrencyResolver {
        override fun resolve(token: String, pkg: String?): CurrencyCode? {
            return BankCurrencyResolver.resolve(token, "md.maib.maibank")
        }
    }

    @BeforeEach
    fun setUp() {
        extractor = MaibNotificationExtractor()
    }

    private fun createInput(text: String, title: String? = null): ExtractorInput {
        return ExtractorInput(
            text = text,
            senderOrTitle = title,
            postedAt = now,
            currencyResolver = resolver
        )
    }

    @Test
    fun `extract identifies Russian declined payment in Temu and extracts amount and merchant`() {
        val text = "Платеж с карты ***6159 на сумму 664 MDL в Temu.com ОТКЛОНЕН из-за недостаточности средств. Пополните карту и попробуйте снова."
        val input = createInput(text, title = "Транзакция отклонена")

        val result = extractor.extract(input)

        assertTrue(result is ParsedFinanceResult.Declined, "Must return ParsedFinanceResult.Declined")
        val declined = result as ParsedFinanceResult.Declined
        assertEquals(66400L, declined.amount.minor)
        assertEquals(CurrencyCode.MDL, declined.amount.currency)
        assertEquals("Temu.com", declined.merchant)
        assertEquals("***6159", declined.accountMask)
        assertTrue(declined.reason.contains("недостаточности средств") || declined.reason.isNotEmpty())
    }

    @Test
    fun `extract identifies Romanian declined transaction with respinsa keyword`() {
        val text = "Tranzactie respinsa: plata cu cardul ***1234 in suma de 150 MDL la Orange. Motiv: Fonduri insuficiente"
        val input = createInput(text, title = "Tranzactie respinsa")

        val result = extractor.extract(input)

        assertTrue(result is ParsedFinanceResult.Declined)
        val declined = result as ParsedFinanceResult.Declined
        assertEquals(15000L, declined.amount.minor)
        assertEquals(CurrencyCode.MDL, declined.amount.currency)
        assertEquals("Orange", declined.merchant)
        assertEquals("***1234", declined.accountMask)
    }

    @Test
    fun `extract identifies Romanian declined transaction with refuzata keyword`() {
        val text = "Tranzactie refuzata: plata cu cardul ***5678 in suma de 25.50 EUR la Amazon.com"
        val input = createInput(text, title = "MAIB Alert")

        val result = extractor.extract(input)

        assertTrue(result is ParsedFinanceResult.Declined)
        val declined = result as ParsedFinanceResult.Declined
        assertEquals(2550L, declined.amount.minor)
        assertEquals(CurrencyCode.EUR, declined.amount.currency)
        assertEquals("Amazon.com", declined.merchant)
    }

    @Test
    fun `successful transaction is NOT classified as declined`() {
        val text = "Оплата на сумму 664 MDL в Temu.com с карты ***1555 прошла успешно. Доступный остаток: 66.83 EUR."
        val input = createInput(text, title = "maibank")

        val result = extractor.extract(input)

        assertTrue(result is ParsedFinanceResult.Success, "Successful transaction must not be marked Declined")
        val success = result as ParsedFinanceResult.Success
        assertEquals(66400L, success.amount.minor)
        assertEquals(CurrencyCode.MDL, success.amount.currency)
    }
}
