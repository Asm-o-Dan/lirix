# Спецификация: zone/core-storage (Фаза 1: «Хардкод-MVP»)

## Модуль: `:core:storage` | Зона: `zone/core-storage` | Версия спеки: `v2.0` | Статус: APPROVED

---

### 1. Назначение и границы зоны ответственности

Модуль `:core:storage` реализует защищённый транзакционный слой долговременного хранения данных приложения на базе Room 2.6+ и SQLCipher 4.5.4/4.6.0 с аппаратным ключом `AndroidKeyStore`.

В Фазе 1 («Хардкод-MVP: Семантическая классификация, изолированные экстракторы и Timeline 2.0», [`.sdd/architecture_phase1.md`](file:///c:/Users/DaniilTuT/Documents/antigravity/vibrant-hawking/.sdd/architecture_phase1.md)) модуль расширяется для поддержки:
1. **Эволюции схемы БД (Room v1 → v2)** со 100% гарантией сохранности накопленных данных Dogfooding на Poco M7 (785 событий, 137 транзакций в `event_engine.db`).
2. **Предмиграционного защитного снимка (`PreMigrationBackup`)** с принудительным `PRAGMA wal_checkpoint(TRUNCATE)` в директорию `noBackupFilesDir`.
3. **Финансового реестра (`financial_transaction`)** с защитой от каскадного удаления через политику `ON DELETE SET NULL` на `event_id`.
4. **Хранилища пользовательских прототипов (`user_prototype`)** с уникальным композитным индексом `(package_name, fingerprint)` и атомарным механизмом обучения (Feedback Loop).
5. **Целостностного шлюза `PRAGMA foreign_key_check`** в теле миграции.
6. **Расширенного шлюза `StorageGateway`**, предоставляющего атомарную фиксацию обработанных событий и транзакций (`saveProcessedEvent`), запись пользовательской коррекции (`recordUserCorrection`), поиск прототипов и реактивные потоки `Flow` для Timeline 2.0.

#### Архитектурные границы и запреты:
- **Чистый Kotlin JVM модуль:** Модуль компилируется плагином `kotlin("jvm")` + KSP (`ksp(libs.androidx.room.compiler)`).
- **Изоляция Room DAO:** Интерфейсы DAO строго `internal` внутри модуля `:core:storage`. Внешние модули (`:app`, `:ui:timeline`, `:ingest:*`) взаимодействуют с хранилищем **исключительно** через интерфейс `StorageGateway`.
- **Запрет бизнес-логики парсинга:** Модуль хранилища не выполняет нормализацию текста, парсинг регулярных выражений, резолвинг валют или семантический анализ. Он оперирует готовыми DTO из `:core:model`.
- **Категорический запрет деструктивной миграции:** `fallbackToDestructiveMigration()` строго запрещен в продакшене.

---

### 2. Структура файлов и пакетов модуля `:core:storage`

```
com.example.npc.core.storage/
├── AppDatabase.kt                           # RoomDatabase v2, экспорт схемы, регистрация Entities & DAOs
├── StorageGateway.kt                        # Публичный контракт шлюза хранения (Фаза 0 + Фаза 1)
├── StorageGatewayImpl.kt                    # Реализация контракта с поддержкой db.withTransaction
├── SqlCipherSupportFactoryProvider.kt       # Фабрика шифрования SQLCipher 4.6.0 + 32-байтный ключ
│
├── entity/
│   ├── RawEventEntity.kt                    # Фаза 0 (таблица raw_event)
│   ├── EventEntity.kt                       # Фаза 1 (модифицированная таблица event с DEFAULT)
│   ├── FinancialTransactionEntity.kt        # Фаза 1 (новая таблица financial_transaction)
│   ├── UserPrototypeEntity.kt               # Фаза 1 (новая таблица user_prototype)
│   └── SourceHealthEntity.kt                # Фаза 0 (таблица source_health)
│
├── dao/
│   ├── RawEventDao.kt                       # DAO для raw_event
│   ├── EventDao.kt                          # DAO для event (v2 с фильтрами по категориям/fingerprint)
│   ├── FinancialTransactionDao.kt           # DAO для financial_transaction (периоды, агрегаты, Flow)
│   ├── UserPrototypeDao.kt                  # DAO для user_prototype (поиск, upsert, feedback loop)
│   └── SourceHealthDao.kt                   # DAO для source_health
│
├── converter/
│   ├── MoneyConverters.kt                   # TypeConverters для примитивов валют и направлений
│   └── InstantConverter.kt                  # Конвертер Instant <-> Long (epoch ms)
│
├── mapper/
│   ├── RawEventMapper.kt                    # DTO RawEvent <-> RawEventEntity
│   ├── EventMapper.kt                       # DTO Event <-> EventEntity (v2)
│   ├── FinancialTransactionMapper.kt        # DTO FinancialTransaction <-> FinancialTransactionEntity
│   ├── UserPrototypeMapper.kt               # DTO UserPrototype <-> UserPrototypeEntity
│   └── SourceHealthMapper.kt                # DTO SourceHealth <-> SourceHealthEntity
│
└── migration/
    ├── MIGRATION_1_2.kt                     # Исполняемый объект миграции Room v1 -> v2
    └── PreMigrationBackup.kt                # Утилита холодного бэкапа перед накатом схемы
```

---

### 3. Схема персистенции Room v2 и миграция `MIGRATION_1_2`

#### 3.1 Полный DDL схемы Room v2

```sql
-- ============================================================================
-- 1. Таблица сырых событий (Фаза 0, без изменений)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `raw_event` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `seq` INTEGER NOT NULL,
    `source` TEXT NOT NULL,
    `package_name` TEXT NOT NULL,
    `received_at` INTEGER NOT NULL,
    `payload_json` TEXT NOT NULL,
    `hash` TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS `index_raw_event_hash` ON `raw_event` (`hash`);
CREATE INDEX IF NOT EXISTS `index_raw_event_seq` ON `raw_event` (`seq`);

-- ============================================================================
-- 2. Таблица структурированных событий (Фаза 1: добавлены 5 колонок и 2 индекса)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `event` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `raw_id` INTEGER NOT NULL,
    `ts` INTEGER NOT NULL,
    `title` TEXT NOT NULL,
    `text` TEXT NOT NULL,
    `normalized_text` TEXT NOT NULL,
    `lang` TEXT NOT NULL,
    `thread_key` TEXT,
    `is_update_of` INTEGER,
    `category` TEXT NOT NULL DEFAULT 'UNCLASSIFIED',
    `confidence` REAL NOT NULL DEFAULT 0.0,
    `engine_used` TEXT NOT NULL DEFAULT 'NONE',
    `is_user_corrected` INTEGER NOT NULL DEFAULT 0,
    `content_fingerprint` TEXT DEFAULT NULL,
    FOREIGN KEY (`raw_id`) REFERENCES `raw_event` (`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
    FOREIGN KEY (`is_update_of`) REFERENCES `event` (`id`) ON UPDATE NO ACTION ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS `index_event_raw_id` ON `event` (`raw_id`);
CREATE INDEX IF NOT EXISTS `index_event_ts` ON `event` (`ts`);
CREATE INDEX IF NOT EXISTS `index_event_thread_key` ON `event` (`thread_key`);
CREATE INDEX IF NOT EXISTS `index_event_is_update_of` ON `event` (`is_update_of`);
CREATE INDEX IF NOT EXISTS `index_event_category` ON `event` (`category`);
CREATE INDEX IF NOT EXISTS `index_event_content_fingerprint` ON `event` (`content_fingerprint`);

-- ============================================================================
-- 3. Новая таблица: Финансовые транзакции (Фаза 1)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `financial_transaction` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `event_id` INTEGER,
    `bank` TEXT NOT NULL,
    `direction` TEXT NOT NULL,
    `amount_minor` INTEGER NOT NULL,
    `currency` TEXT NOT NULL,
    `balance_minor` INTEGER,
    `balance_currency` TEXT,
    `merchant` TEXT,
    `account_mask` TEXT,
    `occurred_at` INTEGER NOT NULL,
    `extractor_id` TEXT NOT NULL,
    `extractor_version` INTEGER NOT NULL,
    `created_at` INTEGER NOT NULL,
    FOREIGN KEY (`event_id`) REFERENCES `event` (`id`) ON UPDATE NO ACTION ON DELETE SET NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS `index_financial_transaction_event_id` ON `financial_transaction` (`event_id`);
CREATE INDEX IF NOT EXISTS `index_financial_transaction_occurred_at` ON `financial_transaction` (`occurred_at`);
CREATE INDEX IF NOT EXISTS `index_financial_transaction_bank` ON `financial_transaction` (`bank`);

-- ============================================================================
-- 4. Новая таблица: Пользовательские прототипы (Фаза 1: Feedback Loop)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `user_prototype` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `package_name` TEXT NOT NULL,
    `fingerprint` TEXT NOT NULL,
    `category` TEXT NOT NULL,
    `support_count` INTEGER NOT NULL DEFAULT 1,
    `created_at` INTEGER NOT NULL,
    `last_seen_at` INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS `index_user_prototype_package_fingerprint` ON `user_prototype` (`package_name`, `fingerprint`);

-- ============================================================================
-- 5. Таблица метрик здоровья источников (Фаза 0, без изменений)
-- ============================================================================
CREATE TABLE IF NOT EXISTS `source_health` (
    `source` TEXT PRIMARY KEY NOT NULL,
    `last_event_at` INTEGER,
    `events_24h` INTEGER NOT NULL,
    `last_error` TEXT,
    `queue_depth` INTEGER NOT NULL
);
```

---

#### 3.2 Механизм холодного резервного копирования `PreMigrationBackup`

Перед вызовом Room-миграции на физическом Poco M7 (HyperOS, Android 14/15) файл базы данных должен быть гарантированно забэкаплен. Это защищает базу от повреждения при внезапном сбросе питания, аварийной перезагрузке ОС или нехватке памяти во время миграции.

```kotlin
package com.example.npc.core.storage.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

object PreMigrationBackup {
    /**
     * Создает снимок БД перед RoomDatabase.Builder.build().
     * Вызывается строго в фоновом потоке до открытия базы Room.
     */
    fun executeIfNeeded(context: Context, databaseName: String, targetVersion: Int) {
        val dbFile = context.getDatabasePath(databaseName).takeIf { it.exists() } ?: return

        val currentVersion = try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                // Сброс WAL-журнала в основной файл для консистентного холодного снимка
                db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                db.version
            }
        } catch (_: Throwable) {
            return
        }

        if (currentVersion in 1 until targetVersion) {
            val backupDir = context.noBackupFilesDir
            // Проверка свободного места: требуется минимум двукратный запас размера базы
            val requiredSpace = dbFile.length() * 2
            if (backupDir.usableSpace > requiredSpace) {
                val backupFile = File(backupDir, "$databaseName.v$currentVersion.bak")
                dbFile.copyTo(backupFile, overwrite = true)
            }
        }
    }
}
```

---

#### 3.3 Исполняемый объект `MIGRATION_1_2` и валидация целостности

Миграция строго аддитивна: все добавляемые `NOT NULL` колонки содержат явный `DEFAULT`, идентичный значениям `@ColumnInfo(defaultValue = ...)`.

```kotlin
package com.example.npc.core.storage.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // =================================================================
        // 1. Таблица event: строго аддитивное добавление столбцов
        // =================================================================
        db.execSQL("ALTER TABLE `event` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'UNCLASSIFIED'")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `confidence` REAL NOT NULL DEFAULT 0.0")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `engine_used` TEXT NOT NULL DEFAULT 'NONE'")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `is_user_corrected` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `event` ADD COLUMN `content_fingerprint` TEXT DEFAULT NULL")

        // Индексы для фильтрации в Timeline 2.0
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_category` ON `event` (`category`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_content_fingerprint` ON `event` (`content_fingerprint`)")

        // =================================================================
        // 2. Новая таблица: financial_transaction
        // =================================================================
        // Внешний ключ event_id имеет ON DELETE SET NULL, чтобы удаление
        // старых событий (retention) не уничтожало финансовый реестр!
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

