## Задача STORAGE-P1-006: Создать интерфейс FinancialTransactionDao

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/dao/FinancialTransactionDao.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#51-financialtransactiondao`  
**Архитектура:** `.sdd/architecture_phase1.md#4`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.storage.dao

import androidx.room.*
import com.example.npc.core.storage.entity.FinancialTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface FinancialTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: FinancialTransactionEntity): Long

    @Query("SELECT * FROM financial_transaction ORDER BY occurred_at DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<FinancialTransactionEntity>>

    @Query("SELECT * FROM financial_transaction WHERE event_id = :eventId LIMIT 1")
    suspend fun findByEventId(eventId: Long): FinancialTransactionEntity?

    @Query("SELECT * FROM financial_transaction WHERE occurred_at BETWEEN :fromMs AND :toMs ORDER BY occurred_at ASC")
    suspend fun findBetween(fromMs: Long, toMs: Long): List<FinancialTransactionEntity>

    @Query("DELETE FROM financial_transaction")
    suspend fun deleteAll()
}
```

### Инварианты и алгоритм:
1. `internal` видимость DAO внутри модуля `:core:storage`.
2. Реактивный поток `observeRecent` эмитит свежий список при любой мутации таблицы.

### Критерии приемки (DoD):
- [ ] DAO успешно скомпилирован Room KSP компилятором.
- [ ] Тесты покрывают `insert`, `observeRecent`, `findByEventId`, `findBetween` и `deleteAll`.
