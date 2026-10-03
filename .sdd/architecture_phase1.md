# Architecture: Phase 1 — «Хардкод-MVP: Семантическая классификация, изолированные экстракторы и Timeline 2.0»

**TASK_ID:** ARCH-002  
**Status:** APPROVED (Architecture Review with Claude Opus 5.5 completed)  
**Date:** 2026-09-27  
**Author:** Главный Архитектор проекта Notification Pipeline Constructor  
**Target Device / Telemetry Baseline:** Poco M7 (HyperOS, Android 14/15, Device ID: `2440cbe2`), 44.5 ч телеметрии (785 событий, 137 транзакций в `event_engine.db`)  
**Stack:** Android minSdk 26 / targetSdk 35 · Kotlin 2.0 · Jetpack Compose · Coroutines & Flow · Room 2.6+ + SQLCipher 4.5.4 + Android Keystore · RE2/J (O(n) Regex Engine) · Hilt · Gradle KTS (build-logic convention plugins)

---

## 1. Executive Summary & Контекст перехода Фаза 0 → Фаза 1

В ходе 44.5-часового непрерывного цикла dogfooding на боевом смартфоне Poco M7 (`event_engine.db`, 785 событий) фундамент захвата данных Фазы 0 продемонстрировал **100% надёжность удержания процесса и захвата входящих потоков** (DoD 1, DoD 2, DoD 5 выполнены). 

Однако глубокий аудит выявил два критических архитектурных дефекта текущей реализации:
1. **Глобальный неизолированный парсинг (Ungated Regex Matching):**  
   Из 137 финансовых транзакций **83.2% (114 записей) оказались ложноположительными (False Positives)**. Спам турагентства InTour из Telegram (113 раз) распарсился как доход `1.00 RUB` из-за подстроки *«вылет из Кишинева... от 499 евро...»*, а пуш Яндекс.Погоды *«+10°C»* — как доход `10.00 RUB`.
2. **Игнорирование пользовательской обратной связи (Prototypes Feedback Inversion):**  
   Пользователь 6 раз вручную размечал спам InTour как `advertisement` (`support_count = 6` в таблице `prototypes`), однако жестко зашитый статический regex исполнялся **до** проверки прототипов, обесценивая действия пользователя.

### Цели Архитектуры Фазы 1:
- **Strict Package-Gated Pipelines:** Полная изоляция финансовых экстракторов по кортежу `(packageName, sender)`. Доменные парсеры исполняются только для авторизованных банковских источников (Агропромбанк ПМР, Приднестровский Сбербанк, MAIB Молдова, Банковские SMS).
- **Безусловный приоритет прототипов (Prototype-First Resolution):** При наличии пользовательского прототипа с `support_count >= 2` категория фиксируется с `confidence = 1.0`, а разметка `ADVERTISEMENT` / `SPAM` **немедленно блокирует** запуск любых финансовых экстракторов.
- **Безопасная Room-миграция v1 → v2 (Zero Data Loss):** Аддитивная миграция схемы базы данных со 100% сохранением накопленных данных Dogfooding на Poco M7.
- **Региональный финансовый движок:** Поддержка валюты `RUP` (Приднестровский рубль, отсутствует в ISO-4217), `MDL`, `RUB`, `EUR`, `USD`, хранение сумм строго в целочисленных копейках/центах (`Long`), банковско-зависимый резолвинг валюты.
- **ReDoS Protection:** Защита от катастрофического бэктрекинга регулярных выражений на базе чистого линейного движка `RE2/J` (гарантия O(n)) и Circuit Breaker.
- **Timeline 2.0:** Обогащение UI ленты финансовыми карточками, статусами транзакций, бейджами категорий и встроенным диалогом коррекции (Feedback Loop).

---

## 2. Карта модулей Фазы 1

### 2.1 Таблица модулей

| Модуль | Gradle-плагин | Тип | Статус в Фазе 1 | Публичный API / Контракты | Ключевые классы |
|---|---|---|---|---|---|
| `:core:model` | `kotlin-jvm` | pure-JVM lib | **Модифицируемый** | `RawEvent`, `Event`, `Category`, `Engine`, `CurrencyCode`, `Money`, `Direction`, `FinancialTransaction`, `UserPrototype`, `ClassificationResult` | Data-классы, value-классы, enum'ы, чистые интерфейсы контрактов |
| `:core:storage` | `kotlin-jvm` + Room KSP | pure-JVM lib | **Модифицируемый** | `StorageGateway`, `EventDao`, `RawEventDao`, `FinancialTransactionDao`, `UserPrototypeDao` | `AppDatabase` (v2), `MIGRATION_1_2`, `PreMigrationBackup`, `StorageGatewayImpl` |
| `:classify:rules` | `kotlin-jvm` | pure-JVM lib | **НОВЫЙ** | `SemanticClassifier`, `PackageGate`, `Fingerprinter`, `PrototypeStage` | `PackageGateImpl`, `Fingerprinter`, `RuleClassifierImpl`, `SemanticClassifierImpl` |
| `:extract:finance` | `kotlin-jvm` | pure-JVM lib | **НОВЫЙ** | `FinanceExtractor`, `ExtractionResult`, `ExtractorInput`, `CurrencyResolver` | `ApbExtractor`, `PrisbankExtractor`, `MaibExtractor`, `BankSmsExtractor`, `AmountParser`, `IsolatedExtractorRunner` |
| `:ingest:notification` | `android-library` | Android lib | Без изменений | `NotificationContract`, `NotificationMapper` | `PipelineNotificationListenerService`, `IngestWatchdog` |
| `:ingest:sms` | `android-library` | Android lib | Без изменений | `SmsContract`, `SmsMapper` | `SmsBroadcastReceiver`, `SmsPoller` |
| `:ingest:media` | `android-library` | Android lib | Без изменений | `MediaSessionContract` | `MediaSessionObserver`, `MediaSessionBoundaryDetector` |
| `:ui:timeline` | `android-library` + Compose | Android lib | **Модифицируемый** | `TimelineViewModel`, `TimelineUiState`, `TimelineEventUiModel`, `TimelineTransactionUiModel` | `TimelineScreen`, `TransactionCard`, `CategoryBadge`, `UserCorrectionDialog` |
| `:app` | `android-application` | App | **Модифицируемый** | — | `App`, `MainActivity`, `EventProcessingOrchestrator`, `AppModule`, `Hilt-компоненты` |