---

### 4. Room Entities и индексы

#### 4.1 `EventEntity` (версия схемы v2)

```kotlin
package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
        Index(value = ["raw_id"], unique = false),
        Index(value = ["ts"], unique = false),
        Index(value = ["thread_key"], unique = false),
        Index(value = ["is_update_of"], unique = false),
        Index(value = ["category"], unique = false),
        Index(value = ["content_fingerprint"], unique = false)
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

    // --- Поля Фазы 1 ---
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

---

#### 4.2 `FinancialTransactionEntity`

Представляет изолированную финансовую запись, извлечённую из банковского пуша или SMS.

```kotlin
package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
        Index(value = ["occurred_at"], unique = false),
        Index(value = ["bank"], unique = false)
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

**Особенности хранения Money и CurrencyCode:**
1. Суммы (`amount_minor`, `balance_minor`) хранятся в целочисленном формате `INTEGER` (`Long`), представляющем неделимые единицы (копейки, центы). Это полностью исключает ошибки округления с плавающей точкой (`Double`).
2. Код валюты (`currency`, `balance_currency`) хранится строкой `TEXT` (`"RUP"`, `"MDL"`, `"RUB"`, `"EUR"`, `"USD"`).
3. Раздельное хранение колонок `amount_minor` и `currency` позволяет производить прямой агрегационный расчет в SQLite: `SELECT currency, SUM(amount_minor) FROM financial_transaction WHERE direction = :dir GROUP BY currency`.

