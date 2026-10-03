package com.example.npc.core.model.extract

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import java.time.Instant

/**
 * Входные данные для доменного экстрактора.
 */
data class ExtractorInput(
    val text: String,
    val senderOrTitle: String?,
    val postedAt: Instant,
    val currencyResolver: CurrencyResolver
)

/**
 * Результат работы финансового экстрактора.
 */
sealed interface ParsedFinanceResult {

    /** Успешно извлеченная транзакция. */
    data class Success(
        val type: TransactionType,
        val amount: Money,
        val balance: Money?,
        val merchant: String?,
        val accountMask: String?,
        val status: TransactionStatus = TransactionStatus.COMPLETED
    ) : ParsedFinanceResult

    /** Уведомление является банковским, но транзакция отклонена (недостаточно средств, лимит и т.д.). */
    data class Declined(
        val reason: String,
        val type: TransactionType,
        val amount: Money,
        val merchant: String?,
        val accountMask: String?
    ) : ParsedFinanceResult

    /** Банковское сообщение без финансовой проводки (2FA/OTP код, справочный баланс, реклама). */
    data object NotApplicable : ParsedFinanceResult

    /** Ошибка разбора текста при известном банке-отправителе. */
    data class Failed(val reason: String) : ParsedFinanceResult
}

/**
 * Контракт изолированного банковского экстрактора.
 * Выполняется в песочнице с защитой по таймауту (Circuit Breaker 50ms) и линейным RE2/J движком.
 */
interface FinanceExtractor {
    val id: String              // Уникальный ID ("apb.push", "prisbank.push", "maib.push", "bank.sms")
    val version: Int            // Версия парсера
    val supportedBank: String   // "APB", "PRISBANK", "MAIB", "GENERIC_SMS"

    fun extract(input: ExtractorInput): ParsedFinanceResult
}
