## Задача QA-NOTIF: Тест-дизайн и реализация тестов для :ingest:notification

**Файлы тестов:**
- `ingest/notification/src/test/kotlin/com/example/npc/ingest/notification/mapper/NotificationMapperTest.kt`
- `ingest/notification/src/test/kotlin/com/example/npc/ingest/notification/filter/NotificationFilterTest.kt`
- `ingest/notification/src/test/kotlin/com/example/npc/ingest/notification/tracker/NotificationUpdateTrackerTest.kt`
- `ingest/notification/src/test/kotlin/com/example/npc/ingest/notification/watchdog/NotificationWatchdogTest.kt`
(создать)

**Спека:** `.sdd/specs/ingest-notification/overview.md` (v1)  
**Зависит от:** `NOTIF-001`  

**Требования к тестам:**
1. **NotificationFilterTest:**
   - Отклоняет уведомление с `FLAG_ONGOING_EVENT` (`ONGOING_EVENT`).
   - Отклоняет уведомление с `FLAG_FOREGROUND_SERVICE` (`FOREGROUND_SERVICE`).
   - Отклоняет уведомление с активным прогресс-баром (`PROGRESS_BAR`).
   - Отклоняет карточку сводки группы `FLAG_GROUP_SUMMARY` (`GROUP_SUMMARY`).
   - Отклоняет собственное уведомление приложения (`OWN_PACKAGE`).
   - Отклоняет уведомление без текста и заголовка (`EMPTY_CONTENT`).
   - Принимает обычное уведомление мессенджера/банка (`isAccepted = true, rejectionReason = null`).

2. **NotificationUpdateTrackerTest:**
   - Вычисляет `ThreadKey` по заголовку беседы (`MessagingStyle`).
   - Вычисляет `ThreadKey` по имени пакета и id для обычных уведомлений.
   - Корректно связывает второе уведомление с первым через `resolvePreviousEventId`.
   - Удаляет ключ из оперативного кэша при вызове `evictNotification`.

3. **NotificationWatchdogTest:**
   - `checkHeartbeat`: возвращает `true`, если прошло меньше 30 минут.
   - `checkHeartbeat`: возвращает `false`, если прошло больше 30 минут.
   - `checkHeartbeat`: возвращает `true` при отрицательной дельте (перевод часов назад).

4. **NotificationMapperTest:**
   - `toPayloadJson`: сериализует все поля в валидный JSON с корректным экранированием кавычек и переносов строк.

**Критерий GATE 5:**
- Тесты созданы до реализации функционала.
- Запуск тестов падает с ожидаемой ошибкой компиляции (RED).