---

#### 4.3 `UserPrototypeEntity`

Представляет запись обученного пользовательского прототипа классификации.

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

*Примечание к именованию:* В архитектуре колонка пакета именуется `package_name` (доменный алиас `target_package`), а хеш шаблона текста — `fingerprint` (доменный алиас `pattern_or_title`). Композитный индекс `(package_name, fingerprint)` гарантирует отсутствие дублирующихся прототипов для одного и того же шаблона источника.

---

### 5. DAO Интерфейсы

#### 5.1 `FinancialTransactionDao`

```kotlin
package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.npc.core.storage.entity.FinancialTransactionEntity
import kotlinx.coroutines.flow.Flow

data class CurrencyTotalDto(
    val currency: String,
    val totalMinor: Long,
    val transactionCount: Int
)

@Dao
interface FinancialTransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: FinancialTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<FinancialTransactionEntity>): List<Long>

    @Query("SELECT * FROM financial_transaction WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FinancialTransactionEntity?

    @Query("SELECT * FROM financial_transaction WHERE event_id = :eventId LIMIT 1")
    suspend fun getByEventId(eventId: Long): FinancialTransactionEntity?

    @Query("SELECT * FROM financial_transaction ORDER BY occurred_at DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<FinancialTransactionEntity>>

    @Query("SELECT * FROM financial_transaction WHERE occurred_at BETWEEN :fromEpochMs AND :toEpochMs ORDER BY occurred_at DESC")
    fun observeByPeriod(fromEpochMs: Long, toEpochMs: Long): Flow<List<FinancialTransactionEntity>>

    @Query("SELECT * FROM financial_transaction WHERE occurred_at BETWEEN :fromEpochMs AND :toEpochMs ORDER BY occurred_at DESC")
    suspend fun getByPeriod(fromEpochMs: Long, toEpochMs: Long): List<FinancialTransactionEntity>

    @Query("SELECT * FROM financial_transaction WHERE bank = :bank ORDER BY occurred_at DESC LIMIT :limit")
    fun observeByBank(bank: String, limit: Int): Flow<List<FinancialTransactionEntity>>

    @Query("SELECT currency, SUM(amount_minor) AS totalMinor, COUNT(*) AS transactionCount FROM financial_transaction WHERE direction = :direction AND occurred_at BETWEEN :fromEpochMs AND :toEpochMs GROUP BY currency")
    suspend fun getAggregatedTotalsByCurrency(
        direction: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<CurrencyTotalDto>

    @Query("SELECT * FROM financial_transaction ORDER BY occurred_at DESC, id DESC")
    suspend fun getAll(): List<FinancialTransactionEntity>

    @Query("DELETE FROM financial_transaction WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM financial_transaction")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM financial_transaction")
    suspend fun count(): Long
}
```

