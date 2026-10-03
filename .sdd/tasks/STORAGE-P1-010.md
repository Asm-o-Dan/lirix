## Задача STORAGE-P1-010: Реализовать UserPrototypeMapper

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/mapper/UserPrototypeMapper.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#62-userprototypemapper`  
**Архитектура:** `.sdd/architecture_phase1.md#4`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.storage.mapper

import com.example.npc.core.model.classify.UserPrototype
import com.example.npc.core.storage.entity.UserPrototypeEntity

internal object UserPrototypeMapper {
    fun toEntity(domain: UserPrototype): UserPrototypeEntity
    fun toDomain(entity: UserPrototypeEntity): UserPrototype
}
```

### Инварианты и алгоритм:
1. Маппинг `Category` через `Category.fromStringOrUnclassified`.
2. Маппинг `Instant` $\leftrightarrow$ epoch millis.
3. Сохранение `supportCount`, `packageName`, `fingerprint`.

### Критерии приемки (DoD):
- [ ] Двусторонний маппинг `toDomain(toEntity(model)) == model`.
- [ ] Тесты покрывают граничные значения supportCount и категории.