### 2.2 Граф зависимостей Фазы 1

```mermaid
graph TD
    subgraph UI & App Layer
        APP[":app"]
        UI[":ui:timeline"]
    end

    subgraph Processing Pipeline Layer
        CLASS[":classify:rules"]
        EXTR[":extract:finance"]
    end

    subgraph Ingest Layer (Phase 0)
        NOTIF[":ingest:notification"]
        SMS[":ingest:sms"]
        MEDIA[":ingest:media"]
    end

    subgraph Core Layer
        STORAGE[":core:storage"]
        MODEL[":core:model"]
    end

    APP --> UI
    APP --> CLASS
    APP --> EXTR
    APP --> NOTIF
    APP --> SMS
    APP --> MEDIA
    APP --> STORAGE

    UI --> STORAGE
    UI --> MODEL

    CLASS --> MODEL
    EXTR --> MODEL

    NOTIF --> STORAGE
    SMS --> STORAGE
    MEDIA --> STORAGE

    STORAGE --> MODEL
```

### 2.3 Архитектурные границы и изоляция
1. **Чистый Kotlin JVM для бизнес-логики (`:classify:rules`, `:extract:finance`, `:core:model`):**
   - Никаких зависимостей от `android.content.Context`, `android.os.*` или Android UI.
   - Быстрый запуск unit-тестов за миллисекунды на JVM без robolectric/эмулятора.
   - Изоляция алгоритмов классификации и экстракции от жизненного цикла Android.
2. **Листовой модуль `:core:model`:**
   - Не зависит ни от одного модуля системы.
   - Не содержит зависимостей от Room, SQLCipher, Coroutines Flow или сторонних библиотек.
3. **Прямой запрет взаимных зависимостей экстракторов и классификаторов:**
   - `:classify:rules` **НЕ зависит** от `:extract:finance`.
   - `:extract:finance` **НЕ зависит** от `:classify:rules`.
   - Оркестрацию их совместной работы выполняет слой приложения (`:app`) через `EventProcessingOrchestrator`.

---

## 3. Зоны ответственности (для параллельной спецификации)

Для параллельной разработки спецификаций и реализации выделено 5 изолированных зон ответственности:

### 3.1 zone/core-model
- **Область:** Доменные модели данных, иммутабельные сущности, контракты взаимодействия.
- **Включает:**
  - Value-классы `Money`, `MinorUnits`, `SourceId`, `ThreadKey`, `DeduplicationKey`.
  - Enum-реестры: `CurrencyCode` (нативная поддержка `RUP`, `MDL`, `RUB`, `EUR`, `USD`), `Direction` (`DEBIT`, `CREDIT`, `TRANSFER`), `Category` (`FINANCE`, `COMMUNICATION`, `TRANSPORT`, `SERVICES`, `ADVERTISEMENT`, `OTHER`, `UNCLASSIFIED`), `Engine` (`NONE`, `PROTOTYPE`, `RULES`, `USER`).
  - DTO: `Event`, `RawEvent`, `FinancialTransaction`, `UserPrototype`, `ClassificationResult`, `ExtractionResult`.
  - Контрактные интерфейсы: `SemanticClassifier`, `FinanceExtractor`, `CurrencyResolver`.
- **Запрещено:** Зависимости от Android SDK, Room аннотаций, сторонних I/O библиотек.

### 3.2 zone/core-storage
- **Область:** Персистентное хранилище Room + SQLCipher, миграции схемы, транзакционные шлюзы.
- **Включает:**
  - `AppDatabase` (схема v2).
  - Room Entities: `EventEntity` (v2), `FinancialTransactionEntity`, `UserPrototypeEntity`, `RawEventEntity`, `SourceHealthEntity`.
  - DAO: `EventDao`, `RawEventDao`, `FinancialTransactionDao`, `UserPrototypeDao`, `SourceHealthDao`.
  - Миграция `MIGRATION_1_2` с валидацией `PRAGMA foreign_key_check`.
  - Резервное копирование `PreMigrationBackup` перед миграцией.
  - Реализация `StorageGatewayImpl` с поддержкой транзакционной записи событий и транзакций, а также реактивных потоков `Flow`.
- **Запрещено:** Бизнес-логика парсинга текста, прямое выставление Room DAO наружу (только через `StorageGateway`).

### 3.3 zone/classify-rules
- **Область:** Семантическая классификация, гейтинг источников, генерация отпечатков и матчинг прототипов.
- **Включает:**
  - `PackageGate`: валидация источников по кортежу `(packageName, sender)`.
  - `Fingerprinter`: O(n) нормализация текста в шаблон с маскированием цифр/дат/сумм и вычислением SHA-256 хэша отпечатка.
  - `PrototypeStage`: сопоставление отпечатка с БД пользовательских прототипов; при `support_count >= 2` — мгновенное присвоение категории с `confidence = 1.0` (`Engine.PROTOTYPE`).
  - `RuleClassifier`: эвристические правила классификации для первичных уведомлений.
  - `SemanticClassifierImpl`: координатор этапов классификации.
- **Запрещено:** Прямой вызов финансовых парсеров, прямое чтение сырого диска.

