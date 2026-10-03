# Спецификация: zone/core-model

## Модуль: :core:model   Зона: zone/core-model   Версия спеки: v2 (Фаза 1: «Хардкод-MVP»)   Статус: READY FOR IMPLEMENTATION

---

### 1. Назначение и контекст модуля

Модуль `:core:model` определяет базовую неизменяемую доменную модель данных предметной области и фундаментальные межмодульные контрактные интерфейсы системы **Notification Pipeline Constructor**.

В рамках **Фазы 1 («Хардкод-MVP: Семантическая классификация, изолированные экстракторы и Timeline 2.0»)** модуль расширяется моделями финансового домена, моделями пользовательских прототипов для обучения на обратной связи (Feedback Loop) и контрактами обработки событий:
1. **Финансовый домен:**
   - Высокоточный учет денежных средств в минимальных неделимых единицах (`amountMinor: Long`, копейки/центы) с математической защитой от переполнения (`Math.addExact`/`Math.subtractExact`).
   - Полный запрет отрицательных сумм в `amountMinor`: знак операции (дебет/кредит) однозначно определяется через отдельное перечисление `TransactionType` (EXPENSE, INCOME, TRANSFER), что устраняет архитектурный антипаттерн «двойного знака» (`- - 100.00`).
   - Нативная поддержка регионального валютного реестра: Приднестровский рубль (`RUP` — не входит в ISO-4217 и изолирован от `java.util.Currency` во избежание сбоев в Android/JVM рантайме), Молдавский лей (`MDL`), Российский рубль (`RUB`), Евро (`EUR`), Доллар США (`USD`).
   - Доменная сущность финансовой транзакции `FinancialTransaction` со статусом исполнения (`TransactionStatus`), балансом, контрагентом/мерчантом и версионированием парсера.
2. **Семантическая классификация и прототипы:**
   - Категоризация событий `Category` (FINANCE, COMMUNICATION, MUSIC, SERVICES, UNKNOWN).
   - Сущность пользовательского шаблона `UserPrototype` для закрепления пользовательской обратной связи (Prototype-First Resolution).
   - Результат классификации `ClassificationResult` с фиксацией движка (`Engine`: NONE, PROTOTYPE, RULES, USER), уровня уверенности (`confidence`) и SHA-256 отпечатка контента (`contentFingerprint`).
3. **Контрактные интерфейсы Фазы 1:**
   - `SemanticClassifier`: контракт детерминированной двухфазной классификации событий.
   - `FinanceExtractor`: контракт изолированного извлечения финансовых параметров из событий банковских источников.
   - `CurrencyResolver`: контракт контекстно-зависимого разрешения строковых маркеров валюты с учетом пакета приложения-источника.
4. **Сохранение фундамента Фазы 0:**
   - Полная обратная совместимость (Zero Breaking Changes) для сущностей `RawEvent`, `Event`, `SourceHealth`, типов `SourceId`, `ThreadKey`, `DeduplicationKey`, `EmbeddingRef`, `Lang` и объекта нормализации `EventNormalizer`.

#### Архитектурные ограничения и инварианты модуля:
- **Pure Kotlin JVM:** модуль компилируется стандартным Kotlin/JVM плагином (`kotlin("jvm")`).
- **Строгий запрет зависимостей от Android SDK:** категорически запрещены импорты `android.*`, `androidx.*`, доступ к `android.content.Context` или Android UI.
- **Строгий запрет зависимостей от Room и БД:** модуль не содержит аннотаций `@Entity`, `@Dao`, `@PrimaryKey`, зависимостей от Room KSP или SQLite/SQLCipher.
- **Листовой модуль (Leaf module):** `:core:model` не зависит ни от одного другого проектного модуля (`:core:storage`, `:classify:rules`, `:extract:finance`, `:ingest:*`, `:ui:*`, `:app`).
- **Иммутабельность:** все поля сущностей объявляются как `val`. Изменяемое состояние (`var`) запрещено.

---

### 2. Структура пакетов и файлов

Все классы, перечисления, структуры и интерфейсы модуля размещаются в пакете `com.example.npc.core.model`:

