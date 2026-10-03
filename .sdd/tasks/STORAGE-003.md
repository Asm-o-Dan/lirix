## Задача STORAGE-003: реализовать Room DAO интерфейсы RawEventDao, EventDao, SourceHealthDao

**Файл:**
- `core/storage/src/main/kotlin/com/example/npc/core/storage/dao/RawEventDao.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/dao/EventDao.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/dao/SourceHealthDao.kt`
(создать)

**Спека:** `.sdd/specs/core-storage/overview.md#интерфейсы-доступа-к-данным-dao` (v1)  
**Зависит от:** `STORAGE-002` (сущности)  

**Сигнатуры (НЕ МЕНЯТЬ):**

```kotlin
package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.npc.core.storage.entity.EventEntity
import com.example.npc.core.storage.entity.RawEventEntity
import com.example.npc.core.storage.entity.SourceHealthEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RawEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: RawEventEntity): Long

    @Query("SELECT * FROM raw_event WHERE hash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): RawEventEntity?

    @Query("SELECT id FROM raw_event WHERE hash = :hash LIMIT 1")
    suspend fun findIdByHash(hash: String): Long?

    @Query("SELECT * FROM raw_event WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RawEventEntity?

    @Query("SELECT * FROM raw_event ORDER BY id ASC")
    suspend fun getAll(): List<RawEventEntity>

    @Query("DELETE FROM raw_event")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM raw_event")
    suspend fun count(): Long
}

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: EventEntity): Long

    @Query("SELECT * FROM event WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EventEntity?

    @Query("SELECT * FROM event ORDER BY ts DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM event ORDER BY ts DESC, id DESC")
    suspend fun getAll(): List<EventEntity>

    @Query("SELECT COUNT(*) FROM event WHERE ts >= :sinceEpochMs")
    suspend fun countSince(sinceEpochMs: Long): Int

    @Query("DELETE FROM event")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM event")
    suspend fun count(): Long
}

@Dao
interface SourceHealthDao {
    @Upsert
    suspend fun upsert(entity: SourceHealthEntity)

    @Query("SELECT * FROM source_health ORDER BY source ASC")
    fun observeAll(): Flow<List<SourceHealthEntity>>

    @Query("SELECT * FROM source_health WHERE source = :source LIMIT 1")
    suspend fun getBySource(source: String): SourceHealthEntity?

    @Query("SELECT * FROM source_health ORDER BY source ASC")
    suspend fun getAll(): List<SourceHealthEntity>

    @Query("DELETE FROM source_health")
    suspend fun deleteAll(): Int
}
```

**Критерий приёмки:**
- Файлы созданы и корректно валидируются KSP Room компилятором.
