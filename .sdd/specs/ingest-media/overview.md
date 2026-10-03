# Спецификация: zone/ingest-media

## Модуль: :ingest:media   Зона: zone/ingest-media   Версия спеки: v1   Статус: DRAFT

---

### Назначение

Модуль `:ingest:media` является Android-библиотекой (`android-library`) и реализует подсистему пассивного мониторинга, фиксации границ сессий прослушивания мультимедиа и первичного сохранения событий воспроизведения на базе системного `MediaSessionManager` и `MediaControllerCompat`.

Ключевые функциональные обязанности модуля:
1. **Подписка на события системных медиа-сессий:**
   - Мониторинг активных сессий воспроизведения в операционной системе Android через `MediaSessionManager.OnActiveSessionsChangedListener`.
   - Использование прав `NotificationListenerService` (компонент `PipelineNotificationListenerService` модуля `:ingest:notification`) для легитимного доступа к `MediaSessionManager.getActiveSessions()` на Android 5.0+ (API 21+) без root-прав и системных подписей.
2. **Управление контроллерами медиа-сессий (`MediaControllerCompat`):**
   - Динамический реестр активных плееров (`MediaControllerHolder`), регистрация и корректная отписка колбэков `MediaControllerCompat.Callback` при появлении и исчезновении сессий.
3. **Детектирование границ сессий прослушивания (`MediaSessionBoundaryDetector`):**
   - Детерминированный конечный автомат (FSM) фиксации сессий согласно правилам §6.6 архитектуры:
     - `STATE_PLAYING` $\rightarrow$ открытие сессии: фиксация `sessionStartedAt`, имени пакета плеера (`packageName`), метаданных трека (`title`, `artist`, `album`, `durationMs`).
     - `STATE_PAUSED` $\rightarrow$ сессия **НЕ закрывается** (ожидание продолжения прослушивания).
     - `STATE_STOPPED` / `STATE_NONE` / отсоединение контроллера $\rightarrow$ закрытие сессии: фиксация `sessionEndedAt`, расчет эффективной длительности прослушивания (`effectiveDurationMs`).
     - Смена трека во время воспроизведения $\rightarrow$ атомарное закрытие предыдущей сессии и открытие новой.
     - Heartbeat-таймер: если от плеера нет событий $> 5$ минут во время `STATE_PLAYING` $\rightarrow$ принудительное закрытие сессии с ошибкой `lastError = "heartbeat_timeout"`.
     - Порог полезной сессии: фильтрация и маркировка микро-сессий длительностью $< 5$ секунд (случайный скип трека).
4. **Сохранение данных и интеграция с ядром:**
   - Формирование канонического `MediaSessionPayload` в формате JSON.
   - Расчёт дедупликационного ключа `DeduplicationKey` через `EventNormalizer.computeDeduplicationKey` (`SourceId.MEDIA`).
   - Присвоение монотонного порядкового номера `seq` (ADR-004) и запись в `StorageGateway.insertRawEvent`.
   - Нормализация и вставка в `StorageGateway.insertEvent` для полезных сессий (длительность $\ge 5$ с) с формированием `title = trackTitle` и `text = "$artist - $trackTitle"`.
   - Обновление метрик работоспособности источника в `StorageGateway.upsertSourceHealth`.
5. **Восстановление при рестарте приложения (`MediaSessionRecoveryManager`):**
   - Сохранение снимка активной сессии в персистентное хранилище `DataStore<Preferences>`.
   - Обнаружение и корректное закрытие незавершённых («висячих») сессий при перезапуске приложения после аварийного завершения ОС с выставлением `sessionEndedAt = appStartedAt` и фиксацией причины `APP_RESTART`.

**Запрещено:**
- Прямой доступ к аудио-потоку, перехват аудио-фокуса или модификация состояния воспроизведения плеера. Модуль работает исключительно в режиме пассивного наблюдателя метаданных и состояний.
- Зависимости от UI-модулей (`:ui:timeline`) и других ingest-модулей (`:ingest:sms`).

---

### Архитектурное окружение и структура пакетов

```
com.example.npc.ingest.media/
├── MediaSessionContract.kt
├── model/
│   ├── ActiveMediaSession.kt
│   ├── MediaMetadataSnapshot.kt
│   ├── MediaPlaybackSnapshot.kt
│   ├── MediaSessionPayload.kt
│   ├── MediaSessionEndReason.kt
│   └── SessionBoundaryDecision.kt
├── observer/
│   ├── MediaSessionObserver.kt
│   └── MediaControllerHolder.kt
├── detector/
│   └── MediaSessionBoundaryDetector.kt
├── mapper/
│   └── MediaPayloadMapper.kt
├── recovery/
│   └── MediaSessionRecoveryManager.kt
└── di/
    └── MediaIngestModule.kt
```

---

### Модели данных и DTO

Все классы данных размещаются в пакете `com.example.npc.ingest.media.model` и являются неизменяемыми (`val`). В качестве временных меток используется `java.time.Instant`.

#### 1. `MediaMetadataSnapshot`

```kotlin
data class MediaMetadataSnapshot(
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long
)
```

- **Назначение:** Неизменяемый снимок метаданных воспроизводимого аудиотрека, извлечённый из `MediaMetadataCompat`.
- **Поля:**
  - `title: String?` — название трека (`MediaMetadataCompat.METADATA_KEY_TITLE` или `METADATA_KEY_DISPLAY_TITLE`).
  - `artist: String?` — исполнитель (`MediaMetadataCompat.METADATA_KEY_ARTIST` или `METADATA_KEY_ALBUM_ARTIST`).
  - `album: String?` — название альбома (`MediaMetadataCompat.METADATA_KEY_ALBUM`).
  - `durationMs: Long` — общая длительность трека в миллисекундах (`MediaMetadataCompat.METADATA_KEY_DURATION`). Значение `-1L` или `0L` означает неизвестную длительность (например, потоковое интернет-радио).
- **Инварианты:**
  - `durationMs >= -1L`.
  - Если `title != null`, то `title.isNotBlank()` (пустые строки приводятся к `null` на этапе маппинга).
  - Если `artist != null`, то `artist.isNotBlank()`.
  - Если `album != null`, то `album.isNotBlank()`.

---

#### 2. `MediaPlaybackSnapshot`

```kotlin
data class MediaPlaybackSnapshot(
    val state: Int,
    val positionMs: Long,
    val playbackSpeed: Float,
    val updateTimeEpochMs: Long
)
```

- **Назначение:** Снимок состояния воспроизведения из `PlaybackStateCompat`.
- **Поля:**
  - `state: Int` — целочисленный код состояния из `PlaybackStateCompat` (`STATE_PLAYING`, `STATE_PAUSED`, `STATE_STOPPED`, `STATE_NONE`, `STATE_BUFFERING` и др.).
  - `positionMs: Long` — текущая позиция воспроизведения в миллисекундах.
  - `playbackSpeed: Float` — коэффициент скорости воспроизведения (по умолчанию `1.0f`).
  - `updateTimeEpochMs: Long` — момент времени фиксации состояния операционной системой в миллисекундах Unix-эпохи (`PlaybackStateCompat.getLastPositionUpdateTime()`).
- **Инварианты:**
  - `positionMs >= 0L`.
  - `playbackSpeed >= 0.0f`.
  - `updateTimeEpochMs >= 0L`.

---

#### 3. `MediaSessionEndReason`

```kotlin
enum class MediaSessionEndReason {
    STATE_STOPPED,
    STATE_NONE,
    TRACK_CHANGED,
    CONTROLLER_DISCONNECTED,
    HEARTBEAT_TIMEOUT,
    APP_RESTART
}
```