```
core/model/src/main/kotlin/com/example/npc/core/model/
├── Category.kt                     // [Продвинут] Enum доменных категорий (FINANCE, COMMUNICATION, MUSIC, SERVICES, UNKNOWN)
├── CurrencyCode.kt                 // [NEW] Value class кода валюты (RUP, MDL, RUB, EUR, USD)
├── Money.kt                        // [NEW] Data/value class денег: amountMinor, currency
├── TransactionType.kt              // [NEW] Enum типа транзакции: EXPENSE, INCOME, TRANSFER
├── TransactionStatus.kt            // [NEW] Enum статуса транзакции: SUCCESS, DECLINED
├── FinancialTransaction.kt         // [NEW] Data class финансовой транзакции
├── UserPrototype.kt                // [NEW] Data class пользовательского шаблона обратной связи
├── Event.kt                        // [Phase 0] Структурированное доменное событие
├── RawEvent.kt                     // [Phase 0] Сырое входящее событие
├── SourceHealth.kt                 // [Phase 0] Метрика здоровья источника
├── SourceId.kt                     // [Phase 0] Value class идентификатора источника
├── ThreadKey.kt                    // [Phase 0] Value class ключа треда
├── DeduplicationKey.kt             // [Phase 0] Value class хеш-ключа дедупликации
├── EmbeddingRef.kt                 // [Phase 0] Value class ссылки на векторный эмбеддинг
├── Lang.kt                         // [Phase 0] Enum языка (RU, EN, UNK)
├── classify/
│   ├── Engine.kt                   // [NEW] Enum механизма классификации: NONE, PROTOTYPE, RULES, USER
│   ├── ClassificationResult.kt     // [NEW] Data class результата классификации
│   └── SemanticClassifier.kt       // [NEW] Интерфейс контракта семантического классификатора
├── extract/
│   ├── FinanceExtractor.kt         // [NEW] Интерфейс контракта финансового экстрактора
│   └── CurrencyResolver.kt         // [NEW] Интерфейс контракта контекстного резолвера валют
└── normalize/
    └── EventNormalizer.kt          // [Phase 0] Детерминированная очистка текста и расчет хешей
```

---

### 3. Спецификация доменных моделей и перечислений

#### 3.1 `Category`

- **Пакет:** `com.example.npc.core.model`
- **Файл:** `core/model/src/main/kotlin/com/example/npc/core/model/Category.kt`
- **Назначение:** Доменная категоризация входящих событий, уведомлений и транзакций в системе.

```kotlin
package com.example.npc.core.model

/**
 * Доменная категоризация захваченных событий и уведомлений.
 */
enum class Category {
    FINANCE,        // Банковские операции, SMS-банкинг, чеки, баланс, переводы
    COMMUNICATION,  // Личные и групповые мессенджеры, SMS-переписка, почта, звонки
    MUSIC,          // Мультимедиа, воспроизведение аудио/видео треков
    SERVICES,       // Сервисные уведомления, доставка, такси, системные статусы, утилиты
    UNKNOWN;        // Неопределенная категория / спам / рекламные рассылки без полезной нагрузки

    companion object {
        fun fromStringOrUnknown(raw: String?): Category {
            if (raw.isNullOrBlank()) return UNKNOWN
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: UNKNOWN
        }
    }
}
```

- **Семантика значений:**
  - `FINANCE`: транзакции, изменения баланса, банковские пуши, сервисные коды авторизации операций.
  - `COMMUNICATION`: Telegram, WhatsApp, Viber, SMS от контактов.
  - `MUSIC`: плееры (Яндекс.Музыка, Spotify, VK Музыка), медиасессии.
  - `SERVICES`: Wildberries, Ozon, такси, доставка еды, ЖКХ, погодные информеры.
  - `UNKNOWN`: информационный шум, реклама, спам, неклассифицированные сообщения.

---

#### 3.2 `CurrencyCode`

- **Пакет:** `com.example.npc.core.model`
- **Файл:** `core/model/src/main/kotlin/com/example/npc/core/model/CurrencyCode.kt`
- **Назначение:** Строго типизированный `value class` кода валюты. Обеспечивает нулевой runtime overhead в JVM и валидацию регионального реестра валют.

```kotlin
package com.example.npc.core.model

/**
 * Закрытый строго типизированный код валюты.
 * Поддерживает региональные валюты (включая Приднестровский рубль RUP,
 * отсутствующий в стандартном ISO-4217 java.util.Currency).
 */
@JvmInline
value class CurrencyCode(val value: String) {

    init {
        require(value in SUPPORTED_CODES) {
            "Unsupported currency code: '$value'. Supported codes are: $SUPPORTED_CODES"
        }
    }

    /** Количество знаков в дробной части (копейки, центы). Для всех 5 поддерживаемых равно 2. */
    val minorDigits: Int get() = 2

    /** Общепринятый типографический символ валюты. */
    val symbol: String
        get() = when (value) {
            CODE_RUP -> "р."
            CODE_MDL -> "L"
            CODE_RUB -> "₽"
            CODE_EUR -> "€"
            CODE_USD -> "$"
            else -> value
        }

    /** Признак соответствия международному стандарту ISO-4217. */
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

        /**
         * Фабричный метод получения CurrencyCode с приведением к верхнему регистру.
         * @throws IllegalArgumentException если код валюты не поддерживается.
         */
        fun of(code: String): CurrencyCode = CurrencyCode(code.trim().uppercase())

        /**
         * Безопасный фабричный метод получения CurrencyCode или null при несовпадении.
         */
        fun ofOrNull(code: String?): CurrencyCode? {
            if (code.isNullOrBlank()) return null
            val upper = code.trim().uppercase()
            return if (upper in SUPPORTED_CODES) CurrencyCode(upper) else null
        }
    }
}
```

