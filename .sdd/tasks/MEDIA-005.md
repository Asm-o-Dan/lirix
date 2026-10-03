## Задача MEDIA-005: MediaControllerHolder и MediaSessionObserver

**Файлы:**
- `ingest/media/src/main/kotlin/com/example/npc/ingest/media/observer/MediaControllerHolder.kt`
- `ingest/media/src/main/kotlin/com/example/npc/ingest/media/observer/MediaSessionObserver.kt`
(создать)

**Спека:**
- `.sdd/specs/ingest-media/overview.md#компоненты-наблюдения-mediasessionobserver-и-mediacontrollerholder` (v1)

**Зависит от:** `MEDIA-004`

**Поведение:**
1. `MediaControllerHolder`:
   - Хранит активные `MediaControllerCompat` и зарегистрированные колбэки `MediaControllerCompat.Callback`.
   - Методы: `register(token)`, `unregister(token)`, `clear()`.
   - Потокобезопасная коллекция (ConcurrentHashMap).
2. `MediaSessionObserver`:
   - `MediaSessionManager.OnActiveSessionsChangedListener`:
     - Вызывается при изменении списка активных сессий ОС.
     - Сопоставляет новые и удалённые токены с `MediaControllerHolder`.
   - Получает обновления метаданных (`onMetadataChanged`) и состояния воспроизведения (`onPlaybackStateChanged`).
   - Передаёт снимки в `MediaSessionBoundaryDetector`.
   - При принятии решения о закрытии сессии -> формирует `RawEvent` и `Event` и передаёт в `StorageGateway` (`insertRawEvent`, `insertEvent`, `upsertSourceHealth`).

**Критерий приёмки:**
- Корректная регистрация и снятие слушателей без утечек памяти.
- Обработка `SecurityException` при отсутствии разрешения доступа к уведомлениям.