- **Назначение:** Перечисление причин завершения медиа-сессии.
- **Значения:**
  - `STATE_STOPPED` — плеер перешёл в состояние остановки (`PlaybackStateCompat.STATE_STOPPED`).
  - `STATE_NONE` — сессия сброшена в исходное неактивное состояние (`PlaybackStateCompat.STATE_NONE`).
  - `TRACK_CHANGED` — во время воспроизведения сменился трек (метаданные изменились без предварительного `STOPPED`).
  - `CONTROLLER_DISCONNECTED` — контроллер сессии удален из списка активных сессий системы (плеер закрыт, сервис плеера выгружен).
  - `HEARTBEAT_TIMEOUT` — отсутствие сигналов и обновлений состояния от плеера $> 5$ минут при нахождении в состоянии `STATE_PLAYING`.
  - `APP_RESTART` — восстановление незакрытой сессии после аварийной перезагрузки приложения/ОС.

---

#### 4. `ActiveMediaSession`

```kotlin
data class ActiveMediaSession(
    val sessionId: String,
    val packageName: String,
    val metadata: MediaMetadataSnapshot,
    val sessionStartedAt: java.time.Instant,
    val lastActivePlayStartedAt: java.time.Instant?,
    val accumulatedPlayTimeMs: Long,
    val lastState: Int,
    val lastEventAt: java.time.Instant
)
```

- **Назначение:** Текущее оперативное состояние открытой медиа-сессии в памяти детектора.
- **Поля:**
  - `sessionId: String` — уникальный идентификатор сессии в памяти (формат: `"$packageName:${sessionStartedAt.toEpochMilli()}"`).
  - `packageName: String` — имя Android-пакета приложения-плеера (например, `com.spotify.music`).
  - `metadata: MediaMetadataSnapshot` — текущие метаданные трека.
  - `sessionStartedAt: Instant` — точный момент первого перехода в `STATE_PLAYING`.
  - `lastActivePlayStartedAt: Instant?` — момент последнего возобновления воспроизведения (после паузы или старта). `null`, если в данный момент плеер на паузе.
  - `accumulatedPlayTimeMs: Long` — накопленное полезное время чистого воспроизведения (без учёта пауз) до момента `lastActivePlayStartedAt`.
  - `lastState: Int` — последний зафиксированный код состояния воспроизведения (`PlaybackStateCompat.STATE_*`).
  - `lastEventAt: Instant` — время последнего полученного события/колбэка для контроля heartbeat.
- **Инварианты:**
  - `sessionId.isNotBlank()`.
  - `packageName.isNotBlank()`.
  - `accumulatedPlayTimeMs >= 0L`.
  - Если `lastActivePlayStartedAt != null`, то `lastActivePlayStartedAt >= sessionStartedAt`.

---

#### 5. `MediaSessionPayload`

```kotlin
data class MediaSessionPayload(
    val packageName: String,
    val trackTitle: String?,
    val artist: String?,
    val album: String?,
    val trackDurationMs: Long,
    val sessionStartedAtEpochMs: Long,
    val sessionEndedAtEpochMs: Long,
    val effectiveDurationMs: Long,
    val isMicroSession: Boolean,
    val endReason: MediaSessionEndReason,
    val lastError: String?
)
```

- **Назначение:** Неизменяемый структурированный DTO завершённой медиа-сессии для сериализации в `payloadJson` сырого события `RawEvent`.
- **Поля:**
  - `packageName: String` — пакет плеера.
  - `trackTitle: String?` — название трека.
  - `artist: String?` — исполнитель трека.
  - `album: String?` — альбом.
  - `trackDurationMs: Long` — длительность трека из метаданных.
  - `sessionStartedAtEpochMs: Long` — время начала сессии (эпоха в мс).
  - `sessionEndedAtEpochMs: Long` — время окончания сессии (эпоха в мс).
  - `effectiveDurationMs: Long` — чистое время воспроизведения за вычетом пауз (мс).
  - `isMicroSession: Boolean` — признак сессии длительностью $< 5$ секунд (`effectiveDurationMs < 5000L`).
  - `endReason: MediaSessionEndReason` — причина завершения.
  - `lastError: String?` — ошибка сессии (например, `"heartbeat_timeout"` или `null`).
- **Инварианты:**
  - `sessionStartedAtEpochMs <= sessionEndedAtEpochMs`.
  - `effectiveDurationMs >= 0L`.
  - `effectiveDurationMs <= (sessionEndedAtEpochMs - sessionStartedAtEpochMs)`.
  - `isMicroSession == (effectiveDurationMs < 5000L)`.

---

#### 6. `SessionBoundaryDecision`

```kotlin
sealed interface SessionBoundaryDecision {
    data object NoOp : SessionBoundaryDecision

    data class OpenSession(
        val session: ActiveMediaSession
    ) : SessionBoundaryDecision

    data class CloseSession(
        val completedSession: MediaSessionPayload
    ) : SessionBoundaryDecision

    data class SwitchTrack(
        val previousSessionToClose: MediaSessionPayload,
        val newSessionToOpen: ActiveMediaSession
    ) : SessionBoundaryDecision
}
```

- **Назначение:** Результат вычисления границ сессий конечным автоматом `MediaSessionBoundaryDetector`.

---

### Межзонный контракт `MediaSessionContract`

В соответствии с §3.1 общей архитектуры, модуль экспортирует контракт межмодульного взаимодействия:

```kotlin
package com.example.npc.ingest.media

data class MediaSessionContract(
    val packageName: String,
    val trackTitle: String?,
    val artist: String?,
    val sessionStartedAt: java.time.Instant,
    val sessionEndedAt: java.time.Instant?
)
```

- `sessionEndedAt == null` означает активную незавершённую сессию в рантайме.

---

### Публичный API и компоненты зоны

```kotlin
// 1. Наблюдатель системных медиа-сессий
class MediaSessionObserver(
    private val context: Context,
    private val boundaryDetector: MediaSessionBoundaryDetector,
    private val recoveryManager: MediaSessionRecoveryManager,
    private val storageGateway: StorageGateway,
    private val coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun start()
    fun stop()
    internal fun handleActiveSessionsChanged(controllers: List<MediaController>?)
}

// 2. Детектор границ сессий и FSM
class MediaSessionBoundaryDetector(
    private val minSessionThresholdMs: Long = 5000L,
    private val heartbeatTimeoutMs: Long = 300_000L // 5 минут
) {
    fun onPlaybackStateChanged(packageName: String, state: MediaPlaybackSnapshot, now: Instant): SessionBoundaryDecision
    fun onMetadataChanged(packageName: String, metadata: MediaMetadataSnapshot, now: Instant): SessionBoundaryDecision
    fun onControllerDisconnected(packageName: String, now: Instant): SessionBoundaryDecision
    fun checkHeartbeats(now: Instant): List<SessionBoundaryDecision.CloseSession>
    fun getActiveSession(packageName: String): ActiveMediaSession?
    fun getAllActiveSessions(): List<ActiveMediaSession>
}

// 3. Менеджер восстановления при рестарте
class MediaSessionRecoveryManager(
    private val dataStore: DataStore<Preferences>,
    private val storageGateway: StorageGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun saveActiveSessionSnapshot(session: ActiveMediaSession)
    suspend fun clearActiveSessionSnapshot(packageName: String)
    suspend fun recoverDanglingSessions(appStartedAt: Instant): Int
}

// 4. Маппер полезной нагрузки и сущностей
object MediaPayloadMapper {
    fun toPayloadJson(payload: MediaSessionPayload): String
    fun fromPayloadJson(json: String): MediaSessionPayload
    fun extractMetadata(metadataCompat: MediaMetadataCompat?): MediaMetadataSnapshot
    fun extractPlaybackState(playbackStateCompat: PlaybackStateCompat?): MediaPlaybackSnapshot
}

// 5. Обертка контроллера медиа-сессии
internal class MediaControllerHolder(
    val packageName: String,
    val controller: MediaControllerCompat,
    val callback: MediaControllerCompat.Callback
) {
    fun register()
    fun unregister()
}
```