- **Инварианты и валидация:**
  - `value` должен строго входить в `SUPPORTED_CODES` (`{"RUP", "MDL", "RUB", "EUR", "USD"}`).
  - При попытке создания `CurrencyCode("XYZ")` выбрасывается `IllegalArgumentException`.
  - Регистронезависимый парсинг через фабричные методы `of` и `ofOrNull`.

---

#### 3.3 `Money`

- **Пакет:** `com.example.npc.core.model`
- **Файл:** `core/model/src/main/kotlin/com/example/npc/core/model/Money.kt`
- **Назначение:** Неизменяемый класс представления денежных сумм. Хранит значение строго в целых неделимых единицах (`amountMinor: Long`, копейки/центы), исключая ошибки округления чисел с плавающей точкой (`Double`/`Float`).
- **Инвариант знака:** Сумма `amountMinor` **всегда строго неотрицательна** (`amountMinor >= 0L`). Направление операции (списание/пополнение) вынесено в `TransactionType`.

```kotlin
package com.example.npc.core.model

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Неотрицательная денежная сумма в неделимых единицах (копейки, центы).
 *
 * Инвариант: amountMinor >= 0L. Знак операции задается внешним TransactionType.
 * Арифметика защищена от переполнения Long через Math.addExact / Math.subtractExact.
 */
data class Money(
    val amountMinor: Long,
    val currency: CurrencyCode
) : Comparable<Money> {

    init {
        require(amountMinor >= 0L) {
            "Money amountMinor must be non-negative (got $amountMinor). Operation direction is determined via TransactionType."
        }
    }

    /**
     * Безопасное сложение сумм одной валюты с проверкой на переполнение Long.
     * @throws IllegalArgumentException при несовпадении валют.
     * @throws ArithmeticException при переполнении Long.MAX_VALUE.
     */
    operator fun plus(other: Money): Money {
        checkSameCurrency(other)
        return Money(Math.addExact(amountMinor, other.amountMinor), currency)
    }

    /**
     * Безопасное вычитание сумм одной валюты.
     * @throws IllegalArgumentException при несовпадении валют или если результат становится отрицательным.
     * @throws ArithmeticException при переполнении Long.MIN_VALUE.
     */
    operator fun minus(other: Money): Money {
        checkSameCurrency(other)
        val result = Math.subtractExact(amountMinor, other.amountMinor)
        require(result >= 0L) {
            "Subtraction would result in negative Money amount: $amountMinor - ${other.amountMinor} = $result"
        }
        return Money(result, currency)
    }

    override fun compareTo(other: Money): Int {
        checkSameCurrency(other)
        return amountMinor.compareTo(other.amountMinor)
    }

    /**
     * Преобразование в мажорные единицы (рубли, леи, доллары) в виде BigDecimal.
     */
    fun toMajorBigDecimal(): BigDecimal =
        BigDecimal.valueOf(amountMinor, currency.minorDigits)

    /**
     * Форматированный вывод для UI и логов (например: "150.50 р.", "49.00 $").
     */
    fun formatDisplay(): String {
        val majorStr = toMajorBigDecimal()
            .setScale(currency.minorDigits, RoundingMode.UNNECESSARY)
            .toPlainString()
        return "$majorStr ${currency.symbol}"
    }

    private fun checkSameCurrency(other: Money) {
        require(currency == other.currency) {
            "Currency mismatch: cannot perform operation between $currency and ${other.currency}"
        }
    }

    companion object {
        val ZERO_RUP = Money(0L, CurrencyCode.RUP)
        val ZERO_MDL = Money(0L, CurrencyCode.MDL)
        val ZERO_RUB = Money(0L, CurrencyCode.RUB)
        val ZERO_EUR = Money(0L, CurrencyCode.EUR)
        val ZERO_USD = Money(0L, CurrencyCode.USD)

        /**
         * Фабричный метод создания Money из мажорных единиц (например, BigDecimal("12.50")).
         */
        fun ofMajor(major: BigDecimal, currency: CurrencyCode): Money {
            require(major.signum() >= 0) { "Major amount must be non-negative: $major" }
            val scaled = major.setScale(currency.minorDigits, RoundingMode.UNNECESSARY)
            return Money(scaled.unscaledValue().longValueExact(), currency)
        }

        /**
         * Фабричный метод создания Money из минорных единиц.
         */
        fun ofMinor(amountMinor: Long, currency: CurrencyCode): Money =
            Money(amountMinor, currency)
    }
}
```

