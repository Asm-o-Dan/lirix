# TASK-FLT-01: Системный сервис FloatingLyricsService на SYSTEM_ALERT_WINDOW

**Файл:** `app/src/main/java/com/eventengine/app/service/FloatingLyricsService.kt`
**Зона:** `media-ui` / `service`
**Спецификация:** `.sdd/intake/TRACK_C_FLOATING_OVERLAY.md`

## 1. Цель
Реализовать Android Service (`FloatingLyricsService`) для отображения системного плавающего оверлея поверх любых приложений через `WindowManager` и разрешение `SYSTEM_ALERT_WINDOW`.

## 2. Требования
1. Foreground Service с типом `mediaPlayback` и тихим уведомлением в шторке ("Lirix Плавающий оверлей").
2. Реактивная подписка на `MediaSessionCollector.livePlaybackFlow` и синхронизация с текстами песен из `AppDatabase.lyricsDao()`.
3. Периодический расчет текущей строки караоке с учетом `SyncOffsetStore`.
4. Методы управления жизненным циклом: `start(context)`, `stop(context)`, `toggle(context)`, `isPermissionGranted(context)`.
5. Потокобезопасный `StateFlow<Boolean>` состояния активности `isRunning`.
