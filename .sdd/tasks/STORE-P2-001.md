## Задача STORE-P2-001: Реализовать Room v3 сущности конвейера

**Файлы:** 
- `core/storage/src/main/kotlin/com/example/npc/core/storage/entity/PipelineDefinitionEntity.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/entity/PipelineRevisionEntity.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/entity/RuntimeAlertEntity.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/model/PipelineWithRevision.kt`
(создать)

**Модуль:** `:core:storage`  
**Спека:** `.sdd/specs/pipeline-store/overview.md#3-схема-базы-данных-room-v3-ddl-и-entity`  
**Контракт:** `.sdd/contracts/pipeline-store__dsl.md#2-схема-room-v3-сущности-бд`  

---

### Сигнатуры (НЕ МЕНЯТЬ):

```kotlin
package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
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
    val description: String? = null,

    @ColumnInfo(name = "schema_version", defaultValue = "1")
    val schemaVersion: Int = 1,

    @ColumnInfo(name = "enabled", defaultValue = "1")
    val enabled: Boolean = true,

    @ColumnInfo(name = "priority", defaultValue = "100")
    val priority: Int = 100,

    @ColumnInfo(name = "package_whitelist", defaultValue = "'[]'")
    val packageWhitelist: String = "[]",

    @ColumnInfo(name = "active_revision_id", defaultValue = "NULL")
    val activeRevisionId: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

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

    @ColumnInfo(name = "definition_json")
    val definitionJson: String,

    @ColumnInfo(name = "canonical_sha256")
    val canonicalSha256: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "commit_message")
    val commitMessage: String? = null
)

@Entity(
    tableName = "runtime_alert",
    indices = [
        Index(value = ["pipeline_id", "timestamp"], unique = false),
        Index(value = ["level"], unique = false),
        Index(value = ["is_dismissed", "timestamp"], unique = false)
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

    @ColumnInfo(name = "level")
    val level: String,

    @ColumnInfo(name = "code")
    val code: String,

    @ColumnInfo(name = "message")
    val message: String,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String? = null,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_dismissed", defaultValue = "0")
    val isDismissed: Boolean = false
)
```

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

### Поведение:

1. **`PipelineDefinitionEntity` (`pipeline_definition`):**
   - Первичный ключ `id` (slug-строка, например `legacy-1.1-preset`).
   - Поле `packageWhitelist` хранит сериализованный JSON-массив пакетов (например, `["com.apb.mobile"]`) или `[]` (все пакеты).
   - Поле `activeRevisionId` ссылается на текущую рабочую ревизию конвейера. Допускает `NULL` (мягкая связь без циклического FK) для создания конвейера до вставки первой ревизии.
   - Составной индекс `(enabled, priority)` ускоряет выборку активных конвейеров для рантайма.

2. **`PipelineRevisionEntity` (`pipeline_revision`):**
   - Иммутабельный снимок конфигурации конвейера.
   - Внешний ключ `pipeline_id` ссылается на `pipeline_definition(id)` с правилом `ON DELETE CASCADE`: удаление конвейера каскадно удаляет всю историю его ревизий.
   - Уникальный составной индекс `(pipeline_id, revision_number)` исключает появление дублирующихся номеров ревизий в рамках одного конвейера.
   - Поле `canonicalSha256` (64 символа hex) используется для быстрого сравнения и кеширования скомпилированных графов в памяти.

3. **`RuntimeAlertEntity` (`runtime_alert`):**
   - Журнал инцидентов, ошибок узлов и срабатываний Circuit Breaker в рантайме.
   - Поле `isDismissed` позволяет пользователю квитировать алерты в UI панели здоровья конвейера.
   - Индекс `(is_dismissed, timestamp)` ускоряет выборку активных неквитированных предупреждений.

4. **`PipelineWithRevision`:**
   - Модель связки Room через `@Relation` для атомарной загрузки метаданных конвейера и тела активной ревизии одним запросом без ручных JOIN.

---

### Ошибки:

- Попытка вставки ревизии с несуществующим `pipeline_id` вызывает `SQLiteConstraintException` (нарушение Foreign Key).
- Попытка вставки дублирующегося номера ревизии для одного `pipeline_id` вызывает `SQLiteConstraintException` (нарушение уникального индекса).
- Несовпадение схемы и DDL в `MIGRATION_2_3.kt` приводит к фатальной ошибке Room KSP генератора / `IllegalStateException: Room cannot verify the data integrity`.

---

### Граничные случаи:

- `description`, `commitMessage`, `nodeId`, `payloadJson` могут быть `null`.
- `packageWhitelist` может быть пустой строкой `[]`.
- `definitionJson` может содержать большой JSON (до 500 КБ для конвейеров из 50 узлов) — тип SQLite `TEXT` поддерживает строки до 1 ГБ.
- `activeRevisionId == null` означает конвейер без опубликованных ревизий (черновик).

---

### Запрещено:

- Использовать изменяемые (`var`) свойства сущностей — все поля строго `val`.
- Использовать аннотации или типы UI (`androidx.compose.*`, Android Views).
- Менять имена таблиц, колонок или дефолтные значения (`defaultValue`), зафиксированные в контракте.
- Добавлять циклический внешний ключ Room с `ForeignKey` на уровне `@Entity` между `definition` и `revision` (разрешено только `revision -> definition` с `CASCADE`).

---

### Критерий приёмки:

- Все 4 файла скомпилированы в модуле `:core:storage`.
- KSP компилятор Room успешно генерирует `PipelineDefinitionEntityDao_Impl` и метаданные схемы `3.json`.
- Unit-тесты проверяют создание экземпляров сущностей со всеми значениями по умолчанию.
