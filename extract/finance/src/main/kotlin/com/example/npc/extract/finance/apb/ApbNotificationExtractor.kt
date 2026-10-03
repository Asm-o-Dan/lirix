package com.example.npc.extract.finance.apb

import com.example.npc.core.model.extract.ExtractorInput
import com.example.npc.core.model.extract.FinanceExtractor
import com.example.npc.core.model.extract.ParsedFinanceResult
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.extract.finance.AmountParser
import com.example.npc.extract.finance.RegionalTextSanitizer
import com.google.re2j.Pattern

class ApbNotificationExtractor : FinanceExtractor {
    override val id: String = "apb.push"
    override val version: Int = 1
    override val supportedBank: String = "APB"

    private val purchasePattern = Pattern.compile(
        "^(?:Покупка|Оплата) по карте (?P<mask>\\S+) на сумму (?P<amount>[\\d\\s,.]+) (?P<curr>[A-Za-zА-Яа-я.]+)(?:\\.?\\s*Баланс:?\\s*(?P<bal>[\\d\\s,.]+)\\s*(?P<balcurr>[A-Za-zА-Яа-я.]+))?"
    )

    private val reservationPattern = Pattern.compile(
        "^Резервирование по карте (?P<mask>\\S+) на сумму (?P<amount>[\\d\\s,.]+) (?P<curr>[A-Za-zА-Яа-я.]+)(?:\\.?\\s*Баланс:?\\s*(?P<bal>[\\d\\s,.]+)\\s*(?P<balcurr>[A-Za-zА-Яа-я.]+))?"
    )

    private val refundPattern = Pattern.compile(
        "^Отмена операции по карте (?P<mask>\\S+) на сумму (?P<amount>[\\d\\s,.]+)\\s*(?P<curr>[A-Za-zА-Яа-я.]+)(?:\\.?\\s*Баланс:?\\s*(?P<bal>[\\d\\s,.]+)\\s*(?P<balcurr>[A-Za-zА-Яа-я.]+))?"
    )

    private val topupPattern = Pattern.compile(
        "^Пополнение счета по карте Клевер (?P<mask>\\S+),\\s*(?P<amount>[\\d\\s,.]+)\\s*(?P<curr>[A-Za-zА-Яа-я.]+)"
    )

    private val incomingTransferPattern = Pattern.compile(
        "^Перевод на карту (?P<mask>\\S+) от (?P<sender>.+?) зачислен,\\s*(?P<amount>[\\d\\s,.]+)\\s*(?P<curr>[A-Za-zА-Яа-я.]+)"
    )

    private val outgoingTransferPattern = Pattern.compile(
        "^Перевод по карте (?P<mask>\\S+) на сумму (?P<amount>[\\d\\s,.]+) (?P<curr>[A-Za-zА-Яа-я.]+)(?:\\.?\\s*Баланс:?\\s*(?P<bal>[\\d\\s,.]+)\\s*(?P<balcurr>[A-Za-zА-Яа-я.]+))?"
    )