---

#### 5.2 `UserPrototypeDao`

```kotlin
package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.npc.core.storage.entity.UserPrototypeEntity
import kotlinx.coroutines.flow.Flow

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
                insert(
                    UserPrototypeEntity(
                        packageName = pkg,
                        fingerprint = fp,
                        category = category,
                        supportCount = 1,
                        createdAt = now,
                        lastSeenAt = now
                    )
                )
            }
            existing.category == category -> {
                incrementSupport(existing.id, now)
            }
            else -> {
                // Если пользователь переназначил категорию на другую — сброс доверия в 1
                reassignCategory(existing.id, category, now)
            }
        }
    }

    @Query("SELECT * FROM user_prototype ORDER BY last_seen_at DESC")
    abstract fun observeAll(): Flow<List<UserPrototypeEntity>>

    @Query("SELECT * FROM user_prototype ORDER BY last_seen_at DESC")
    abstract suspend fun getAll(): List<UserPrototypeEntity>

    @Query("DELETE FROM user_prototype WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM user_prototype")
    abstract suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM user_prototype")
    abstract suspend fun count(): Long
}
```

---

#### 5.3 Обновление `EventDao` (Фаза 1)

```kotlin
package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.npc.core.storage.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: EventEntity): Long

    @Query("SELECT * FROM event WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EventEntity?

    @Query("SELECT * FROM event ORDER BY ts DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM event WHERE category = :category ORDER BY ts DESC, id DESC LIMIT :limit")
    fun observeByCategory(category: String, limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM event ORDER BY ts DESC, id DESC")
    suspend fun getAll(): List<EventEntity>

    @Query("SELECT COUNT(*) FROM event WHERE ts >= :sinceEpochMs")
    suspend fun countSince(sinceEpochMs: Long): Int

    @Query("UPDATE event SET category = :category, is_user_corrected = 1, engine_used = 'USER' WHERE id = :id")
    suspend fun updateCategoryFromUser(id: Long, category: String): Int

    @Query("DELETE FROM event")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM event")
    suspend fun count(): Long
}
```

