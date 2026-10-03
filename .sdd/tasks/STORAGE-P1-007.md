## Задача STORAGE-P1-007: Создать интерфейс UserPrototypeDao

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/dao/UserPrototypeDao.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#52-userprototypedao`  
**Архитектура:** `.sdd/architecture_phase1.md#4`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.storage.dao

import androidx.room.*
import com.example.npc.core.storage.entity.UserPrototypeEntity

@Dao
internal interface UserPrototypeDao {

    @Query("SELECT * FROM user_prototype WHERE package_name = :packageName AND fingerprint = :fingerprint LIMIT 1")
    suspend fun findByPackageAndFingerprint(packageName: String, fingerprint: String): UserPrototypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prototype: UserPrototypeEntity): Long

    @Query("UPDATE user_prototype SET support_count = support_count + 1, last_seen_at = :lastSeenAt WHERE id = :id")
    suspend fun incrementSupportCount(id: Long, lastSeenAt: Long)

    @Query("SELECT * FROM user_prototype ORDER BY support_count DESC, last_seen_at DESC")
    suspend fun getAllPrototypes(): List<UserPrototypeEntity>

    @Query("DELETE FROM user_prototype")
    suspend fun deleteAll()
}
```

### Инварианты и алгоритм:
1. `findByPackageAndFingerprint`: точечный поиск по индексу за $O(1)$.
2. `incrementSupportCount`: атомарный инкремент подтверждения пользователем без перезаписи остальных полей.
3. `internal` интерфейс.

### Критерии приемки (DoD):
- [ ] DAO успешно скомпилирован Room KSP компилятором.
- [ ] Тесты покрывают поиск, upsert, инкремент счетчика и очистку.