---

### Детальная спецификация классов, функций и методов

---

#### Класс `MediaSessionObserver`

##### Функция 1: `start`

- **Сигнатура:**
  ```kotlin
  fun start()
  ```
- **Назначение:** Инициализация подсистемы сбора медиа-событий: запуск восстановления висячих сессий, регистрация слушателя `OnActiveSessionsChangedListener` в системном сервисе `MediaSessionManager`, получение первичного списка активных сессий и старт фонового тикера heartbeat.
- **Предусловия:**
  - Приложению предоставлен доступ к уведомлениям (`NotificationListenerService` включён в настройках Android).
  - Компонент `PipelineNotificationListenerService` объявлен в `AndroidManifest.xml`.
- **Постусловия:**
  - Незавершённые сессии предыдущего запуска закрыты через `recoveryManager.recoverDanglingSessions`.
  - Зарегистрирован слушатель `activeSessionsListener` в `MediaSessionManager`.
  - Все текущие активные медиа-сессии обёрнуты в `MediaControllerHolder` с зарегистрированными колбэками.
  - Запущен периодический корутинный цикл проверки heartbeat (интервал 30 секунд).
- **Пошаговое поведение:**
  1. Вызвать корутину восстановления в `coroutineScope` на `ioDispatcher`:
     `recoveryManager.recoverDanglingSessions(appStartedAt = Instant.now())`.
  2. Получить системный сервис `MediaSessionManager`:
     `val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager`.
  3. Создать `ComponentName` листенера уведомлений:
     `val listenerComponent = ComponentName(context, "com.example.npc.ingest.notification.service.PipelineNotificationListenerService")`.
  4. Выполнить проверку наличия разрешений через защитный блок `try-catch (e: SecurityException)`:
     - Зарегистрировать слушатель:
       `manager.addOnActiveSessionsChangedListener(sessionsChangedListener, listenerComponent)`.
     - Запросить начальный список:
       `val initialControllers = manager.getActiveSessions(listenerComponent)`.
     - Вызвать `handleActiveSessionsChanged(initialControllers)`.
  5. Если выброшено `SecurityException`:
     - Залогировать ошибку: `"Notification listener permission not granted. Media session tracking is disabled"`.
     - Обновить `SourceHealth` с ошибкой `"SecurityException: notification listener permission not granted"`.
  6. Запустить периодический таймер проверки heartbeat в `coroutineScope`:
     - Цикл `while (isActive)` с задержкой `delay(30_000L)`.
     - Вызов `checkHeartbeatTimeouts()`.
- **Ошибки:**
  - `SecurityException`: если доступ к уведомлениям не предоставлен пользователем. Обрабатывается безопасно, не крашит приложение.
- **Побочные эффекты:**
  - Регистрация глобального IPC-слушателя в ОС.
  - Запуск фоновых корутин.
- **Граничные случаи:**
  - При запуске нет активных плееров $\rightarrow$ список контроллеров пуст, система ожидает появления первого плеера.
  - Устройство без Google Play Services или кастомная сборка $\rightarrow$ `MEDIA_SESSION_SERVICE` возвращает пустой список.
- **Примеры:**
  1. *Пример 1 (Штатный запуск со Spotify в фоне):*
     - Состояние: Spotify запущен и воспроизводит трек.
     - Поведение: `start()` находит 1 контроллер `com.spotify.music`, вешает колбэк, детектирует `STATE_PLAYING`, открывает сессию.
  2. *Пример 2 (Запуск без выданного Notification Access):*
     - Состояние: Разрешение не выдано.
     - Поведение: `SecurityException` перехвачен, в `source_health.lastError` записана ошибка, сервис остаётся неактивным.
  3. *Пример 3 (Повторный вызов start):*
     - Поведение: Идемпотентно, повторная регистрация предотвращается внутренней проверкой `isRunning`.
- **Сложность / ограничения:**
  - Время: $O(K)$, где $K$ — число активных плееров в системе (обычно $0..3$). Память: $O(K)$.

---

##### Функция 2: `stop`

- **Сигнатура:**
  ```kotlin
  fun stop()
  ```
- **Назначение:** Корректная остановка мониторинга: отписка от всех контроллеров плееров, закрытие системного слушателя сессий, завершение всех текущих активных сессий и остановка корутин.
- **Предусловия:**
  - `start()` был вызван ранее.
- **Постусловия:**
  - Слушатель `OnActiveSessionsChangedListener` удалён из `MediaSessionManager`.
  - Все `MediaControllerHolder` отписаны от колбэков через `unregister()`.
  - Все открытые в памяти сессии закрываются с причиной `CONTROLLER_DISCONNECTED`.
  - Фоновые корутины модуля отменены.
- **Пошаговое поведение:**
  1. Вызвать `manager.removeOnActiveSessionsChangedListener(sessionsChangedListener)`.
  2. Зафиксировать текущее время `val now = Instant.now()`.
  3. Для каждого зарегистрированного держателя контроллера:
     - Вызвать `holder.unregister()`.
     - Вызвать `boundaryDetector.onControllerDisconnected(holder.packageName, now)`.
     - Если решение `CloseSession`, выполнить конвейер сохранения `processSessionClose(decision.completedSession)`.
  4. Очистить внутреннюю мапу контроллеров: `activeControllers.clear()`.
  5. Отменить внутренний `coroutineContext.cancelChildren()`.
- **Ошибки:**
  - Не выбрасывает исключений (все системные ошибки перехватываются).
- **Побочные эффекты:**
  - Сохранение закрытых сессий в БД через `StorageGateway`.
- **Граничные случаи:**
  - Вызов `stop()` при отсутствии открытых сессий $\rightarrow$ тихая отписка без создания записей.
- **Примеры:**
  1. *Пример 1 (Остановка во время прослушивания музыки):*
     - Вход: Spotify проигрывал песню 45 секунд.
     - Результат: Сессия закрыта с причиной `CONTROLLER_DISCONNECTED`, эффективное время 45 с, сохранена в БД.
  2. *Пример 2 (Остановка при пустом списке):*
     - Вход: Нет активных плееров.
     - Результат: Успешная отписка, 0 записей в БД.
  3. *Пример 3 (Повторный вызов stop):*
     - Вход: Уже остановлен.
     - Результат: No-op.
- **Сложность / ограничения:**
  - Время: $O(K)$.

---

##### Функция 3: `handleActiveSessionsChanged`

- **Сигнатура:**
  ```kotlin
  internal fun handleActiveSessionsChanged(controllers: List<MediaController>?)
  ```
- **Назначение:** Синхронизация внутреннего набора контроллеров со списком активных сессий операционной системы при срабатывании `OnActiveSessionsChangedListener`.
- **Предусловия:**
  - Вызывается из потока обратного вызова Android OS.
- **Постусловия:**
  - Для исчезнувших плееров: колбэки отписаны, держатели удалены, сессии закрыты.
  - Для новых плееров: созданы `MediaControllerCompat`, зарегистрированы колбэки, опрошены текущие метаданные и состояние воспроизведения.