    override fun extract(input: ExtractorInput): ParsedFinanceResult {
        val text = RegionalTextSanitizer.sanitize(input.text)
        if (text.isBlank()) return ParsedFinanceResult.NotApplicable

        // Фильтрация нефинансовых сообщений (ПИН-коды, 2FA/OTP)
        if (text.contains("ПИН-код") || text.contains("пароль") || text.contains("код подтверждения")) {
            return ParsedFinanceResult.NotApplicable
        }

        // 1. Покупка / Оплата (DEBIT)
        val purchaseMatcher = purchasePattern.matcher(text)
        if (purchaseMatcher.find()) {
            return parseMatched(
                input = input,
                type = TransactionType.DEBIT,
                mask = purchaseMatcher.group("mask"),
                amountStr = purchaseMatcher.group("amount"),
                currStr = purchaseMatcher.group("curr"),
                balStr = purchaseMatcher.group("bal"),
                balCurrStr = purchaseMatcher.group("balcurr"),
                merchant = null
            )
        }

        // 2. Резервирование (Hold / Pre-auth) (DEBIT)
        val reservationMatcher = reservationPattern.matcher(text)
        if (reservationMatcher.find()) {
            return parseMatched(
                input = input,
                type = TransactionType.DEBIT,
                mask = reservationMatcher.group("mask"),
                amountStr = reservationMatcher.group("amount"),
                currStr = reservationMatcher.group("curr"),
                balStr = reservationMatcher.group("bal"),
                balCurrStr = reservationMatcher.group("balcurr"),
                merchant = null
            )
        }

        // 3. Отмена операции / Возврат (CREDIT)
        val refundMatcher = refundPattern.matcher(text)
        if (refundMatcher.find()) {
            return parseMatched(
                input = input,
                type = TransactionType.CREDIT,
                mask = refundMatcher.group("mask"),
                amountStr = refundMatcher.group("amount"),
                currStr = refundMatcher.group("curr"),
                balStr = refundMatcher.group("bal"),
                balCurrStr = refundMatcher.group("balcurr"),
                merchant = null
            )
        }

        // 4. Пополнение счета по карте Клевер (CREDIT)
        val topupMatcher = topupPattern.matcher(text)
        if (topupMatcher.find()) {
            return parseMatched(
                input = input,
                type = TransactionType.CREDIT,
                mask = topupMatcher.group("mask"),
                amountStr = topupMatcher.group("amount"),
                currStr = topupMatcher.group("curr"),
                balStr = null,
                balCurrStr = null,
                merchant = null
            )
        }

        // 5. Входящий перевод P2P (CREDIT)
        val inTransferMatcher = incomingTransferPattern.matcher(text)
        if (inTransferMatcher.find()) {
            val sender = inTransferMatcher.group("sender")?.trim()
            return parseMatched(
                input = input,
                type = TransactionType.CREDIT,
                mask = inTransferMatcher.group("mask"),
                amountStr = inTransferMatcher.group("amount"),
                currStr = inTransferMatcher.group("curr"),
                balStr = null,
                balCurrStr = null,
                merchant = sender
            )
        }

        // 6. Исходящий перевод (TRANSFER)
        val outTransferMatcher = outgoingTransferPattern.matcher(text)
        if (outTransferMatcher.find()) {
            return parseMatched(
                input = input,
                type = TransactionType.TRANSFER,
                mask = outTransferMatcher.group("mask"),
                amountStr = outTransferMatcher.group("amount"),
                currStr = outTransferMatcher.group("curr"),
                balStr = outTransferMatcher.group("bal"),
                balCurrStr = outTransferMatcher.group("balcurr"),
                merchant = null
            )
        }

        return ParsedFinanceResult.NotApplicable
    }

    private fun parseMatched(
        input: ExtractorInput,
        type: TransactionType,
        mask: String?,
        amountStr: String?,
        currStr: String?,
        balStr: String?,
        balCurrStr: String?,
        merchant: String?
    ): ParsedFinanceResult {
        if (amountStr == null) return ParsedFinanceResult.Failed("Missing amount")
        val minor = AmountParser.parseMinor(amountStr)
            ?: return ParsedFinanceResult.Failed("Failed to parse amount: $amountStr")

        val currency = currStr?.let { input.currencyResolver.resolve(it, "com.apb.mobile") }
            ?: CurrencyCode.RUP

        val balanceMoney = if (!balStr.isNullOrBlank()) {
            val balMinor = AmountParser.parseMinor(balStr)
            if (balMinor != null) {
                val balCurr = balCurrStr?.let { input.currencyResolver.resolve(it, "com.apb.mobile") } ?: currency
                Money.ofMinor(balMinor, balCurr)
            } else null
        } else null

        return ParsedFinanceResult.Success(
            type = type,
            amount = Money.ofMinor(minor, currency),
            balance = balanceMoney,
            merchant = merchant,
            accountMask = mask,
            status = TransactionStatus.COMPLETED
        )
    }
}