- **Инварианты и безопасность:**
  - `amountMinor >= 0L`: гарантирует невозможность состояния с отрицательными деньгами.
  - `checkSameCurrency`: сложение `Money(100, RUP) + Money(100, MDL)` немедленно выбрасывает `IllegalArgumentException`.
  - Защита от переполнения: `Math.addExact(Long.MAX_VALUE, 1L)` выбрасывает `ArithmeticException`.
  - Вычитание большего из меньшего: `Money(50, RUP) - Money(60, RUP)` выбрасывает `IllegalArgumentException`.

---

#### 3.4 `TransactionType`

- **Пакет:** `com.example.npc.core.model`
- **Файл:** `core/model/src/main/kotlin/com/example/npc/core/model/TransactionType.kt`
- **Назначение:** Доменная классификация финансовой направленности операции.

```kotlin
package com.example.npc.core.model

/**
 * Тип финансовой операции (направление движения денежных средств).
 */
enum class TransactionType {
    EXPENSE,   // Списание, покупка, оплата услуг, комиссия банка (дебет)
    INCOME,    // Зачисление, зарплата, входящий перевод, кэшбэк (кредит)
    TRANSFER;  // Перевод между своими счетами или P2P перевод

    companion object {
        fun fromStringOrNull(raw: String?): TransactionType? {
            if (raw.isNullOrBlank()) return null
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
        }
    }
}
```

---

#### 3.5 `TransactionStatus`

- **Пакет:** `com.example.npc.core.model`
- **Файл:** `core/model/src/main/kotlin/com/example/npc/core/model/TransactionStatus.kt`
- **Назначение:** Статус авторизации/исполнения банковской транзакции.

```kotlin
package com.example.npc.core.model

/**
 * Статус банковской операции.
 */
enum class TransactionStatus {
    SUCCESS,   // Операция успешно выполнена / авторизована
    DECLINED;  // Отказ / отклонена (недостаточно средств, лимит, блокировка, сбой авторизации)

    companion object {
        fun fromStringOrDefault(raw: String?, default: TransactionStatus = SUCCESS): TransactionStatus {
            if (raw.isNullOrBlank()) return default
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: default
        }
    }
}
```

---

#### 3.6 `FinancialTransaction`

- **Пакет:** `com.example.npc.core.model`
- **Файл:** `core/model/src/main/kotlin/com/example/npc/core/model/FinancialTransaction.kt`
- **Назначение:** Доменная модель финансовой транзакции, извлеченной из банковского push-уведомления или SMS.

```kotlin
package com.example.npc.core.model

import java.time.Instant

/**
 * Структурированная финансовая транзакция, извлеченная изолированным парсером.
 *
 * @property id Первичный идентификатор (0L до сохранения в хранилище).
 * @property eventId Идентификатор связанного структурированного события Event (nullable FK).
 * @property type Направление операции (EXPENSE, INCOME, TRANSFER).
 * @property money Неотрицательная сумма операции и валюта.
 * @property balance Доступный остаток счета после проведения операции (если указан в пуше).
 * @property counterparty Контрагент операции: получатель перевода, мерчант/магазин, банк или сервис.
 * @property status Статус выполнения операции (SUCCESS, DECLINED).
 * @property parserVersion Версия парсера-экстрактора, извлекшего транзакцию.
 * @property rawText Исходный сырой текст уведомления/SMS, из которого извлечена транзакция.
 * @property timestamp Точный момент времени совершения операции (из текста или времени события).
 * @property createdAt Момент сохранения записи в систему.
 */
data class FinancialTransaction(
    val id: Long = 0L,
    val eventId: Long?,
    val type: TransactionType,
    val money: Money,
    val balance: Money?,
    val counterparty: String?,
    val status: TransactionStatus,
    val parserVersion: Int,
    val rawText: String,
    val timestamp: Instant,
    val createdAt: Instant = Instant.now()
) {
    init {
        require(id >= 0L) { "FinancialTransaction id must be >= 0 (got $id)" }
        require(eventId == null || eventId > 0L) { "eventId must be > 0 if specified (got $eventId)" }
        require(parserVersion >= 1) { "parserVersion must be >= 1 (got $parserVersion)" }
        require(rawText.isNotBlank()) { "rawText must not be blank" }
        require(counterparty == null || counterparty.isNotBlank()) { "counterparty must not be blank if specified" }
        if (balance != null) {
            require(balance.currency == money.currency) {
                "Balance currency (${balance.currency}) must match transaction currency (${money.currency})"
            }
        }
    }
}
```