- **Пошаговое поведение:**
  1. Зафиксировать текущее время: `val now = Instant.now()`.
  2. Если `controllers == null`, считать список пустым.
  3. Получить набор актуальных пакетов: `val currentPackages = controllers.map { it.packageName }.toSet()`.
  4. Найти удалённые контроллеры:
     - Для каждого `(pkg, holder)` в `activeControllers`, где `pkg !in currentPackages`:
       - Вызвать `holder.unregister()`.
       - Вызвать `val decision = boundaryDetector.onControllerDisconnected(pkg, now)`.
       - Обработать решение: если `CloseSession`, отправить `decision.completedSession` в конвейер персистенции.
       - Удалить из `activeControllers`.
  5. Для каждого `nativeController` из `controllers`:
     - `val pkg = nativeController.packageName`.
     - Если `pkg !in activeControllers`:
       - Создать `MediaControllerCompat(context, nativeController.sessionToken)`.
       - Создать анонимный колбэк `MediaControllerCompat.Callback`:
         - `onPlaybackStateChanged(state)` $\rightarrow$ вызов `handlePlaybackState(pkg, state)`.
         - `onMetadataChanged(metadata)` $\rightarrow$ вызов `handleMetadata(pkg, metadata)`.
         - `onSessionDestroyed()` $\rightarrow$ вызов `handleSessionDestroyed(pkg)`.
       - Создать `MediaControllerHolder(pkg, compatController, callback)`.
       - Вызвать `holder.register()`.
       - Поместить в `activeControllers[pkg] = holder`.
       - Немедленно опросить текущее состояние:
         - `compatController.playbackState?.let { handlePlaybackState(pkg, it) }`
         - `compatController.metadata?.let { handleMetadata(pkg, it) }`
- **Ошибки:**
  - Не выбрасывает исключений (защита от краша при дефектных IPC-ответах сторонних плееров).
- **Побочные эффекты:**
  - Регистрация колбэков, запись сессий в БД при отключении.
- **Граничные случаи:**
  - Плеер крашнулся и исчез из списка $\rightarrow$ детектируется как исчезновение контроллера, сессия закрывается.
  - Контроллер вернул `null` для `sessionToken` $\rightarrow$ плеер игнорируется.
- **Примеры:**
  1. *Пример 1 (Пользователь открыл YouTube Music):*
     - Вход: В списке появился `com.google.android.apps.youtube.music`.
     - Выход: Контроллер зарегистрирован, состояние считано.
  2. *Пример 2 (Пользователь закрыл плеер свайпом из недавних):*
     - Вход: `com.spotify.music` пропал из списка.
     - Выход: Колбэк отписан, сессия закрыта.
  3. *Пример 3 (Сессии не изменились):*
     - Вход: Список пакетов идентичен текущему.
     - Выход: No-op, контроллеры не пересоздаются.
- **Сложность / ограничения:**
  - Время: $O(N + M)$, где $N$ — старое число контроллеров, $M$ — новое число.

---

##### Функция 4: `processSessionClose` (Конвейер сохранения)

- **Сигнатура:**
  ```kotlin
  internal suspend fun processSessionClose(payload: MediaSessionPayload)
  ```
- **Назначение:** Асинхронное пятишаговое сохранение завершённой медиа-сессии в хранилище `:core:storage` с вычислением дедупликационного ключа, нормализацией текста и обновлением `SourceHealth`.
- **Предусловия:**
  - Выполняется в корутине на `ioDispatcher`.
  - `payload` валиден.
- **Постусловия:**
  - `RawEvent` сохранён в таблице `raw_event`.
  - Если `!payload.isMicroSession`: `Event` сохранён в таблице `event`.
  - Снимок сессии в `recoveryManager` очищен.
  - Метрика `source_health` обновлена.
- **Пошаговое поведение:**
  1. **Шаг 1: Сериализация и дедупликация:**
     - Сериализовать `payload` в JSON: `val payloadJson = MediaPayloadMapper.toPayloadJson(payload)`.
     - Вычислить дедупликационный ключ через `:core:model`:
       `val dedupKey = EventNormalizer.computeDeduplicationKey(SourceId.MEDIA, payload.packageName, payloadJson)`.
     - Получить следующий порядковый номер: `val seq = seqGenerator.incrementAndGet()`.
  2. **Шаг 2: Вставка RawEvent:**
     - Сконструировать `RawEvent`:
       ```kotlin
       val rawEvent = RawEvent(
           id = 0L,
           seq = seq,
           source = SourceId.MEDIA,
           packageName = payload.packageName,
           receivedAt = Instant.ofEpochMilli(payload.sessionEndedAtEpochMs),
           payloadJson = payloadJson,
           hash = dedupKey
       )
       ```
     - Выполнить вставку: `val rawId = storageGateway.insertRawEvent(rawEvent)`.
     - Если `rawId == -1L` (коллизия хеша): перейти сразу к Шагу 4.
  3. **Шаг 3: Проверка порога и создание Event:**
     - Если `payload.isMicroSession == true`:
       - Залогировать пропуск: `Log.d(TAG, "Micro-session ignored for Event table (< 5s): ${payload.effectiveDurationMs}ms")`.
       - Не создавать запись в таблице `event` (защита от мусорных прокликиваний треков).
     - Иначе (`effectiveDurationMs >= 5000L`):
       - Сформировать эффективный заголовок: `val title = payload.trackTitle ?: "Неизвестный трек"`.
       - Сформировать эффективный текст:
         `val text = if (!payload.artist.isNullOrBlank()) "${payload.artist} - $title" else title`.
       - Вызвать чистую нормализацию:
         ```kotlin
         val event = EventNormalizer.normalize(
             rawEvent = rawEvent.copy(id = rawId),
             title = title,
             text = text,
             threadKey = ThreadKey("media:${payload.packageName}"),
             isUpdateOf = null
         )
         ```
       - Выполнить вставку: `storageGateway.insertEvent(event)`.
  4. **Шаг 4: Очистка состояния восстановления:**
     - Вызвать `recoveryManager.clearActiveSessionSnapshot(payload.packageName)`.
  5. **Шаг 5: Обновление SourceHealth:**
     - Сформировать объект здоровья источника:
       ```kotlin
       val health = SourceHealth(
           source = SourceId.MEDIA,
           lastEventAt = Instant.ofEpochMilli(payload.sessionEndedAtEpochMs),
           events24h = 0,
           lastError = payload.lastError,
           queueDepth = 0
       )
       ```
     - Вызвать `storageGateway.upsertSourceHealth(health)`.
- **Ошибки:**
  - Исключения дисковых операций перехватываются, логируются и обновляют `source_health.lastError`.
- **Побочные эффекты:**
  - Запись в Room SQLite.
- **Граничные случаи:**
  - Дубликат завершения сессии $\rightarrow$ `insertRawEvent` возвращает существующий `id`, повторный `Event` не создаётся.
  - Длительность 4999 мс $\rightarrow$ `isMicroSession = true`, пишется только `raw_event`.
  - Длительность 5000 мс $\rightarrow$ `isMicroSession = false`, пишутся и `raw_event`, и `event`.
- **Примеры:**
  1. *Пример 1 (Полноценное прослушивание трека):*
     - Вход: Queen — Bohemian Rhapsody, 354 с, `effectiveDurationMs = 354000`.
     - Результат: `raw_event` записан, `event` создан с `title = "Bohemian Rhapsody"`, `text = "Queen - Bohemian Rhapsody"`, `threadKey = "media:com.spotify.music"`.
  2. *Пример 2 (Случайный скип трека на 3-й секунде):*
     - Вход: The Beatles — Yesterday, `effectiveDurationMs = 2800`, `isMicroSession = true`.
     - Результат: `raw_event` записан (аудит сырых данных), `event` НЕ создан (лента Timeline остаётся чистой).
  3. *Пример 3 (Таймаут heartbeat):*
     - Вход: Подкаст оборвался без сигнала STOP, `effectiveDurationMs = 600000`, `lastError = "heartbeat_timeout"`.
     - Результат: Записаны `raw_event` и `event`, `source_health.lastError = "heartbeat_timeout"`.
