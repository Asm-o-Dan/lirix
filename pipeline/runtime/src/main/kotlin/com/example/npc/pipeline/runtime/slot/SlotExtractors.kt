package com.example.npc.pipeline.runtime.slot

import com.google.re2j.Pattern

/**
 * Специализированный экстрактор суммы и валюты (Amount & Currency Slot Extractor).
 */
class AmountSlotExtractor(
    val pattern: Pattern = Pattern.compile(DEFAULT_PATTERN)
) {
    constructor(patternStr: String) : this(Pattern.compile(patternStr))

    data class AmountResult(
        val amount: String,
        val currency: String?
    )

    fun extract(text: String): AmountResult? {
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val amount = extractGroupSafely(matcher, "amount") ?: return null
            val curr = extractGroupSafely(matcher, "curr")
            return AmountResult(amount, curr)
        }
        return null
    }

    companion object {
        const val DEFAULT_PATTERN = """(?i)(?P<amount>\d+(?:[.,]\d{2})?)\s*(?P<curr>RUP|MDL|USD|EUR|RUB)?"""
    }
}

/**
 * Специализированный экстрактор маски карты (Card Mask Slot Extractor).
 */
class CardMaskSlotExtractor(
    val pattern: Pattern = Pattern.compile(DEFAULT_PATTERN)
) {
    constructor(patternStr: String) : this(Pattern.compile(patternStr))

    fun extract(text: String): String? {
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return extractGroupSafely(matcher, "card") ?: extractGroupSafely(matcher, "mask")
        }
        return null
    }

    companion object {
        const val DEFAULT_PATTERN = """(?i)(?:карте|карту|card)\s*(?:[A-Za-zА-Яа-яЁё]+\s+)?(?P<card>\d*\*+\d+|\*\d+)"""
    }
}

/**
 * Специализированный экстрактор мерчанта (Merchant Slot Extractor).
 */
class MerchantSlotExtractor(
    val pattern: Pattern = Pattern.compile(DEFAULT_PATTERN)
) {
    constructor(patternStr: String) : this(Pattern.compile(patternStr))

    fun extract(text: String): String? {
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            return extractGroupSafely(matcher, "merchant")
        }
        return null
    }

    companion object {
        const val DEFAULT_PATTERN = """(?i)(?:от|в|списано)\s+(?P<merchant>[A-ZА-ЯЁ][a-zа-яё]+(?:\s+[A-ZА-ЯЁ]\.)?)"""
    }
}

/**
 * Специализированный экстрактор баланса (Balance Slot Extractor).
 */
class BalanceSlotExtractor(
    val pattern: Pattern = Pattern.compile(DEFAULT_PATTERN)
) {
    constructor(patternStr: String) : this(Pattern.compile(patternStr))

    data class BalanceResult(
        val balance: String,
        val currency: String?
    )

    fun extract(text: String): BalanceResult? {
        val matcher = pattern.matcher(text)
        if (matcher.find()) {
            val bal = extractGroupSafely(matcher, "bal") ?: return null
            val curr = extractGroupSafely(matcher, "balcurr") ?: extractGroupSafely(matcher, "curr")
            return BalanceResult(bal, curr)
        }
        return null
    }

    companion object {
        const val DEFAULT_PATTERN = """(?i)(?:Остаток|Баланс|Sold):\s*(?P<bal>\d+(?:[.,]\d{2})?)"""
    }
}

/**
 * Конвейер слотовых регулярок (Slot-Decomposed Regex Pipeline, OPT-PIPE-001).
 * Объединяет якорное правило быстрой детекции и независимые слотовые экстракторы.
 */
