package com.example.npc.extract.finance

import com.example.npc.core.model.extract.CurrencyResolver
import com.example.npc.core.model.extract.ExtractorInput
import com.example.npc.core.model.extract.ParsedFinanceResult
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.extract.finance.apb.ApbNotificationExtractor
import com.example.npc.extract.finance.maib.MaibNotificationExtractor
import com.example.npc.extract.finance.prisbank.PrisbankNotificationExtractor
import com.example.npc.extract.finance.sms.BankSmsExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class GoldenBankTransactionsTest {

    private lateinit var apbExtractor: ApbNotificationExtractor
    private lateinit var prisbankExtractor: PrisbankNotificationExtractor
    private lateinit var maibExtractor: MaibNotificationExtractor
    private lateinit var bankSmsExtractor: BankSmsExtractor

    private val now = Instant.parse("2026-09-27T12:00:00Z")

    @BeforeEach
    fun setUp() {
        apbExtractor = ApbNotificationExtractor()
        prisbankExtractor = PrisbankNotificationExtractor()
        maibExtractor = MaibNotificationExtractor()
        bankSmsExtractor = BankSmsExtractor()
    }

    private fun createInput(
        text: String,
        senderOrTitle: String?,
        contextPackage: String
    ): ExtractorInput {
        val resolver = object : CurrencyResolver {
            override fun resolve(token: String, pkg: String?): CurrencyCode? {
                return BankCurrencyResolver.resolve(token, pkg ?: contextPackage)
            }
        }
        return ExtractorInput(
            text = text,
            senderOrTitle = senderOrTitle,
            postedAt = now,
            currencyResolver = resolver
        )
    }

    // --- 1. PRISBANK Transport (Poco M7 telemetry) ---
    @Test
    fun `Golden 01 - Prisbank transport 4_40 RUP`() {
        val input = createInput(
            text = "4.40 RUP",
            senderOrTitle = "💸 Деньги списаны",
            contextPackage = "com.prisbank.app"
        )
        val result = prisbankExtractor.extract(input)

        assertTrue(result is ParsedFinanceResult.Success)
        val success = result as ParsedFinanceResult.Success
        assertEquals(TransactionType.DEBIT, success.type)
        assertEquals(440L, success.amount.minor)
        assertEquals(CurrencyCode.RUP, success.amount.currency)
        assertNull(success.balance)
    }

    // --- 2. APB Purchases ---
    @Test
    fun `Golden 02 - APB Purchase 5_47 RUP with balance`() {
        val input = createInput(
            text = "Покупка по карте **5576 на сумму 5,47 RUP Баланс 422,01 RUP",
            senderOrTitle = "Агропромбанк",
            contextPackage = "com.apb.mobile"
        )
        val result = apbExtractor.extract(input) as ParsedFinanceResult.Success

        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(547L, result.amount.minor)
        assertEquals(CurrencyCode.RUP, result.amount.currency)
        assertEquals(42201L, result.balance?.minor)
        assertEquals(CurrencyCode.RUP, result.balance?.currency)
        assertEquals("**5576", result.accountMask)
    }

    @Test
    fun `Golden 03 - APB Purchase 15_15 RUP with balance`() {
        val input = createInput(
            text = "Покупка по карте **5576 на сумму 15,15 RUP Баланс 406,86 RUP",
            senderOrTitle = "Агропромбанк",
            contextPackage = "com.apb.mobile"
        )
        val result = apbExtractor.extract(input) as ParsedFinanceResult.Success

        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(1515L, result.amount.minor)
        assertEquals(40686L, result.balance?.minor)
        assertEquals("**5576", result.accountMask)
    }

    // --- 3. APB Deposits / Incomes ---
    @Test
    fun `Golden 04 - APB Clever card topup 50_00 RUP`() {
        val input = createInput(
            text = "Пополнение счета по карте Клевер 910401******5576, 50.00 RUP",
            senderOrTitle = "Агропромбанк",
            contextPackage = "com.apb.mobile"
        )
        val result = apbExtractor.extract(input) as ParsedFinanceResult.Success

        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals(5000L, result.amount.minor)
        assertEquals(CurrencyCode.RUP, result.amount.currency)
        assertEquals("910401******5576", result.accountMask)
    }

    // --- 4. MAIB Declined Transaction ---
    @Test
    fun `Golden 05 - MAIB Declined Temu 664 MDL must NOT create expense`() {
        val input = createInput(
            text = "Платеж с карты ***6159 на сумму 664 MDL в Temu.com ОТКЛОНЕН из-за недостаточности средств. Пополните карту и попробуйте снова.",
            senderOrTitle = "Транзакция отклонена",
            contextPackage = "md.maib.maibank"
        )
        val result = maibExtractor.extract(input)

        assertTrue(result is ParsedFinanceResult.Declined, "Declined transaction must return Declined result")
        val declined = result as ParsedFinanceResult.Declined
        assertEquals(66400L, declined.amount.minor)
        assertEquals(CurrencyCode.MDL, declined.amount.currency)
        assertEquals("Temu.com", declined.merchant)
        assertEquals("***6159", declined.accountMask)
    }

    // --- 5. MAIB Success Multi-currency ---
    @Test
    fun `Golden 06 - MAIB Success Temu 664 MDL with EUR balance`() {
        val input = createInput(
            text = "Оплата на сумму 664 MDL в Temu.com с карты ***1555 прошла успешно. Доступный остаток: 66.83 EUR.",
            senderOrTitle = "maibank",
            contextPackage = "md.maib.maibank"
        )
        val result = maibExtractor.extract(input) as ParsedFinanceResult.Success

        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(66400L, result.amount.minor)
        assertEquals(CurrencyCode.MDL, result.amount.currency)
        assertEquals(6683L, result.balance?.minor)
        assertEquals(CurrencyCode.EUR, result.balance?.currency)
        assertEquals("Temu.com", result.merchant)
        assertEquals("***1555", result.accountMask)
    }

    // --- 6. APB Reservations & Refunds ---
    @Test
    fun `Golden 07 - APB Hold Reservation 50_00 RUP`() {
        val input = createInput(
            text = "Резервирование по карте **5576 на сумму 50,00 RUP Баланс 388,39 RUP",
            senderOrTitle = "Агропромбанк",
            contextPackage = "com.apb.mobile"
        )
        val result = apbExtractor.extract(input) as ParsedFinanceResult.Success

        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(5000L, result.amount.minor)
        assertEquals(38839L, result.balance?.minor)
    }

    @Test
    fun `Golden 08 - APB Refund Reversal 50_00 RUP`() {
        val input = createInput(
            text = "Отмена операции по карте ****5576 на сумму 50,00 RUP. Баланс: 438,39 RUP",
            senderOrTitle = "Агропромбанк",
            contextPackage = "com.apb.mobile"
        )
        val result = apbExtractor.extract(input) as ParsedFinanceResult.Success

        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals(5000L, result.amount.minor)
        assertEquals(43839L, result.balance?.minor)
    }

    @Test
    fun `Golden 09 - APB Reservation 25_75 RUP`() {
        val input = createInput(
            text = "Резервирование по карте **5576 на сумму 25,75 RUP Баланс 412,64 RUP",
            senderOrTitle = "Агропромбанк",
            contextPackage = "com.apb.mobile"
        )
        val result = apbExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(2575L, result.amount.minor)
    }

    @Test
    fun `Golden 10 - APB Payment Confirmation 25_75 RUP`() {
        val input = createInput(
            text = "Оплата по карте **5576 на сумму 25,75 RUP Баланс 412,64 RUP",
            senderOrTitle = "Агропромбанк",
            contextPackage = "com.apb.mobile"
        )
        val result = apbExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(2575L, result.amount.minor)
    }

    @Test
    fun `Golden 11 - APB Purchase 22_40 RUP`() {
        val input = createInput(
            text = "Покупка по карте **5576 на сумму 22,40 RUP Баланс 390,24 RUP",
            senderOrTitle = "Агропромбанк",
            contextPackage = "com.apb.mobile"
        )
        val result = apbExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(2240L, result.amount.minor)
    }

    @Test
    fun `Golden 12 - APB Incoming P2P Transfer from Artur M`() {
        val input = createInput(
            text = "Перевод на карту *5576 от Артур М. зачислен, 50,00 RUP",
            senderOrTitle = "Агропромбанк",
            contextPackage = "com.apb.mobile"
        )
        val result = apbExtractor.extract(input) as ParsedFinanceResult.Success

        assertEquals(TransactionType.CREDIT, result.type)
        assertEquals(5000L, result.amount.minor)
        assertEquals(CurrencyCode.RUP, result.amount.currency)
        assertEquals("Артур М.", result.merchant)
        assertEquals("*5576", result.accountMask)
    }

    // --- 7. More Prisbank Microtransactions ---
    @Test
    fun `Golden 13 - Prisbank 7_00 RUP`() {
        val input = createInput("7.00 RUP", "💸 Деньги списаны", "com.prisbank.app")
        val result = prisbankExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(700L, result.amount.minor)
    }

    @Test
    fun `Golden 14 - Prisbank 5_47 RUP`() {
        val input = createInput("5.47 RUP", "💸 Деньги списаны", "com.prisbank.app")
        val result = prisbankExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(547L, result.amount.minor)
    }

    @Test
    fun `Golden 15 - APB Outgoing Transfer 40_00 RUP`() {
        val input = createInput(
            text = "Перевод по карте **5576 на сумму 40,00 RUP Баланс 350,24 RUP",
            senderOrTitle = "Агропромбанк",
            contextPackage = "com.apb.mobile"
        )
        val result = apbExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(TransactionType.TRANSFER, result.type)
        assertEquals(4000L, result.amount.minor)
    }

    @Test
    fun `Golden 16 - APB Purchase 11_96 RUP`() {
        val input = createInput(
            text = "Покупка по карте **5576 на сумму 11,96 RUP Баланс 338,28 RUP",
            senderOrTitle = "Агропромбанк",
            contextPackage = "com.apb.mobile"
        )
        val result = apbExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(1196L, result.amount.minor)
    }

    @Test
    fun `Golden 17 - MAIB Temu 364 MDL with EUR balance`() {
        val input = createInput(
            text = "Оплата на сумму 364 MDL в Temu.com с карты ***1555 прошла успешно. Доступный остаток: 48.63 EUR.",
            senderOrTitle = "maibank",
            contextPackage = "md.maib.maibank"
        )
        val result = maibExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(36400L, result.amount.minor)
        assertEquals(4863L, result.balance?.minor)
    }

    @Test
    fun `Golden 18 - Prisbank 50_00 RUP`() {
        val input = createInput("50.00 RUP", "💸 Деньги списаны", "com.prisbank.app")
        val result = prisbankExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(5000L, result.amount.minor)
    }

    @Test
    fun `Golden 19 - MAIB Temu 541 MDL with EUR balance`() {
        val input = createInput(
            text = "Оплата на сумму 541 MDL в Temu.com с карты ***1555 прошла успешно. Доступный остаток: 21.58 EUR.",
            senderOrTitle = "maibank",
            contextPackage = "md.maib.maibank"
        )
        val result = maibExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(54100L, result.amount.minor)
        assertEquals(2158L, result.balance?.minor)
    }

    @Test
    fun `Golden 20 - Prisbank 12_80 RUP`() {
        val input = createInput("12.80 RUP", "💸 Деньги списаны", "com.prisbank.app")
        val result = prisbankExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(1280L, result.amount.minor)
    }

    @Test
    fun `Golden 21 - Prisbank 22_10 RUP`() {
        val input = createInput("22.10 RUP", "💸 Деньги списаны", "com.prisbank.app")
        val result = prisbankExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(2210L, result.amount.minor)
    }

    @Test
    fun `Golden 22 - Prisbank 26_24 RUP`() {
        val input = createInput("26.24 RUP", "💸 Деньги списаны", "com.prisbank.app")
        val result = prisbankExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(2624L, result.amount.minor)
    }

    @Test
    fun `Golden 23 - Prisbank 11_00 RUP`() {
        val input = createInput("11.00 RUP", "💸 Деньги списаны", "com.prisbank.app")
        val result = prisbankExtractor.extract(input) as ParsedFinanceResult.Success
        assertEquals(1100L, result.amount.minor)
    }

    // --- SMS 900 Bank SMS test ---
    @Test
    fun `Bank SMS from 900 extracts RUB purchase correctly`() {
        val input = createInput(
            text = "Сбербанк Онлайн. Покупка 1500 руб карта **1234 Пятерочка. Баланс: 12500 руб",
            senderOrTitle = "900",
            contextPackage = "com.google.android.apps.messaging"
        )
        val result = bankSmsExtractor.extract(input) as ParsedFinanceResult.Success

        assertEquals(TransactionType.DEBIT, result.type)
        assertEquals(150000L, result.amount.minor)
        assertEquals(CurrencyCode.RUB, result.amount.currency)
        assertEquals(1250000L, result.balance?.minor)
        assertEquals(CurrencyCode.RUB, result.balance?.currency)
    }

    // --- Negative scenarios ---
    @Test
    fun `Bank SMS 2FA OTP codes return NotApplicable`() {
        val otpInput = createInput(
            text = "Сбербанк Онлайн. Пароль для входа в приложение: 8492. Никому не сообщайте!",
            senderOrTitle = "900",
            contextPackage = "com.google.android.apps.messaging"
        )
        val result = bankSmsExtractor.extract(otpInput)
        assertTrue(result is ParsedFinanceResult.NotApplicable)
    }

    @Test
    fun `PIN change notifications return NotApplicable`() {
        val pinInput = createInput(
            text = "ПИН-код для карты **5576 успешно изменен.",
            senderOrTitle = "Агропромбанк",
            contextPackage = "com.apb.mobile"
        )
        val result = apbExtractor.extract(pinInput)
        assertTrue(result is ParsedFinanceResult.NotApplicable)
    }
}