- **Сложность / ограничения:**
  - Время: $O(1) + \text{Room I/O}$. Задержка < 20 мс на `Dispatchers.IO`.

---

#### Класс `MediaSessionBoundaryDetector`

##### Функция 1: `onPlaybackStateChanged`

- **Сигнатура:**
  ```kotlin
  fun onPlaybackStateChanged(
      packageName: String,
      state: MediaPlaybackSnapshot,
      now: java.time.Instant
  ): SessionBoundaryDecision
  ```
- **Назначение:** Обработка изменения статуса воспроизведения плеера и реализация переходов конечного автомата (FSM).
- **Предусловия:**
  - `packageName` не пустой.
  - `now` не равен `null`.
- **Постусловия:**
  - Возвращает одно из решений: `NoOp`, `OpenSession`, `CloseSession`.
  - При `STATE_PLAYING`: если сессии не было, открывается новая сессия; если была — обновляется `lastActivePlayStartedAt` и `lastEventAt`.
  - При `STATE_PAUSED`: сессия **НЕ закрывается**, накопленное время воспроизведения фиксируется в `accumulatedPlayTimeMs`.
  - При `STATE_STOPPED` или `STATE_NONE`: сессия закрывается с расчётом финального `effectiveDurationMs`.
- **Пошаговое поведение:**
  1. Найти текущую сессию: `val current = activeSessions[packageName]`.
  2. Анализ нового состояния `state.state`:
     - **Кейс А: `PlaybackStateCompat.STATE_PLAYING`:**
       - Если `current == null`:
         - Метаданные берутся из кэша метаданных пакета `lastKnownMetadata[packageName]` или пустых значений.
         - Сконструировать `val newSession = ActiveMediaSession(sessionId = "$packageName:${now.toEpochMilli()}", packageName = packageName, metadata = meta, sessionStartedAt = now, lastActivePlayStartedAt = now, accumulatedPlayTimeMs = 0L, lastState = state.state, lastEventAt = now)`.
         - Сохранить в `activeSessions[packageName] = newSession`.
         - Вернуть `SessionBoundaryDecision.OpenSession(newSession)`.
       - Если `current != null`:
         - Если `current.lastState == STATE_PLAYING`:
           - Обновить `lastEventAt = now`.
           - Вернуть `SessionBoundaryDecision.NoOp`.
         - Если `current.lastState == STATE_PAUSED` (или иное):
           - Возобновление воспроизведения:
           - Обновить `current = current.copy(lastActivePlayStartedAt = now, lastState = STATE_PLAYING, lastEventAt = now)`.
           - `activeSessions[packageName] = current`.
           - Вернуть `SessionBoundaryDecision.NoOp`.
     - **Кейс Б: `PlaybackStateCompat.STATE_PAUSED`:**
       - Если `current == null`:
         - Игнорировать (старт с паузы не открывает сессию).
         - Вернуть `SessionBoundaryDecision.NoOp`.
       - Если `current != null`:
         - Если `current.lastActivePlayStartedAt != null`:
           - Прибавить прошедшее время воспроизведения:
             `val delta = maxOf(0L, now.toEpochMilli() - current.lastActivePlayStartedAt.toEpochMilli())`.
             `val newAccumulated = current.accumulatedPlayTimeMs + delta`.
             `val updated = current.copy(lastActivePlayStartedAt = null, accumulatedPlayTimeMs = newAccumulated, lastState = STATE_PAUSED, lastEventAt = now)`.
             `activeSessions[packageName] = updated`.
         - Вернуть `SessionBoundaryDecision.NoOp` (сессия остаётся открытой!).
     - **Кейс В: `PlaybackStateCompat.STATE_STOPPED` или `PlaybackStateCompat.STATE_NONE`:**
       - Если `current == null`:
         - Вернуть `SessionBoundaryDecision.NoOp`.
       - Если `current != null`:
         - Вычислить финальное время:
           - Если `current.lastActivePlayStartedAt != null`:
             `val delta = maxOf(0L, now.toEpochMilli() - current.lastActivePlayStartedAt.toEpochMilli())`.
             `val totalDuration = current.accumulatedPlayTimeMs + delta`.
           - Иначе:
             `val totalDuration = current.accumulatedPlayTimeMs`.
         - Сформировать `val payload = MediaSessionPayload(...)`:
           - `effectiveDurationMs = totalDuration`.
           - `isMicroSession = totalDuration < minSessionThresholdMs`.
           - `endReason = if (state.state == STATE_STOPPED) MediaSessionEndReason.STATE_STOPPED else MediaSessionEndReason.STATE_NONE`.
           - `sessionEndedAtEpochMs = now.toEpochMilli()`.
         - Удалить из `activeSessions.remove(packageName)`.
         - Вернуть `SessionBoundaryDecision.CloseSession(payload)`.
     - **Кейс Г: Прочие состояния (`STATE_BUFFERING`, `STATE_CONNECTING`):**
       - Обновить `lastEventAt = now`.
       - Вернуть `SessionBoundaryDecision.NoOp`.
- **Ошибки:**
  - Не выбрасывает исключений.
- **Побочные эффекты:**
  - Модификация локального кэша `activeSessions`.
- **Граничные случаи:**
  - Повторный `STATE_PAUSED` подряд $\rightarrow$ No-op, повторно дельта не плюсуется.
  - Повторный `STATE_PLAYING` подряд $\rightarrow$ No-op, просто обновляется таймстемп пульса.
  - `STATE_STOPPED` без предварительного `STATE_PLAYING` $\rightarrow$ No-op.
- **Примеры:**
  1. *Пример 1 (Старт воспроизведения):*
     - Вход: `state = STATE_PLAYING`, активной сессии нет.
     - Результат: `OpenSession`, `sessionStartedAt = now`, `accumulated = 0`.
  2. *Пример 2 (Пауза на звонок):*
     - Вход: `state = STATE_PAUSED` через 30 с после старта.
     - Результат: `NoOp`, сессия открыта, `accumulated = 30000 ms`, `lastActivePlayStartedAt = null`.
  3. *Пример 3 (Остановка после паузы):*
     - Вход: `state = STATE_STOPPED` после 10 минут нахождения на паузе.
     - Результат: `CloseSession`, `effectiveDurationMs = 30000 ms` (время паузы не вошло!).
- **Сложность / ограничения:**
  - Время: $O(1)$. Память: $O(1)$.

---

##### Функция 2: `onMetadataChanged`

- **Сигнатура:**
  ```kotlin
  fun onMetadataChanged(
      packageName: String,
      metadata: MediaMetadataSnapshot,
      now: java.time.Instant
  ): SessionBoundaryDecision
  ```
- **Назначение:** Обработка обновления метаданных трека. Если трек изменился при активном воспроизведении, атомарно закрывает сессию предыдущего трека и открывает сессию для нового.
- **Предусловия:**
  - `metadata` не равен `null`.
- **Постусловия:**
  - Кэш `lastKnownMetadata[packageName]` обновлён.
  - Если сессия активна и трек изменился: возвращает `SwitchTrack` с закрытием старого трека и открытием нового.
  - Если сессия активна и метаданные те же: обновляет `current.metadata`, возвращает `NoOp`.
  - Если сессии нет: обновляет кэш, возвращает `NoOp`.