- **Инварианты и валидация:**
  - `id >= 0L`: значение `0L` означает transient состояние (до персистенции в Room).
  - `eventId`: если указан, должен быть strictly positive (`> 0L`). Nullable для поддержки транзакций, импортированных независимо от событийной таблицы.
  - `parserVersion >= 1`: версия экстрактора для сценариев повторного парсинга при обновлении правил.
  - `rawText.isNotBlank()`: исходный текст сохраняется для аудита и ReDoS-безопасного дебага.
  - Если передан `balance`, его валюта обязана совпадать с валютой операции `money.currency`.

---

#### 3.7 `UserPrototype`

- **Пакет:** `com.example.npc.core.model`
- **Файл:** `core/model/src/main/kotlin/com/example/npc/core/model/UserPrototype.kt`
- **Назначение:** Шаблон пользовательской обратной связи. Фиксирует корректировку категории пользователем и обучаемость конвейера (Prototype-First Resolution).

```kotlin
package com.example.npc.core.model

import java.time.Instant

/**
 * Пользовательский прототип (шаблон обратной связи).
 *
 * При совпадении отпечатка входящего события с прототипом, имеющим supportCount >= 2,
 * классификатор присваивает assignedCategory с confidence = 1.0 (Engine.PROTOTYPE).
 *
 * @property id Первичный суррогатный ключ (0L до сохранения).
 * @property targetPackage Имя Android-пакета целевого приложения (например: "org.telegram.messenger").
 * @property patternOrTitle SHA-256 отпечаток шаблона контента либо нормализованный заголовок.
 * @property assignedCategory Категория, назначенная пользователем для данного шаблона.
 * @property supportCount Количество подтверждений пользователем (инкрементируется при каждом фидбеке).
 * @property createdAt Момент первой ручной классификации.
 * @property updatedAt Момент последнего подтверждения пользователем.
 */
data class UserPrototype(
    val id: Long = 0L,
    val targetPackage: String,
    val patternOrTitle: String,
    val assignedCategory: Category,
    val supportCount: Int,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(id >= 0L) { "UserPrototype id must be >= 0 (got $id)" }
        require(targetPackage.isNotBlank()) { "targetPackage must not be blank" }
        require(patternOrTitle.isNotBlank()) { "patternOrTitle must not be blank" }
        require(supportCount >= 1) { "supportCount must be at least 1 (got $supportCount)" }
        require(!createdAt.isAfter(updatedAt)) { "createdAt ($createdAt) cannot be after updatedAt ($updatedAt)" }
    }

    /** Проверяет, достиг ли прототип порога доверия для безусловного применения. */
    val isConfident: Boolean get() = supportCount >= CONFIDENCE_THRESHOLD

    companion object {
        const val CONFIDENCE_THRESHOLD = 2
    }
}
```

- **Инварианты и валидация:**
  - `supportCount >= 1`: при создании первого фидбека счетчик равен 1.
  - `createdAt <= updatedAt`: временная консистентность.
  - Порог безусловного применения `CONFIDENCE_THRESHOLD = 2`.

---

### 4. Контрактные интерфейсы и сопутствующие модели

Модели классификации размещаются в подпакете `com.example.npc.core.model.classify`, модели экстракции — в `com.example.npc.core.model.extract`.

#### 4.1 Классификация: `Engine`, `ClassificationResult`, `SemanticClassifier`

##### `Engine` (`com.example.npc.core.model.classify.Engine`)

```kotlin
package com.example.npc.core.model.classify

/**
 * Механизм, определивший категорию события.
 */
enum class Engine {
    NONE,       // Категория не определена (дефолтная инициализация)
    PROTOTYPE,  // Определено по базе пользовательских прототипов (supportCount >= 2)
    RULES,      // Определено статическим детерминированным правилом
    USER        // Прямая ручная правка пользователем в UI Timeline
}
```

##### `ClassificationResult` (`com.example.npc.core.model.classify.ClassificationResult`)

