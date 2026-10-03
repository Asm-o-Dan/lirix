## Задача STORAGE-002: реализовать Room сущности RawEventEntity, EventEntity, SourceHealthEntity

**Файл:**
- `core/storage/src/main/kotlin/com/example/npc/core/storage/entity/RawEventEntity.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/entity/EventEntity.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/entity/SourceHealthEntity.kt`
(создать)

**Спека:** `.sdd/specs/core-storage/overview.md#сущности-room-room-entities` (v1)  
**Зависит от:** `STORAGE-001`  

**Сигнатуры (НЕ МЕНЯТЬ):**

```kotlin
package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "raw_event",
    indices = [
        Index(value = ["hash"], unique = true),
        Index(value = ["seq"], unique = false)
    ]
)
data class RawEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "seq")
    val seq: Long,

    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "received_at")
    val receivedAt: Long,

    @ColumnInfo(name = "payload_json")
    val payloadJson: String,

    @ColumnInfo(name = "hash")
    val hash: String
)

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
        Index(value = ["is_update_of"], unique = false)
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
    val isUpdateOf: Long?
)

@Entity(tableName = "source_health")
data class SourceHealthEntity(
    @PrimaryKey
    @ColumnInfo(name = "source")
    val source: String,

    @ColumnInfo(name = "last_event_at")
    val lastEventAt: Long?,

    @ColumnInfo(name = "events_24h")
    val events24h: Int,

    @ColumnInfo(name = "last_error")
    val lastError: String?,

    @ColumnInfo(name = "queue_depth")
    val queueDepth: Int
)
```

**Запрещено:**
- Добавлять колонку `embedding_ref` в `EventEntity` (согласно ADR-006).

**Критерий приёмки:**
- Файлы созданы и успешно компилируются KSP.
