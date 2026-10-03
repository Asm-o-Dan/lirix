## Задача STORAGE-P1-011: Обновить EventMapper для полей классификации Фазы 1

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/mapper/EventMapper.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#63-обновление-eventmapper`  
**Архитектура:** `.sdd/architecture_phase1.md#4`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.storage.mapper

import com.example.npc.core.model.Event
import com.example.npc.core.storage.entity.EventEntity

internal object EventMapper {
    fun toEntity(domain: Event): EventEntity
    fun toDomain(entity: EventEntity): Event
}
```

### Инварианты и алгоритм:
1. Маппинг всех полей Фазы 0 (`id`, `rawId`, `ts`, `title`, `text`, `normalizedText`, `lang`, `threadKey`, `isUpdateOf`).
2. Сохранение и считывание полей Фазы 1: `category`, `confidence`, `engineUsed`, `isUserCorrected`, `contentFingerprint`.
3. Корректная обработка дефолтных значений при миграции существующих сущностей.

### Критерии приемки (DoD):
- [ ] Существующие тесты `EventMapperTest` не ломаются.
- [ ] Двусторонний маппинг подтвержден новыми тестами.
