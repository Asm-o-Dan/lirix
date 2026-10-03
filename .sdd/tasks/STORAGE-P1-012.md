## Задача STORAGE-P1-012: Обновить AppDatabase до схемы v2

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/AppDatabase.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#7-appdatabase-v2`  
**Архитектура:** `.sdd/architecture_phase1.md#5`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.npc.core.storage.dao.*
import com.example.npc.core.storage.entity.*

@Database(
    entities = [
        RawEventEntity::class,
        EventEntity::class,
        FinancialTransactionEntity::class,
        UserPrototypeEntity::class,
        SourceHealthEntity::class
    ],
    version = 2,
    exportSchema = true
)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun rawEventDao(): RawEventDao
    abstract fun eventDao(): EventDao
    abstract fun financialTransactionDao(): FinancialTransactionDao
    abstract fun userPrototypeDao(): UserPrototypeDao
    abstract fun sourceHealthDao(): SourceHealthDao
}
```

### Инварианты и алгоритм:
1. `version = 2`.
2. Регистрация всех 5 сущностей: `RawEventEntity`, `EventEntity`, `FinancialTransactionEntity`, `UserPrototypeEntity`, `SourceHealthEntity`.
3. Регистрация абстрактных геттеров для 5 DAO.
4. Экспорт схемы `exportSchema = true` для KSP валидации и автогенерации схем.

### Критерии приемки (DoD):
- [ ] База компилируется KSP без ошибок.
- [ ] Схема v2 экспортируется в `schemas/`.
- [ ] Тесты создания БД в памяти работают успешно.
