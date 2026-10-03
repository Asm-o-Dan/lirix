## Задача STORAGE-P1-013: Расширить контракт StorageGateway для Фазы 1

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/StorageGateway.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#8-storagegateway-v2`  
**Контракты:** `.sdd/contracts/core-storage__extract.md`, `.sdd/contracts/core-storage__ui.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.storage

import com.example.npc.core.model.*
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.UserPrototype
import com.example.npc.core.model.finance.FinancialTransaction
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface StorageGateway {
    // Ingest (Фаза 0)
    suspend fun insertRawEvent(event: RawEvent): Long
    suspend fun upsertSourceHealth(health: SourceHealth)
    suspend fun findDuplicate(key: DeduplicationKey): Long?

    // Финансовые транзакции (Фаза 1)
    suspend fun insertTransaction(transaction: FinancialTransaction): Long
    suspend fun saveProcessedEvent(
        event: Event,
        classification: ClassificationResult,
        transaction: FinancialTransaction?
    ): Long
    fun observeTransactions(limit: Int): Flow<List<FinancialTransaction>>

    // Обучение и обратная связь (Feedback Loop)
    suspend fun recordUserCorrection(eventId: Long, category: Category)
    suspend fun recordUserCorrection(
        eventId: Long,
        packageName: String,
        contentFingerprint: String,
        newCategory: Category,
        correctedAt: Instant = Instant.now()
    )
    suspend fun findMatchingPrototype(packageName: String, fingerprint: String): UserPrototype?

    // Наблюдение и экспорт
    fun observeEvents(limit: Int): Flow<List<Event>>
    fun observeSourceHealth(): Flow<List<SourceHealth>>
    suspend fun exportEventsJson(): String
    suspend fun exportAllToJson(): String = exportEventsJson()
    suspend fun clearAllData()
    suspend fun deleteAllData() = clearAllData()
}
```

### Инварианты и алгоритм:
1. Единый фасад для всех операций хранения в системе.
2. Поддержка реактивных потоков `Flow` для Timeline 2.0.
3. Полная обратная совместимость с вызовами Фазы 0 (`deleteAllData`, `exportAllToJson`).

### Критерии приемки (DoD):
- [ ] Интерфейс компилируется и экспортируется модулем `:core:storage`.