### 3.4 zone/extract-finance
- **Область:** Изолированное извлечение финансовых параметров из банковских пушей и SMS.
- **Включает:**
  - `CurrencyResolver`: контекстно-зависимый резолвинг валюты (в контексте Агропромбанка и Сбербанка ПМР токен «руб» однозначно резолвится как `RUP`).
  - `AmountParser`: линейный O(n) парсер числовых сумм без регулярных выражений (поддержка запятых, точек, неразрывных пробелов NBSP `\u00A0` и Narrow NBSP `\u202F`).
  - Экстракторы: `ApbNotificationExtractor`, `PrisbankNotificationExtractor`, `MaibNotificationExtractor`, `BankSmsExtractor`.
  - Движок `RE2/J`: все regex-паттерны строятся строго на `com.google.re2j.Pattern` (гарантия линейного времени O(n), защита от ReDoS).
  - `IsolatedExtractorRunner`: запуск экстрактора с Circuit Breaker и контролем лимита времени (budget 50 ms).
- **Запрещено:** Доступ к базе данных, доступ к сети, использование стандартного `java.util.regex` для неконтролируемого пользовательского ввода.

### 3.5 zone/ui-timeline
- **Область:** Пользовательский интерфейс Timeline 2.0 на Jetpack Compose.
- **Включает:**
  - `TimelineViewModel`, `TimelineUiState`.
  - Модели представления: `TimelineEventUiModel`, `TimelineTransactionUiModel`.
  - UI-компоненты: `TransactionCard` (сумма, направление расхода/дохода, локализованный символ валюты, мерчант), `CategoryBadge`, `PrototypeTag`.
  - Диалог ручной коррекции категории `UserCorrectionDialog`: отправка фидбека пользователя в 1 клик, инициирующая сохранение в `user_prototype`.
- **Запрещено:** Прямой доступ к Room DAO, модификация базы данных в обход `StorageGateway`.

---

## 4. Межзонные точки интеграции (Именованные контракты и DTO)

### 4.1 Финансовая модель и валютный реестр (`com.example.npc.core.model.finance`)

```kotlin
package com.example.npc.core.model.finance

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Закрытый реестр поддерживаемых валют.
 * Приднестровский рубль (RUP) изолирован от java.util.Currency во избежание IllegalArgumentException.
 */
enum class CurrencyCode(
    val code: String,          // ISO-код или внутренний тикер, стабилен в БД
    val minorDigits: Int,      // Количество знаков дробной части (копеек/центов)
    val isIso4217: Boolean,
    val symbol: String
) {
    RUP("RUP", 2, false, "р."),   // Приднестровский рубль (ПМР)
    MDL("MDL", 2, true, "L"),     // Молдавский лей
    RUB("RUB", 2, true, "₽"),     // Российский рубль
    EUR("EUR", 2, true, "€"),     // Евро
    USD("USD", 2, true, "$");     // Доллар США

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun of(code: String): CurrencyCode =
            byCode[code.uppercase(Locale.ROOT)] ?: throw IllegalArgumentException("Unsupported currency code: '$code'")
        fun ofOrNull(code: String): CurrencyCode? = byCode[code.uppercase(Locale.ROOT)]
    }
}

enum class Direction {
    DEBIT,    // Списание / Покупка / Оплата услуг (EXPENSE)
    CREDIT,   // Пополнение / Зарплата / Входящий перевод (INCOME)
    TRANSFER  // Перевод между своими счетами / P2P-перевод (TRANSFER)
}

/**
 * Неотрицательная денежная сумма в минимальных неделимых единицах (копейки, центы).
 * Направление операции (DEBIT/CREDIT) вынесено отдельно для исключения ошибок двойного знака.
 */
data class Money(
    val minor: Long,
    val currency: CurrencyCode
) : Comparable<Money> {
    init {
        require(minor >= 0L) { "Money amount must be non-negative (got " + minor + "). Use Direction for debit/credit." }
    }

    operator fun plus(other: Money): Money {
        checkSameCurrency(other)
        return Money(Math.addExact(minor, other.minor), currency)
    }

    operator fun minus(other: Money): Money {
        checkSameCurrency(other)
        return Money(Math.subtractExact(minor, other.minor), currency)
    }

    override fun compareTo(other: Money): Int {
        checkSameCurrency(other)
        return minor.compareTo(other.minor)
    }

    fun toMajorBigDecimal(): BigDecimal = BigDecimal.valueOf(minor, currency.minorDigits)

    fun formatDisplay(): String {
        val major = toMajorBigDecimal().setScale(currency.minorDigits, RoundingMode.UNNECESSARY).toPlainString()
        return "$major ${currency.symbol}"
    }

    private fun checkSameCurrency(other: Money) {
        require(currency == other.currency) { "Currency mismatch: expected $currency, got ${other.currency}" }
    }

    companion object {
        fun ofMajor(major: BigDecimal, currency: CurrencyCode): Money {
            val scaled = major.setScale(currency.minorDigits, RoundingMode.UNNECESSARY)
            return Money(scaled.unscaledValue().longValueExact(), currency)
        }
    }
}
```

### 4.2 Контракт семантической классификации (`com.example.npc.core.model.classify`)

```kotlin
package com.example.npc.core.model.classify

import com.example.npc.core.model.Event

enum class Category {
    FINANCE,
    COMMUNICATION,
    TRANSPORT,
    SERVICES,
    ADVERTISEMENT,
    OTHER,
    UNCLASSIFIED
}

enum class Engine {
    NONE,
    PROTOTYPE,   // Решено на основе подтвержденного шаблона пользователя (support_count >= 2)
    RULES,       // Решено статическим детерминированным правилом
    USER         // Прямая ручная правка пользователя
}

data class ClassificationResult(
    val category: Category,
    val confidence: Double,
    val engine: Engine,
    val contentFingerprint: String
)

interface SemanticClassifier {
    /**
     * Выполняет двухфазную классификацию:
     * 1. Проверка пользовательских прототипов по fingerprint (приоритет!).
     * 2. Статические правила эвристики (если прототип не найден).
     */
    suspend fun classify(event: Event, packageName: String): ClassificationResult
}
```