---

### 6. Обновление `AppDatabase`

```kotlin
package com.example.npc.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.npc.core.storage.dao.EventDao
import com.example.npc.core.storage.dao.FinancialTransactionDao
import com.example.npc.core.storage.dao.RawEventDao
import com.example.npc.core.storage.dao.SourceHealthDao
import com.example.npc.core.storage.dao.UserPrototypeDao
import com.example.npc.core.storage.entity.EventEntity
import com.example.npc.core.storage.entity.FinancialTransactionEntity
import com.example.npc.core.storage.entity.RawEventEntity
import com.example.npc.core.storage.entity.SourceHealthEntity
import com.example.npc.core.storage.entity.UserPrototypeEntity

@Database(
    entities = [
        RawEventEntity::class,
        EventEntity::class,
        FinancialTransactionEntity::class,
        UserPrototypeEntity::class,
        SourceHealthEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawEventDao(): RawEventDao
    abstract fun eventDao(): EventDao
    abstract fun financialTransactionDao(): FinancialTransactionDao
    abstract fun userPrototypeDao(): UserPrototypeDao
    abstract fun sourceHealthDao(): SourceHealthDao
}
```

---

### 7. Обновление контракта `StorageGateway`

Интерфейс `StorageGateway` расширяется для обеспечения потребностей конвейера обработки (`EventProcessingOrchestrator`), обратной связи пользователя (`UserCorrectionDialog`) и карточек Timeline 2.0.

