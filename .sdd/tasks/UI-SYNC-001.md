## Задача UI-SYNC-001: Синхронизация модели данных Event и маппера EventMapper

**Модули:** `:core:model`, `:core:storage`  
**Целевые файлы:**  
- `core/model/src/main/kotlin/com/example/npc/core/model/Event.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/mapper/EventMapper.kt`  

### Входные контракты / сигнатуры:

В `Event.kt`:
```kotlin
package com.example.npc.core.model

import java.time.Instant
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine

data class Event(
    val id: Long = 0L,
    val rawId: Long,
    val ts: Instant,
    val title: String,
    val text: String,
    val normalizedText: String,
    val lang: Lang,
    val threadKey: ThreadKey? = null,
    val isUpdateOf: Long? = null,
    val category: Category = Category.UNCLASSIFIED,
    val confidence: Float = 0.0f,
    val engineUsed: Engine = Engine.NONE,
    val isUserCorrected: Boolean = false,
    val contentFingerprint: String? = null,
    val pipelineRevisionId: Long? = null
) {
    init {
        require(id >= 0L) { "id must be >= 0" }
        require(rawId >= 0L) { "rawId must be >= 0" }
        require(isUpdateOf == null || isUpdateOf > 0L) { "isUpdateOf must be > 0 if specified" }
        require(confidence in 0.0f..1.0f) { "confidence must be between 0.0 and 1.0" }
    }
}
```

В `EventMapper.kt`:
```kotlin
    fun toDomain(entity: EventEntity): Event = Event(
        id = entity.id,
        rawId = entity.rawId,
        ts = Instant.ofEpochMilli(entity.ts),
        title = entity.title,
        text = entity.text,
        normalizedText = entity.normalizedText,
        lang = runCatching { Lang.valueOf(entity.lang) }.getOrDefault(Lang.UNK),
        threadKey = entity.threadKey?.let { ThreadKey(it) },
        isUpdateOf = entity.isUpdateOf,
        category = runCatching { Category.valueOf(entity.category) }.getOrDefault(Category.UNCLASSIFIED),
        confidence = entity.confidence.toFloat(),
        engineUsed = runCatching { Engine.valueOf(entity.engineUsed) }.getOrDefault(Engine.NONE),
        isUserCorrected = entity.isUserCorrected,
        contentFingerprint = entity.contentFingerprint,
        pipelineRevisionId = entity.pipelineRevisionId
    )

    fun toEntity(
        domain: Event,
        category: String = domain.category.name,
        confidence: Double = domain.confidence.toDouble(),
        engineUsed: String = domain.engineUsed.name,
        isUserCorrected: Boolean = domain.isUserCorrected,
        contentFingerprint: String? = domain.contentFingerprint,
        pipelineRevisionId: Long? = domain.pipelineRevisionId
    ): EventEntity = EventEntity(
        id = domain.id,
        rawId = domain.rawId,
        ts = domain.ts.toEpochMilli(),
        title = domain.title,
        text = domain.text,
        normalizedText = domain.normalizedText,
        lang = domain.lang.name,
        threadKey = domain.threadKey?.value,
        isUpdateOf = domain.isUpdateOf,
        category = category,
        confidence = confidence,
        engineUsed = engineUsed,
        isUserCorrected = isUserCorrected,
        contentFingerprint = contentFingerprint,
        pipelineRevisionId = pipelineRevisionId
    )
```

### Инварианты:
1. Обратная совместимость (zero breaking changes): все новые поля имеют дефолтные значения.
2. Безопасный парсинг перечислений `Category` и `Engine` через `runCatching { ... }.getOrDefault(...)`.
3. Сохранение и сквозная передача `pipelineRevisionId`.

### Критерии приёмки (DoD):
- [x] Все тесты `:core:model:test` и `:core:storage:test` проходят без ошибок (0 failures, 0 regressions).
