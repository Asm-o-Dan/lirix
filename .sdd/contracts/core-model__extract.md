# Межзонный контракт: Core Model ↔ Extract Finance (Финансовый домен и экстракторы)

**Версия:** FROZEN v2  
**Дата заморозки:** 2026-09-27  
**Статус:** FROZEN (GATE 3 PASSED)  
**Стороны контракта:**
- Провайдер: `zone/core-model` (`:core:model`)
- Потребители: `zone/extract-finance` (`:extract:finance`), `zone/core-storage` (`:core:storage`), `zone/ui-timeline` (`:ui:timeline`), `zone/app-lifecycle` (`:app`)

---

### 1. Модели финансового домена

```kotlin
package com.example.npc.core.model.finance

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

/**
 * Закрытый реестр поддерживаемых валют.
 * Приднестровский рубль (RUP) изолирован от java.util.Currency во избежание сбоев в Android рантайме.
 */
@JvmInline
value class CurrencyCode(val value: String) {

    init {
        require(value in SUPPORTED_CODES) {
            "Unsupported currency code: '$value'. Supported codes are: $SUPPORTED_CODES"
        }
    }

    /** Количество знаков дробной части (копейки, центы). Равно 2 для всех валют системы. */
    val minorDigits: Int get() = 2

    /** Общепринятый символ валюты для форматирования в UI. */
    val symbol: String
        get() = when (value) {
            CODE_RUP -> "р."
            CODE_MDL -> "L"
            CODE_RUB -> "₽"
            CODE_EUR -> "€"
            CODE_USD -> "$"
            else -> value
        }

    /** Признак соответствия ISO-4217 (RUP не входит в ISO-4217). */
    val isIso4217: Boolean get() = value != CODE_RUP

    override fun toString(): String = value

    companion object {
        const val CODE_RUP = "RUP" // Приднестровский рубль (ПМР)
        const val CODE_MDL = "MDL" // Молдавский лей
        const val CODE_RUB = "RUB" // Российский рубль
        const val CODE_EUR = "EUR" // Евро
        const val CODE_USD = "USD" // Доллар США

        val RUP = CurrencyCode(CODE_RUP)
        val MDL = CurrencyCode(CODE_MDL)
        val RUB = CurrencyCode(CODE_RUB)
        val EUR = CurrencyCode(CODE_EUR)
        val USD = CurrencyCode(CODE_USD)

        val SUPPORTED_CODES: Set<String> = setOf(CODE_RUP, CODE_MDL, CODE_RUB, CODE_EUR, CODE_USD)

        fun of(code: String): CurrencyCode = CurrencyCode(code.trim().uppercase())

        fun ofOrNull(code: String?): CurrencyCode? {
            if (code.isNullOrBlank()) return null
            val upper = code.trim().uppercase()
            return if (upper in SUPPORTED_CODES) CurrencyCode(upper) else null
        }
    }
}

/**
 * Неотрицательная денежная сумма в минимальных неделимых единицах (копейки, центы).
 *
 * Инвариант: minor >= 0L. Знак и направление операции задаются строго через TransactionType.
 * Арифметика защищена от переполнения через Math.addExact / Math.subtractExact.
 */
data class Money(
    val minor: Long,
    val currency: CurrencyCode
) : Comparable<Money> {

    init {
        require(minor >= 0L) {
            "Money minor amount must be non-negative (got $minor). Direction is defined via TransactionType."
        }
    }

    operator fun plus(other: Money): Money {
        checkSameCurrency(other)
        return Money(Math.addExact(minor, other.minor), currency)
    }

    operator fun minus(other: Money): Money {
        checkSameCurrency(other)
        val result = Math.subtractExact(minor, other.minor)
        require(result >= 0L) {
            "Subtraction would result in negative Money amount: $minor - ${other.minor} = $result"
        }
        return Money(result, currency)
    }

    override fun compareTo(other: Money): Int {
        checkSameCurrency(other)
        return minor.compareTo(other.minor)
    }

    fun toMajorBigDecimal(): BigDecimal =
        BigDecimal.valueOf(minor, currency.minorDigits)

    fun formatDisplay(): String {
        val majorStr = toMajorBigDecimal()
            .setScale(currency.minorDigits, RoundingMode.UNNECESSARY)
            .toPlainString()
        return "$majorStr ${currency.symbol}"
    }

    private fun checkSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: expected $currency, got ${other.currency}"
        }
    }

    companion object {
        val ZERO_RUP = Money(0L, CurrencyCode.RUP)
        val ZERO_MDL = Money(0L, CurrencyCode.MDL)
        val ZERO_RUB = Money(0L, CurrencyCode.RUB)
        val ZERO_EUR = Money(0L, CurrencyCode.EUR)
        val ZERO_USD = Money(0L, CurrencyCode.USD)

        fun ofMajor(major: BigDecimal, currency: CurrencyCode): Money {
            require(major.signum() >= 0) { "Major amount must be non-negative: $major" }
            val scaled = major.setScale(currency.minorDigits, RoundingMode.UNNECESSARY)
            return Money(scaled.unscaledValue().longValueExact(), currency)
        }

        fun ofMinor(minor: Long, currency: CurrencyCode): Money = Money(minor, currency)
    }
}

/**
 * Направление финансовой операции.
 */
enum class TransactionType {
    DEBIT,      // Списание / Покупка / Оплата услуг (Расход)
    CREDIT,     // Пополнение / Зарплата / Входящий перевод (Доход)
    TRANSFER;   // Перевод между своими счетами / P2P-перевод

    companion object {
        // Синонимы для совместимости
        val EXPENSE get() = DEBIT
        val INCOME get() = CREDIT

        fun fromStringOrNull(raw: String?): TransactionType? {
            if (raw.isNullOrBlank()) return null
            val upper = raw.trim().uppercase()
            return when (upper) {
                "DEBIT", "EXPENSE" -> DEBIT
                "CREDIT", "INCOME" -> CREDIT
                "TRANSFER" -> TRANSFER
                else -> entries.firstOrNull { it.name == upper }
            }
        }
    }
}

/**
 * Статус банковской операции.
 */
enum class TransactionStatus {
    COMPLETED,  // Операция успешно проведена и подтверждена банком
    DECLINED;   // Отказ в авторизации / операция отклонена банком (недостаточно средств, лимит и т.д.)

    companion object {
        // Синоним для совместимости
        val SUCCESS get() = COMPLETED

        fun fromStringOrDefault(raw: String?, default: TransactionStatus = COMPLETED): TransactionStatus {
            if (raw.isNullOrBlank()) return default
            val upper = raw.trim().uppercase()
            return when (upper) {
                "COMPLETED", "SUCCESS" -> COMPLETED
                "DECLINED", "REFUZATA", "REJECTED", "FAILED" -> DECLINED
                else -> entries.firstOrNull { it.name == upper } ?: default
            }
        }
    }
}

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
    val createdAt: Instant = Instant.now()
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
}
```

---

### 2. Контракт финансовой экстракции `FinanceExtractor` и `ParsedFinanceResult`

```kotlin
package com.example.npc.core.model.extract

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Direction
import com.example.npc.core.model.finance.FinancialTransaction
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

/**
 * Контракт контекстного разрешения валюты.
 */
interface CurrencyResolver {
    fun resolve(token: String, contextPackage: String? = null): CurrencyCode?
}
```

---

### 3. Гарантии и инварианты контракта (GATE 3)
1. **Строго неделимые единицы:** Все суммы — `Long` (минорные единицы: копейки/центы), исключающие потерю точности IEEE 754.
2. **Исключение двойного знака:** `Money.minor` строго `>= 0L`. Расход/доход определяется `TransactionType.DEBIT` / `CREDIT`.
3. **Изоляция ПМР-рубля:** `CurrencyCode.RUP` («р.») никогда не передается в `java.util.Currency`.
4. **Безопасность ReDoS:** Экстракторы используют исключительно RE2/J и рукописный `AmountParser` с гарантией $O(n)$.