### 4.3 Контракт финансовой экстракции (`com.example.npc.core.model.extract`)

```kotlin
package com.example.npc.core.model.extract

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Direction
import com.example.npc.core.model.finance.Money
import java.time.Instant

data class ExtractorInput(
    val text: String,
    val senderOrTitle: String?,
    val postedAt: Instant,
    val currencyResolver: CurrencyResolver
)

sealed interface ExtractionResult {
    data class Success(
        val direction: Direction,
        val amount: Money,
        val balance: Money?,
        val merchant: String?,
        val accountMask: String?
    ) : ExtractionResult

    /** Уведомление банковское, но не содержит транзакции (например: баланс, одноразовый пароль 2FA). */
    data object NotApplicable : ExtractionResult

    /** Ошибка парсинга структуры при валидном банке-отправителе. */
    data class Failed(val reason: String) : ExtractionResult
}

interface FinanceExtractor {
    val id: String           // Уникальный ID экстрактора (например: "apb.push", "maib.push", "prisbank.push")
    val version: Int         // Версия парсера; при инкременте триггерится ре-экстракция
    fun extract(input: ExtractorInput): ExtractionResult
}

interface CurrencyResolver {
    fun resolve(token: String): CurrencyCode?
}
```

### 4.4 Доменные модели транзакции и прототипа (`com.example.npc.core.model`)

```kotlin
package com.example.npc.core.model

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Direction
import com.example.npc.core.model.finance.Money
import java.time.Instant

data class FinancialTransaction(
    val id: Long = 0L,
    val eventId: Long?,             // Nullable FK на event.id (ON DELETE SET NULL)
    val bank: String,               // Идентификатор банка: "APB", "PRISBANK", "MAIB", "UNKNOWN"
    val direction: Direction,
    val amount: Money,
    val balance: Money?,
    val merchant: String?,
    val accountMask: String?,       // Например: "*1234"
    val occurredAt: Instant,
    val extractorId: String,
    val extractorVersion: Int,
    val createdAt: Instant = Instant.now()
)

data class UserPrototype(
    val id: Long = 0L,
    val packageName: String,
    val fingerprint: String,        // SHA-256 хэш шаблона текста
    val category: Category,
    val supportCount: Int,          // Количество подтверждений пользователем
    val createdAt: Instant,
    val lastSeenAt: Instant
)
```

### 4.5 Расширенный контракт `StorageGateway` (Фаза 1)

```kotlin
package com.example.npc.core.storage

import com.example.npc.core.model.*
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface StorageGateway {
    // --- Ingest (Фаза 0) ---
    suspend fun insertRawEvent(event: RawEvent): Long
    suspend fun upsertSourceHealth(health: SourceHealth)
    suspend fun findDuplicate(key: DeduplicationKey): Long?

    // --- Processing Pipeline (Фаза 1) ---
    /**
     * Атомарная транзакционная фиксация классифицированного события и опциональной финансовой транзакции.
     * При сбое экстрактора событие всё равно гарантированно сохраняется в БД.
     */
    suspend fun saveProcessedEvent(
        event: Event,
        classification: ClassificationResult,
        transaction: FinancialTransaction?
    ): Long

    // --- Feedback & Prototype Learning Loop ---
    /**
     * Запись ручной коррекции категории пользователем:
     * 1. Обновляет событие (category = newCategory, is_user_corrected = 1, engine_used = 'USER').
     * 2. Выполняет атомарный upsert прототипа в user_prototype (если совпал - supportCount + 1, если противоречие - сброс в 1).
     */
    suspend fun recordUserCorrection(
        eventId: Long,
        packageName: String,
        contentFingerprint: String,
        newCategory: Category,
        correctedAt: Instant = Instant.now()
    )

    suspend fun findMatchingPrototype(packageName: String, fingerprint: String): UserPrototype?

    // --- UI Timeline 2.0 (Реактивные потоки) ---
    fun observeEvents(limit: Int): Flow<List<Event>>
    fun observeTransactions(limit: Int): Flow<List<FinancialTransaction>>
    fun observeSourceHealth(): Flow<List<SourceHealth>>

    // --- Export / Maintenance ---
    suspend fun exportAllToJson(): String
    suspend fun deleteAllData()
}
```

---

## 5. Схема персистенции и безопасная Room-миграция (v1 → v2)

### 5.1 Гарантии 100% сохранности данных Dogfooding на Poco M7
При обновлении приложения на боевом устройстве Poco M7 **категорически запрещено** использовать `fallbackToDestructiveMigration()`.  
Накопленные данные в таблицах `raw_event`, `event` и `source_health` сохраняются в исходном виде благодаря строго аддитивной миграции.

### 5.2 Защитный снимок перед миграцией (`PreMigrationBackup`)
Для предотвращения риска повреждения БД при сбоях ОС/аккумулятора во время первого старта v2, до открытия Room создается резервная копия файла базы данных в каталоге `noBackupFilesDir`:

```kotlin
package com.example.npc.core.storage.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

object PreMigrationBackup {
    /** Вызывается в фоновом потоке ДО RoomDatabase.Builder.build(). */
    fun executeIfNeeded(context: Context, databaseName: String, targetVersion: Int) {
        val dbFile = context.getDatabasePath(databaseName).takeIf { it.exists() } ?: return
        
        val currentVersion = try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                // Сброс WAL-журнала в основной файл для консистентного снапшота
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                db.version
            }
        } catch (_: Throwable) {
            return
        }

        if (currentVersion in 1 until targetVersion) {
            val backupDir = context.noBackupFilesDir
            // Проверка свободного места (требуется запас минимум 2x размера базы)
            if (backupDir.usableSpace > dbFile.length() * 2) {
                val backupFile = File(backupDir, "$databaseName.v$currentVersion.bak")
                dbFile.copyTo(backupFile, overwrite = true)
            }
        }
    }
}
```

### 5.3 Исполняемый код `MIGRATION_1_2`

