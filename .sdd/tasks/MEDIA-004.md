## Задача MEDIA-004: MediaSessionBoundaryDetector (FSM детекции границ сессий)

**Файлы:**
- `ingest/media/src/main/kotlin/com/example/npc/ingest/media/detector/MediaSessionBoundaryDetector.kt`
(создать)

**Спека:**
- `.sdd/specs/ingest-media/overview.md#класс-mediasessionboundarydetector` (v1)

**Зависит от:** `MEDIA-002`, `MEDIA-003`

**Поведение:**
1. Конечный автомат (FSM) отслеживания сессий воспроизведения:
   - При переходе в `STATE_PLAYING`:
     - Если активной сессии нет -> создать новую `ActiveMediaSession` (`SessionBoundaryDecision.StartSession`).
     - Если сессия уже есть и трек не изменился -> возобновить тайминг (`accumulatedPlayDurationMs`).
     - Если трек изменился (`title` или `artist`) -> закрыть предыдущую сессию с `TRACK_CHANGED` и открыть новую.
   - При переходе в `STATE_PAUSED`:
     - Сессия НЕ закрывается; накапливается отыгранное время до момента паузы.
   - При переходе в `STATE_STOPPED` / `STATE_NONE` / уничтожении сессии контроллера:
     - Сессия закрывается с соответствующим `MediaSessionEndReason`.
2. Фильтрация микро-сессий (< 5 секунд):
   - Сессии короче 5 секунд помечаются как скип.
3. Heartbeat-проверка:
   - Метод `checkHeartbeatTimeouts(now: Instant, timeoutMs: Long = 300_000L)` (5 минут) закрывает зависшие сессии при отсутствии событий от плеера.

**Критерий приёмки:**
- Точное соответствие правилам смены состояний PlaybackStateCompat.
- Тесты проверяют сценарии: Play -> Pause -> Play (единая сессия), Play -> Next Track -> Play (две сессии), Play -> Stop (закрытие).
