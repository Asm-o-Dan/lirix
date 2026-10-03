package com.example.npc.core.model.finance

import java.time.Instant

/**
 * Структурированная финансовая транзакция, извлеченная изолированным парсером.
 *
 * @property id Первичный идентификатор (0L до сохранения в БД).
 * @property eventId Идентификатор связанного события Event (nullable FK на event.id с ON DELETE SET NULL).
 * @property bank Идентификатор банка: "APB", "PRISBANK", "MAIB", "UNKNOWN".
 * @property type Направление операции (DEBIT, CREDIT, TRANSFER).
 * @property amount Неотрицательная сумма операции и валюта.
 * @property balance Доступный остаток счета после операции (если указан).
 * @property merchant Контрагент операции (мерчант/магазин, получатель перевода, банк).
 * @property accountMask Маска карты/счета (например: "*1234").
 * @property status Статус выполнения операции (COMPLETED, DECLINED).
 * @property occurredAt Момент времени совершения транзакции.
 * @property extractorId Уникальный ID экстрактора (например: "apb.push", "maib.push").
 * @property extractorVersion Версия экстрактора для поддержки повторного парсинга.
 * @property rawText Исходный текст уведомления/SMS.
 * @property createdAt Момент сохранения записи в систему.
 */
data class FinancialTransaction(
    val id: Long = 0L,
    val eventId: Long?,
    val bank: String,
    val type: TransactionType,
    val amount: Money,
    val balance: Money?,
    val merchant: String?,
    val accountMask: String?,
    val status: TransactionStatus,
    val occurredAt: Instant,
    val extractorId: String,
    val extractorVersion: Int,
    val rawText: String,
    val createdAt: Instant = Instant.now(),
    val extractorKind: ExtractorKind = ExtractorKind.STATIC,
    val templateId: String? = null,
    val txStatus: TxStatus = TxStatus.CONFIRMED_AUTO
) {
    init {
        require(id >= 0L) { "FinancialTransaction id must be >= 0 (got $id)" }
        require(eventId == null || eventId > 0L) { "eventId must be > 0 if specified (got $eventId)" }
        require(bank.isNotBlank()) { "bank must not be blank" }
        require(extractorVersion >= 1) { "extractorVersion must be >= 1 (got $extractorVersion)" }
        require(rawText.isNotBlank()) { "rawText must not be blank" }
        require(merchant == null || merchant.isNotBlank()) { "merchant must not be blank if specified" }
        if (balance != null) {
            require(balance.currency == amount.currency) {
                "Balance currency (${balance.currency}) must match transaction currency (${amount.currency})"
            }
        }
    }

    // Дополнительные свойства для совместимости со спеками
    val money: Money get() = amount
    val counterparty: String? get() = merchant
    val parserVersion: Int get() = extractorVersion
    val timestamp: Instant get() = occurredAt
}