```kotlin
package com.example.npc.core.model.classify

import com.example.npc.core.model.Category

/**
 * Результат работы семантического классификатора.
 *
 * @property category Назначенная категория.
 * @property confidence Оценка уверенности от 0.0 (полная неопределенность) до 1.0 (абсолютная уверенность).
 * @property engine Механизм, принявший решение.
 * @property contentFingerprint 64-символьный SHA-256 хеш маскированного текста события.
 */
data class ClassificationResult(
    val category: Category,
    val confidence: Double,
    val engine: Engine,
    val contentFingerprint: String
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be between 0.0 and 1.0 (got $confidence)" }
        require(contentFingerprint.isNotBlank()) { "contentFingerprint must not be blank" }
    }

    companion object {
        fun unclassified(fingerprint: String): ClassificationResult =
            ClassificationResult(
                category = Category.UNKNOWN,
                confidence = 0.0,
                engine = Engine.NONE,
                contentFingerprint = fingerprint
            )
    }
}
```

##### `SemanticClassifier` (`com.example.npc.core.model.classify.SemanticClassifier`)

```kotlin
package com.example.npc.core.model.classify

import com.example.npc.core.model.Event
import com.example.npc.core.model.UserPrototype

/**
 * Контракт семантического классификатора событий.
 *
 * Реализует двухфазную чистую детерминированную классификацию:
 * 1. Prototype-First: если передан прототип с supportCount >= 2, возвращает его категорию с confidence = 1.0.
 * 2. Static Rules: если прототип не найден, применяет правила эвристики на основе пакета и текста.
 */
interface SemanticClassifier {

    /**
     * Классифицирует структурированное событие с учетом опционального найденного прототипа.
     *
     * @param event Структурированное событие Event.
     * @param prototype Пользовательский прототип (если найден в базе для отпечатка события), иначе null.
     * @return Результат классификации ClassificationResult.
     */
    fun classify(event: Event, prototype: UserPrototype?): ClassificationResult
}
```

---

#### 4.2 Экстракция: `FinanceExtractor`, `CurrencyResolver`

##### `CurrencyResolver` (`com.example.npc.core.model.extract.CurrencyResolver`)

```kotlin
package com.example.npc.core.model.extract

import com.example.npc.core.model.CurrencyCode

/**
 * Контракт контекстно-зависимого разрешения строковых обозначений валют.
 *
 * Учитывает региональный контекст пакета приложения:
 * - В контексте банков ПМР (Агропромбанк, Сбербанк ПМР) маркеры "руб", "р.", "руб."
 *   однозначно разрешаются в CurrencyCode.RUP.
 * - В контексте банков РФ (Сбербанк, Тинькофф) аналогичные маркеры разрешаются в CurrencyCode.RUB.
 * - В контексте молдавских банков (MAIB) маркеры "лей", "MDL", "L" разрешаются в CurrencyCode.MDL.
 */
interface CurrencyResolver {

    /**
     * Разрешает токен валюты в CurrencyCode с учетом пакета приложения-источника.
     *
     * @param token Строковый токен валюты из текста (например: "руб", "$", "EUR", "MDL").
     * @param contextPackage Имя пакета приложения-источника (например: "com.agroprombank.mobile").
     * @return Разрешенный CurrencyCode или null, если токен не распознан.
     */
    fun resolve(token: String, contextPackage: String): CurrencyCode?
}
```

##### `FinanceExtractor` (`com.example.npc.core.model.extract.FinanceExtractor`)

```kotlin
package com.example.npc.core.model.extract

import com.example.npc.core.model.Event
import com.example.npc.core.model.FinancialTransaction

/**
 * Контракт изолированного извлечения финансовой транзакции из входящего события.
 *
 * Каждый экстрактор отвечает за конкретный банк/источник (например: Агропромбанк, Сбербанк ПМР, MAIB, SMS).
 */
interface FinanceExtractor {

    /** Уникальный строковый идентификатор экстрактора (например: "apb.push", "prisbank.push", "maib.push"). */
    val id: String

    /** Версия логики экстрактора. При изменении правил версия инкрементируется. */
    val version: Int

    /**
     * Выполняет извлечение параметров транзакции из события.
     *
     * @param event Входящее структурированное событие Event.
     * @return Извлеченная FinancialTransaction или null, если событие не содержит валидной транзакции
     *         (например: балансовый запрос, код двухфакторной аутентификации, информационный пуш).
     */
    fun extract(event: Event): FinancialTransaction?
}
```

---

### 5. Сохранение моделей Фазы 0 (Zero Breaking Changes)

Для гарантированного прохождения существующих тестов (`DomainEntitiesTest.kt`, `ValueTypesTest.kt`, `EventNormalizer*Test.kt`) и сохранности захваченных данных Dogfooding на Poco M7, все ранее специфицированные модели Фазы 0 остаются полностью валидными:

