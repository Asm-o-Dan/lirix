## Задача NOTIF-003: Фильтрация, склейка обновлений и PipelineNotificationListenerService

**Файлы:**
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/filter/NotificationFilter.kt`
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/tracker/NotificationUpdateTracker.kt`
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/service/PipelineNotificationListenerService.kt`
(создать)

**Спека:**
- `.sdd/specs/ingest-notification/overview.md#класс-notificationfilter` (v1)
- `.sdd/specs/ingest-notification/overview.md#класс-notificationupdatetracker` (v1)
- `.sdd/specs/ingest-notification/overview.md#класс-pipelinenotificationlistenerservice` (v1)  
**Зависит от:** `NOTIF-002`, `STORAGE-ALL`, `MODEL-ALL`  

**Поведение:**
1. `NotificationFilter`:
   - `evaluate(payload, hostPackageName)`: проверяет `isOwnPackage`, `isOngoing`, `isForegroundService`, `hasProgressBar`, `isGroupSummary`, `hasTextContent`.
   - Возвращает `FilterDecision`.
2. `NotificationUpdateTracker`:
   - `computeThreadKey(packageName, id, tag, extras)`: детерминированное вычисление ключа цепочки/диалога.
   - `resolvePreviousEventId(threadKey, notificationKey)`: LRU-кэш связей и поиск предыдущего события.
   - `recordEventMapping(threadKey, notificationKey, eventId)`: фиксация маппинга.
   - `evictNotification(notificationKey)`: удаление ключа из кэша.
3. `PipelineNotificationListenerService`:
   - `onNotificationPosted`: < 1.5 мс на binder-потоке, `seqGenerator.incrementAndGet()`, отправка в `Channel<NotificationRawPayload>(UNLIMITED)`.
   - `onNotificationRemoved`: вызывает `updateTracker.evictNotification`.
   - `onListenerConnected` / `onListenerDisconnected`: обновление статуса в `source_health` и DataStore.
   - `consumePayloads` (один консьюмер в корутине IO):
     - **Шаг 1:** немедленная вставка `RawEvent` в БД (`insertRawEvent`) до любой нормализации и фильтрации (ADR-004).
     - **Шаг 2:** фильтрация мусора через `NotificationFilter`.
     - **Шаг 3:** трекинг обновлений через `NotificationUpdateTracker`.
     - **Шаг 4:** нормализация через `EventNormalizer.normalize` и вставка в `StorageGateway.insertEvent`.
     - **Шаг 5:** обновление `source_health` (включая `queueDepth`).

**Критерий приёмки:**
- Консьюмер немедленно персистит `RawEvent` до фильтрации.
- Ongoing, progress-bar и group-summary уведомления отсекаются от сохранения в `event`.
- Склейка обновлений связывает последующие события с предыдущими через `isUpdateOf`.