```kotlin
package com.example.npc.core.storage.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // =================================================================
        // 1. Таблица event: строго аддитивное добавление столбцов
        // =================================================================
        // Все NOT NULL столбцы снабжены константным DEFAULT, в точности
        // совпадающим с аннотацией @ColumnInfo(defaultValue = ...) в Entity.
        db.execSQL("ALTER TABLE `event` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'UNCLASSIFIED'")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `confidence` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `engine_used` TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `is_user_corrected` INTEGER NOT NULL DEFAULT 0")
        
        // content_fingerprint nullable, лениво рассчитывается WorkManager'ом
        db.execSQL("ALTER TABLE `event` ADD COLUMN `content_fingerprint` TEXT DEFAULT NULL")

        // Индексы для фильтрации в Timeline 2.0
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_category` ON `event` (`category`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_content_fingerprint` ON `event` (`content_fingerprint`)")

        // =================================================================
        // 2. Новая таблица: financial_transaction
        // =================================================================
        // Внешний ключ event_id имеет ON DELETE SET NULL, чтобы удаление
        // старых событий (retention) не уничтожало финансовый реестр расходов!
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `financial_transaction` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`event_id` INTEGER, " +
            "`bank` TEXT NOT NULL, " +
            "`direction` TEXT NOT NULL, " +
            "`amount_minor` INTEGER NOT NULL, " +
            "`currency` TEXT NOT NULL, " +
            "`balance_minor` INTEGER, " +
            "`balance_currency` TEXT, " +
            "`merchant` TEXT, " +
            "`account_mask` TEXT, " +
            "`occurred_at` INTEGER NOT NULL, " +
            "`extractor_id` TEXT NOT NULL, " +
            "`extractor_version` INTEGER NOT NULL, " +
            "`created_at` INTEGER NOT NULL, " +
            "FOREIGN KEY(`event_id`) REFERENCES `event`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL" +
            ")"
        )

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_transaction_event_id` ON `financial_transaction` (`event_id`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transaction_occurred_at` ON `financial_transaction` (`occurred_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_financial_transaction_bank` ON `financial_transaction` (`bank`)")

        // =================================================================
        // 3. Новая таблица: user_prototype (Feedback Loop)
        // =================================================================
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `user_prototype` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`package_name` TEXT NOT NULL, " +
            "`fingerprint` TEXT NOT NULL, " +
            "`category` TEXT NOT NULL, " +
            "`support_count` INTEGER NOT NULL DEFAULT 1, " +
            "`created_at` INTEGER NOT NULL, " +
            "`last_seen_at` INTEGER NOT NULL" +
            ")"
        )

        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_prototype_package_fingerprint` ON `user_prototype` (`package_name`, `fingerprint`)")

        // =================================================================
        // 4. Проверка целостности внешних ключей (Integrity Gate)
        // =================================================================
        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(cursor.count == 0) {
                "Foreign key integrity check failed after Migration 1->2. Violations count: ${cursor.count}"
            }
        }
    }
}
```

### 5.4 Room Entity v2: соответствие значений по умолчанию

```kotlin
package com.example.npc.core.storage.entity

import androidx.room.*

@Entity(
    tableName = "event",
    foreignKeys = [
        ForeignKey(
            entity = RawEventEntity::class,
            parentColumns = ["id"],
            childColumns = ["raw_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["is_update_of"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["raw_id"]),
        Index(value = ["ts"]),
        Index(value = ["thread_key"]),
        Index(value = ["is_update_of"]),
        Index(value = ["category"]),
        Index(value = ["content_fingerprint"])
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "raw_id")
    val rawId: Long,

    @ColumnInfo(name = "ts")
    val ts: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "normalized_text")
    val normalizedText: String,

    @ColumnInfo(name = "lang")
    val lang: String,

    @ColumnInfo(name = "thread_key")
    val threadKey: String?,

    @ColumnInfo(name = "is_update_of")
    val isUpdateOf: Long?,

    // --- Новые поля Фазы 1 ---
    @ColumnInfo(name = "category", defaultValue = "'UNCLASSIFIED'")
    val category: String,

    @ColumnInfo(name = "confidence", defaultValue = "0.0")
    val confidence: Double,

    @ColumnInfo(name = "engine_used", defaultValue = "'NONE'")
    val engineUsed: String,

    @ColumnInfo(name = "is_user_corrected", defaultValue = "0")
    val isUserCorrected: Boolean,

    @ColumnInfo(name = "content_fingerprint", defaultValue = "NULL")
    val contentFingerprint: String?
)
```

```kotlin
package com.example.npc.core.storage.entity

import androidx.room.*

@Entity(
    tableName = "financial_transaction",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["event_id"], unique = true),
        Index(value = ["occurred_at"]),
        Index(value = ["bank"])
    ]
)
data class FinancialTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "event_id")
    val eventId: Long?,

    @ColumnInfo(name = "bank")
    val bank: String,

    @ColumnInfo(name = "direction")
    val direction: String,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "balance_minor")
    val balanceMinor: Long?,

    @ColumnInfo(name = "balance_currency")
    val balanceCurrency: String?,

    @ColumnInfo(name = "merchant")
    val merchant: String?,

    @ColumnInfo(name = "account_mask")
    val accountMask: String?,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    @ColumnInfo(name = "extractor_id")
    val extractorId: String,

    @ColumnInfo(name = "extractor_version")
    val extractorVersion: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
```

```kotlin
package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_prototype",
    indices = [
        Index(value = ["package_name", "fingerprint"], unique = true)
    ]
)
data class UserPrototypeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "fingerprint")
    val fingerprint: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "support_count", defaultValue = "1")
    val supportCount: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long
)
```

---

## 6. Strict Package-Gated Pipelines & Prototype-First Architecture

### 6.1 Архитектурный поток конвейера (End-to-End Processing)

```
[Входящее уведомление sbn / SMS]
            │
            ▼