```kotlin
package com.example.npc.core.storage

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.FinancialTransaction
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.UserPrototype
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Direction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface StorageGateway {
    // =========================================================================
    // 1. Ingest (Фаза 0)
    // =========================================================================
    suspend fun insertRawEvent(event: RawEvent): Long
    suspend fun insertEvent(event: Event): Long
    suspend fun upsertSourceHealth(health: SourceHealth)
    suspend fun findDuplicate(key: DeduplicationKey): Long?

    // =========================================================================
    // 2. Processing Pipeline (Фаза 1: Атомарная фиксация)
    // =========================================================================
    /**
     * Атомарная транзакционная фиксация классифицированного события и опциональной финансовой транзакции.
     * Гарантирует:
     * - Если транзакция присутствует, она связывается с сохраненным eventId.
     * - Если экстрактор сбоит или транзакция отсутствует, событие все равно гарантированно сохраняется в БД.
     * @return идентификатор созданной записи Event (eventId).
     */
    suspend fun saveProcessedEvent(
        event: Event,
        classification: ClassificationResult,
        transaction: FinancialTransaction?
    ): Long

    // =========================================================================
    // 3. Feedback & Prototype Learning Loop (Фаза 1)
    // =========================================================================
    /**
     * Запись ручной коррекции категории пользователем:
     * 1. Обновляет событие (category = newCategory, is_user_corrected = true, engine_used = 'USER').
     * 2. Выполняет атомарный upsert прототипа в user_prototype (при совпадении supportCount + 1, при смене категории - сброс в 1).
     */
    suspend fun recordUserCorrection(
        eventId: Long,
        packageName: String,
        contentFingerprint: String,
        newCategory: Category,
        correctedAt: Instant = Instant.now()
    )

    /**
     * Поиск обученного пользовательского прототипа по кортежу (packageName, fingerprint).
     */
    suspend fun findMatchingPrototype(packageName: String, fingerprint: String): UserPrototype?

    // =========================================================================
    // 4. UI Timeline 2.0 & Queries (Реактивные потоки)
    // =========================================================================
    fun observeEvents(limit: Int): Flow<List<Event>>
    fun observeTransactions(limit: Int): Flow<List<FinancialTransaction>>
    fun observeTransactionsByPeriod(from: Instant, to: Instant): Flow<List<FinancialTransaction>>
    fun observeSourceHealth(): Flow<List<SourceHealth>>

    suspend fun getTransactionByEventId(eventId: Long): FinancialTransaction?

    /**
     * Агрегации сумм по валютам за указанный период (для сводных виджетов).
     */
    suspend fun getAggregatedTotals(
        direction: Direction,
        from: Instant,
        to: Instant
    ): Map<CurrencyCode, Long>

    // =========================================================================
    // 5. Maintenance / Export
    // =========================================================================
    suspend fun exportAllToJson(): String
    suspend fun deleteAllData()
    suspend fun deleteAll() = deleteAllData()
}
```

---

### 8. Реализация `StorageGatewayImpl` (Ключевые механизмы)

