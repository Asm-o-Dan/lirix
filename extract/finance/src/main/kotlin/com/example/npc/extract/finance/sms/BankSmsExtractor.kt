package com.example.npc.extract.finance.sms

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

class BankSmsExtractor : FinanceExtractor {
    override val id: String = "bank.sms"
    override val version: Int = 1
    override val supportedBank: String = "GENERIC_BANK_SMS"

    private val authorizedSenders = setOf(
        "900", "sberbank", "sber", "tinkoff", "t-bank", "tbank",
        "vtb", "alfa-bank", "alfabank", "raiffeisen",
        "apb", "agroprombank", "prisbank", "prb", "maib"
    )

    private val sberPattern = Pattern.compile(
        "^(?:Сбербанк(?:\\s+Онлайн)?\\.?\\s*)?(?P<type>Покупка|Оплата|Списание|Зачисление|Пополнение|Перевод)\\s+(?P<amount>[\\d\\s,.]+)\\s*(?P<curr>[A-Za-zА-Яа-я.₽]+)(?:\\s+карта\\s+(?P<mask>\\S+))?(?:\\s+(?P<merchant>[^.]+?))?\\.\\s*(?:Остаток|Баланс):?\\s*(?P<bal>[\\d\\s,.]+)\\s*(?P<balcurr>[A-Za-zА-Яа-я.₽]+)?"
    )

    private val tinkoffPattern = Pattern.compile(
        "^(?P<type>Покупка|Оплата|Перевод|Зачисление)\\.\\s*Карта\\s*(?P<mask>\\*\\S+)\\.\\s*(?P<amount>[\\d\\s,.]+)\\s*(?P<curr>[A-Za-zА-Яа-я.]+)\\.\\s*(?P<merchant>[^.]+?)\\.\\s*(?:Доступно|Остаток|Баланс)\\s*(?P<bal>[\\d\\s,.]+)\\s*(?P<balcurr>[A-Za-zА-Яа-я.]+)?"
    )

    override fun extract(input: ExtractorInput): ParsedFinanceResult {
        val text = RegionalTextSanitizer.sanitize(input.text)
        if (text.isBlank()) return ParsedFinanceResult.NotApplicable

        // Проверка доверенного отправителя
        val sender = input.senderOrTitle?.trim()?.lowercase() ?: ""
        val isAuthorized = authorizedSenders.any { sender.contains(it) }
        if (!isAuthorized) {
            return ParsedFinanceResult.NotApplicable
        }

        // Фильтрация 2FA / OTP одноразовых паролей
        val textLower = text.lowercase()
        if (textLower.contains("пароль") ||
            textLower.contains("код") ||
            textLower.contains("никому не сообщайте") ||
            textLower.contains("auth code") ||
            textLower.contains("verification code")
        ) {
            return ParsedFinanceResult.NotApplicable
        }

        // 1. Сбербанк 900
        val sberMatcher = sberPattern.matcher(text)
        if (sberMatcher.find()) {
            val typeStr = sberMatcher.group("type")
            val type = when (typeStr) {
                "Покупка", "Оплата", "Списание" -> TransactionType.DEBIT
                "Зачисление", "Пополнение" -> TransactionType.CREDIT
                "Перевод" -> TransactionType.TRANSFER
                else -> TransactionType.DEBIT
            }

            return parseMatched(
                input = input,
                type = type,
                sender = sender,
                mask = sberMatcher.group("mask"),
                amountStr = sberMatcher.group("amount"),
                currStr = sberMatcher.group("curr"),
                balStr = sberMatcher.group("bal"),
                balCurrStr = sberMatcher.group("balcurr"),
                merchant = sberMatcher.group("merchant")?.trim()
            )
        }

        // 2. Тинькофф / Т-Банк
        val tinkoffMatcher = tinkoffPattern.matcher(text)
        if (tinkoffMatcher.find()) {
            val typeStr = tinkoffMatcher.group("type")
            val type = when (typeStr) {
                "Покупка", "Оплата" -> TransactionType.DEBIT
                "Зачисление" -> TransactionType.CREDIT
                "Перевод" -> TransactionType.TRANSFER
                else -> TransactionType.DEBIT
            }

            return parseMatched(
                input = input,
                type = type,
                sender = sender,
                mask = tinkoffMatcher.group("mask"),
                amountStr = tinkoffMatcher.group("amount"),
                currStr = tinkoffMatcher.group("curr"),
                balStr = tinkoffMatcher.group("bal"),
                balCurrStr = tinkoffMatcher.group("balcurr"),
                merchant = tinkoffMatcher.group("merchant")?.trim()
            )
        }

        return ParsedFinanceResult.NotApplicable
    }

    private fun parseMatched(
        input: ExtractorInput,
        type: TransactionType,
        sender: String,
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

        val currency = currStr?.let { input.currencyResolver.resolve(it, sender) }
            ?: CurrencyCode.RUB

        val balanceMoney = if (!balStr.isNullOrBlank()) {
            val balMinor = AmountParser.parseMinor(balStr)
            if (balMinor != null) {
                val balCurr = balCurrStr?.let { input.currencyResolver.resolve(it, sender) } ?: currency
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