[StorageGateway.insertRawEvent] ──► raw_event (idempotent, hash SHA-256)
            │
            ▼
     ┌──────────────┐
     │ PackageGate  │ ── DROP (неизвестный источник) ──► Health.recordDrop() -> Завершение
     └──────────────┘
            │ PASS (SourceProfile: Bank, SMS или App)
            ▼
   [EventNormalizer]
            │
            ▼
    [Fingerprinter] ──► content_fingerprint = SHA256(profileId | sender | template(text))
            │
            ▼
┌────────────────────────┐
│ PrototypeStage         │ ── НАЙДЕН прототип с support_count >= 2
└────────────────────────┘          │
            │ Промах                ▼
            │               Категория = prototype.category
            │               Confidence = 1.0, Engine = PROTOTYPE
            │                       │
            ▼                       │
┌────────────────────────┐          │
│ RuleClassifier         │          │
└────────────────────────┘          │
            │                       │
            ├───────────────────────┘
            ▼
   Является ли категория FINANCE?
   И разрешен ли экстрактор в SourceProfile?
            ├── НЕТ (например: ADVERTISEMENT от спама туроператора)
            │     └── Финансовый парсер НЕ ВЫЗЫВАЕТСЯ! (0 ложных транзакций)
            │
            └── ДА (Авторизованный банк APB / Prisbank / MAIB)
                  │
                  ▼
       [IsolatedExtractorRunner] (RE2/J, O(n), budget 50ms, CircuitBreaker)
                  │
                  ▼
       [db.withTransaction]
            ├─ eventDao.insertEvent(event)
            └─ if (Extracted) txnDao.insertTransaction(txn)
```

### 6.2 Матрица источников PackageGate

В Android SMS-сообщения от банков поступают не только через системный `SmsBroadcastReceiver`, но и через шторку уведомлений системных SMS-приложений (`com.google.android.apps.messaging`, `com.android.mms`).  
**Архитектурный закон:** Приложения обмена сообщениями (`com.google.android.apps.messaging`, `com.radolyn.ayugram`, `org.telegram.messenger`) **НИ ПРИ КАКИХ ОБСТОЯТЕЛЬСТВАХ** не пропускаются по имени пакета. Для SMS-приложений обязательна проверка заголовка `sender` по allowlist!

| Профиль (`profileId`) | Разрешенные Android-пакеты (`packages`) | Разрешенные отправители (`senders`) | Дефолтная валюта | Экстрактор |
|---|---|---|---|---|
| `bank.apb` | `com.apb.mobile` | `null` (любой пуш банка) | `CurrencyCode.RUP` | `ApbNotificationExtractor` |
| `bank.prisbank` | `com.prisbank.app` | `null` | `CurrencyCode.RUP` | `PrisbankNotificationExtractor` |
| `bank.maib` | `md.maib.maibank` | `null` | `CurrencyCode.MDL` | `MaibNotificationExtractor` |
| `bank.sms` | `com.google.android.apps.messaging`, `com.android.mms` | `"APB"`, `"AGROPOMBANK"`, `"PRISBANK"`, `"SBERBANK"`, `"MAIB"`, `"900"` | Контекстный | `BankSmsExtractor` |

### 6.3 Линейный алгоритм Fingerprinter (без регулярных выражений)

```kotlin
package com.example.npc.classify.rules

import java.security.MessageDigest
import java.util.Locale

object Fingerprinter {
    /**
     * Преобразует текст в стабильный шаблон:
     * - Последовательности цифр схлопываются в одиночный символ '#'
     * - Разделители внутри чисел (, .) проглатываются
     * - Пробельные символы и неразрывные пробелы унифицируются
     */
    fun createTemplate(text: String): String = buildString(text.length) {
        var lastWasDigit = false
        val boundedText = text.take(1024).lowercase(Locale.ROOT)
        
        for (ch in boundedText) {
            when {
                ch.isDigit() -> {
                    if (!lastWasDigit) append('#')
                    lastWasDigit = true
                }
                ch == ',' || ch == '.' -> {
                    if (!lastWasDigit) append(ch)
                }
                ch.isWhitespace() || ch == '\u00A0' || ch == '\u202F' -> {
                    if (lastOrNull() != ' ') append(' ')
                    lastWasDigit = false
                }
                else -> {
                    append(ch)
                    lastWasDigit = false
                }
            }
        }
    }.trim()

