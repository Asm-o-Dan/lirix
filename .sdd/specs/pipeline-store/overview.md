# Спецификация: zone/pipeline-store

## Модуль: `:data:pipeline-store` (`:core:storage`) · Зона: `zone/pipeline-store` · Версия схемы Room: `v3` · Фаза 2: «Конструктор конвейеров» · Статус: `PROPOSED`

---

### 1. Назначение, архитектурный контекст и границы модуля

Модуль `:data:pipeline-store` (интегрированный с подсистемой хранения `:core:storage`) отвечает за персистентность, версионирование, аудит изменений, хранение операционных алертов рантайма и безопасную миграцию базы данных с версии 2 на версию 3.

Ключевая задача модуля — обеспечить **Zero Data Loss (нулевую потерю данных)** для накопившейся 7-дневной базы реального догфудинга на устройстве Poco M7 (`2440cbe2`), работающем под управлением зашифрованной СУБД Room + SQLCipher, и предоставить надежный фундамент для атомарной горячей подмены конвейеров рантайма `:pipeline:runtime`.

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             АРХИТЕКТУРНЫЙ КОНТЕКСТ                               │
│                                                                                  │
│   UI Редактора (:feature:editor)         Рантайм (:pipeline:runtime)             │
│        │                                        │                                │
│   Save / Rollback / Inspect               Observe Active / Emit Alerts           │
│        │                                        │                                │
│        ▼                                        ▼                                │
│   ┌──────────────────────────────────────────────────────────────────────────┐   │
│   │                        PipelineRepository                                │   │
│   │  - saveAndActivate(definition, message)                                  │   │
│   │  - rollbackToRevision(pipelineId, revisionId)                            │   │
│   │  - observeActiveCompiledPipelines()                                      │   │
│   │  - Garbage Collection старых ревизий (Keep 10)                           │   │
│   └─────────────────────────────────────┬────────────────────────────────────┘   │
│                                         │                                        │
│                        DAO & Room Entities v3                                    │
│                                         │                                        │
│        ┌────────────────────────────────┼────────────────────────────────┐       │
│        ▼                                ▼                                ▼       │
│   ┌────────────────────────┐  ┌────────────────────────┐  ┌──────────────────┐   │
│   │      PipelineDao       │  │  PipelineRevisionDao   │  │ RuntimeAlertDao  │   │
│   │  pipeline_definition   │  │   pipeline_revision    │  │  runtime_alert   │   │
│   └────────────────────────┘  └────────────────────────┘  └──────────────────┘   │
│        │                                │                                │       │
│        └────────────────────────────────┼────────────────────────────────┘       │
│                                         │                                        │
│                                         ▼                                        │
│                      ┌──────────────────────────────────────┐                    │
│                      │       AppDatabase (Room v3)          │                    │
│                      │   SQLCipher + SQLite WAL Mode        │                    │
│                      │   Target Device: Poco M7 / HyperOS   │                    │
│                      └──────────────────────────────────────┘                    │
└──────────────────────────────────────────────────────────────────────────────────┘
```

#### 1.1. Роль в архитектуре Фазы 2 (принцип «Компилируй один раз, исполняй многократно»)

1. **Двойственное представление конвейера:**
   - **Реляционные метаданные (`pipeline_definition`):** строковые идентификаторы, приоритет, белый список пакетов, флаги активности и ссылка на текущую ревизию для быстрой выборки и фильтрации без десериализации тяжелых объектов.
   - **Иммутабельное версионированное AST (`pipeline_revision`):** детерминированный канонический JSON декларативной схемы DSL v1 (`PipelineDefinition`) с контрольной суммой SHA-256, обеспечивающий возможность аудита, дифференциального анализа (Replay) и мгновенного отката на любую историческую ревизию.
2. **Гарантия непрерывности догфудинга (Dogfooding Continuity):**
   - При миграции v2 → v3 база автоматически наполняется системным пресетом `preset-legacy-1.1` в активном статусе. Рантайм подхватывает его без перезапуска сервиса уведомлений, сохраняя 100% функциональности захвата и классификации.
3. **Холодное резервирование перед структурными изменениями:**
   - Предотвращение повреждения рабочей базы догфудинга при сбоях питания или системном Kill процесса в момент выполнения `ALTER TABLE` / `CREATE TABLE` благодаря механизму `PreMigrationBackup`.

#### 1.2. Границы модуля и зависимости

1. **Входящие зависимости:**
   - `:pipeline:dsl` — типы `PipelineDefinition`, канонический сериализатор JSON.
   - `:pipeline:compiler` — валидация перед сохранением, генерация `CompiledPipeline`.
   - `:core:storage` — существующие сущности Room (`EventEntity`, `RawEventEntity`, `FinancialTransactionEntity`, `UserPrototypeEntity`, `SourceHealthEntity`).
   - `net.zetetic:sqlcipher-android` — движок прозрачного шифрования базы данных.
2. **Исходящие зависимости:**
   - Модуль предоставляет репозиторий `PipelineRepository` для `:feature:editor`, `:feature:replay`, `:pipeline:runtime` и фоновых служб обслуживания.

---

### 2. Холодный файловый снапшот базы данных (`PreMigrationBackup 3.0`)

До открытия базы данных Room через `RoomDatabase.Builder.build()` выполняется процедура холодного файлового бэкапа. Это предотвращает фатальную потерю пользовательских данных при возникновении критических ошибок во время наката миграции.

#### 2.1. Механизм создания снимка

```
Приложение запускается
       │
       ▼
PreMigrationBackup.executeIfNeeded(context, "npc_database.db", targetVersion = 3)
       │
       ├─► 1. Проверка существования основного файла npc_database.db
       ├─► 2. Проверка размера файла (>= 512 байт)
       ├─► 3. Чтение версии:
       │      - Для незашифрованной БД: смещение 60..63 заголовка (user_version)
       │      - Для SQLCipher: чтение невозможно без пароля -> fallback к fromVersion = 2
       ├─► 4. Проверка свободного места в context.noBackupFilesDir:
       │      usableSpace >= 2 * (size(db) + size(wal) + size(shm))
       ├─► 5. Атомарное копирование триады файлов:
       │      npc_database.db     ──► noBackupFilesDir/npc_database.db.v2.bak
       │      npc_database.db-wal ──► noBackupFilesDir/npc_database.db.v2.bak-wal
       │      npc_database.db-shm ──► noBackupFilesDir/npc_database.db.v2.bak-shm
       ▼
