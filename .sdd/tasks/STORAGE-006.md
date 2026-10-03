## Задача STORAGE-006: реализовать AppDatabase, StorageGateway и StorageGatewayImpl

**Файлы:**
- `core/storage/src/main/kotlin/com/example/npc/core/storage/AppDatabase.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/StorageGateway.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/StorageGatewayImpl.kt`
(создать)

**Спека:**
- `.sdd/specs/core-storage/overview.md#абстракция-базы-данных-room-appdatabase` (v1)
- `.sdd/specs/core-storage/overview.md#контракт-storagegateway` (v1)
- `.sdd/specs/core-storage/overview.md#реализация-storagegatewayimpl` (v1)  
**Зависит от:** `STORAGE-003`, `STORAGE-004`, `STORAGE-005`  

**Сигнатуры и поведение:**

### 1. `AppDatabase.kt`
```kotlin
package com.example.npc.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.npc.core.storage.dao.EventDao
import com.example.npc.core.storage.dao.RawEventDao
import com.example.npc.core.storage.dao.SourceHealthDao
import com.example.npc.core.storage.entity.EventEntity
import com.example.npc.core.storage.entity.RawEventEntity
import com.example.npc.core.storage.entity.SourceHealthEntity

@Database(
    entities = [
        RawEventEntity::class,
        EventEntity::class,
        SourceHealthEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawEventDao(): RawEventDao
    abstract fun eventDao(): EventDao
    abstract fun sourceHealthDao(): SourceHealthDao
}
```

### 2. `StorageGateway.kt`
```kotlin
package com.example.npc.core.storage

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import kotlinx.coroutines.flow.Flow

interface StorageGateway {
    suspend fun insertRawEvent(event: RawEvent): Long
    suspend fun insertEvent(event: Event): Long
    suspend fun upsertSourceHealth(health: SourceHealth)
    suspend fun findDuplicate(key: DeduplicationKey): Long?
    fun observeEvents(limit: Int): Flow<List<Event>>
    fun observeSourceHealth(): Flow<List<SourceHealth>>
    suspend fun exportAllToJson(): String
    suspend fun deleteAllData()
}
```

### 3. `StorageGatewayImpl.kt`
```kotlin
package com.example.npc.core.storage

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.storage.dao.EventDao
import com.example.npc.core.storage.dao.RawEventDao
import com.example.npc.core.storage.dao.SourceHealthDao
import com.example.npc.core.storage.mapper.EventMapper
import com.example.npc.core.storage.mapper.RawEventMapper
import com.example.npc.core.storage.mapper.SourceHealthMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

class StorageGatewayImpl(
    private val database: AppDatabase,
    private val rawEventDao: RawEventDao,
    private val eventDao: EventDao,
    private val sourceHealthDao: SourceHealthDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : StorageGateway {
    // Реализация методов по спеке:
    // 1. insertRawEvent: перехват SQLiteConstraintException -> findIdByHash -> existingId ?: -1L
    // 2. insertEvent: EventMapper.toEntity -> eventDao.insert
    // 3. upsertSourceHealth: SourceHealthMapper.toEntity -> sourceHealthDao.upsert
    // 4. findDuplicate: rawEventDao.findIdByHash
    // 5. observeEvents: limit <= 0 -> throw IllegalArgumentException. eventDao.observeLatest(limit).map { ... }.flowOn(ioDispatcher)
    // 6. observeSourceHealth: sourceHealthDao.observeAll().map { ... }.flowOn(ioDispatcher)
    // 7. exportAllToJson: database.withTransaction { read all tables } -> валидный JSON
    // 8. deleteAllData: database.withTransaction { eventDao.deleteAll(); rawEventDao.deleteAll(); sourceHealthDao.deleteAll() }
}
```

**Критерий приёмки:**
- Все методы `StorageGateway` выполняются в `ioDispatcher`.
- Повторный `insertRawEvent` с тем же hash перехватывает ошибку уникальности и возвращает существующий `id`.
- `observeEvents` с `limit <= 0` выбрасывает `IllegalArgumentException`.
- `exportAllToJson` генерирует детерминированный JSON.
- `deleteAllData` удаляет все строки из трёх таблиц.