    fun calculate(profileId: String, sender: String?, text: String): String {
        val rawKey = "$profileId|${sender.orEmpty().trim().lowercase(Locale.ROOT)}|${createTemplate(text)}"
        val digest = MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
```

### 6.4 Обучение прототипов (User Feedback Loop) в DAO

```kotlin
@Dao
abstract class UserPrototypeDao {
    @Query("SELECT * FROM user_prototype WHERE package_name = :pkg AND fingerprint = :fp LIMIT 1")
    abstract suspend fun findByPackageAndFingerprint(pkg: String, fp: String): UserPrototypeEntity?

    @Insert
    abstract suspend fun insert(entity: UserPrototypeEntity): Long

    @Query("UPDATE user_prototype SET support_count = support_count + 1, last_seen_at = :now WHERE id = :id")
    abstract suspend fun incrementSupport(id: Long, now: Long)

    @Query("UPDATE user_prototype SET category = :category, support_count = 1, last_seen_at = :now WHERE id = :id")
    abstract suspend fun reassignCategory(id: Long, category: String, now: Long)

    @Transaction
    open suspend fun recordCorrection(pkg: String, fp: String, category: String, now: Long) {
        val existing = findByPackageAndFingerprint(pkg, fp)
        when {
            existing == null -> {
                insert(UserPrototypeEntity(
                    packageName = pkg,
                    fingerprint = fp,
                    category = category,
                    supportCount = 1,
                    createdAt = now,
                    lastSeenAt = now
                ))
            }
            existing.category == category -> {
                incrementSupport(existing.id, now)
            }
            else -> {
                // Если пользователь передумал и выбрал другую категорию - сброс доверия в 1
                reassignCategory(existing.id, category, now)
            }
        }
    }
}
```

---

## 7. Защита от ReDoS и катастрофического бэктрекинга

На платформе Android стандартный класс `java.util.regex.Pattern` (и обертка `kotlin.text.Regex`) делегирует сопоставление нативному C++ движку ICU. В силу этого стандартные JVM-трюки (`CharSequence` с тайм-аутом) не прерывают нативное исполнение, а `Thread.interrupt()` игнорируется.

Для гарантии 100% стабильности внедряется **6-уровневый эшелон защиты от ReDoS**:

### Уровень 1: Использование линейного движка RE2/J
Все регулярные выражения финансовых парсеров компилируются исключительно с помощью библиотеки **RE2/J** (`com.google.re2j:re2j`).  
RE2 использует алгоритм конечных автоматов Томпсона и **математически гарантирует выполнение за O(n) от длины строки**, полностью исключая катастрофический бэктрекинг.

### Уровень 2: Жесткое ограничение длины входа (Input Caps)
Перед передачей текста в любой парсер выполняется принудительное усечение:
- Для финансовых экстракторов: максимум **1024 символа**.
- Для классификаторов текста: максимум **4096 символов**.
- Предварительная очистка от мусорных нуль-символов Unicode (`\u200B`–`\u200D`, `\uFEFF`).

### Уровень 3: Рукописный O(n) парсер сумм (`AmountParser`)
Парсинг денежных сумм выполняется без регулярных выражений:

```kotlin
package com.example.npc.extract.finance

object AmountParser {
    fun parseMinor(raw: String, minorDigits: Int): Long? {
        if (raw.length > 32) return null
        val clean = raw.filter { it.isDigit() || it == ',' || it == '.' }
        if (clean.isEmpty() || !clean.first().isDigit()) return null

        val lastSep = clean.indexOfLast { it == ',' || it == '.' }
        val fracLen = if (lastSep >= 0) clean.length - lastSep - 1 else 0
        val isDecimal = lastSep >= 0 && fracLen in 1..minorDigits

        val intPart = (if (isDecimal) clean.substring(0, lastSep) else clean).filter(Char::isDigit)
        val fracPart = if (isDecimal) clean.substring(lastSep + 1).padEnd(minorDigits, '0') else "0".repeat(minorDigits)

        if (intPart.isEmpty() || intPart.length > 15) return null
        return (intPart + fracPart).toLongOrNull()
    }
}
```

### Уровень 4: Статическая компиляция паттернов
Все паттерны объявляются внутри синглтон-объектов `object Patterns`. Ошибки компиляции регулярных выражений выявляются на этапе сборки и старта тестов.

### Уровень 5: CI Adversarial Fuzzing
В CI включен автоматический стресс-тест `AdversarialRegexTest`, прогоняющий через все парсеры патологические строки (`"9".repeat(5000)`, `",".repeat(5000)`, вложенные кавычки) с замером времени выполнения.

### Уровень 6: Runtime Circuit Breaker (`IsolatedExtractorRunner`)
Каждый экстрактор обернут в Circuit Breaker:
- Бюджет выполнения: 50 мс.
- Если экстрактор превышает бюджет или выбрасывает исключение 3 раза подряд — автоматическое размыкание цепи на 10 минут.
- Событие не теряется: оно сохраняется как `FINANCE`, а ошибка логируется в `source_health.last_error`.

---

## 8. Список затрагиваемых файлов и структура проекта

```
vibrant-hawking/
├── settings.gradle.kts                      [MODIFY: add :classify:rules, :extract:finance]
│
├── core/
│   ├── model/
│   │   ├── build.gradle.kts
│   │   └── src/main/kotlin/com/example/npc/core/model/
│   │       ├── finance/
│   │       │   ├── CurrencyCode.kt          [NEW]
│   │       │   ├── Direction.kt             [NEW]
│   │       │   └── Money.kt                 [NEW]
│   │       ├── classify/
│   │       │   ├── Category.kt              [NEW]
│   │       │   ├── Engine.kt                [NEW]
│   │       │   ├── ClassificationResult.kt  [NEW]
│   │       │   └── SemanticClassifier.kt    [NEW]
│   │       ├── extract/
│   │       │   ├── ExtractorInput.kt        [NEW]
│   │       │   ├── ExtractionResult.kt      [NEW]
│   │       │   ├── FinanceExtractor.kt      [NEW]
│   │       │   └── CurrencyResolver.kt      [NEW]
│   │       ├── FinancialTransaction.kt      [NEW]
│   │       ├── UserPrototype.kt             [NEW]
│   │       └── Event.kt                     [MODIFY: add category, confidence, engineUsed, fingerprint]
│   │
│   └── storage/
│       ├── build.gradle.kts
│       └── src/main/kotlin/com/example/npc/core/storage/
│           ├── AppDatabase.kt               [MODIFY: version 2, add new entities & DAOs]
│           ├── StorageGateway.kt            [MODIFY: add transaction & prototype methods]
│           ├── StorageGatewayImpl.kt        [MODIFY: implement v2 transaction contracts]
│           ├── entity/
│           │   ├── EventEntity.kt           [MODIFY: v2 columns with defaultValues]
│           │   ├── FinancialTransactionEntity.kt [NEW]
│           │   └── UserPrototypeEntity.kt   [NEW]
│           ├── dao/
│           │   ├── FinancialTransactionDao.kt [NEW]
│           │   └── UserPrototypeDao.kt      [NEW]
│           └── migration/
│               ├── MIGRATION_1_2.kt         [NEW]
│               └── PreMigrationBackup.kt    [NEW]
│
├── classify/
│   └── rules/                               [NEW MODULE]
│       ├── build.gradle.kts
│       └── src/main/kotlin/com/example/npc/classify/rules/
│           ├── PackageGate.kt               [NEW]
│           ├── SourceProfile.kt             [NEW]
│           ├── Fingerprinter.kt             [NEW]
│           ├── PrototypeStage.kt            [NEW]
│           ├── RuleClassifier.kt            [NEW]
│           └── SemanticClassifierImpl.kt    [NEW]
│
├── extract/
│   └── finance/                             [NEW MODULE]
│       ├── build.gradle.kts                 [NEW: dependencies: re2j, :core:model]
│       └── src/main/kotlin/com/example/npc/extract/finance/
│           ├── AmountParser.kt              [NEW]
│           ├── BankCurrencyResolver.kt      [NEW]
│           ├── IsolatedExtractorRunner.kt   [NEW]
│           ├── CircuitBreaker.kt            [NEW]
│           ├── apb/
│           │   └── ApbNotificationExtractor.kt [NEW]
│           ├── prisbank/
│           │   └── PrisbankNotificationExtractor.kt [NEW]
│           ├── maib/
│           │   └── MaibNotificationExtractor.kt [NEW]
│           └── sms/
│               └── BankSmsExtractor.kt      [NEW]
│
├── ui/
│   └── timeline/
│       ├── build.gradle.kts
│       └── src/main/kotlin/com/example/npc/ui/timeline/
│           ├── TimelineViewModel.kt         [MODIFY: load transactions, handle user corrections]
│           ├── model/
│           │   ├── TimelineEventUiModel.kt  [MODIFY: add category, prototype tags]
│           │   └── TimelineTransactionUiModel.kt [NEW]
│           └── ui/
│               ├── components/
│               │   ├── TransactionCard.kt   [NEW]
│               │   ├── CategoryBadge.kt     [NEW]
│               │   └── UserCorrectionDialog.kt [NEW]
│               └── TimelineScreen.kt        [MODIFY: render transaction cards]
│
└── app/
    ├── build.gradle.kts                     [MODIFY: dependencies on :classify:rules, :extract:finance]
    └── src/main/kotlin/com/example/npc/app/
        ├── EventProcessingOrchestrator.kt   [NEW: coordinates Gate -> Classify -> Extract -> Save]
        └── di/
            ├── ClassifierModule.kt          [NEW]
            └── ExtractorModule.kt           [NEW]
```

---

## 9. Реестр архитектурных рисков и стратегии митигации

| № | Архитектурный риск | Вероятность | Влияние | Стратегия митигации | Статус |
|---|---|:---:|:---:|---|:---:|
| **1** | **Повреждение SQLite БД при обновлении Poco M7 с v1 на v2** | Низкая | Критическое | • Строго аддитивная миграция (без пересоздания таблиц).<br>• `PreMigrationBackup` создает снимок в `noBackupFilesDir` до старта Room.<br>• `foreign_key_check` в конце миграции отменяет транзакцию при малейшем нарушении целостности. | **ЗАКРЫТ** |
| **2** | **ReDoS зависание на агрессивных SMS туроператоров / фишинге** | Средняя | Высокое | • Использование RE2/J (гарантия O(n)).<br>• Входной лимит 1024 символа.<br>• Рукописный `AmountParser`.<br>• Runtime Circuit Breaker с лимитом 50 мс. | **ЗАКРЫТ** |
| **3** | **Смешение валют при подсчете сумм (RUP vs RUB vs MDL)** | Высокая | Высокое | • `Money` содержит жесткий guard: сложение/вычитание разных валют выбрасывает исключение.<br>• `CurrencyResolver` учитывает контекст банка: «руб» от Агропромбанка/Сбербанка ПМР строго резолвится как `RUP`. | **ЗАКРЫТ** |
| **4** | **Ложные транзакции из-за спама в мессенджерах (кейс InTour)** | Высокая | Критическое | • `PackageGate`: мессенджеры (Telegram, AyuGram) полностью изолированы от финансового пайплайна.<br>• Prototype-First: разметка спама пользователем (`support_count >= 2`) блокирует любые доменные экстракторы. | **ЗАКРЫТ** |
| **5** | **Потеря финансовых транзакций при ротации старых уведомлений** | Средняя | Среднее | • Внешний ключ `financial_transaction.event_id` имеет `ON DELETE SET NULL` вместо `CASCADE`. Записи транзакций остаются в финансовом журнале навсегда. | **ЗАКРЫТ** |
| **6** | **Просадка FPS в Timeline 2.0 при рендере финансовых карточек** | Низкая | Среднее | • `@Immutable` на всех моделях `TimelineTransactionUiModel`.<br>• Форматирование сумм и дат выполняется в ViewModel на `Dispatchers.Default`, а не в `@Composable` фазе рендеринга. | **ЗАКРЫТ** |

---

## 10. Порядок сдачи и приемки (Definition of Done Фазы 1)

1. **Gate 1.1 (Model & Storage):**
   - Успешный прогон `MigrationTestHelper` с v1 на v2 на реальном дампе `event_engine.db`.
   - 100% прохождение тестов `MoneyTest` и `CurrencyCodeTest`.
2. **Gate 1.2 (Classification & Gating):**
   - Прогон 785 событий Poco M7 через `PackageGate`: 0 спам-сообщений Telegram пропущено в `FinanceExtractor`.
   - Проверка кейса InTour: `support_count = 6` гарантирует категорию `ADVERTISEMENT` и 0 ложных транзакций.
3. **Gate 1.3 (Financial Extraction & Regional Banking):**
   - 100% точность извлечения для 23 реальных банковских транзакций (APB, Сбербанк ПМР, MAIB).
   - Транзакции со статусом `DECLINED` / `REFUZATA` не создают расходных проводок.
4. **Gate 1.4 (Timeline 2.0 & Feedback):**
   - Корректное отображение символов `р.` (RUP), `L` (MDL), `€` (EUR).
   - Работоспособность диалога ручной коррекции категории с мгновенным созданием прототипа в БД.