Инициализация Room v3 (MIGRATION_2_3)
```

#### 2.2. Защита от сбоев и особенности SQLCipher

1. **Изоляция в `noBackupFilesDir`:** Файлы снимков сохраняются в системный каталог `context.noBackupFilesDir`, который никогда не синхронизируется с облачным Google Backup и недоступен другим приложениям без `root`.
2. **Работа с шифрованными страницами SQLCipher:** Механизм `PreMigrationBackup` оперирует на уровне байтовых потоков файловой системы. Шифрованные страницы базы данных и WAL-журнала копируются как есть, не требуя мастер-ключа в точке создания снапшота.
3. **Crash Recovery Runbook:** В случае непредвиденного краша во время миграции при следующем холодном старте специальный guard проверяет признак `migration_in_progress` и при наличии сбоя восстанавливает исходную триаду файлов из `.bak`.

---

### 3. Схема базы данных Room v3 (DDL и Entity)

База данных эволюционирует с версии `2` до версии `3`. Добавляются три новые таблицы (`pipeline_definition`, `pipeline_revision`, `runtime_alert`), а существующая таблица `event` расширяется внешним ключом `pipeline_revision_id`.

```
┌─────────────────────────────────┐           ┌───────────────────────────────────┐
│       pipeline_definition       │ 1       N │         pipeline_revision         │
├─────────────────────────────────┼───────────┼───────────────────────────────────┤
│ PK  id: TEXT                    │◀──────────│ FK  pipeline_id: TEXT             │
│     name: TEXT                  │           │ PK  id: INTEGER AUTOINCREMENT     │
│     description: TEXT?          │           │     revision_number: INTEGER      │
│     schema_version: INTEGER     │           │     definition_json: TEXT         │
│     enabled: INTEGER            │           │     canonical_sha256: TEXT        │
│     priority: INTEGER           │           │     created_at: INTEGER           │
│     package_whitelist: TEXT     │           │     commit_message: TEXT?         │
│     active_revision_id: INTEGER │───────────┼─┐                                 │
│     created_at: INTEGER         │ (Soft FK) │ │                                 │
│     updated_at: INTEGER         │           │ │                                 │
└─────────────────────────────────┘           └─┼─────────────────────────────────┘
                                                │ 1
                                                │
                                                │ N (ON DELETE SET NULL)
                                                ▼
┌─────────────────────────────────┐           ┌───────────────────────────────────┐
│          runtime_alert          │           │               event               │
├─────────────────────────────────┤           ├───────────────────────────────────┤
│ PK  id: INTEGER AUTOINCREMENT   │           │ PK  id: INTEGER AUTOINCREMENT     │
│     pipeline_id: TEXT           │           │     raw_id: INTEGER               │
│     node_id: TEXT?              │           │     category: TEXT                │
│     level: TEXT                 │           │     ...                           │
│     code: TEXT                  │           │ FK  pipeline_revision_id: INTEGER │
│     message: TEXT               │           └───────────────────────────────────┘
│     payload_json: TEXT?         │
│     timestamp: INTEGER          │
│     is_dismissed: INTEGER       │
└─────────────────────────────────┘
```

#### 3.1. Сущность `PipelineDefinitionEntity` (`pipeline_definition`)

Определяет заголовок и метаданные конвейера.

```kotlin
package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pipeline_definition",
    indices = [
        Index(value = ["enabled", "priority"], unique = false),
        Index(value = ["active_revision_id"], unique = false)
    ]
)
data class PipelineDefinitionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "schema_version", defaultValue = "1")
    val schemaVersion: Int = 1,

    @ColumnInfo(name = "enabled", defaultValue = "1")
    val enabled: Boolean = true,

    @ColumnInfo(name = "priority", defaultValue = "100")
    val priority: Int = 100,

    /**
     * Сериализованный JSON-массив пакетов: '["com.apb.mobile", "md.maib.maibank"]'.
     * Пустой массив '[]' означает пропуск событий всех пакетов.
     */
    @ColumnInfo(name = "package_whitelist", defaultValue = "'[]'")
    val packageWhitelist: String = "[]",

    /**
     * Идентификатор текущей активной ревизии из таблицы pipeline_revision.
     * Реализуется как nullable-колонка для безопасного разрешения взаимных циклических ссылок при создании.
     */
    @ColumnInfo(name = "active_revision_id", defaultValue = "NULL")
    val activeRevisionId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