#### 5.1 `SourceId`, `ThreadKey`, `DeduplicationKey`, `EmbeddingRef`, `Lang`

```kotlin
package com.example.npc.core.model

@JvmInline
value class SourceId(val value: String) {
    init {
        require(value.matches(VALID_REGEX)) { "SourceId must match ^[a-zA-Z0-9_-]{1,64}$, but was: '$value'" }
    }
    companion object {
        private val VALID_REGEX = Regex("^[a-zA-Z0-9_-]{1,64}$")
        val NOTIFICATION = SourceId("notification")
        val SMS = SourceId("sms")
        val MEDIA = SourceId("media")
    }
}

@JvmInline
value class ThreadKey(val value: String) {
    init {
        require(value.isNotBlank() && value.length in 1..256) {
            "ThreadKey must not be blank and length in 1..256, but was length: ${value.length}"
        }
    }
}

@JvmInline
value class DeduplicationKey(val value: String) {
    init {
        require(value.matches(HEX_REGEX)) {
            "DeduplicationKey must be 64-char lowercase hex SHA-256, but was: '$value'"
        }
    }
    companion object {
        private val HEX_REGEX = Regex("^[0-9a-f]{64}$")
    }
}

@JvmInline
value class EmbeddingRef(val vectorId: String) {
    init {
        require(vectorId.isNotBlank() && vectorId.length in 1..128) {
            "EmbeddingRef vectorId must not be blank and length in 1..128"
        }
    }
}

enum class Lang {
    RU,
    EN,
    UNK
}
```

#### 5.2 `RawEvent`, `Event`, `SourceHealth`

```kotlin
package com.example.npc.core.model

import java.time.Instant

data class RawEvent(
    val id: Long = 0L,
    val seq: Long,
    val source: SourceId,
    val packageName: String,
    val receivedAt: Instant,
    val payloadJson: String,
    val hash: DeduplicationKey
) {
    init {
        require(id >= 0L) { "id must be >= 0" }
        require(seq >= 0L) { "seq must be >= 0" }
        require(packageName.isNotBlank() && packageName.length in 1..255) { "packageName must not be blank" }
        require(payloadJson.isNotBlank()) { "payloadJson must not be blank" }
    }
}

data class Event(
    val id: Long = 0L,
    val rawId: Long,
    val ts: Instant,
    val title: String,
    val text: String,
    val normalizedText: String,
    val lang: Lang,
    val threadKey: ThreadKey? = null,
    val isUpdateOf: Long? = null
) {
    init {
        require(id >= 0L) { "id must be >= 0" }
        require(rawId >= 0L) { "rawId must be >= 0" }
        require(isUpdateOf == null || isUpdateOf > 0L) { "isUpdateOf must be > 0 if specified" }
    }
}

data class SourceHealth(
    val source: SourceId,
    val lastEventAt: Instant?,
    val events24h: Int,
    val lastError: String?,
    val queueDepth: Int
) {
    init {
        require(events24h >= 0) { "events24h must be >= 0" }
        require(queueDepth >= 0) { "queueDepth must be >= 0" }
        require(lastError == null || (lastError.isNotBlank() && lastError.length <= 1000)) {
            "lastError length must be <= 1000"
        }
    }
}
```

#### 5.3 `EventNormalizer` (`com.example.npc.core.model.normalize`)

Объект `EventNormalizer` сохраняет все 4 функции:
- `cleanText(rawText: String?): String` — удаление Unicode control characters, схлопывание пробелов и переносов строк.
- `detectLang(text: String): Lang` — $O(N)$ эвристическое распознавание языка (порог 70% кириллицы/латиницы).
- `computeDeduplicationKey(source: SourceId, packageName: String, payloadJson: String): DeduplicationKey` — расчет канонического SHA-256 хеша с исключением полей `postTime` и `when`.
- `normalize(rawEvent: RawEvent, title: String, text: String, threadKey: ThreadKey? = null, isUpdateOf: Long? = null): Event` — фабрика сущности `Event`.

---

### 6. Матрица валидации, инвариантов и обработки ошибок