```kotlin
package com.example.npc.core.storage

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.npc.core.model.*
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Direction
import com.example.npc.core.storage.dao.*
import com.example.npc.core.storage.mapper.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

class StorageGatewayImpl(
    private val database: AppDatabase,
    private val rawEventDao: RawEventDao,
    private val eventDao: EventDao,
    private val transactionDao: FinancialTransactionDao,
    private val prototypeDao: UserPrototypeDao,
    private val sourceHealthDao: SourceHealthDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val transactionRunner: (suspend (suspend () -> Any?) -> Any?)? = null
) : StorageGateway {

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (transactionRunner != null) {
            transactionRunner.invoke(block as suspend () -> Any?) as T
        } else {
            database.withTransaction(block)
        }
    }

    override suspend fun saveProcessedEvent(
        event: Event,
        classification: ClassificationResult,
        transaction: FinancialTransaction?
    ): Long = withContext(ioDispatcher) {
        runInTransaction {
            val eventEntity = EventMapper.toEntity(
                event.copy(
                    category = classification.category,
                    confidence = classification.confidence,
                    engineUsed = classification.engine,
                    contentFingerprint = classification.contentFingerprint
                )
            )
            val eventId = eventDao.insert(eventEntity)

            if (transaction != null) {
                val txnEntity = FinancialTransactionMapper.toEntity(
                    transaction.copy(eventId = eventId)
                )
                transactionDao.insert(txnEntity)
            }

            eventId
        }
    }

    override suspend fun recordUserCorrection(
        eventId: Long,
        packageName: String,
        contentFingerprint: String,
        newCategory: Category,
        correctedAt: Instant
    ): Unit = withContext(ioDispatcher) {
        runInTransaction {
            eventDao.updateCategoryFromUser(eventId, newCategory.name)
            prototypeDao.recordCorrection(
                pkg = packageName,
                fp = contentFingerprint,
                category = newCategory.name,
                now = correctedAt.toEpochMilli()
            )
        }
    }

    override suspend fun findMatchingPrototype(
        packageName: String,
        fingerprint: String
    ): UserPrototype? = withContext(ioDispatcher) {
        prototypeDao.findByPackageAndFingerprint(packageName, fingerprint)
            ?.let { UserPrototypeMapper.toDomain(it) }
    }

    override fun observeTransactions(limit: Int): Flow<List<FinancialTransaction>> {
        require(limit > 0) { "limit must be greater than 0" }
        return transactionDao.observeLatest(limit)
            .map { list -> list.map { FinancialTransactionMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override fun observeTransactionsByPeriod(
        from: Instant,
        to: Instant
    ): Flow<List<FinancialTransaction>> {
        return transactionDao.observeByPeriod(from.toEpochMilli(), to.toEpochMilli())
            .map { list -> list.map { FinancialTransactionMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getTransactionByEventId(eventId: Long): FinancialTransaction? = withContext(ioDispatcher) {
        transactionDao.getByEventId(eventId)?.let { FinancialTransactionMapper.toDomain(it) }
    }

    override suspend fun getAggregatedTotals(
        direction: Direction,
        from: Instant,
        to: Instant
    ): Map<CurrencyCode, Long> = withContext(ioDispatcher) {
        val dtos = transactionDao.getAggregatedTotalsByCurrency(
            direction = direction.name,
            fromEpochMs = from.toEpochMilli(),
            toEpochMs = to.toEpochMilli()
        )
        dtos.associate { dto ->
            val code = CurrencyCode.ofOrNull(dto.currency) ?: CurrencyCode.RUP
            code to dto.totalMinor
        }
    }

    // Методы Ingest, Health, Delete, Export расширяются для транзакций и прототипов
    override suspend fun insertRawEvent(event: RawEvent): Long = withContext(ioDispatcher) {
        val entity = RawEventMapper.toEntity(event)
        try {
            rawEventDao.insert(entity)
        } catch (e: SQLiteConstraintException) {
            rawEventDao.findIdByHash(event.hash.value) ?: -1L
        }
    }

    override suspend fun insertEvent(event: Event): Long = withContext(ioDispatcher) {
        eventDao.insert(EventMapper.toEntity(event))
    }

    override suspend fun upsertSourceHealth(health: SourceHealth): Unit = withContext(ioDispatcher) {
        sourceHealthDao.upsert(SourceHealthMapper.toEntity(health))
    }

    override suspend fun findDuplicate(key: DeduplicationKey): Long? = withContext(ioDispatcher) {
        rawEventDao.findIdByHash(key.value)
    }

    override fun observeEvents(limit: Int): Flow<List<Event>> {
        require(limit > 0) { "limit must be greater than 0" }
        return eventDao.observeLatest(limit)
            .map { list -> list.map { EventMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override fun observeSourceHealth(): Flow<List<SourceHealth>> {
        return sourceHealthDao.observeAll()
            .map { list -> list.map { SourceHealthMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override suspend fun deleteAllData(): Unit = withContext(ioDispatcher) {
        runInTransaction {
            transactionDao.deleteAll()
            prototypeDao.deleteAll()
            eventDao.deleteAll()
            rawEventDao.deleteAll()
            sourceHealthDao.deleteAll()
        }
    }

    override suspend fun exportAllToJson(): String = withContext(ioDispatcher) {
        val (rawList, eventList, txnList, protoList, healthList) = runInTransaction {
            val r = rawEventDao.getAll()
            val e = eventDao.getAll().sortedBy { it.id }
            val t = transactionDao.getAll().sortedBy { it.id }
            val p = prototypeDao.getAll().sortedBy { it.id }
            val h = sourceHealthDao.getAll()
            Tuple5(r, e, t, p, h)
        }

        // Сериализация всех сущностей в JSON с версией 2
        buildString {
            append("{\"version\":2,\"exportedAt\":\"").append(Instant.now()).append("\",")
            append("\"rawEvents\":[").append(rawList.joinToString(",") { serializeRawEvent(it) }).append("],")
            append("\"events\":[").append(eventList.joinToString(",") { serializeEvent(it) }).append("],")
            append("\"transactions\":[").append(txnList.joinToString(",") { serializeTransaction(it) }).append("],")
            append("\"prototypes\":[").append(protoList.joinToString(",") { serializePrototype(it) }).append("],")
            append("\"sourceHealth\":[").append(healthList.joinToString(",") { serializeSourceHealth(it) }).append("]}")
        }
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
    // Сериализаторы JSON вынесены в приватные функции
}
```

