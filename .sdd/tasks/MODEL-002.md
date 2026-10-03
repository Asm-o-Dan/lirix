## Задача MODEL-002: реализовать доменные классы RawEvent, Event, SourceHealth

**Файл:**
- `core/model/src/main/kotlin/com/example/npc/core/model/RawEvent.kt`
- `core/model/src/main/kotlin/com/example/npc/core/model/Event.kt`
- `core/model/src/main/kotlin/com/example/npc/core/model/SourceHealth.kt`
(создать)

**Спека:** `.sdd/specs/core-model/overview.md#типы-данных` (v1)

**Зависит от:** `MODEL-001` (value-классы)

**Сигнатуры (НЕ МЕНЯТЬ):**

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
        require(lastError == null || (lastError.isNotBlank() && lastError.length <= 1000)) { "lastError length must be <= 1000" }
    }
}
```

**Ошибки:**
- При нарушении `init`-инвариантов выбрасывать `IllegalArgumentException`.

**Запрещено:**
- Добавлять поле `embeddingRef` в `Event` (согласно ADR-006).
- Использовать Room-аннотации.
- Использовать Android SDK (`android.*`).

**Критерий приёмки:**
- Проходят юнит-тесты `core/model/src/test/kotlin/com/example/npc/core/model/DomainEntitiesTest.kt`.