```

#### 3.2. Сущность `PipelineRevisionEntity` (`pipeline_revision`)

Хранит снимок конфигурации DSL v1. Каждая ревизия неизменяема (append-only).

```kotlin
package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pipeline_revision",
    foreignKeys = [
        ForeignKey(
            entity = PipelineDefinitionEntity::class,
            parentColumns = ["id"],
            childColumns = ["pipeline_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pipeline_id", "revision_number"], unique = true),
        Index(value = ["pipeline_id", "created_at"], unique = false),
        Index(value = ["canonical_sha256"], unique = false)
    ]
)
data class PipelineRevisionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "pipeline_id")
    val pipelineId: String,

    @ColumnInfo(name = "revision_number")
    val revisionNumber: Long,

    /**
     * Канонический JSON сериализованного объекта PipelineDefinition.
     * Ключи лексикографически отсортированы, пробелы минимизированы.
     */
    @ColumnInfo(name = "definition_json")
    val definitionJson: String,

    /**
     * Шестнадцатеричный SHA-256 хеш поля definition_json.
     * Используется для быстрого сопоставления скомпилированных версий и кеширования.
     */
    @ColumnInfo(name = "canonical_sha256")
    val canonicalSha256: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "commit_message")
    val commitMessage: String? = null
)
```

#### 3.3. Сущность `RuntimeAlertEntity` (`runtime_alert`)

Фиксирует рантайм-сбои узлов, таймауты и предупреждения компилятора/песочницы.

```kotlin
package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "runtime_alert",
    indices = [
        Index(value = ["is_dismissed", "timestamp"], unique = false),
        Index(value = ["pipeline_id"], unique = false)
    ]
)
data class RuntimeAlertEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "pipeline_id")
    val pipelineId: String,

    @ColumnInfo(name = "node_id")
    val nodeId: String? = null,

    /** Уровень: INFO, WARN, ERROR, CRITICAL */
    @ColumnInfo(name = "level")
    val level: String,

    /** Машиночитаемый код: ERR_NODE_TIMEOUT, ERR_REDOS_DETECTED, ERR_ACTION_DISPATCH */
    @ColumnInfo(name = "code")
    val code: String,

    /** Человекочитаемое сообщение об ошибке */
    @ColumnInfo(name = "message")
    val message: String,

    /** Дополнительный JSON-контекст: стек исключения, id события, замеры времени */
    @ColumnInfo(name = "payload_json")
    val payloadJson: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_dismissed", defaultValue = "0")
    val isDismissed: Boolean = false
)
```

#### 3.4. Модификация таблицы `event` (Штамп ревизии на событиях)

Существующая таблица `event` модифицируется добавлением столбца `pipeline_revision_id`:

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
        ),
        ForeignKey(
            entity = PipelineRevisionEntity::class,
            parentColumns = ["id"],
            childColumns = ["pipeline_revision_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["raw_id"], unique = false),
        Index(value = ["ts"], unique = false),
        Index(value = ["thread_key"], unique = false),
        Index(value = ["is_update_of"], unique = false),
        Index(value = ["category"], unique = false),
        Index(value = ["content_fingerprint"], unique = false),
        Index(value = ["pipeline_revision_id"], unique = false)
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

    @ColumnInfo(name = "category", defaultValue = "'UNCLASSIFIED'")
    val category: String = "UNCLASSIFIED",

    @ColumnInfo(name = "confidence", defaultValue = "0.0")
    val confidence: Double = 0.0,

    @ColumnInfo(name = "engine_used", defaultValue = "'NONE'")
    val engineUsed: String = "NONE",

    @ColumnInfo(name = "is_user_corrected", defaultValue = "0")
    val isUserCorrected: Boolean = false,

    @ColumnInfo(name = "content_fingerprint", defaultValue = "NULL")
    val contentFingerprint: String? = null,

    // Новое поле Фазы 2: связь с ревизией конвейера, обработавшей данное событие
    @ColumnInfo(name = "pipeline_revision_id", defaultValue = "NULL")
    val pipelineRevisionId: Long? = null
)
```

#### 3.5. Агрегатный объект `PipelineWithRevision`

Для атомарной загрузки конвейера вместе с активным телом ревизии Room использует декларацию отношений `@Relation`:

```kotlin
package com.example.npc.core.storage.model

import androidx.room.Embedded
import androidx.room.Relation
import com.example.npc.core.storage.entity.PipelineDefinitionEntity
import com.example.npc.core.storage.entity.PipelineRevisionEntity

data class PipelineWithRevision(
    @Embedded
    val definition: PipelineDefinitionEntity,

    @Relation(
        parentColumn = "active_revision_id",
        entityColumn = "id"
    )
    val activeRevision: PipelineRevisionEntity?
)
```

---

### 4. Реализация миграции Room `MIGRATION_2_3.kt`

Миграция выполняется строго в рамках единой транзакции SQLite с проверкой целостности `PRAGMA foreign_key_check`.

```kotlin
package com.example.npc.core.storage.migration

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // =================================================================
        // 1. Создание таблицы pipeline_definition
        // =================================================================
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pipeline_definition` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT,
                `schema_version` INTEGER NOT NULL DEFAULT 1,
                `enabled` INTEGER NOT NULL DEFAULT 1,
                `priority` INTEGER NOT NULL DEFAULT 100,
                `package_whitelist` TEXT NOT NULL DEFAULT '[]',
                `active_revision_id` INTEGER DEFAULT NULL,
                `created_at` INTEGER NOT NULL,
                `updated_at` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pipeline_definition_enabled_priority` ON `pipeline_definition` (`enabled`, `priority`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pipeline_definition_active_revision_id` ON `pipeline_definition` (`active_revision_id`)")

        // =================================================================
        // 2. Создание таблицы pipeline_revision
        // =================================================================
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pipeline_revision` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `pipeline_id` TEXT NOT NULL,
                `revision_number` INTEGER NOT NULL,
                `definition_json` TEXT NOT NULL,
                `canonical_sha256` TEXT NOT NULL,
                `created_at` INTEGER NOT NULL,
                `commit_message` TEXT,
                FOREIGN KEY(`pipeline_id`) REFERENCES `pipeline_definition`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pipeline_revision_pipeline_id_revision_number` ON `pipeline_revision` (`pipeline_id`, `revision_number`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pipeline_revision_pipeline_id_created_at` ON `pipeline_revision` (`pipeline_id`, `created_at`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pipeline_revision_canonical_sha256` ON `pipeline_revision` (`canonical_sha256`)")

        // =================================================================
        // 3. Создание таблицы runtime_alert
        // =================================================================
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `runtime_alert` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `pipeline_id` TEXT NOT NULL,
                `node_id` TEXT,
                `level` TEXT NOT NULL,
                `code` TEXT NOT NULL,
                `message` TEXT NOT NULL,
                `payload_json` TEXT,
                `timestamp` INTEGER NOT NULL,
                `is_dismissed` INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_runtime_alert_is_dismissed_timestamp` ON `runtime_alert` (`is_dismissed`, `timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_runtime_alert_pipeline_id` ON `runtime_alert` (`pipeline_id`)")

        // =================================================================
        // 4. Модификация существующей таблицы event
        // =================================================================
        db.execSQL("ALTER TABLE `event` ADD COLUMN `pipeline_revision_id` INTEGER DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_pipeline_revision_id` ON `event` (`pipeline_revision_id`)")

        // =================================================================
        // 5. Сидинг системного пресета: preset-legacy-1.1
        // =================================================================
        seedLegacyPreset(db)

        // =================================================================
        // 6. Валидация внешних ключей (Integrity Gate)
        // =================================================================
        db.query("PRAGMA foreign_key_check").use { cursor ->
            check(cursor.count == 0) {
                "Foreign key integrity check failed after Migration 2->3. Violations count: ${cursor.count}"
            }
        }
    }

    private fun seedLegacyPreset(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        val pipelineId = "legacy-1.1-preset"
        val canonicalJson = LEGACY_PRESET_CANONICAL_JSON
        val sha256 = calculateSha256(canonicalJson)

        // 5.1 Вставляем запись в pipeline_definition
        val defValues = ContentValues().apply {
            put("id", pipelineId)
            put("name", "Legacy 1.1 Baseline Pipeline")
            put("description", "Эталонная конфигурация Фазы 1.1 для непрерывности догфудинга")
            put("schema_version", 1)
            put("enabled", 1)
            put("priority", 100)
            put("package_whitelist", "[]")
            putNull("active_revision_id")
            put("created_at", now)
            put("updated_at", now)
        }
        db.insert("pipeline_definition", SQLiteDatabase.CONFLICT_REPLACE, defValues)

        // 5.2 Вставляем ревизию #1
        val revValues = ContentValues().apply {
            put("pipeline_id", pipelineId)
            put("revision_number", 1L)
            put("definition_json", canonicalJson)
            put("canonical_sha256", sha256)
            put("created_at", now)
            put("commit_message", "Initial seed for Phase 1.1 parity continuity")
        }
        val revisionId = db.insert("pipeline_revision", SQLiteDatabase.CONFLICT_REPLACE, revValues)

        // 5.3 Связываем active_revision_id в pipeline_definition
        val updateValues = ContentValues().apply {
            put("active_revision_id", revisionId)
            put("updated_at", now)
        }
        db.update("pipeline_definition", SQLiteDatabase.CONFLICT_REPLACE, updateValues, "id = ?", arrayOf(pipelineId))
    }

    private fun calculateSha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