---

### 9. План верификации и приемочные критерии (DoD)

1. **Тест миграции Room v1 → v2 на боевом дампе Poco M7:**
   - Выполнение `MigrationTestHelper` с накатом схемы v1, импортом реального дампа `event_engine.db` (785 событий), накатом `MIGRATION_1_2` и валидацией схемы v2.
   - 0 потерянных строк в `raw_event`, `event`, `source_health`.
   - Проверка `PRAGMA foreign_key_check` возвращает 0 нарушений.
2. **Тест политики внешнего ключа `ON DELETE SET NULL`:**
   - Вставка `EventEntity` и связанного `FinancialTransactionEntity`.
   - Удаление записи `event`.
   - Проверка: строка `financial_transaction` сохраняется в БД, поле `event_id` становится `NULL`.
3. **Тест контура обратной связи `UserPrototypeDao`:**
   - Первый вызов `recordCorrection`: вставка строки с `support_count = 1`.
   - Второй вызов с той же категорией: `support_count = 2`.
   - Третий вызов со сменой категории (например, `FINANCE` -> `ADVERTISEMENT`): `support_count` сбрасывается в `1`, категория обновляется.
4. **Тест защитного снимка `PreMigrationBackup`:**
   - При наличии свободного места создается валидный `.bak` файл в `noBackupFilesDir`.
   - При нехватке места миграция не крашится, бэкап пропускается без фатальной ошибки.
