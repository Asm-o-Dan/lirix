## Задача NOTIF-004: IngestWatchdog, NotificationWatchdogWorker и LiveWatchdogForegroundService

**Файлы:**
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/NotificationContract.kt`
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/watchdog/NotificationWatchdogWorker.kt`
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/watchdog/IngestWatchdog.kt`
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/service/LiveWatchdogForegroundService.kt`
(создать)

**Спека:**
- `.sdd/specs/ingest-notification/overview.md#класс-notificationwatchdogworker` (v1)
- `.sdd/specs/ingest-notification/overview.md#класс-ingestwatchdog` (v1)
- `.sdd/specs/ingest-notification/overview.md#живучесть-на-hyperos-и-фоновые-сервисы` (v1)  
**Зависит от:** `NOTIF-003`, `STORAGE-ALL`  

**Поведение:**
1. `NotificationContract`:
   - `data class NotificationContract(val statusBarNotification: Any, val receivedAt: Instant)`
2. `NotificationWatchdogWorker` (`CoroutineWorker`):
   - Проверяет `checkListenerPermission(context)` через `NotificationManagerCompat.getEnabledListenerPackages`.
   - Если отозвано: показывает уведомление-алерт и фиксирует ошибку в `source_health`.
   - Проверяет `checkHeartbeat`: если превышен интервал молчания (> 30 минут):
     - Шаг 1: `executeRebindStep(context)` (`NotificationListenerService.requestRebind`).
     - Шаг 2 (запасной toggle): `executeComponentToggleStep(context)` (`PackageManager.setComponentEnabledSetting(disable -> enable)`).
3. `IngestWatchdog`:
   - Планирует `PeriodicWorkRequest` на 15 минут в `WorkManager` с `ExistingPeriodicWorkPolicy.KEEP`.
   - Опционально запускает/останавливает `LiveWatchdogForegroundService` при включении режима повышенной живучести (ADR-003).
4. `LiveWatchdogForegroundService`:
   - Фоновый сервис типа `specialUse` (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE = "notification_ingest_watchdog"`).
   - Показывает постоянное системное уведомление минимального приоритета для защиты процесса от убийства на HyperOS.

**Критерий приёмки:**
- `checkHeartbeat` корректно определяет превышение порога в 30 минут.
- Двухэтапный recovery вызывает `requestRebind`, а при необходимости — toggle компонента.
