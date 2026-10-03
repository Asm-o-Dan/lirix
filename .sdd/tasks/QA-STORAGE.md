## Задача QA-STORAGE: Тест-дизайн и реализация тестов для :core:storage

**Файлы тестов:**
- `core/storage/src/test/kotlin/com/example/npc/core/storage/mapper/MappersTest.kt`
- `core/storage/src/test/kotlin/com/example/npc/core/storage/SqlCipherSupportFactoryProviderTest.kt`
- `core/storage/src/test/kotlin/com/example/npc/core/storage/StorageGatewayImplTest.kt`
- `core/storage/src/test/kotlin/com/example/npc/core/storage/dao/RoomDaoTest.kt`
(создать)

**Спека:** `.sdd/specs/core-storage/overview.md` (v1)  
**Зависит от:** `STORAGE-001`  

**Требования к тестам:**

1. **MappersTest:**
   - `RawEventMapper`: `toDomain` и `toEntity` сохраняют все поля (`id`, `seq`, `source`, `packageName`, `receivedAt`, `payloadJson`, `hash`).
   - `EventMapper`: корректно маппит все поля, включая `threadKey` (nullable), `isUpdateOf` (nullable).
   - `EventMapper`: неизвестный `lang` ("UNKNOWN", "FR") преобразуется в `Lang.UNK` без выброса исключений.
   - `SourceHealthMapper`: сохраняет все поля (`source`, `lastEventAt`, `events24h`, `lastError`, `queueDepth`).

2. **SqlCipherSupportFactoryProviderTest:**
   - `createOpenHelperFactory` с размером `passphrase != 32` выбрасывает `IllegalArgumentException`.
   - `wipePassphrase` полностью зануляет переданный массив `ByteArray`.

3. **StorageGatewayImplTest (Unit / Mocks / In-Memory):**
   - `insertRawEvent`: успешная вставка возвращает положительный id.
   - `insertRawEvent`: при `SQLiteConstraintException` перехватывает ошибку и возвращает существующий id через `findIdByHash`.
   - `insertEvent`: вставляет событие и возвращает сгенерированный id.
   - `upsertSourceHealth`: вызывает `sourceHealthDao.upsert`.
   - `findDuplicate`: возвращает id при наличии дубликата и null при его отсутствии.
   - `observeEvents`: выбрасывает `IllegalArgumentException` при `limit <= 0`.
   - `observeEvents`: маппит сущности в доменные объекты `Event`.
   - `observeSourceHealth`: маппит сущности в доменные объекты `SourceHealth`.
   - `exportAllToJson`: возвращает валидный JSON, содержащий поля `version: 1`, `exportedAt`, `rawEvents`, `events`, `sourceHealth`.
   - `deleteAllData`: очищает все три таблицы в единой транзакции.

**Критерий GATE 5:**
- Тесты созданы до реализации функционала.
- Запуск тестов падает с ожидаемой ошибкой компиляции/выполнения (RED).