data class SlotDecomposedPipeline(
    val anchorPattern: Pattern? = null,
    val amountExtractor: AmountSlotExtractor? = null,
    val cardMaskExtractor: CardMaskSlotExtractor? = null,
    val merchantExtractor: MerchantSlotExtractor? = null,
    val balanceExtractor: BalanceSlotExtractor? = null,
    val customRules: Map<String, Pattern> = emptyMap()
) {
    data class ExtractedSlots(
        val amount: String,
        val currency: String?,
        val cardMask: String?,
        val merchant: String?,
        val balance: String?,
        val balanceCurrency: String?
    )

    fun matchesAnchor(text: String): Boolean {
        if (anchorPattern == null) return true
        return anchorPattern.matcher(text).find()
    }

    fun extract(text: String): ExtractedSlots? {
        // 1. Извлечение суммы (обязательное поле для финансовой транзакции)
        val amountRes = amountExtractor?.extract(text)
        val amountStr: String
        val currFromAmount: String?
        if (amountRes != null) {
            amountStr = amountRes.amount
            currFromAmount = amountRes.currency
        } else {
            val p = customRules["amount"] ?: customRules["tx_amount"]
            if (p != null) {
                val m = p.matcher(text)
                if (m.find()) {
                    amountStr = extractGroupSafely(m, "amount") ?: return null
                    currFromAmount = extractGroupSafely(m, "curr")
                } else {
                    return null
                }
            } else {
                return null
            }
        }

        // 2. Извлечение валюты (если не найдена в сумме)
        var finalCurrency = currFromAmount
        if (finalCurrency == null) {
            val currPattern = customRules["curr"] ?: customRules["currency"]
            if (currPattern != null) {
                val m = currPattern.matcher(text)
                if (m.find()) {
                    finalCurrency = extractGroupSafely(m, "curr")
                }
            }
        }

        // 3. Извлечение карты/маски
        val cardMask = cardMaskExtractor?.extract(text) ?: customRules["card"]?.let { p ->
            val m = p.matcher(text)
            if (m.find()) extractGroupSafely(m, "card") else null
        } ?: customRules["mask"]?.let { p ->
            val m = p.matcher(text)
            if (m.find()) extractGroupSafely(m, "mask") else null
        }

        // 4. Извлечение мерчанта
        val merchant = merchantExtractor?.extract(text) ?: customRules["merchant"]?.let { p ->
            val m = p.matcher(text)
            if (m.find()) extractGroupSafely(m, "merchant") else null
        }

        // 5. Извлечение остатка/баланса
        val balanceRes = balanceExtractor?.extract(text)
        val balStr = balanceRes?.balance ?: customRules["bal"]?.let { p ->
            val m = p.matcher(text)
            if (m.find()) extractGroupSafely(m, "bal") else null
        } ?: customRules["balance"]?.let { p ->
            val m = p.matcher(text)
            if (m.find()) extractGroupSafely(m, "bal") else null
        }
        val balCurr = balanceRes?.currency ?: customRules["balcurr"]?.let { p ->
            val m = p.matcher(text)
            if (m.find()) extractGroupSafely(m, "balcurr") ?: extractGroupSafely(m, "curr") else null
        } ?: customRules["balance_currency"]?.let { p ->
            val m = p.matcher(text)
            if (m.find()) extractGroupSafely(m, "balcurr") ?: extractGroupSafely(m, "curr") else null
        }

        return ExtractedSlots(
            amount = amountStr,
            currency = finalCurrency,
            cardMask = cardMask,
            merchant = merchant,
            balance = balStr,
            balanceCurrency = balCurr
        )
    }

    companion object {
        fun fromRules(
            anchorPattern: Pattern? = null,
            slotRules: Map<String, Pattern> = emptyMap()
        ): SlotDecomposedPipeline {
            val amountPattern = slotRules["amount"] ?: slotRules["tx_amount"]
            val cardPattern = slotRules["card"] ?: slotRules["card_mask"] ?: slotRules["mask"]
            val merchantPattern = slotRules["merchant"]
            val balancePattern = slotRules["bal"] ?: slotRules["balance"]

            return SlotDecomposedPipeline(
                anchorPattern = anchorPattern,
                amountExtractor = if (amountPattern != null) AmountSlotExtractor(amountPattern) else AmountSlotExtractor(),
                cardMaskExtractor = if (cardPattern != null) CardMaskSlotExtractor(cardPattern) else CardMaskSlotExtractor(),
                merchantExtractor = if (merchantPattern != null) MerchantSlotExtractor(merchantPattern) else MerchantSlotExtractor(),
                balanceExtractor = if (balancePattern != null) BalanceSlotExtractor(balancePattern) else BalanceSlotExtractor(),
                customRules = slotRules
            )
        }
    }
}

private fun extractGroupSafely(matcher: com.google.re2j.Matcher, name: String): String? {
    return try {
        matcher.group(name)
    } catch (_: Exception) {
        null
    }
}