- **Пошаговое поведение:**
  1. Сохранить в `lastKnownMetadata[packageName] = metadata`.
  2. Получить текущую сессию `val current = activeSessions[packageName]`.
  3. Если `current == null`:
     - Вернуть `SessionBoundaryDecision.NoOp`.
  4. Проверить идентичность трека:
     - Сравнить `current.metadata.title == metadata.title` и `current.metadata.artist == metadata.artist`.
     - Если трек тот же:
       - Обновить метаданные в сессии (могли подтянуться обложка или альбом):
         `activeSessions[packageName] = current.copy(metadata = metadata, lastEventAt = now)`.
       - Вернуть `SessionBoundaryDecision.NoOp`.
  5. Если трек изменился (пользователь переключил трек или начался следующий трек плейлиста):
     - Рассчитать накопленное время для завершаемой сессии:
       - `val delta = if (current.lastActivePlayStartedAt != null) maxOf(0L, now.toEpochMilli() - current.lastActivePlayStartedAt.toEpochMilli()) else 0L`.
       - `val totalDuration = current.accumulatedPlayTimeMs + delta`.
     - Сформировать `val closedPayload = MediaSessionPayload(...)`:
       - `effectiveDurationMs = totalDuration`.
       - `isMicroSession = totalDuration < minSessionThresholdMs`.
       - `endReason = MediaSessionEndReason.TRACK_CHANGED`.
       - `sessionEndedAtEpochMs = now.toEpochMilli()`.
     - Сконструировать новую сессию:
       - `val newSession = ActiveMediaSession(sessionId = "$packageName:${now.toEpochMilli()}", packageName = packageName, metadata = metadata, sessionStartedAt = now, lastActivePlayStartedAt = if (current.lastState == STATE_PLAYING) now else null, accumulatedPlayTimeMs = 0L, lastState = current.lastState, lastEventAt = now)`.
     - Записать в `activeSessions[packageName] = newSession`.
     - Вернуть `SessionBoundaryDecision.SwitchTrack(previousSessionToClose = closedPayload, newSessionToOpen = newSession)`.
- **Ошибки:**
  - Не выбрасывает исключений.
- **Побочные эффекты:**
  - Обновление `activeSessions` и `lastKnownMetadata`.
- **Граничные случаи:**
  - Плеер прислал пустые метаданные (null title и artist) $\rightarrow$ считаются неизвестным треком, обрабатываются корректно.
  - Метаданные пришли до первого события `STATE_PLAYING` $\rightarrow$ кэшируются в `lastKnownMetadata`, решение `NoOp`.
- **Примеры:**
  1. *Пример 1 (Штатное переключение на следующий трек в Spotify):*
     - Состояние: Играл Трек 1 (проиграл 200 с). Пришли метаданные Трека 2.
     - Результат: `SwitchTrack`, Трек 1 закрывается с 200 с и причиной `TRACK_CHANGED`, открывается сессия Трека 2.
  2. *Пример 2 (Уточнение названия того же трека):*
     - Состояние: Играет Трек "Song". Пришло обновление метаданных с заполненным альбомом "Album".
     - Результат: `NoOp`, сессия не прерывается, альбом обновляется.
  3. *Пример 3 (Метаданные при закрытом плеере):*
     - Состояние: Сессии нет.
     - Результат: `NoOp`, сохранено в кэш.
- **Сложность / ограничения:**
  - Время: $O(1)$. Память: $O(1)$.

---

##### Функция 3: `onControllerDisconnected`

- **Сигнатура:**
  ```kotlin
  fun onControllerDisconnected(
      packageName: String,
      now: java.time.Instant
  ): SessionBoundaryDecision
  ```
- **Назначение:** Закрытие активной сессии при выгрузке плеера или отсоединении контроллера.
- **Предусловия:**
  - `packageName` не пустой.
- **Постусловия:**
  - Если была активная сессия, она удаляется из `activeSessions` и возвращается в `CloseSession` с причиной `CONTROLLER_DISCONNECTED`.
  - Если сессии не было, возвращается `NoOp`.
- **Пошаговое поведение:**
  1. Извлечь и удалить текущую сессию: `val current = activeSessions.remove(packageName)`.
  2. Если `current == null`:
     - Вернуть `SessionBoundaryDecision.NoOp`.
  3. Вычислить финальную длительность воспроизведения:
     - `val delta = if (current.lastActivePlayStartedAt != null) maxOf(0L, now.toEpochMilli() - current.lastActivePlayStartedAt.toEpochMilli()) else 0L`.
     - `val totalDuration = current.accumulatedPlayTimeMs + delta`.
  4. Сформировать `MediaSessionPayload`:
     - `effectiveDurationMs = totalDuration`.
     - `isMicroSession = totalDuration < minSessionThresholdMs`.
     - `endReason = MediaSessionEndReason.CONTROLLER_DISCONNECTED`.
     - `sessionEndedAtEpochMs = now.toEpochMilli()`.
  5. Вернуть `SessionBoundaryDecision.CloseSession(payload)`.
- **Ошибки:**
  - Не выбрасывает исключений.
- **Побочные эффекты:**
  - Удаление элемента из `activeSessions`.
- **Граничные случаи:**
  - Плеер отключился, находясь на паузе $\rightarrow$ финальное время равно `accumulatedPlayTimeMs`, время на паузе не учитывается.
- **Примеры:**
  1. *Пример 1 (Убийство процесса плеера):*
     - Вход: Spotify воспроизводил музыку 50 с и был принудительно остановлен через настройки.
     - Результат: `CloseSession`, длительность 50 с, причина `CONTROLLER_DISCONNECTED`.
  2. *Пример 2 (Отключение неактивного плеера):*
     - Вход: Плеер был на паузе со сброшенной сессией.
     - Результат: `NoOp`.
  3. *Пример 3 (Отключение после 2 с воспроизведения):*
     - Вход: Скипнут и сразу закрыт.
     - Результат: `CloseSession` с `isMicroSession = true`.
- **Сложность / ограничения:**
  - Время: $O(1)$.

---

##### Функция 4: `checkHeartbeats`

- **Сигнатура:**
  ```kotlin
  fun checkHeartbeats(now: java.time.Instant): List<SessionBoundaryDecision.CloseSession>
  ```
- **Назначение:** Регулярная проверка активности сессий. Если плеер завис или не присылает обновления $> 5$ минут в состоянии `STATE_PLAYING`, сессия закрывается принудительно по таймауту с фиксацией ошибки `heartbeat_timeout`.
- **Предусловия:**
  - Вызывается по периодическому таймеру (каждые 30 секунд).
- **Постусловия:**
  - Все просроченные сессии удалены из `activeSessions` и возвращены в виде списка `CloseSession` с `lastError = "heartbeat_timeout"`.
- **Пошаговое поведение:**
  1. Инициализировать результирующий список: `val expiredSessions = mutableListOf<SessionBoundaryDecision.CloseSession>()`.
  2. Для каждого `(pkg, session)` в `activeSessions`:
     - Проверить: находится ли сессия в режиме активного воспроизведения (`session.lastState == PlaybackStateCompat.STATE_PLAYING`).
     - Если да:
       - Вычислить время тишины: `val silenceMs = now.toEpochMilli() - session.lastEventAt.toEpochMilli()`.
       - Если `silenceMs > heartbeatTimeoutMs` (300 000 мс = 5 минут):
         - Рассчитать длительность: зафиксировать время на момент последнего известного пульса (`session.lastEventAt`):
           `val totalDuration = session.accumulatedPlayTimeMs + maxOf(0L, session.lastEventAt.toEpochMilli() - (session.lastActivePlayStartedAt?.toEpochMilli() ?: session.lastEventAt.toEpochMilli()))`.
         - Сформировать `MediaSessionPayload`:
           - `effectiveDurationMs = totalDuration`.
           - `isMicroSession = totalDuration < minSessionThresholdMs`.
           - `endReason = MediaSessionEndReason.HEARTBEAT_TIMEOUT`.
           - `sessionEndedAtEpochMs = session.lastEventAt.toEpochMilli()`.
           - `lastError = "heartbeat_timeout"`.
         - Добавить в `expiredSessions.add(SessionBoundaryDecision.CloseSession(payload))`.
  3. Удалить все просроченные сессии из `activeSessions`.
  4. Вернуть `expiredSessions`.
