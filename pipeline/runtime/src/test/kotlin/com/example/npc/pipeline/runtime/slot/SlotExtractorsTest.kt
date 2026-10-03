package com.example.npc.pipeline.runtime.slot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlotExtractorsTest {

    @Test
    fun `AmountSlotExtractor extracts amount and currency correctly`() {
        val extractor = AmountSlotExtractor()

        val res1 = extractor.extract("Пополнение 150.50 MDL успешно")
        assertNotNull(res1)
        assertEquals("150.50", res1!!.amount)
        assertEquals("MDL", res1.currency)

        val res2 = extractor.extract("Перевод 245,90 RUP на карту")
        assertNotNull(res2)
        assertEquals("245,90", res2!!.amount)
        assertEquals("RUP", res2.currency)

        val res3 = extractor.extract("Зачислено 1000 USD")
        assertNotNull(res3)
        assertEquals("1000", res3!!.amount)
        assertEquals("USD", res3.currency)

        // Without currency
        val res4 = extractor.extract("Сумма операции 500")
        assertNotNull(res4)
        assertEquals("500", res4!!.amount)
        assertNull(res4.currency)
    }

    @Test
    fun `CardMaskSlotExtractor extracts masked card`() {
        val extractor = CardMaskSlotExtractor()

        val card1 = extractor.extract("Оплата по карте *1234")
        assertEquals("*1234", card1)

        val card2 = extractor.extract("Перевод на карту Visa *5678")
        assertEquals("*5678", card2)

        val card3 = extractor.extract("Transaction with card *9999 completed")
        assertEquals("*9999", card3)

        val cardNone = extractor.extract("Перевод по номеру телефона")
        assertNull(cardNone)
    }

    @Test
    fun `MerchantSlotExtractor extracts merchant name`() {
        val extractor = MerchantSlotExtractor()

        val m1 = extractor.extract("Покупка в Supermarket в 12:00")
        assertEquals("Supermarket", m1)

        val m2 = extractor.extract("Перевод от Ivan I. получен")
        assertEquals("Ivan I.", m2)

        val m3 = extractor.extract("Успешно списано Magazin")
        assertEquals("Magazin", m3)
    }

    @Test
    fun `BalanceSlotExtractor extracts remaining balance`() {
        val extractor = BalanceSlotExtractor()

        val b1 = extractor.extract("Операция выполнена. Баланс: 12500.50 MDL")
        assertNotNull(b1)
        assertEquals("12500.50", b1!!.balance)

        val b2 = extractor.extract("Успешно. Остаток: 450,00")
        assertNotNull(b2)
        assertEquals("450,00", b2!!.balance)

        val b3 = extractor.extract("Sold: 10 MDL")
        assertNotNull(b3)
        assertEquals("10", b3!!.balance)
    }

    @Test
    fun `SlotDecomposedPipeline end-to-end extraction and anchor check`() {
        val pipeline = SlotDecomposedPipeline.fromRules(
            anchorPattern = com.google.re2j.Pattern.compile("(?i)(?:Пополнение счета|Перевод на карту|Зачисление)")
        )

        val validPush = "Зачисление 250 MDL от Magazin карте *4321. Баланс: 5000 MDL"
        assertTrue(pipeline.matchesAnchor(validPush))

        val extracted = pipeline.extract(validPush)
        assertNotNull(extracted)
        assertEquals("250", extracted!!.amount)
        assertEquals("MDL", extracted.currency)
        assertEquals("*4321", extracted.cardMask)
        assertEquals("Magazin", extracted.merchant)
        assertEquals("5000", extracted.balance)

        val invalidPush = "Рекламное сообщение со скидками 50%"
        assertFalse(pipeline.matchesAnchor(invalidPush))
    }
}
