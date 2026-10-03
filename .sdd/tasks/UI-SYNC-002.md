## Задача UI-SYNC-002: Методы получения RawEvent по ID в StorageGateway

**Модули:** `:core:model`, `:core:storage`  
**Целевые файлы:**  
- `core/model/src/main/kotlin/com/example/npc/core/storage/StorageGateway.kt`
- `core/storage/src/main/kotlin/com/example/npc/core/storage/StorageGatewayImpl.kt`  

### Входные контракты / сигнатуры:

В `StorageGateway.kt`:
```kotlin
    suspend fun getRawEvent(id: Long): RawEvent?
    suspend fun getRawEventByEventId(eventId: Long): RawEvent?
```

В `StorageGatewayImpl.kt`:
```kotlin
    override suspend fun getRawEvent(id: Long): RawEvent? = withContext(ioDispatcher) {
        rawEventDao.getById(id)?.let { RawEventMapper.toDomain(it) }
    }

    override suspend fun getRawEventByEventId(eventId: Long): RawEvent? = withContext(ioDispatcher) {
        val eventEntity = eventDao.getById(eventId) ?: return@withContext null
        rawEventDao.getById(eventEntity.rawId)?.let { RawEventMapper.toDomain(it) }
    }
```

### Инварианты:
1. Вызовы выполняются в контексте `ioDispatcher`.
2. Если событие или связанный сырой объект не найден, возвращается `null` без выброса исключений.

### Критерии приёмки (DoD):
- [x] Методы объявлены в `StorageGateway` и реализованы в `StorageGatewayImpl`.
- [x] Unit-тесты в `StorageGatewayImplTest` покрывают получение сырого события.
- [x] Сборка и тесты `:core:storage:test` успешны.