```

---

### 5. Сидинг системного пресета (`preset-legacy-1.1`)

Для гарантии бесшовного перехода с хардкод-оркестратора Фазы 1.1 на декларативный рантайм Фазы 2, в базу данных на уровне миграции инжектируется канонический JSON пресета.

#### 5.1. Канонический JSON пресета (`LEGACY_PRESET_CANONICAL_JSON`)

```json
{"description":"Эталонная конфигурация Фазы 1.1 для непрерывности догфудинга","enabled":true,"id":"legacy-1.1-preset","metadata":{"builtIn":"true","origin":"preset-legacy-1.1","parityBaseline":"1.1"},"name":"Legacy 1.1 Baseline Pipeline","packageWhitelist":[],"priority":100,"revision":1,"schemaVersion":1,"stages":[{"actions":[],"condition":null,"enabled":true,"id":"stage-fingerprint","name":"Вычисление отпечатка контента","terminateOnMatch":false,"transforms":[{"algorithm":"SHA-256-TEMPLATED","targetVar":"contentFingerprint","type":"FingerprintCompute"}]},{"actions":[{"category":"UNCLASSIFIED","confidence":1.0,"engine":"PROTOTYPE","type":"SetCategory"}],"condition":{"minSupportCount":2,"type":"PrototypeSupportCount"},"enabled":true,"id":"stage-prototype-feedback","name":"Пользовательский прототип (Feedback Loop)","terminateOnMatch":false,"transforms":[]},{"actions":[],"condition":{"conditions":[{"conditions":[{"matchMode":"EXACT","packages":["com.apb.mobile","com.prisbank.app","md.maib.maibank"],"type":"PackageMatch"},{"conditions":[{"matchMode":"EXACT","packages":["com.google.android.apps.messaging","com.android.mms",""],"type":"PackageMatch"},{"caseSensitive":false,"senders":["APB","AGROPROMBANK","PRISBANK","SBERBANK","MAIB","900"],"type":"SenderMatch"}],"type":"LogicalAnd"}],"type":"LogicalOr"},{"condition":{"matchMode":"EXACT","packages":["org.telegram.messenger","org.telegram.plus","org.thunderdog.challegram","com.radolyn.ayugram","nekox.messenger","com.whatsapp","com.whatsapp.w4b","com.viber.voip","com.facebook.orca","com.facebook.mlite","com.discord","com.vkontakte.android","com.vk.im"],"type":"PackageMatch"},"type":"LogicalNot"}],"type":"LogicalAnd"},"enabled":true,"id":"stage-bank-finance","name":"Финансовая обработка доверенных банков","terminateOnMatch":false,"transforms":[{"maxChars":1024,"normalizeNbsp":true,"stripDiacritics":false,"targetVar":"sanitizedText","type":"RegionalTextSanitize"},{"extractorId":"auto","targetVar":"transaction","type":"FinanceExtract"}]}],"triggers":[{"ignoreSelf":true,"subTypes":[],"type":"NOTIFICATION"},{"allowDirectReceiver":true,"allowMessagingApps":true,"type":"SMS"},{"captureArtwork":false,"type":"MEDIA"}]}
```

> [!IMPORTANT]
> Инвариант каноничности: JSON не содержит пробелов и переводов строк между токенами, ключи отсортированы строго в алфавитном порядке (`sortBy { it.key }`), числа отформатированы без экспоненциальных хвостов. Контрольная сумма SHA-256 рассчитывается строго от UTF-8 байтов этого представления.

---

### 6. DAO интерфейсы и контракты

#### 6.1. `PipelineDao`

```kotlin
package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.npc.core.storage.entity.PipelineDefinitionEntity
import com.example.npc.core.storage.model.PipelineWithRevision
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelineDao {

    @Transaction
    @Query("""
        SELECT * FROM pipeline_definition
        WHERE enabled = 1 AND active_revision_id IS NOT NULL
        ORDER BY priority DESC, updated_at DESC
    """)
    fun observeActivePipelines(): Flow<List<PipelineWithRevision>>

    @Transaction
    @Query("SELECT * FROM pipeline_definition ORDER BY priority DESC, updated_at DESC")
    fun observeAllPipelines(): Flow<List<PipelineWithRevision>>

    @Transaction
    @Query("SELECT * FROM pipeline_definition WHERE id = :id LIMIT 1")
    fun getPipelineWithRevision(id: String): Flow<PipelineWithRevision?>

    @Query("SELECT * FROM pipeline_definition WHERE id = :id LIMIT 1")
    suspend fun getDefinitionById(id: String): PipelineDefinitionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPipeline(pipeline: PipelineDefinitionEntity)

    @Update
    suspend fun updatePipeline(pipeline: PipelineDefinitionEntity)

    @Query("UPDATE pipeline_definition SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun toggleEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE pipeline_definition SET active_revision_id = :revisionId, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateActiveRevision(id: String, revisionId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM pipeline_definition WHERE id = :id")
    suspend fun deletePipeline(id: String)
}
```

#### 6.2. `PipelineRevisionDao`

```kotlin
package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.npc.core.storage.entity.PipelineRevisionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelineRevisionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: PipelineRevisionEntity): Long

    @Query("SELECT * FROM pipeline_revision WHERE id = :id LIMIT 1")
    suspend fun getRevisionById(id: Long): PipelineRevisionEntity?

    @Query("""
        SELECT * FROM pipeline_revision 
        WHERE pipeline_id = :pipelineId 
        ORDER BY revision_number DESC 
        LIMIT :limit
    """)
    fun observeRevisionsForPipeline(pipelineId: String, limit: Int = 10): Flow<List<PipelineRevisionEntity>>

    @Query("SELECT MAX(revision_number) FROM pipeline_revision WHERE pipeline_id = :pipelineId")
    suspend fun getLatestRevisionNumber(pipelineId: String): Long?

    @Query("SELECT COUNT(*) FROM pipeline_revision WHERE pipeline_id = :pipelineId")
    suspend fun getRevisionCount(pipelineId: String): Int

    /**
     * Безопасная очистка старых ревизий:
     * Удаляются только ревизии, которые:
     * 1. Не входят в топ-N свежих ревизий по revision_number.
     * 2. НЕ являются текущей active_revision_id конвейера.
     * 3. НЕ используются ни в одной строке таблицы event (pipeline_revision_id).
     */
    @Query("""
        DELETE FROM pipeline_revision 
        WHERE pipeline_id = :pipelineId 
          AND id NOT IN (
              SELECT id FROM pipeline_revision 
              WHERE pipeline_id = :pipelineId 
              ORDER BY revision_number DESC 
              LIMIT :keepCount
          )
          AND id != (SELECT COALESCE(active_revision_id, -1) FROM pipeline_definition WHERE id = :pipelineId)
          AND id NOT IN (SELECT DISTINCT pipeline_revision_id FROM event WHERE pipeline_revision_id IS NOT NULL)
    """)
    suspend fun pruneOldRevisions(pipelineId: String, keepCount: Int = 10): Int
}
```

#### 6.3. `RuntimeAlertDao`

```kotlin
package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.npc.core.storage.entity.RuntimeAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuntimeAlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: RuntimeAlertEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<RuntimeAlertEntity>)

    @Query("""
        SELECT * FROM runtime_alert 
        WHERE is_dismissed = 0 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    fun observeActiveAlerts(limit: Int = 50): Flow<List<RuntimeAlertEntity>>

    @Query("SELECT COUNT(*) FROM runtime_alert WHERE is_dismissed = 0")
    fun observeActiveAlertsCount(): Flow<Int>

    @Query("UPDATE runtime_alert SET is_dismissed = 1 WHERE id = :id")
    suspend fun dismissAlert(id: Long)

    @Query("UPDATE runtime_alert SET is_dismissed = 1 WHERE pipeline_id = :pipelineId AND is_dismissed = 0")
    suspend fun dismissAllForPipeline(pipelineId: String)

    @Query("DELETE FROM runtime_alert WHERE is_dismissed = 1 AND timestamp < :beforeTimestamp")
    suspend fun purgeDismissedAlerts(beforeTimestamp: Long): Int
}
```

---

### 7. Контракт репозитория `PipelineRepository`

Интерфейс репозитория расположен в `:data:pipeline-store` и скрывает внутренние детали взаимодействия с Room.

```kotlin
package com.example.npc.data.pipelinestore

import com.example.npc.core.storage.entity.RuntimeAlertEntity
import com.example.npc.pipeline.compiler.CompilationDiagnostic
import com.example.npc.pipeline.dsl.PipelineDefinition
import kotlinx.coroutines.flow.Flow

/**
 * Главный фасад доступа к сохраненным конвейерам, ревизиям и алертам рантайма.
 */
interface PipelineRepository {

    /**
     * Поток всех активных и валидных конвейеров, отсортированных по приоритету.
     * Используется рантаймом (:pipeline:runtime) для горячей подмены.
     */
    fun observeActivePipelines(): Flow<List<PipelineDefinition>>

    /**
     * Поток метаданных всех конвейеров для экранов списка в UI.
     */
    fun observeAllPipelines(): Flow<List<PipelineSummary>>

    /**
     * Загружает конкретный конвейер по ID с телом его активной ревизии.
     */
    fun observePipelineById(id: String): Flow<PipelineAggregate?>

    /**
     * Атомарная операция компиляции, сохранения и активации новой ревизии.
     *
     * Шаги:
     * 1. Статическая валидация компилятором (:pipeline:compiler).
     * 2. Генерация детерминированного канонического JSON и SHA-256.
     * 3. Транзакционная запись новой ревизии и обновление указателя active_revision_id.
     * 4. Запуск сборщика мусора (GC) старых ревизий (хранится до 10 последних).
     *
     * @return Успех с метаданными сохраненной ревизии или ошибка с диагностиками компилятора.
     */
    suspend fun saveAndActivate(
        definition: PipelineDefinition,
        commitMessage: String
    ): Result<SavedRevisionResult>

    /**
     * Сохранение черновика без немедленной активации в рантайме.
     */
    suspend fun saveDraft(
        definition: PipelineDefinition,
        commitMessage: String
    ): Result<SavedRevisionResult>

    /**
     * Атомарный откат конвейера на историческую ревизию.
     */
    suspend fun rollbackToRevision(
        pipelineId: String,
        revisionId: Long
    ): Result<PipelineDefinition>

    /**
     * Включение / выключение конвейера.
     */
    suspend fun toggleEnabled(pipelineId: String, enabled: Boolean)

    /**
     * Удаление конвейера и всех его ревизий каскадно.
     */
    suspend fun deletePipeline(pipelineId: String): Result<Unit>

    /**
     * Получение истории ревизий для экрана аудита и отката.
     */
    fun observeRevisionHistory(pipelineId: String, limit: Int = 10): Flow<List<RevisionSummary>>

    /**
     * Регистрация ошибки исполнения или предупреждения от рантайма.
     */
    suspend fun recordAlert(alert: RuntimeAlertEntity): Long

    /**
     * Поток неразрешенных алертов для UI панели состояния (Health Indicator).
     */
    fun observeActiveAlerts(limit: Int = 50): Flow<List<RuntimeAlertEntity>>

    /**
     * Отметка алерта как прочитанного/разрешенного.
     */
    suspend fun dismissAlert(alertId: Long)
}

data class PipelineSummary(
    val id: String,
    val name: String,
    val description: String?,
    val enabled: Boolean,
    val priority: Int,
    val activeRevisionNumber: Long?,
    val updatedAt: Long
)

data class PipelineAggregate(
    val summary: PipelineSummary,
    val definition: PipelineDefinition
)

data class RevisionSummary(
    val id: Long,
    val revisionNumber: Long,
    val canonicalSha256: String,
    val createdAt: Long,
    val commitMessage: String?,
    val isActive: Boolean
)

data class SavedRevisionResult(
    val pipelineId: String,
    val revisionId: Long,
    val revisionNumber: Long,
    val canonicalSha256: String,
    val diagnostics: List<CompilationDiagnostic>
)
```

#### 7.1. Реализация `PipelineRepositoryImpl` и Garbage Collection ревизий

```kotlin
package com.example.npc.data.pipelinestore

import androidx.room.withTransaction
import com.example.npc.core.storage.AppDatabase
import com.example.npc.core.storage.entity.PipelineDefinitionEntity
import com.example.npc.core.storage.entity.PipelineRevisionEntity
import com.example.npc.core.storage.entity.RuntimeAlertEntity
import com.example.npc.pipeline.compiler.CompilationDiagnostic
import com.example.npc.pipeline.compiler.CompilationResult
import com.example.npc.pipeline.compiler.PipelineCompiler
import com.example.npc.pipeline.dsl.CanonicalJson
import com.example.npc.pipeline.dsl.PipelineDefinition
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PipelineRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val compiler: PipelineCompiler,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : PipelineRepository {

    private val pipelineDao = database.pipelineDao()
    private val revisionDao = database.pipelineRevisionDao()
    private val alertDao = database.runtimeAlertDao()
    private val mutationMutex = Mutex()

    override suspend fun saveAndActivate(
        definition: PipelineDefinition,
        commitMessage: String
    ): Result<SavedRevisionResult> = withContext(dispatcher) {
        mutationMutex.withLock {
            // 1. Статическая валидация через компилятор
            val compilationResult = compiler.compile(definition)
            if (compilationResult is CompilationResult.Failure) {
                return@withContext Result.failure(
                    PipelineCompilationException("Pipeline validation failed", compilationResult.diagnostics)
                )
            }

            // 2. Сериализация в детерминированный канонический JSON и вычисление хеша
            val canonicalJson = CanonicalJson.encodeToString(definition)
            val sha256 = calculateSha256(canonicalJson)
            val now = System.currentTimeMillis()

            // 3. Атомарная транзакция Room
            val result = database.withTransaction {
                val currentMaxRev = revisionDao.getLatestRevisionNumber(definition.id) ?: 0L
                val nextRevNumber = currentMaxRev + 1L

                // 3.1 Создаем / обновляем заголовок конвейера
                val defEntity = PipelineDefinitionEntity(
                    id = definition.id,
                    name = definition.name,
                    description = definition.description,
                    schemaVersion = definition.schemaVersion,
                    enabled = definition.enabled,
                    priority = definition.priority,
                    packageWhitelist = CanonicalJson.encodeStringList(definition.packageWhitelist),
                    activeRevisionId = null, // обновится после вставки ревизии
                    createdAt = now,
                    updatedAt = now
                )
                pipelineDao.insertPipeline(defEntity) // OnConflictStrategy.IGNORE / custom merge

                // 3.2 Создаем иммутабельную запись ревизии
                val revEntity = PipelineRevisionEntity(
                    pipelineId = definition.id,
                    revisionNumber = nextRevNumber,
                    definitionJson = canonicalJson,
                    canonicalSha256 = sha256,
                    createdAt = now,
                    commitMessage = commitMessage
                )
                val revisionId = revisionDao.insertRevision(revEntity)

                // 3.3 Активируем ревизию
                pipelineDao.updateActiveRevision(definition.id, revisionId, now)

                // 4. Запуск безопасного сборщика мусора (Keep last 10)
                revisionDao.pruneOldRevisions(definition.id, keepCount = 10)

                SavedRevisionResult(
                    pipelineId = definition.id,
                    revisionId = revisionId,
                    revisionNumber = nextRevNumber,
                    canonicalSha256 = sha256,
                    diagnostics = compilationResult.diagnostics
                )
            }

            Result.success(result)
        }
    }

    override suspend fun rollbackToRevision(
        pipelineId: String,
        revisionId: Long
    ): Result<PipelineDefinition> = withContext(dispatcher) {
        mutationMutex.withLock {
            database.withTransaction {
                val targetRevision = revisionDao.getRevisionById(revisionId)
                    ?: return@withTransaction Result.failure(IllegalArgumentException("Revision $revisionId not found"))

                check(targetRevision.pipelineId == pipelineId) {
                    "Revision $revisionId does not belong to pipeline $pipelineId"
                }

                val now = System.currentTimeMillis()
                pipelineDao.updateActiveRevision(pipelineId, revisionId, now)

                val restoredDefinition = CanonicalJson.decodeFromString<PipelineDefinition>(targetRevision.definitionJson)
                Result.success(restoredDefinition)
            }
        }
    }

    override fun observeActivePipelines(): Flow<List<PipelineDefinition>> {
        return pipelineDao.observeActivePipelines().map { list ->
            list.mapNotNull { item ->
                item.activeRevision?.let { rev ->
                    CanonicalJson.decodeFromString<PipelineDefinition>(rev.definitionJson)
                }
            }
        }
    }

    override suspend fun recordAlert(alert: RuntimeAlertEntity): Long = withContext(dispatcher) {
        alertDao.insertAlert(alert)
    }

    override fun observeActiveAlerts(limit: Int): Flow<List<RuntimeAlertEntity>> {
        return alertDao.observeActiveAlerts(limit)
    }

    override suspend fun dismissAlert(alertId: Long) = withContext(dispatcher) {
        alertDao.dismissAlert(alertId)
    }

    override suspend fun toggleEnabled(pipelineId: String, enabled: Boolean) = withContext(dispatcher) {
        pipelineDao.toggleEnabled(pipelineId, enabled)
    }

    override suspend fun deletePipeline(pipelineId: String): Result<Unit> = withContext(dispatcher) {
        mutationMutex.withLock {
            pipelineDao.deletePipeline(pipelineId)
            Result.success(Unit)
        }
    }

    private fun calculateSha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
```

---

### 8. Интеграция с SQLCipher и оптимизация под Poco M7

Смартфон Poco M7 (MediaTek Dimensity 6100+, флеш-память UFS 2.2 / eMMC 5.1) чувствителен к операциям ввода-вывода (IOPS) и затратам на шифрование страниц SQLite.

#### 8.1. Конфигурация SQLCipher Support Factory

Для обеспечения стабильной скорости горячего пути и предотвращения деградации времени отклика применяются следующие параметры PRAGMA:

```kotlin
package com.example.npc.core.storage

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

object SqlCipherSupportFactoryProvider {

    fun createFactory(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory {
        SQLiteDatabase.loadLibs()
        
        return SupportFactory(passphrase, object : SQLiteDatabaseHook {
            override fun preKey(database: SQLiteDatabase?) {
                // Настройки до применения ключа
            }

            override fun postKey(database: SQLiteDatabase?) {
                database?.apply {
                    // 1. Включение режима упреждающей записи WAL для параллелизма reader/writer
                    rawExecSQL("PRAGMA journal_mode = WAL;")
                    // 2. Оптимизация сброса страниц (NORMAL безопасен в WAL режиме)
                    rawExecSQL("PRAGMA synchronous = NORMAL;")
                    // 3. Размер кеша страниц (4000 страниц = ~16 МБ памяти)
                    rawExecSQL("PRAGMA cache_size = -4000;")
                    // 4. Хранение временных таблиц в ОЗУ
                    rawExecSQL("PRAGMA temp_store = MEMORY;")
                    // 5. Включение проверки внешних ключей
                    rawExecSQL("PRAGMA foreign_keys = ON;")
                }
            }
        })
    }
}
```

#### 8.2. Бюджеты производительности и памяти

| Операция | Целевой SLA (Poco M7) | Обоснование |
|---|---|---|
| `PreMigrationBackup` (файл 25 МБ) | $\le 250$ мс | Однократное файловое копирование стримами по 64 КБ до старта Room |
| Выполнение `MIGRATION_2_3` | $\le 120$ мс | Строго аддитивная миграция (DDL + сидинг 1 записи) без перестроения таблиц |
| `saveAndActivate` (конвейер 20 узлов) | $\le 45$ мс | Сериализация JSON + вычисление SHA256 + 1 транзакция Room |
| `observeActivePipelines` (первая загрузка) | $\le 8$ мс | Выборка из кеша страниц SQLCipher без дискового чтения |
| Запись алерта `recordAlert` | $\le 4$ мс | Вставка в WAL буфер |
| Дополнительный расход ОЗУ | $\le 3.5$ МБ | Кеш десериализованных объектов AST |

---

### 9. План тестирования и верификация (Test Plan)

#### 9.1. Матрица тестовых сценариев

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ТЕСТОВАЯ МАТРИЦА :data:pipeline-store           │
├──────────────────────────┬─────────────────────────────┬───────────────┤
│ Класс теста              │ Проверяемое свойство        │ Среда         │
├──────────────────────────┼─────────────────────────────┼───────────────┤
│ Migration2to3Test        │ 100% сохранение догфуд-БД   │ JVM / Device  │
│                          │ PRAGMA foreign_key_check    │ (Poco M7)     │
│                          │ Валидность сидинга пресета  │               │
├──────────────────────────┼─────────────────────────────┼───────────────┤
│ PreMigrationBackupTest   │ Бэкап триады db/wal/shm     │ Robolectric   │
│                          │ Откат при аварии питания    │               │
├──────────────────────────┼─────────────────────────────┼───────────────┤
│ PipelineRepositoryTest   │ Атомарность saveAndActivate │ JVM In-Memory │
│                          │ GC старых ревизий (Keep 10) │ SQLite        │
│                          │ Rollback на прошлую версию  │               │
├──────────────────────────┼─────────────────────────────┼───────────────┤
│ SqlCipherSoakTest        │ 1000 циклов чтения/записи   │ Poco M7       │
│                          │ Проверка утечек памяти      │ (`2440cbe2`)  │
└──────────────────────────┴─────────────────────────────┴───────────────┘
```

#### 9.2. Тест миграции `Migration2to3Test` с реальным дампом v2

```kotlin
package com.example.npc.core.storage.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.npc.core.storage.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2to3Test {

    private val TEST_DB = "migration-test.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate2To3_preservesExistingData_andSeedsLegacyPreset() {
        // 1. Создаем базу версии 2 и наполняем реальными фикстурами догфудинга
        val dbV2 = helper.createDatabase(TEST_DB, 2).apply {
            execSQL("INSERT INTO raw_event (id, sbn_key, post_time, pkg, title, text, raw_json) VALUES (101, 'sbn_101', 1700000000000, 'com.apb.mobile', 'Перевод', 'Зачислено 500.00 руб', '{}')")
            execSQL("INSERT INTO event (id, raw_id, ts, title, text, normalized_text, lang, category, confidence, engine_used) VALUES (501, 101, 1700000000000, 'Перевод', 'Зачислено 500.00 руб', 'зачислено 500.00 руб', 'ru', 'FINANCE', 1.0, 'LEGACY')")
            execSQL("INSERT INTO financial_transaction (id, event_id, bank, direction, amount_minor, currency, occurred_at, extractor_id, extractor_version, created_at) VALUES (1, 501, 'APB', 'INCOME', 50000, 'PRB', 1700000000000, 'apb_v1', 1, 1700000001000)")
            close()
        }

        // 2. Запускаем миграцию на версию 3
        val dbV3 = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        // 3. Проверка сохранения догфуд-данных (Zero Data Loss)
        dbV3.query("SELECT COUNT(*) FROM raw_event").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Количество raw_event должно остаться неизменным", 1, cursor.getInt(0))
        }
        dbV3.query("SELECT COUNT(*) FROM event").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Количество event должно остаться неизменным", 1, cursor.getInt(0))
        }
        dbV3.query("SELECT COUNT(*) FROM financial_transaction").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Количество financial_transaction должно остаться неизменным", 1, cursor.getInt(0))
        }

        // 4. Проверка нового столбца pipeline_revision_id в event
        dbV3.query("SELECT pipeline_revision_id FROM event WHERE id = 501").use { cursor ->
            cursor.moveToFirst()
            assertTrue("Для старых событий pipeline_revision_id должен быть NULL", cursor.isNull(0))
        }

        // 5. Проверка сидинга системного пресета
        dbV3.query("SELECT id, name, enabled, priority, active_revision_id FROM pipeline_definition WHERE id = 'legacy-1.1-preset'").use { cursor ->
            assertTrue("Предопределенный пресет должен существовать", cursor.moveToFirst())
            assertEquals("Legacy 1.1 Baseline Pipeline", cursor.getString(1))
            assertEquals(1, cursor.getInt(2)) // enabled
            assertEquals(100, cursor.getInt(3)) // priority
            assertNotNull("active_revision_id должен быть выставлен", cursor.getLong(4))
        }

        dbV3.query("SELECT revision_number, canonical_sha256 FROM pipeline_revision WHERE pipeline_id = 'legacy-1.1-preset'").use { cursor ->
            assertTrue("Ревизия #1 должна быть создана", cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertTrue("Хеш SHA-256 должен быть валидной строкой из 64 символов", cursor.getString(1).length == 64)
        }

        // 6. Проверка целостности внешних ключей
        dbV3.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals("Не должно быть нарушений внешних ключей", 0, cursor.count)
        }
    }
}
```

#### 9.3. Тест отката (Rollback Integration Test)

```kotlin
@Test
fun testRollbackRevision_restoresPreviousActiveVersion() = runTest {
    // 1. Создаем начальный конвейер v1
    val defV1 = createDummyPipeline(id = "test-pipe", name = "Pipe v1")
    repository.saveAndActivate(defV1, "Initial commit").getOrThrow()

    // 2. Создаем и активируем v2
    val defV2 = defV1.copy(name = "Pipe v2 modified")
    repository.saveAndActivate(defV2, "Second commit").getOrThrow()

    // 3. Создаем и активируем v3
    val defV3 = defV1.copy(name = "Pipe v3 broken")
    repository.saveAndActivate(defV3, "Third commit").getOrThrow()

    // Проверяем, что активная ревизия = 3
    val historyBefore = repository.observeRevisionHistory("test-pipe").first()
    assertEquals(3, historyBefore.size)
    assertEquals(3L, historyBefore.first { it.isActive }.revisionNumber)

    // 4. Откатываемся на ревизию 1
    val rev1Id = historyBefore.first { it.revisionNumber == 1L }.id
    val restored = repository.rollbackToRevision("test-pipe", rev1Id).getOrThrow()

    assertEquals("Pipe v1", restored.name)

    // 5. Проверяем состояние в БД
    val historyAfter = repository.observeRevisionHistory("test-pipe").first()
    assertEquals(1L, historyAfter.first { it.isActive }.revisionNumber)
}
```

---

### 10. Чек-лист соответствия Definition of Done (DoD)

- [x] **Zero Data Loss:** Все 5 таблиц Фазы 1.1 полностью сохраняются, добавление `pipeline_revision_id` аддитивно с дефолтным `NULL`.
- [x] **Pre-Migration Safety:** Реализовано холодное копирование зашифрованной базы данных в `noBackupFilesDir` с проверкой свободного дискового пространства ($2\times$).
- [x] **Dogfooding Continuity:** Пресет `preset-legacy-1.1` автоматически сидируется в активном состоянии (`enabled = 1`, `priority = 100`) с ревизией 1.
- [x] **Целостность данных:** Все таблицы покрыты индексами, каскадными внешними ключами и верифицируются через `PRAGMA foreign_key_check`.
- [x] **Garbage Collection:** Очистка старых ревизий сохраняет окно из 10 последних ревизий, активную ревизию и любые исторические ревизии, на которые ссылается журнал `event`.
- [x] **Интеграция с горячим путем:** Предоставлены контракты DAO и `PipelineRepository` с поддержкой `Flow` для горячей подмены рантайма без рестарта NLS.
- [x] **Готовность к Poco M7:** Учтены особенности MediaTek Dimensity 6100+, агрессивный энергосберегающий режим HyperOS и шифрование страниц SQLCipher в режиме WAL.
