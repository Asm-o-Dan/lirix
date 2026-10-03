## Задача STORAGE-P1-005: Создать сущность UserPrototypeEntity

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/entity/UserPrototypeEntity.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#43-userprototypeentity`  
**Архитектура:** `.sdd/architecture_phase1.md#54`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.storage.entity

import androidx.room.*

@Entity(
    tableName = "user_prototype",
    indices = [
        Index(value = ["package_name", "fingerprint"], unique = true)
    ]
)
data class UserPrototypeEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "package_name")
    val packageName: String,

    @ColumnInfo(name = "fingerprint")
    val fingerprint: String,

    @ColumnInfo(name = "category")
    val category: String,

    @ColumnInfo(name = "support_count", defaultValue = "1")
    val supportCount: Int = 1,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "last_seen_at")
    val lastSeenAt: Long
)
```

### Инварианты и алгоритм:
1. Композитный уникальный индекс `(package_name, fingerprint)` гарантирует отсутствие дубликатов прототипов для одного шаблона контента.
2. `supportCount` имеет `defaultValue = "1"`.

### Критерии приемки (DoD):
- [ ] Сущность скомпилирована в Room KSP.
- [ ] Соответствует DDL в `MIGRATION_1_2`.
