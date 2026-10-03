# Межзонный контракт: Pipeline Store ↔ Pipeline DSL

**Версия:** FROZEN v3  
**Дата заморозки:** 2026-09-28  
**Статус:** FROZEN (GATE 3 PASSED)  
**Стороны контракта:**
- Провайдер декларативной схемы DSL: `zone/pipeline-dsl` (`:pipeline:dsl`)
- Провайдер хранилища Room и миграций: `zone/pipeline-store` (`:core:storage`)
- Потребители: `zone/pipeline-runtime` (`:pipeline:runtime`), `zone/ui-timeline` (`:ui:timeline`), `zone/pipeline-replay` (`:feature:replay`)

---

### 1. Архитектурный контекст и границы

Контракт регламентирует долговременное персистентное хранение декларативных конвейеров, версионирование ревизий, схему базы данных Room v3, процедуру миграции v2 $\to$ v3 без потери данных (Zero Data Loss) и контракт репозитория `PipelineRepository`.

```
┌────────────────────────────────────────────────────────┐
│               zone/pipeline-dsl (:pipeline:dsl)        │
│  - PipelineDefinition, StageDefinition                 │
│  - PipelineJsonCodec (encodeToString / decode)         │
└───────────────────────────┬────────────────────────────┘
                            │ Сериализованный JSON конвейера
                            ▼
┌────────────────────────────────────────────────────────┐
│         zone/pipeline-store (:core:storage)            │
│  - PreMigrationBackup 3.0 (холодный снимок триады БД)   │
│  - Room v3: pipeline_definition, pipeline_revision,    │
│             runtime_alert, event.pipeline_revision_id  │
│  - Автоматический сид preset-legacy-1.1                │
│  - PipelineRepository (Save, Activate, GC 10 revisions)│
└────────────────────────────────────────────────────────┘
```

---

### 2. Схема Room v3 (Сущности БД)

```kotlin
package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Метаданные конвейера в базе данных.
 */
@Entity(tableName = "pipeline_definition")
data class PipelineDefinitionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String?,

    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean,

    @ColumnInfo(name = "priority")
    val priority: Int,

    @ColumnInfo(name = "package_whitelist")
    val packageWhitelist: String, // CSV или JSON-массив

    @ColumnInfo(name = "active_revision_id")
    val activeRevisionId: Long?,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

/**
 * Иммутабельный снимок ревизии конвейера с полным текстом JSON.
 */
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
        Index(value = ["created_at"])
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

    @ColumnInfo(name = "definition_json")
    val definitionJson: String,

    @ColumnInfo(name = "canonical_sha256")
    val canonicalSha256: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "commit_message")
    val commitMessage: String?
)

/**
 * Журнал рантайм-алертов и инцидентов конвейера.
 */
@Entity(
    tableName = "runtime_alert",
    indices = [
        Index(value = ["pipeline_id", "timestamp"]),
        Index(value = ["level"])
    ]
)
data class RuntimeAlertEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "pipeline_id")
    val pipelineId: String,

    @ColumnInfo(name = "node_id")
    val nodeId: String?,

    @ColumnInfo(name = "level")
    val level: String, // "WARNING", "ERROR", "CRITICAL"

    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "message")
    val message: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String?,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long
)
```

#### 2.1. Дополнение таблицы `event`
Таблица `event` расширяется внешним ключом ревизии:
```sql
ALTER TABLE event ADD COLUMN pipeline_revision_id INTEGER DEFAULT NULL 
REFERENCES pipeline_revision(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS index_event_pipeline_revision_id ON event(pipeline_revision_id);
```

---

### 3. Миграция v2 → v3 и PreMigrationBackup 3.0

1. **PreMigrationBackup 3.0:**
   Перед открытием базы данных создается холодный снимок файлов `npc_database.db`, `npc_database.db-wal`, `npc_database.db-shm` в директорию `context.noBackupFilesDir`.
   - Требование свободного места: `usableSpace >= 2 * (size(db) + size(wal) + size(shm))`.
   - Полная совместимость со страницами SQLCipher (бинарное пофайловое копирование).
2. **Сидирование пресета по умолчанию (`preset-legacy-1.1`):**
   При накате `MIGRATION_2_3` или на чистой установке автоматически вставляется запись конвейера `preset-legacy-1.1` со статусом `active_revision_id = 1`, реализующая 100% логики и правил Фазы 1.1.

---

### 4. Контракт репозитория конвейеров (`PipelineRepository`)

```kotlin
package com.example.npc.core.storage.repository

import com.example.npc.pipeline.dsl.PipelineDefinition
import kotlinx.coroutines.flow.Flow

interface PipelineRepository {

    /**
     * Сохраняет новую ревизию конвейера в БД.
     * Автоматически сериализует AST через [PipelineJsonCodec] и вычисляет SHA-256.
     *
     * @param definition Определение конвейера.
     * @param commitMessage Описание изменений.
     * @return Первичный ключ созданной записи ревизии.
     */
    suspend fun saveRevision(
        definition: PipelineDefinition,
        commitMessage: String? = null
    ): Long

    /**
     * Активирует указанную ревизию конвейера как рабочую.
     */
    suspend fun activateRevision(pipelineId: String, revisionNumber: Long)

    /**
     * Загружает текущую активную ревизию конвейера.
     */
    suspend fun getActiveDefinition(pipelineId: String): PipelineDefinition?

    /**
     * Наблюдает за изменением активной версии конвейера.
     */
    fun observeActiveDefinition(pipelineId: String): Flow<PipelineDefinition?>

    /**
     * Получает конкретную ревизию конвейера по номеру.
     */
    suspend fun getRevision(pipelineId: String, revisionNumber: Long): PipelineDefinition?

    /**
     * Очищает старые неактивные ревизии, оставляя не более [keepCount] последних версий.
     */
    suspend fun gcOldRevisions(pipelineId: String, keepCount: Int = 10)

    /**
     * Сохраняет рантайм-алерт о сбое или срабатывании Circuit Breaker.
     */
    suspend fun recordAlert(
        pipelineId: String,
        nodeId: String?,
        level: String,
        code: String,
        message: String,
        payloadJson: String? = null
    )
}
```
