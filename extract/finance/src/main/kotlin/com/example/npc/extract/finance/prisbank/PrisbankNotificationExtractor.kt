package com.example.npc.extract.finance.prisbank

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

class PrisbankNotificationExtractor : FinanceExtractor {
    override val id: String = "prisbank.push"
    override val version: Int = 1
    override val supportedBank: String = "PRISBANK"

    // Компактный формат (например: "4.40 RUP", "7.00 RUP")
    private val compactPattern = Pattern.compile(
        "^(?P<amount>[\\d\\s,.]+)\\s*(?P<curr>[A-Za-zА-Яа-я.]+)"
    )

    // Расширенный формат со списанием
    private val debitPattern = Pattern.compile(
        "^(?:Оплата|Списание|Покупка):?\\s*(?P<amount>[\\d\\s,.]+)\\s*(?P<curr>[A-Za-zА-Яа-я.]+)(?:\\.\\s*Карта:?\\s*(?P<mask>\\S+))?(?:\\.\\s*(?:Остаток|Баланс):?\\s*(?P<bal>[\\d\\s,.]+)\\s*(?P<balcurr>[A-Za-zА-Яа-я.]+))?"
    )

    // Расширенный формат с зачислением
    private val creditPattern = Pattern.compile(
        "^(?:Зачисление|Пополнение):?\\s*(?P<amount>[\\d\\s,.]+)\\s*(?P<curr>[A-Za-zА-Яа-я.]+)(?:\\.\\s*Карта:?\\s*(?P<mask>\\S+))?(?:\\.\\s*(?:Остаток|Баланс):?\\s*(?P<bal>[\\d\\s,.]+)\\s*(?P<balcurr>[A-Za-zА-Яа-я.]+))?"
    )

    override fun extract(input: ExtractorInput): ParsedFinanceResult {
        val text = RegionalTextSanitizer.sanitize(input.text)
        if (text.isBlank()) return ParsedFinanceResult.NotApplicable

        val titleLower = input.senderOrTitle?.lowercase() ?: ""

        // 1. Расширенный DEBIT
        val debitMatcher = debitPattern.matcher(text)
        if (debitMatcher.find()) {
            return parseMatched(
                input = input,
                type = TransactionType.DEBIT,
                mask = debitMatcher.group("mask"),
                amountStr = debitMatcher.group("amount"),
                currStr = debitMatcher.group("curr"),
                balStr = debitMatcher.group("bal"),
                balCurrStr = debitMatcher.group("balcurr")
            )
        }

        // 2. Расширенный CREDIT
        val creditMatcher = creditPattern.matcher(text)
        if (creditMatcher.find()) {
            return parseMatched(
                input = input,
                type = TransactionType.CREDIT,
                mask = creditMatcher.group("mask"),
                amountStr = creditMatcher.group("amount"),
                currStr = creditMatcher.group("curr"),
                balStr = creditMatcher.group("bal"),
                balCurrStr = creditMatcher.group("balcurr")
            )
        }

        // 3. Компактный формат (Poco M7 шторка)
        val compactMatcher = compactPattern.matcher(text)
        if (compactMatcher.find()) {
            val type = if (titleLower.contains("зачисл") || titleLower.contains("пополн")) {
                TransactionType.CREDIT
            } else {
                TransactionType.DEBIT
            }

            return parseMatched(
                input = input,
                type = type,
                mask = null,
                amountStr = compactMatcher.group("amount"),
                currStr = compactMatcher.group("curr"),
                balStr = null,
                balCurrStr = null
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
        balCurrStr: String?
    ): ParsedFinanceResult {
        if (amountStr == null) return ParsedFinanceResult.Failed("Missing amount")
        val minor = AmountParser.parseMinor(amountStr)
            ?: return ParsedFinanceResult.Failed("Failed to parse amount: $amountStr")

        val currency = currStr?.let { input.currencyResolver.resolve(it, "com.prisbank.app") }
            ?: CurrencyCode.RUP

        val balanceMoney = if (!balStr.isNullOrBlank()) {
            val balMinor = AmountParser.parseMinor(balStr)
            if (balMinor != null) {
                val balCurr = balCurrStr?.let { input.currencyResolver.resolve(it, "com.prisbank.app") } ?: currency
                Money.ofMinor(balMinor, balCurr)
            } else null
        } else null

        return ParsedFinanceResult.Success(
            type = type,
            amount = Money.ofMinor(minor, currency),
            balance = balanceMoney,
            merchant = null,
            accountMask = mask,
            status = TransactionStatus.COMPLETED
        )
    }
}
