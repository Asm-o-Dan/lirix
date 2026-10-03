## Задача STORAGE-P1-014: Реализовать методы StorageGatewayImpl для Фазы 1

**Модуль:** `:core:storage`  
**Целевой файл:** `core/storage/src/main/kotlin/com/example/npc/core/storage/StorageGatewayImpl.kt`  
**Спецификация:** `.sdd/specs/core-storage/overview.md#8-storagegateway-v2`  
**Контракты:** `.sdd/contracts/core-storage__extract.md`, `.sdd/contracts/core-storage__ui.md`  

### Входные контракты / сигнатуры:
```kotlin
package com.example.npc.core.storage

import androidx.room.withTransaction
// импорты DTO, DAO, Entities и Mappers

internal class StorageGatewayImpl(
    private val db: AppDatabase
) : StorageGateway {
    // Реализация всех методов StorageGateway
}
```

### Инварианты и алгоритм:
1. `saveProcessedEvent`:
   - Внутри `db.withTransaction`:
     - Сохраняет обогащенное событие через `db.eventDao().insert(eventEntity)`.
     - Если `transaction != null`, связывает `transaction.copy(eventId = savedEventId)` и сохраняет через `financialTransactionDao().insert(txEntity)`.
     - Возвращает `savedEventId`.
2. `recordUserCorrection`:
   - Внутри `db.withTransaction`:
     - Обновляет событие `db.eventDao().recordUserCorrection(eventId, category.name)`.
     - Проверяет существующий прототип `userPrototypeDao().findByPackageAndFingerprint(...)`.
     - Если найден — вызывает `incrementSupportCount(id)`.
     - Если не найден — вставляет новую запись `userPrototypeDao().upsert(UserPrototypeEntity(...))`.
3. `observeTransactions(limit)`:
   - Маппит `db.financialTransactionDao().observeRecent(limit)` через `map { list -> list.map { toDomain(it) } }`.
4. `clearAllData`:
   - Внутри транзакции удаляет данные из 5 таблиц: `raw_event`, `event`, `financial_transaction`, `user_prototype`, сбрасывает `source_health`.

### Критерии приемки (DoD):
- [ ] Все транзакционные операции выполняются в `withTransaction`.
- [ ] 100% покрытие unit- и интеграционными тестами Room.
