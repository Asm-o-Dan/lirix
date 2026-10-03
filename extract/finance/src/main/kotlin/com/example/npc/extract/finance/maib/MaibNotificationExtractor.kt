package com.example.npc.extract.finance.maib

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

class MaibNotificationExtractor : FinanceExtractor {
    override val id: String = "maib.push"
    override val version: Int = 1
    override val supportedBank: String = "MAIB"

    private val declinedRuPattern = Pattern.compile(
        "^Платеж с карты (?P<mask>\\S+) на сумму (?P<amount>[\\d\\s,.]+) (?P<curr>[A-Za-z]+) в (?P<merchant>.+?)\\s+(?:ОТКЛОНЕН|отклонен)(?:\\s+из-за\\s+(?P<reason>[^.]+))?"
    )

    private val declinedRoWithMotivPattern = Pattern.compile(
        "^Tranzactie (?:respinsa|refuzata): plata cu cardul (?P<mask>\\S+) in suma de (?P<amount>[\\d\\s,.]+) (?P<curr>[A-Za-z]+) la (?P<merchant>.+?)\\.\\s*Motiv:?\\s*(?P<reason>.+)"
    )

    private val declinedRoPattern = Pattern.compile(
        "^Tranzactie (?:respinsa|refuzata): plata cu cardul (?P<mask>\\S+) in suma de (?P<amount>[\\d\\s,.]+) (?P<curr>[A-Za-z]+) la (?P<merchant>.+)"
    )

    private val successRuPattern = Pattern.compile(
        "^Оплата на сумму (?P<amount>[\\d\\s,.]+) (?P<curr>[A-Za-z]+) в (?P<merchant>.+?) с карты (?P<mask>\\S+) прошла успешно(?:\\.\\s*Доступный остаток:\\s*(?P<bal>[\\d\\s,.]+)\\s*(?P<balcurr>[A-Za-z]+))?"
    )

    private val successRoPattern = Pattern.compile(
        "^Plata in suma de (?P<amount>[\\d\\s,.]+) (?P<curr>[A-Za-z]+) la (?P<merchant>.+?) cu cardul (?P<mask>\\S+) a fost efectuata cu succes(?:\\.\\s*Sold disponibil:\\s*(?P<bal>[\\d\\s,.]+)\\s*(?P<balcurr>[A-Za-z]+))?"
    )

