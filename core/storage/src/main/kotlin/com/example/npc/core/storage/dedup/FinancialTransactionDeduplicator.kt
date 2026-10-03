package com.example.npc.core.storage.dedup

import com.example.npc.core.model.finance.ExtractorKind
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.TransactionType
import kotlin.math.abs

/**
 * Семантический дедупликатор финансовых транзакций с плавающим окном.
 *
 * Критерии совпадения:
 * - Временное окно: 5 минут (300 секунд) по occurredAt
 * - Одинаковый банк / packageName (или региональный эмитент)
 * - Одинаковая сумма amount (в minor units) и currency
 * - Одинаковое направление (TransactionType.CREDIT / INCOME)
 * - Совпадающая маска карты: суффикс 4 цифр совпадает (*5576 и 910401******5576), либо одна из масок не указана
 *
 * Политика слияния (Enrich & Merge):
 * - Не удваивает сумму
 * - Сохраняет имя отправителя / мерчанта из P2P сообщения
 * - Сохраняет детализированную маску карты из процессинга
 * - Сохраняет метаданные шаблона (templateId, extractorKind=TEMPLATE) для аналитики
 */
class FinancialTransactionDeduplicator(
    val windowSeconds: Long = DEFAULT_WINDOW_SECONDS
) {
    companion object {
        const val DEFAULT_WINDOW_SECONDS = 300L // 5 минут
    }

    /**
     * Поиск существующей транзакции-дубликата в переданном списке кандидатов.
     */
    fun findDuplicate(
        candidate: FinancialTransaction,
        existingTransactions: List<FinancialTransaction>
    ): FinancialTransaction? {
        return existingTransactions.firstOrNull { existing ->
            isDuplicate(candidate, existing)
        }
    }

    /**
     * Проверка, являются ли две транзакции дубликатами.
     */
    fun isDuplicate(
        a: FinancialTransaction,
        b: FinancialTransaction
    ): Boolean {
        // A declined attempt and a successful payment are separate operations. An
        // unresolved direction is not enough evidence for cross-event deduplication.
        if (a.status != b.status || a.type == TransactionType.UNKNOWN || b.type == TransactionType.UNKNOWN) {
            return false
        }

        // 1. Окно сопоставления по occurredAt
        val diffMs = abs(a.occurredAt.toEpochMilli() - b.occurredAt.toEpochMilli())
        if (diffMs > windowSeconds * 1000L) {
            return false
        }

        // 2. Одинаковый банк / packageName
        if (!isSameBank(a.bank, b.bank)) {
            return false
        }

        // 3. Одинаковая сумма и валюта
        if (a.amount.minor != b.amount.minor || a.amount.currency != b.amount.currency) {
            return false
        }

        // 4. Одинаковое направление (CREDIT/INCOME, DEBIT/EXPENSE)
        if (!isSameDirection(a.type, b.type)) {
            return false
        }

        // 5. Совпадающая маска карты (суффикс 4 цифр или одна из масок отсутствует)
        if (!isCardMaskMatching(a.accountMask, b.accountMask)) {
            return false
        }

        return true
    }

    /**
     * Слияние данных транзакций (Enrich & Merge).
     * [existing] обогащается данными из [incoming].
     * Сумма не удваивается. Идентификатор сохраняется.
     */
    fun merge(
        existing: FinancialTransaction,
        incoming: FinancialTransaction,
        replaceFinancialDetails: Boolean = false
    ): FinancialTransaction {
        // Automatic reprocessing or alternate notifications must never rewrite a
        // user's edit. A later explicit user edit to the same event is authoritative.
        val isExplicitUserUpdate = replaceFinancialDetails && incoming.txStatus.isUserProtected
        if (existing.txStatus.isUserProtected && !isExplicitUserUpdate) return existing
        if (incoming.txStatus.isUserProtected) {
            return incoming.copy(id = existing.id, eventId = existing.eventId, createdAt = existing.createdAt)
        }

        val corrected = if (replaceFinancialDetails) {
            existing.copy(
                type = incoming.type,
                amount = incoming.amount,
                status = incoming.status,
                txStatus = incoming.txStatus,
                isRefund = incoming.isRefund,
                balance = incoming.balance ?: existing.balance?.takeIf { it.currency == incoming.amount.currency },
                extractorVersion = incoming.extractorVersion
            )
        } else {
            existing
        }
        val mergedMerchant = when {
            !existing.merchant.isNullOrBlank() -> existing.merchant
            !incoming.merchant.isNullOrBlank() -> incoming.merchant
            else -> null
        }

        val mergedAccountMask = selectBestAccountMask(existing.accountMask, incoming.accountMask)
        val mergedBalance = corrected.balance ?: incoming.balance?.takeIf { it.currency == corrected.amount.currency }

        // Обогащение свойствами шаблона: если хотя бы один из источников шаблонный,
        // сохраняем его в объединенной записи для корректного отображения в аналитике
        val hasTemplate = incoming.extractorKind == ExtractorKind.TEMPLATE ||
                existing.extractorKind == ExtractorKind.TEMPLATE ||
                incoming.templateId != null || existing.templateId != null

        val mergedExtractorKind = when {
            hasTemplate -> ExtractorKind.TEMPLATE
            incoming.extractorKind != ExtractorKind.STATIC -> incoming.extractorKind
            else -> existing.extractorKind
        }

        val mergedTemplateId = incoming.templateId ?: existing.templateId
        val mergedExtractorId = if (incoming.templateId != null || incoming.extractorKind == ExtractorKind.TEMPLATE) {
            incoming.extractorId
        } else {
            existing.extractorId
        }

        return corrected.copy(
            merchant = mergedMerchant,
            accountMask = mergedAccountMask,
            balance = mergedBalance,
            isRefund = if (replaceFinancialDetails) incoming.isRefund else existing.isRefund || incoming.isRefund,
            extractorKind = mergedExtractorKind,
            templateId = mergedTemplateId,
            extractorId = mergedExtractorId,
            rawText = if (existing.rawText.isNotBlank()) existing.rawText else incoming.rawText
        )
    }

    private fun isSameBank(bank1: String, bank2: String): Boolean {
        if (bank1.equals(bank2, ignoreCase = true)) return true
        val b1 = normalizeBank(bank1)
        val b2 = normalizeBank(bank2)
        return b1 != null && b1 == b2
    }

    private fun normalizeBank(bank: String): String? {
        val lower = bank.lowercase()
        return when {
            lower.contains("apb") || lower.contains("агропром") || lower.contains("com.apb.mobile") -> "APB"
            lower.contains("prisbank") || lower.contains("сбербанк") || lower.contains("sberbank") -> "PRISBANK"
            lower.contains("maib") -> "MAIB"
            else -> null
        }
    }

    private fun isSameDirection(type1: TransactionType, type2: TransactionType): Boolean {
        return type1 == type2
    }

    private fun extractLast4Digits(mask: String?): String? {
        if (mask.isNullOrBlank()) return null
        val digits = mask.filter { it.isDigit() }
        return if (digits.length >= 4) digits.takeLast(4) else null
    }

    private fun isCardMaskMatching(mask1: String?, mask2: String?): Boolean {
        if (mask1.isNullOrBlank() || mask2.isNullOrBlank()) {
            return true
        }
        val last4First = extractLast4Digits(mask1)
        val last4Second = extractLast4Digits(mask2)
        return if (last4First != null && last4Second != null) {
            last4First == last4Second
        } else true
    }

    private fun selectBestAccountMask(mask1: String?, mask2: String?): String? {
        if (mask1.isNullOrBlank()) return mask2
        if (mask2.isNullOrBlank()) return mask1
        val digits1 = mask1.filter { it.isDigit() }.length
        val digits2 = mask2.filter { it.isDigit() }.length
        return when {
            digits2 > digits1 -> mask2
            digits1 > digits2 -> mask1
            mask2.length > mask1.length -> mask2
            else -> mask1
        }
    }
}
