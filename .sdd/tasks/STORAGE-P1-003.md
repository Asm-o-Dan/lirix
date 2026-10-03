## Задача STORAGE-P1-003: Обновить сущность EventEntity для схемы Room v2

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/entity/EventEntity.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#41-evententity-v2`  
**Архитектура:** `.sdd/architecture_phase1.md#54`  

### Входные контракты / сигнатуры:
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

    // Новые поля Фазы 1 со строгими defaultValue
    @ColumnInfo(name = "category", defaultValue = "'UNCLASSIFIED'")
    val category: String = "UNCLASSIFIED",

    @ColumnInfo(name = "confidence", defaultValue = "0.0")
    val confidence: Double = 0.0,

    @ColumnInfo(name = "engine_used", defaultValue = "'NONE'")
    val engineUsed: String = "NONE",

    @ColumnInfo(name = "is_user_corrected", defaultValue = "0")
    val isUserCorrected: Boolean = false,

    @ColumnInfo(name = "content_fingerprint", defaultValue = "NULL")
    val contentFingerprint: String? = null
)
```

### Инварианты и алгоритм:
1. Значения `defaultValue` в `@ColumnInfo` обязаны посимвольно совпадать с DDL в `MIGRATION_1_2.kt`.
2. Индексы на `category` и `content_fingerprint` ускоряют фильтрацию ленты Timeline 2.0.

### Критерии приемки (DoD):
- [ ] Room schema v2 генерируется KSP компилятором без предупреждений о расхождении схемы.
- [ ] Сущность компилируется и проходит Schema Validation в Unit-тестах Room.
