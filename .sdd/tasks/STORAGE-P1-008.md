## Задача STORAGE-P1-008: Обновить EventDao для поддержки классификации Фазы 1

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/dao/EventDao.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#53-обновление-eventdao`  
**Архитектура:** `.sdd/architecture_phase1.md#4`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.storage.dao

import androidx.room.*
import com.example.npc.core.storage.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface EventDao {
    // Сохранение Фазы 0
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Query("SELECT * FROM event ORDER BY ts DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM event WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): EventEntity?

    @Query("DELETE FROM event")
    suspend fun deleteAll()

    // Новые методы Фазы 1
    @Query("""
        UPDATE event 
        SET category = :category, 
            is_user_corrected = 1, 
            engine_used = 'USER' 
        WHERE id = :eventId
    """)
    suspend fun recordUserCorrection(eventId: Long, category: String)

    @Query("SELECT * FROM event WHERE category = :category ORDER BY ts DESC LIMIT :limit")
    fun observeByCategory(category: String, limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM event WHERE content_fingerprint = :fingerprint ORDER BY ts DESC")
    suspend fun findByFingerprint(fingerprint: String): List<EventEntity>
}
```

### Инварианты и алгоритм:
1. `recordUserCorrection`: атомарно выставляет флаг `is_user_corrected = 1` и `engine_used = 'USER'`.
2. Обратная совместимость с запросами Фазы 0 сохранена.

### Критерии приемки (DoD):
- [ ] Существующие 24 теста `core-storage` остаются 100% зелёными.
- [ ] Новые методы покрыты тестами Room.
