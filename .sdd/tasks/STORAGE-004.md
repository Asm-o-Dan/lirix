## Задача STORAGE-004: реализовать Entity <-> Domain мапперы

**Файлы:**
- `core/storage/src/main/kotlin/com/example/npc/core/storage/mapper/RawEventMapper.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/mapper/EventMapper.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/mapper/SourceHealthMapper.kt`
(создать)

**Спека:** `.sdd/specs/core-storage/overview.md#мапперы-entity--domain-model` (v1)  
**Зависит от:** `STORAGE-002`, `MODEL-ALL`  

**Сигнатуры и поведение:**

```kotlin
package com.example.npc.core.storage.mapper

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import com.example.npc.core.storage.entity.EventEntity
import com.example.npc.core.storage.entity.RawEventEntity
import com.example.npc.core.storage.entity.SourceHealthEntity
import java.time.Instant

object RawEventMapper {
    fun toDomain(entity: RawEventEntity): RawEvent = RawEvent(
        id = entity.id,
        seq = entity.seq,
        source = SourceId(entity.source),
        packageName = entity.packageName,
        receivedAt = Instant.ofEpochMilli(entity.receivedAt),
        payloadJson = entity.payloadJson,
        hash = DeduplicationKey(entity.hash)
    )

    fun toEntity(domain: RawEvent): RawEventEntity = RawEventEntity(
        id = domain.id,
        seq = domain.seq,
        source = domain.source.value,
        packageName = domain.packageName,
        receivedAt = domain.receivedAt.toEpochMilli(),
        payloadJson = domain.payloadJson,
        hash = domain.hash.value
    )
}

object EventMapper {
    fun toDomain(entity: EventEntity): Event = Event(
        id = entity.id,
        rawId = entity.rawId,
        ts = Instant.ofEpochMilli(entity.ts),
        title = entity.title,
        text = entity.text,
        normalizedText = entity.normalizedText,
        lang = runCatching { Lang.valueOf(entity.lang) }.getOrDefault(Lang.UNK),
        threadKey = entity.threadKey?.let { ThreadKey(it) },
        isUpdateOf = entity.isUpdateOf
    )

    fun toEntity(domain: Event): EventEntity = EventEntity(
        id = domain.id,
        rawId = domain.rawId,
        ts = domain.ts.toEpochMilli(),
        title = domain.title,
        text = domain.text,
        normalizedText = domain.normalizedText,
        lang = domain.lang.name,
        threadKey = domain.threadKey?.value,
        isUpdateOf = domain.isUpdateOf
    )
}

object SourceHealthMapper {
    fun toDomain(entity: SourceHealthEntity): SourceHealth = SourceHealth(
        source = SourceId(entity.source),
        lastEventAt = entity.lastEventAt?.let { Instant.ofEpochMilli(it) },
        events24h = entity.events24h,
        lastError = entity.lastError,
        queueDepth = entity.queueDepth
    )

    fun toEntity(domain: SourceHealth): SourceHealthEntity = SourceHealthEntity(
        source = domain.source.value,
        lastEventAt = domain.lastEventAt?.toEpochMilli(),
        events24h = domain.events24h,
        lastError = domain.lastError,
        queueDepth = domain.queueDepth
    )
}
```

**Критерий приёмки:**
- Мапперы корректно трансформируют сущности в обе стороны без потери данных.
- Fallback для неизвестного значения `lang` возвращает `Lang.UNK`.