    override fun extract(input: ExtractorInput): ParsedFinanceResult {
        val text = RegionalTextSanitizer.sanitize(input.text)
        if (text.isBlank()) return ParsedFinanceResult.NotApplicable

        val title = input.senderOrTitle ?: ""
        val isDeclinedContext = isDeclined(text, title)

        // 1. Проверка Declined (Отказы) - приоритетная обработка
        if (isDeclinedContext) {
            val ruDeclinedMatcher = declinedRuPattern.matcher(text)
            if (ruDeclinedMatcher.find()) {
                val amountStr = ruDeclinedMatcher.group("amount")
                val minor = AmountParser.parseMinor(amountStr)
                    ?: return ParsedFinanceResult.Failed("Failed to parse amount: $amountStr")
                val currStr = ruDeclinedMatcher.group("curr")
                val currency = input.currencyResolver.resolve(currStr, "md.maib.maibank") ?: CurrencyCode.MDL
                val merchant = ruDeclinedMatcher.group("merchant")?.trim()
                val mask = ruDeclinedMatcher.group("mask")
                val reason = ruDeclinedMatcher.group("reason")?.trim() ?: "Транзакция отклонена"

                return ParsedFinanceResult.Declined(
                    reason = reason,
                    type = TransactionType.DEBIT,
                    amount = Money.ofMinor(minor, currency),
                    merchant = merchant,
                    accountMask = mask
                )
            }

            val roMotivMatcher = declinedRoWithMotivPattern.matcher(text)
            if (roMotivMatcher.find()) {
                val amountStr = roMotivMatcher.group("amount")
                val minor = AmountParser.parseMinor(amountStr)
                    ?: return ParsedFinanceResult.Failed("Failed to parse amount: $amountStr")
                val currStr = roMotivMatcher.group("curr")
                val currency = input.currencyResolver.resolve(currStr, "md.maib.maibank") ?: CurrencyCode.MDL
                val merchant = roMotivMatcher.group("merchant")?.trim()?.removeSuffix(".")
                val mask = roMotivMatcher.group("mask")
                val reason = roMotivMatcher.group("reason")?.trim() ?: "Tranzactie respinsa"

                return ParsedFinanceResult.Declined(
                    reason = reason,
                    type = TransactionType.DEBIT,
                    amount = Money.ofMinor(minor, currency),
                    merchant = merchant,
                    accountMask = mask
                )
            }

            val roDeclinedMatcher = declinedRoPattern.matcher(text)
            if (roDeclinedMatcher.find()) {
                val amountStr = roDeclinedMatcher.group("amount")
                val minor = AmountParser.parseMinor(amountStr)
                    ?: return ParsedFinanceResult.Failed("Failed to parse amount: $amountStr")
                val currStr = roDeclinedMatcher.group("curr")
                val currency = input.currencyResolver.resolve(currStr, "md.maib.maibank") ?: CurrencyCode.MDL
                val merchant = roDeclinedMatcher.group("merchant")?.trim()?.removeSuffix(".")
                val mask = roDeclinedMatcher.group("mask")
                val reason = "Tranzactie refuzata"

                return ParsedFinanceResult.Declined(
                    reason = reason,
                    type = TransactionType.DEBIT,
                    amount = Money.ofMinor(minor, currency),
                    merchant = merchant,
                    accountMask = mask
                )
            }
        }

        // 2. Успешные операции (DEBIT)
        val successRuMatcher = successRuPattern.matcher(text)
        if (successRuMatcher.find()) {
            return parseSuccess(
                input = input,
                amountStr = successRuMatcher.group("amount"),
                currStr = successRuMatcher.group("curr"),
                merchant = successRuMatcher.group("merchant")?.trim(),
                mask = successRuMatcher.group("mask"),
                balStr = successRuMatcher.group("bal"),
                balCurrStr = successRuMatcher.group("balcurr")
            )
        }

        val successRoMatcher = successRoPattern.matcher(text)
        if (successRoMatcher.find()) {
            return parseSuccess(
                input = input,
                amountStr = successRoMatcher.group("amount"),
                currStr = successRoMatcher.group("curr"),
                merchant = successRoMatcher.group("merchant")?.trim(),
                mask = successRoMatcher.group("mask"),
                balStr = successRoMatcher.group("bal"),
                balCurrStr = successRoMatcher.group("balcurr")
            )
        }

        return ParsedFinanceResult.NotApplicable
    }

    private fun isDeclined(text: String, title: String): Boolean {
        val tLower = text.lowercase()
        val titleLower = title.lowercase()
        return tLower.contains("отклонен") ||
                tLower.contains("respins") ||
                tLower.contains("refuzat") ||
                tLower.contains("declined") ||
                tLower.contains("failed") ||
                tLower.contains("esuat") ||
                titleLower.contains("отклонен") ||
                titleLower.contains("respins") ||
                titleLower.contains("refuzat") ||
                titleLower.contains("alert")
    }

    private fun parseSuccess(
        input: ExtractorInput,
        amountStr: String?,
        currStr: String?,
        merchant: String?,
        mask: String?,
        balStr: String?,
        balCurrStr: String?
    ): ParsedFinanceResult {
        if (amountStr == null) return ParsedFinanceResult.Failed("Missing amount")
        val minor = AmountParser.parseMinor(amountStr)
            ?: return ParsedFinanceResult.Failed("Failed to parse amount: $amountStr")

        val currency = currStr?.let { input.currencyResolver.resolve(it, "md.maib.maibank") }
            ?: CurrencyCode.MDL

        val balanceMoney = if (!balStr.isNullOrBlank()) {
            val balMinor = AmountParser.parseMinor(balStr)
            if (balMinor != null) {
                val balCurr = balCurrStr?.let { input.currencyResolver.resolve(it, "md.maib.maibank") } ?: currency
                Money.ofMinor(balMinor, balCurr)
            } else null
        } else null

        return ParsedFinanceResult.Success(
            type = TransactionType.DEBIT,
            amount = Money.ofMinor(minor, currency),
            balance = balanceMoney,
            merchant = merchant,
            accountMask = mask,
            status = TransactionStatus.COMPLETED
        )
    }
}