| Сущность / Метод | Поле / Аргумент | Правило валидации | Тип ошибки при нарушении | Сообщение / Поведение |
|---|---|---|---|---|
| `CurrencyCode` | `value: String` | `value in {"RUP", "MDL", "RUB", "EUR", "USD"}` | `IllegalArgumentException` | `"Unsupported currency code: '$value'. Supported codes are: ..."` |
| `Money` | `amountMinor: Long` | `amountMinor >= 0L` | `IllegalArgumentException` | `"Money amountMinor must be non-negative (got ...)"` |
| `Money.plus` | `other: Money` | `currency == other.currency` | `IllegalArgumentException` | `"Currency mismatch: cannot perform operation between ..."` |
| `Money.plus` | `other: Money` | Не превышает `Long.MAX_VALUE` | `ArithmeticException` | `Math.addExact` overflow |
| `Money.minus` | `other: Money` | `currency == other.currency` | `IllegalArgumentException` | `"Currency mismatch: cannot perform operation between ..."` |
| `Money.minus` | `other: Money` | `amountMinor >= other.amountMinor` | `IllegalArgumentException` | `"Subtraction would result in negative Money amount: ..."` |
| `FinancialTransaction` | `id: Long` | `id >= 0L` | `IllegalArgumentException` | `"FinancialTransaction id must be >= 0"` |
| `FinancialTransaction` | `eventId: Long?` | `eventId == null \|\| eventId > 0L` | `IllegalArgumentException` | `"eventId must be > 0 if specified"` |
| `FinancialTransaction` | `parserVersion: Int` | `parserVersion >= 1` | `IllegalArgumentException` | `"parserVersion must be >= 1"` |
| `FinancialTransaction` | `balance: Money?` | `balance.currency == money.currency` | `IllegalArgumentException` | `"Balance currency (...) must match transaction currency (...)"` |
| `UserPrototype` | `supportCount: Int` | `supportCount >= 1` | `IllegalArgumentException` | `"supportCount must be at least 1"` |
| `UserPrototype` | Временные метки | `!createdAt.isAfter(updatedAt)` | `IllegalArgumentException` | `"createdAt (...) cannot be after updatedAt (...)"` |
| `ClassificationResult` | `confidence: Double`| `confidence in 0.0..1.0` | `IllegalArgumentException` | `"confidence must be between 0.0 and 1.0"` |

---

### 7. Сценарии тестирования (Acceptance Criteria)

Для реализации зоны `zone/core-model` Фазы 1 должны быть созданы следующие тестовые классы в `core/model/src/test/kotlin/com/example/npc/core/model/`:

1. **`CurrencyCodeTest`**:
   - Создание валидных кодов: `RUP`, `MDL`, `RUB`, `EUR`, `USD`.
   - Проверка `minorDigits == 2` для всех поддерживаемых валют.
   - Проверка `isIso4217 == false` для `RUP` и `true` для остальных.
   - Выброс `IllegalArgumentException` при создании неподдерживаемых валют (`GBP`, `JPY`, `XYZ`, `""`).
   - Регистронезависимость фабрик: `CurrencyCode.of("rup") == CurrencyCode.RUP`.
   - Безопасный метод `CurrencyCode.ofOrNull("invalid") == null`.
2. **`MoneyTest`**:
   - Успешное создание `Money(1500L, CurrencyCode.RUP)` (15.00 руб).
   - Выброс `IllegalArgumentException` при попытке создать отрицательную сумму `Money(-1L, CurrencyCode.RUP)`.
   - Сложение одинаковых валют: `Money(100L, RUP) + Money(200L, RUP) == Money(300L, RUP)`.
   - Ошибка сложения разных валют: `Money(100L, RUP) + Money(100L, MDL)` -> `IllegalArgumentException`.
   - Переполнение сложения: `Money(Long.MAX_VALUE, RUP) + Money(1L, RUP)` -> `ArithmeticException`.
   - Корректное вычитание: `Money(500L, RUB) - Money(200L, RUB) == Money(300L, RUB)`.
   - Запрет отрицательного остатка: `Money(100L, RUB) - Money(200L, RUB)` -> `IllegalArgumentException`.
   - Форматирование: `Money(1550L, RUP).formatDisplay() == "15.50 р."`.
   - Фабрика `ofMajor(BigDecimal("10.50"), CurrencyCode.USD) == Money(1050L, CurrencyCode.USD)`.
3. **`FinancialTransactionTest`**:
   - Успешное создание транзакции с валидными параметрами.
   - Валидация совпадения валюты суммы и баланса.
   - Запрет пустых `rawText` и отрицательных `id`.
4. **`UserPrototypeTest`**:
   - Валидация `supportCount >= 1`.
   - Проверка свойства `isConfident`: `false` при `supportCount == 1`, `true` при `supportCount >= 2`.
   - Проверка инварианта дат `createdAt <= updatedAt`.
5. **`ContractsMockTest`**:
   - Проверка вызовов mock-реализаций `SemanticClassifier`, `FinanceExtractor`, `CurrencyResolver` на чистом JVM.