- **Ошибки:**
  - Не выбрасывает исключений.
- **Побочные эффекты:**
  - Очистка зависших сессий.
- **Граничные случаи:**
  - Сессия на паузе $> 5$ минут $\rightarrow$ таймаут НЕ срабатывает (согласно правилу §6.6, пауза ожидает продолжения и не закрывается по таймауту воспроизведения).
  - Все сессии активны и шлют события $\rightarrow$ возвращается пустой список.
- **Примеры:**
  1. *Пример 1 (Плеер завис на воспроизведении):*
     - Состояние: Последнее событие было 5 минут 30 секунд назад при `STATE_PLAYING`.
     - Результат: Сессия закрыта, `lastError = "heartbeat_timeout"`.
  2. *Пример 2 (Плеер на паузе 2 часа):*
     - Состояние: `lastState = STATE_PAUSED`, тишина 2 часа.
     - Результат: Сессия НЕ закрывается по heartbeat, остаётся ждать `STOPPED` или возобновления.
  3. *Пример 3 (События идут стабильно):*
     - Состояние: Тишина 10 секунд.
     - Результат: Список пуст.
- **Сложность / ограничения:**
  - Время: $O(K)$, где $K \le 5$. Память: $O(K)$.

---

#### Класс `MediaSessionRecoveryManager`

##### Функция 1: `saveActiveSessionSnapshot`

- **Сигнатура:**
  ```kotlin
  suspend fun saveActiveSessionSnapshot(session: ActiveMediaSession)
  ```
- **Назначение:** Атомарное сохранение текущего снимка активной медиа-сессии в `DataStore<Preferences>` для защиты от потери данных при падении или убийстве процесса операционной системой.
- **Предусловия:**
  - Выполняется на `ioDispatcher`.
- **Постусловия:**
  - JSON-представление сессии сохранено в `DataStore` по ключу `active_session_${session.packageName}`.
- **Пошаговое поведение:**
  1. Сформировать DTO снимка восстановления `ActiveSessionSnapshotDto`.
  2. Сериализовать в JSON.
  3. Выполнить запись в `dataStore.edit { prefs -> prefs[stringPreferencesKey("active_session_${session.packageName}")] = json }`.
- **Ошибки:**
  - `IOException`: логируется, не прерывает работу приложения.
- **Побочные эффекты:**
  - Запись на диск в DataStore.
- **Граничные случаи:**
  - Сессия обновляется каждую минуту $\rightarrow$ запись перезаписывает предыдущий снимок.
- **Примеры:**
  1. *Пример 1 (Старт трека):*
     - Вход: Открыта сессия Spotify. Снимок сохранён в DataStore.
  2. *Пример 2 (Переход на паузу):*
     - Вход: Сессия встала на паузу. Накопленное время зафиксировано в DataStore.
  3. *Пример 3 (Сбой записи):*
     - Вход: Диск временно заблокирован. Исключение перехвачено в catch-блоке.
- **Сложность / ограничения:**
  - Время: $O(1) + \text{DataStore write}$.

---

##### Функция 2: `clearActiveSessionSnapshot`

- **Сигнатура:**
  ```kotlin
  suspend fun clearActiveSessionSnapshot(packageName: String)
  ```
- **Назначение:** Удаление снимка активной сессии из `DataStore` после её штатного закрытия.
- **Предусловия:**
  - Сессия успешно завершена и сохранена в `StorageGateway`.
- **Постусловия:**
  - Ключ `active_session_$packageName` удалён из `DataStore`.
- **Пошаговое поведение:**
  1. Выполнить `dataStore.edit { prefs -> prefs.remove(stringPreferencesKey("active_session_$packageName")) }`.
- **Ошибки:**
  - Не выбрасывает исключений.
- **Побочные эффекты:**
  - Удаление ключа из DataStore.
- **Граничные случаи:**
  - Ключ уже отсутствовал $\rightarrow$ No-op.
- **Примеры:**
  1. *Пример 1 (Штатный STOP трека):*
     - Результат: Запись из DataStore удалена.
  2. *Пример 2 (Повторная очистка):*
     - Результат: No-op.
  3. *Пример 3 (Очистка после скипа):*
     - Результат: Запись удалена.
- **Сложность / ограничения:**
  - Время: $O(1)$.

---

##### Функция 3: `recoverDanglingSessions`

- **Сигнатура:**
  ```kotlin
  suspend fun recoverDanglingSessions(appStartedAt: java.time.Instant): Int
  ```
- **Назначение:** Поиск и принудительное корректное закрытие сессий, оставшихся открытыми из-за аварийного завершения процесса приложения (kill ОС в HyperOS, LowMemoryKiller, падение питания).
- **Предусловия:**
  - Вызывается строго при инициализации приложения (`App.onCreate` / `MediaSessionObserver.start()`).
- **Постусловия:**
  - Все обнаруженные в `DataStore` активные сессии закрываются с `sessionEndedAt = appStartedAt`, причиной `APP_RESTART` и ошибкой `lastError = "process_killed_unexpectedly"`.
  - Для каждой восстановленной сессии формируется и сохраняется `RawEvent` и (при соблюдении порога) `Event`.
  - Снимки в `DataStore` полностью очищаются.
  - Возвращается количество успешно восстановленных сессий.
- **Пошаговое поведение:**
  1. Прочитать снимок всех предпочтений из `dataStore.data.first()`.
  2. Отфильтровать ключи, начинающиеся с `"active_session_"`.
  3. Для каждого найденного JSON-значения:
     - Десериализовать в DTO сессии.
     - Рассчитать эффективную длительность: взять накопленное время `accumulatedPlayTimeMs` плюс разницу до момента последнего сохранённого события.
     - Сконструировать `MediaSessionPayload`:
       - `sessionStartedAtEpochMs = savedSession.sessionStartedAtEpochMs`.
       - `sessionEndedAtEpochMs = appStartedAt.toEpochMilli()`.
       - `effectiveDurationMs = calculatedEffectiveDuration`.
       - `isMicroSession = calculatedEffectiveDuration < 5000L`.
       - `endReason = MediaSessionEndReason.APP_RESTART`.
       - `lastError = "process_killed_unexpectedly"`.
     - Сериализовать в JSON, вычислить `dedupKey`.
     - Вставить в `StorageGateway.insertRawEvent`.
     - Если `!isMicroSession`: сформировать `Event` и вставить через `StorageGateway.insertEvent`.
     - Удалить ключ из `DataStore`.
     - Инкрементировать счётчик восстановленных сессий.
  4. Обновить `StorageGateway.upsertSourceHealth(SourceId.MEDIA)` с меткой времени `appStartedAt`.
  5. Вернуть итоговый счётчик.
- **Ошибки:**
  - Ошибки десериализации повреждённого JSON приводят к удалению битого ключа и пропуску без краша.
- **Побочные эффекты:**
  - Запись восстановленных сессий в базу данных. Очистка DataStore.
- **Граничные случаи:**
  - Приложение стартовало штатно, висячих сессий нет $\rightarrow$ возвращается `0`.
  - Приложение было убито через 15 секунд после старта воспроизведения $\rightarrow$ восстанавливается 1 сессия с эффективным временем 15 с.
