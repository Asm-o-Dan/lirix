# Задача PIPE-003: Расширить StorageGateway и EventDao методами claim и complete

**Файлы:**
- `core/storage/src/main/kotlin/com/example/npc/core/storage/dao/EventDao.kt` (изменить)
- `core/storage/src/main/kotlin/com/example/npc/core/storage/StorageGateway.kt` (изменить)
- `core/storage/src/main/kotlin/com/example/npc/core/storage/StorageGatewayImpl.kt` (изменить)
**Спека:** `.sdd/specs/app-pipeline/overview.md#секция-5`

## Сигнатуры:
В `EventDao`:
```kotlin
@Query("UPDATE event SET category = 'PROCESSING' WHERE id = :id AND (category = 'UNCLASSIFIED' OR category = 'PROCESSING')")
suspend fun tryClaim(id: Long): Int

@Query("UPDATE event SET category = :category, confidence = :confidence, engine_used = :engineUsed, content_fingerprint = :fingerprint WHERE id = :id")
suspend fun updateClassification(id: Long, category: String, confidence: Double, engineUsed: String, fingerprint: String): Int

@Query("SELECT id FROM event WHERE category = 'UNCLASSIFIED' OR category = 'PROCESSING' ORDER BY id ASC LIMIT :limit")
suspend fun getPendingUnprocessedIds(limit: Int): List<Long>
```

В `StorageGateway`:
```kotlin
suspend fun tryClaimEvent(eventId: Long): Boolean
suspend fun getEventWithPackage(eventId: Long): Pair<Event, String>? // или EventProcessingTarget
suspend fun completeEventProcessing(eventId: Long, classification: ClassificationResult, transaction: FinancialTransaction?): Boolean
suspend fun getPendingUnprocessedEventIds(limit: Int = 1000): List<Long>
```

В `StorageGatewayImpl`:
Реализовать вышеуказанные методы в потоке `withContext(ioDispatcher)` и с использованием `appDatabase.withTransaction { ... }` для атомарности `completeEventProcessing`.

## Критерий приёмки:
- Все тесты хранилища проходят: `./gradlew.bat :core:storage:test`.