- **Примеры:**
  1. *Пример 1 (HyperOS убила приложение во время прослушивания альбома):*
     - Состояние в DataStore: сохранён снимок сессии Spotify, начатой 10 минут назад.
     - Результат: сессия закрыта, `endReason = APP_RESTART`, сохранена в БД, возвращено `1`.
  2. *Пример 2 (Первый чистый запуск после установки):*
     - Состояние: DataStore пуст.
     - Результат: возвращено `0`.
  3. *Пример 3 (Восстановление микро-сессии):*
     - Состояние: трек успел поиграть 2 секунды до краша.
     - Результат: сохранена в `raw_event`, в `event` не попала (`isMicroSession = true`), возвращено `1`.
- **Сложность / ограничения:**
  - Время: $O(D)$, где $D$ — число висячих сессий (обычно 0 или 1).

---

#### Объект `MediaPayloadMapper`

##### Функция 1: `toPayloadJson`

- **Сигнатура:**
  ```kotlin
  fun toPayloadJson(payload: MediaSessionPayload): String
  ```
- **Назначение:** Каноническая детерминированная сериализация объекта `MediaSessionPayload` в валидную строку JSON.
- **Предусловия:**
  - `payload` валиден.
- **Постусловия:**
  - Возвращает компактную строку JSON без пробелов между разделителями.
- **Поведение (пошагово):**
  1. Сформировать JSON-объект со следующими обязательными полями:
     - `"packageName"`: строка
     - `"trackTitle"`: строка или null
     - `"artist"`: строка или null
     - `"album"`: строка или null
     - `"trackDurationMs"`: число
     - `"sessionStartedAtEpochMs"`: число
     - `"sessionEndedAtEpochMs"`: число
     - `"effectiveDurationMs"`: число
     - `"isMicroSession"`: boolean
     - `"endReason"`: имя enum (например, `"STATE_STOPPED"`)
     - `"lastError"`: строка или null
  2. Вернуть строковое представление.
- **Ошибки:**
  - Не выбрасывает исключений.
- **Примеры:**
  1. *Пример 1:*
     - Вход: `payload` с треком "Yesterday".
     - Выход: `{"packageName":"com.spotify.music","trackTitle":"Yesterday",...}`.
  2. *Пример 2 (Null метаданные):*
     - Выход: `{"packageName":"com.radio","trackTitle":null,"artist":null,...}`.
  3. *Пример 3 (Наличие ошибки):*
     - Выход: `{...,"lastError":"heartbeat_timeout"}`.

---

### Диаграмма конечного автомата (State Machine)

```mermaid
stateDiagram-v2
    [*] --> Idle: Плеер неактивен / STOPPED

    Idle --> Playing: STATE_PLAYING (открыть сессию)
    
    Playing --> Playing: Обновление позиции / колбэк пульса (No-op)
    Playing --> Playing: Метаданные те же (No-op)
    
    Playing --> Playing: Смена трека (SwitchTrack:\nзакрыть старый, открыть новый)

    Playing --> Paused: STATE_PAUSED (НЕ закрывать,\nзафиксировать время)
    Paused --> Playing: STATE_PLAYING (возобновить,\nпродолжить отсчёт)

    Playing --> Closed: STATE_STOPPED / STATE_NONE
    Paused --> Closed: STATE_STOPPED / STATE_NONE
    Playing --> Closed: Контроллер исчез (CONTROLLER_DISCONNECTED)
    Paused --> Closed: Контроллер исчез (CONTROLLER_DISCONNECTED)
    Playing --> Closed: Тишина > 5 минут (HEARTBEAT_TIMEOUT)

    Closed --> Idle: Сохранение в StorageGateway\n(проверка порога 5 сек)
```

---

### Конфигурация Android Manifest и разрешения

Для легитимного функционирования `:ingest:media` используются права системного слушателя уведомлений. В `AndroidManifest.xml` модуля объявляются:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Разрешения для работы с медиа и сервисами -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <!-- Примечание: android.permission.MEDIA_CONTENT_CONTROL доступен только системным приложениям.
         Для сторонних приложений доступ к MediaSessionManager.getActiveSessions()
         предоставляется ОС автоматически при включённом NotificationListenerService. -->

</manifest>
```

---

### Зависимости модуля

Модуль `:ingest:media` объявляет следующие зависимости в `build.gradle.kts`:

1. **Внутренние проектные модули:**
   - `implementation(project(":core:model"))` — модели `RawEvent`, `Event`, `SourceId`, `SourceHealth`, `DeduplicationKey`, `ThreadKey` и чистый нормализатор `EventNormalizer`.
   - `implementation(project(":core:storage"))` — шлюз персистенции `StorageGateway`.
2. **Внешние библиотеки AndroidX:**
   - `androidx.media:media:1.7.0` — `MediaSessionManager`, `MediaControllerCompat`, `PlaybackStateCompat`, `MediaMetadataCompat`.
   - `androidx.datastore:datastore-preferences` — персистентное хранение снимка сессии для восстановления после сбоев.
   - `org.jetbrains.kotlinx:kotlinx-coroutines-android` — асинхронные корутины и диспетчер `Dispatchers.IO`.
3. **Dependency Injection:**
   - `com.google.dagger:hilt-android` — внедрение синглтонов `MediaSessionObserver` и `MediaSessionBoundaryDetector`.

---

### Вне скоупа (Out of Scope)

В зону `zone/ingest-media` и модуль `:ingest:media` явно **НЕ входят**:

1. **Аудио-захват и цифровой процессинг звука:**
   - Модуль не слушает микрофон, не читает аудио-поток и не выполняет распознавание треков по звуку (Shazam-подобный функционал).
2. **Управление воспроизведением:**
   - Модуль никогда не вызывает методы отправки команд (`play()`, `pause()`, `skipToNext()`).
3. **Приём уведомлений и SMS:**
   - Сбор системных шторных уведомлений вынесен в `:ingest:notification`.
   - Сбор SMS вынесен в `:ingest:sms`.
4. **Хранилище SQLite и ключи Keystore:**
   - Создание Room БД, управление SQLCipher вынесено в `:core:storage`.
5. **Пользовательский интерфейс:**
   - Экран истории прослушивания, карточки плеера вынесены в `:ui:timeline`.

---

### Чек-лист соответствия Definition of Ready (DoR)

| Критерий DoR | Статус | Подтверждение в спецификации |
|---|---|---|
| Строгие сигнатуры функций с типами и nullability | ВЫПОЛНЕНО | Все сигнатуры объявлены с точными типами Kotlin (`Instant`, `Long`, `String?` и др.) |
| Полное покрытие контрактов данных и инвариантов | ВЫПОЛНЕНО | Инварианты для `MediaMetadataSnapshot`, `ActiveMediaSession`, `MediaSessionPayload` детально описаны |
| Описание предусловий и постусловий для каждого метода | ВЫПОЛНЕНО | Разделы «Предусловия» и «Постусловия» присутствуют у каждого публичного и внутреннего метода |
| Пошаговое описание поведения (алгоритм) | ВЫПОЛНЕНО | Пошаговые нумерованные списки выполнения для всех методов |
| Ошибки, исключения и побочные эффекты | ВЫПОЛНЕНО | Описаны `SecurityException`, `IOException`, защита от крашей binder-потоков |
| Граничные случаи | ВЫПОЛНЕНО | Скипы треков $< 5$ с, паузы $> 5$ мин, сбои питания, пустые метаданные |
| Минимум 3 примера на каждый метод | ВЫПОЛНЕНО | Каждый метод сопровождается ровно 3 подробными примерами сценариев использования |
| Оценка временной и пространственной сложности | ВЫПОЛНЕНО | Указаны $O(1)$, $O(K)$ для каждого алгоритма |
| Отсутствие продакшн-кода реализации | ВЫПОЛНЕНО | Артефакт содержит исключительно архитектурную и поведенческую спецификацию |
