# Architecture — Notification Pipeline Constructor
**TASK_ID:** ARCH-001  
**Status:** DRAFT  
**Date:** 2026-09-26  
**Stack:** Android minSdk 26 / targetSdk 35 · Kotlin · Compose · Coroutines · Gradle KTS + version catalog + build-logic/ · AGP 8.x · JDK 17 · Room + SQLCipher + Android Keystore

---

## 1. Карта модулей

### 1.1 Таблица модулей

| Модуль | Gradle-плагин | Тип | Публичный API (интерфейсы / data-классы) | Главные классы |
|---|---|---|---|---|
| `:core:model` | `kotlin-jvm` (convention) | pure-JVM lib | `RawEvent`, `Event`, `SourceHealth`, `SourceId`, `EmbeddingRef`, `DeduplicationKey` | — только data-классы и value-классы |
| `:core:storage` | `kotlin-jvm` + Room KSP | pure-JVM lib | `RawEventDao`, `EventDao`, `SourceHealthDao`, `StorageGateway` | `AppDatabase`, `StorageGatewayImpl`, `SqlCipherSupportFactoryProvider` |
| `:ingest:notification` | `android-library` (convention) | Android lib | `NotificationIngestService`, `NotificationContract` | `PipelineNotificationListenerService`, `NotificationMapper`, `IngestWatchdog` |
| `:ingest:sms` | `android-library` (convention) | Android lib | `SmsIngestController`, `SmsContract` | `SmsBroadcastReceiver`, `SmsPoller`, `SmsMapper` |
| `:ingest:media` | `android-library` (convention) | Android lib | `MediaSessionContract`, `MediaSessionObserver` | `MediaControllerCompat` wrapper, `MediaSessionBoundaryDetector` |
| `:ui:timeline` | `android-library` (convention) | Android lib | `TimelineViewModel`, `TimelineUiState`, `EventUiModel` | `TimelineScreen`, `TimelineItemRow` |
| `:app` | `android-application` | App | — | `App`, `MainActivity`, `AppComponent` (Hilt), `AppNotificationChannels` |

### 1.2 Граф зависимостей

```
:app
 ├── :ingest:notification
 ├── :ingest:sms
 ├── :ingest:media
 └── :ui:timeline
       └── :core:storage
             └── :core:model
:ingest:notification ──► :core:storage ──► :core:model
:ingest:sms          ──► :core:storage
:ingest:media        ──► :core:storage
```

> **Правило**: `:core:model` — листовой модуль. Никто не зависит вниз от него.  
> `:core:storage` не знает ни о каком `ingest`-модуле.  
> `ingest`-модули не зависят друг от друга.

---

## 2. Зоны и их границы

### zone/core-model

**Входит:** `RawEvent`, `Event`, `SourceHealth`, `SourceId` (value class), `DeduplicationKey` (value class), `EmbeddingRef`, `ThreadKey` (value class), `Lang` (enum/value class).  
**Границы:** Нет Android-зависимостей. Не содержит логики персистенции. Не знает о Room-аннотациях (они в `:core:storage` в виде entity-классов, которые *реализуют* модельные интерфейсы или копируют поля).  
**Запрещено:** Context, android.*, coroutines Flow из этого модуля.

---

### zone/core-storage

**Входит:** Room entities (`RawEventEntity`, `EventEntity`, `SourceHealthEntity`), DAO-интерфейсы, `AppDatabase`, `StorageGateway`, `StorageGatewayImpl`, `SqlCipherSupportFactoryProvider`, миграции.  
**Границы:** Знает о `:core:model` (для маппинга entity → model и обратно). Не знает об ingest-логике. Выставляет реактивный API через `Flow<List<Event>>`.  
**SQLCipher:** Ключ извлекается через Android Keystore исключительно в `SqlCipherSupportFactoryProvider`; из `RoomDatabase.Builder` передаётся `SupportSQLiteOpenHelper.Factory`. Смена ключа — отдельная операция (фаза 1+).

---

### zone/ingest-notification

**Входит:** `PipelineNotificationListenerService` (extends `NotificationListenerService`), `NotificationMapper`, `IngestWatchdog`, `NotificationContract`.  
**Границы:** Получает `StatusBarNotification`; через `StorageGateway` пишет `RawEvent`. Не знает об SMS или Media. Не знает о UI.  
**Жизненный цикл:** Сервис — системный; стартует/стопится ОС. `IngestWatchdog` следит за связью с сервисом через `isNotificationListenerConnected` API.

---

### zone/ingest-sms

**Входит:** `SmsBroadcastReceiver`, `SmsPoller` (ContentProvider-based polling для устройств без `SMS_RECEIVED` broadcast), `SmsMapper`, `SmsContract`.  
**Границы:** Получает `Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)`; пишет `RawEvent` через `StorageGateway`.  
**Примечание:** `SmsPoller` активируется только если broadcast не получен за N минут — алерт через `WorkManager`.

---

### zone/ingest-media

**Входит:** `MediaSessionObserver`, `MediaSessionBoundaryDetector`, `MediaSessionContract`.  
**Границы:** Использует `MediaSessionManager` (API 21+); получает `MediaController` callback'и. Определяет «сессию» по правилам (см. §6). Пишет `RawEvent` типа `SOURCE_MEDIA` через `StorageGateway`.  
**Запрещено:** Прямой доступ к аудио-потоку. Только метаданные и статус воспроизведения.

---

### zone/app-lifecycle

**Входит:** `App` (Application), `MainActivity`, Hilt-компоненты, `AppNotificationChannels`, `BootCompletedReceiver`.  
**Границы:** Инициализирует DI-граф, регистрирует каналы уведомлений, запускает `IngestWatchdog` при старте приложения. Единственная зона, знающая обо всех остальных.  
**Запрещено:** Бизнес-логика в `Application.onCreate()` — только инициализация контейнера.

---

### zone/ui-timeline

**Входит:** `TimelineScreen`, `TimelineViewModel`, `TimelineUiState`, `EventUiModel`, `TimelineItemRow`, `TimelinePagingSource`.  
**Границы:** Читает из `StorageGateway` через `Flow`; не знает о деталях ingest. Трансформирует `Event` в `EventUiModel` внутри ViewModel.  
**Запрещено:** Прямой доступ к Room DAO. Любая запись в БД.

---

## 3. Межзонные контракты

### 3.1 Каталог named types

```kotlin
// ── zone/core-model ──────────────────────────────────────────────────
@JvmInline value class SourceId(val value: String)
@JvmInline value class ThreadKey(val value: String)
@JvmInline value class DeduplicationKey(val value: String) // SHA-256(source+packageName+payloadHash)
@JvmInline value class EmbeddingRef(val vectorId: String)

enum class Lang { RU, EN, UNK }

data class RawEvent(
    val id: Long = 0,
    val source: SourceId,
    val packageName: String,
    val receivedAt: Instant,
    val payloadJson: String,   // immutable after write
    val hash: DeduplicationKey // computed before insert
)

data class Event(
    val id: Long = 0,
    val rawId: Long,
    val ts: Instant,
    val title: String,
    val text: String,
    val normalizedText: String,
    val lang: Lang,
    val threadKey: ThreadKey?,
    val isUpdateOf: Long?,      // nullable FK → Event.id
    val embeddingRef: EmbeddingRef?
)

data class SourceHealth(
    val source: SourceId,       // PK
    val lastEventAt: Instant?,
    val events24h: Int,
    val lastError: String?
)

// ── zone/core-storage → все потребители ──────────────────────────────
interface StorageGateway {
    // Ingest-сторона (suspend = single coroutine, не блокирует Main)
    suspend fun insertRawEvent(event: RawEvent): Long          // returns generated id
    suspend fun insertEvent(event: Event): Long
    suspend fun upsertSourceHealth(health: SourceHealth)
    suspend fun findDuplicate(key: DeduplicationKey): Long?    // null = нет дубля

    // UI-сторона (Flow = горячий поток из Room)
    fun observeEvents(limit: Int): Flow<List<Event>>
    fun observeSourceHealth(): Flow<List<SourceHealth>>
}

// ── zone/ingest-notification → zone/core-storage ─────────────────────
data class NotificationContract(
    val statusBarNotification: Any, // android.service.notification.StatusBarNotification
    val receivedAt: Instant
)
// Маппер превращает NotificationContract → RawEvent перед вызовом StorageGateway

// ── zone/ingest-sms → zone/core-storage ──────────────────────────────
data class SmsContract(
    val originAddress: String,
    val body: String,
    val timestampMillis: Long
)

// ── zone/ingest-media → zone/core-storage ────────────────────────────
data class MediaSessionContract(
    val packageName: String,
    val trackTitle: String?,
    val artist: String?,
    val sessionStartedAt: Instant,
    val sessionEndedAt: Instant?   // null = сессия ещё открыта
)

// ── zone/ui-timeline ←─ zone/core-storage ────────────────────────────
data class EventUiModel(
    val id: Long,
    val displayTitle: String,
    val displayText: String,
    val timeLabel: String,         // отформатированное время
    val sourceIcon: Int,           // drawable res id
    val isUpdate: Boolean
)

sealed interface TimelineUiState {
    data object Loading : TimelineUiState
    data class Success(val events: List<EventUiModel>) : TimelineUiState
    data class Error(val message: String) : TimelineUiState
}
```

### 3.2 Направления и механизмы

| От | До | Тип | Механизм |
|---|---|---|---|
| `ingest:notification` | `core:storage` | push | `suspend fun insertRawEvent()` в `IO`-диспетчере |
| `ingest:sms` | `core:storage` | push | `suspend fun insertRawEvent()` в `IO`-диспетчере |
| `ingest:media` | `core:storage` | push | `suspend fun insertRawEvent()` в `IO`-диспетчере |
| `core:storage` | `ui:timeline` | pull/reactive | `Flow<List<Event>>` (Room LiveData under the hood) |
| `app` | `ingest:notification` | lifecycle | `ComponentName` binding, `IngestWatchdog` |
| `app` | `ingest:sms` | lifecycle | Manifest receiver registration |
| `app` | `ingest:media` | lifecycle | `MediaSessionObserver.start()` / `.stop()` |

---

## 4. Горячий путь: onNotificationPosted → БД, p95 < 50 ms

### 4.1 Последовательность

```
[OS-binder thread]
  PipelineNotificationListenerService.onNotificationPosted(sbn)
    │  ← СИНХРОННО (< 1 ms): null-guard, extract packageName + key fields
    │
    ▼
  launch(Dispatchers.IO)  ← граница: уходим с binder thread
    │
    ├─ NotificationMapper.toRawEvent(sbn)            ~ 1-2 ms  (JSON serialize)
    │
    ├─ StorageGateway.findDuplicate(deduplicationKey) ~ 2-5 ms  (Room SELECT, indexed)
    │    └─ если дубль → emit SourceHealth.update, return
    │
    ├─ StorageGateway.insertRawEvent(rawEvent)        ~ 3-8 ms  (Room INSERT)
    │
    ├─ EventNormalizer.normalize(rawEvent)            ~ 1-3 ms  (text cleanup, lang detect)
    │
    └─ StorageGateway.insertEvent(event)              ~ 3-8 ms  (Room INSERT)
         └─ StorageGateway.upsertSourceHealth(...)   ~ 1-3 ms  (Room UPSERT)

Итого worst-case: ~22 ms → p95 запас > 2x до 50 ms
```

### 4.2 Что синхронно (на binder thread)

- Null-guard `sbn == null → return`
- Извлечение `packageName`, `id`, `tag` (простые поля, без JSON)
- `launch(Dispatchers.IO)` — только запуск корутины (< 0.1 ms)

### 4.3 Что в корутине (IO dispatcher)

- Сериализация payload в JSON
- Вычисление `DeduplicationKey` (SHA-256)
- Все операции с Room (SELECT dedup + INSERT raw + INSERT event + UPSERT health)
- Нормализация текста

### 4.4 Гарантии

- `Dispatchers.IO` — пул потоков (default 64), не блокирует `Main`
- Room-операции в одной транзакции (`@Transaction`) для `insertRawEvent` + `insertEvent` — атомарность
- `SQLiteDatabase.setMaximumSize` не ограничивается; WAL-режим (`journalMode = WAL`) — параллельные reads не блокируют write
- `onNotificationPosted` возвращается немедленно; OS не ждёт завершения

---

## 5. Структура файлов проекта

```
vibrant-hawking/
│
├── .sdd/
│   └── architecture.md               ← этот файл
│
├── build-logic/                       ← convention plugins
│   ├── build.gradle.kts
│   └── src/main/kotlin/
│       ├── AndroidLibraryConventionPlugin.kt
│       ├── AndroidApplicationConventionPlugin.kt
│       ├── KotlinJvmConventionPlugin.kt
│       ├── HiltConventionPlugin.kt
│       └── RoomConventionPlugin.kt
│
├── gradle/
│   ├── libs.versions.toml             ← version catalog
│   └── wrapper/
│
├── settings.gradle.kts
├── build.gradle.kts                   ← root, no code
│
├── core/
│   ├── model/
│   │   ├── build.gradle.kts           ← plugin: kotlin-jvm-convention
│   │   └── src/main/kotlin/com/example/npc/core/model/
│   │       ├── RawEvent.kt
│   │       ├── Event.kt
│   │       ├── SourceHealth.kt
│   │       ├── SourceId.kt
│   │       ├── ThreadKey.kt
│   │       ├── DeduplicationKey.kt
│   │       ├── EmbeddingRef.kt
│   │       └── Lang.kt
│   │
│   └── storage/
│       ├── build.gradle.kts           ← plugin: kotlin-jvm-convention + room-convention
│       └── src/main/kotlin/com/example/npc/core/storage/
│           ├── AppDatabase.kt
│           ├── StorageGateway.kt
│           ├── StorageGatewayImpl.kt
│           ├── SqlCipherSupportFactoryProvider.kt
│           ├── dao/
│           │   ├── RawEventDao.kt
│           │   ├── EventDao.kt
│           │   └── SourceHealthDao.kt
│           ├── entity/
│           │   ├── RawEventEntity.kt
│           │   ├── EventEntity.kt
│           │   └── SourceHealthEntity.kt
│           ├── mapper/
│           │   ├── RawEventMapper.kt
│           │   ├── EventMapper.kt
│           │   └── SourceHealthMapper.kt
│           └── migration/
│               └── Migration_1_2.kt   ← пример
│
├── ingest/
│   ├── notification/
│   │   ├── build.gradle.kts           ← plugin: android-library-convention + hilt
│   │   └── src/main/
│   │       ├── AndroidManifest.xml    ← <service android:name=".PipelineNotificationListenerService"/>
│   │       └── kotlin/com/example/npc/ingest/notification/
│   │           ├── PipelineNotificationListenerService.kt
│   │           ├── NotificationMapper.kt
│   │           ├── IngestWatchdog.kt
│   │           ├── NotificationContract.kt
│   │           └── di/
│   │               └── NotificationIngestModule.kt
│   │
│   ├── sms/
│   │   ├── build.gradle.kts
│   │   └── src/main/
│   │       ├── AndroidManifest.xml
│   │       └── kotlin/com/example/npc/ingest/sms/
│   │           ├── SmsBroadcastReceiver.kt
│   │           ├── SmsPoller.kt
│   │           ├── SmsMapper.kt
│   │           ├── SmsContract.kt
│   │           └── di/
│   │               └── SmsIngestModule.kt
│   │
│   └── media/
│       ├── build.gradle.kts
│       └── src/main/
│           ├── AndroidManifest.xml
│           └── kotlin/com/example/npc/ingest/media/
│               ├── MediaSessionObserver.kt
│               ├── MediaSessionBoundaryDetector.kt
│               ├── MediaSessionContract.kt
│               └── di/
│                   └── MediaIngestModule.kt
│
├── ui/
│   └── timeline/
│       ├── build.gradle.kts           ← plugin: android-library-convention + hilt + compose
│       └── src/main/kotlin/com/example/npc/ui/timeline/
│           ├── TimelineScreen.kt
│           ├── TimelineViewModel.kt
│           ├── TimelineUiState.kt
│           ├── EventUiModel.kt
│           ├── TimelineItemRow.kt
│           ├── TimelinePagingSource.kt
│           └── di/
│               └── TimelineModule.kt
│
└── app/
    ├── build.gradle.kts               ← plugin: android-application-convention + hilt
    ├── src/main/
    │   ├── AndroidManifest.xml
    │   └── kotlin/com/example/npc/app/
    │       ├── App.kt
    │       ├── MainActivity.kt
    │       ├── AppNotificationChannels.kt
    │       ├── BootCompletedReceiver.kt
    │       └── di/
    │           └── AppModule.kt
    └── src/test/
        └── kotlin/com/example/npc/app/
            └── SmokeTest.kt
```

---

## 6. Архитектурные решения с обоснованием

### 6.1 DI — Hilt

**Решение:** Hilt (Dagger-based).  
**Обоснование:**
- `NotificationListenerService` и `BroadcastReceiver` требуют `@AndroidEntryPoint` — Hilt поддерживает оба out-of-box; Koin и ручной DI требуют дополнительного бойлерплейта для инъекции в сервисы, запускаемые ОС.
- Compile-time граф (vs Koin runtime) — ошибки DI обнаруживаются на сборке, а не в продакшне.
- `@Singleton` scope на `StorageGateway` гарантирует один инстанс `AppDatabase` на процесс — критично для SQLCipher (один ключ, один connection pool).
- Hilt — официальная рекомендация Google для Android; меньше surprises при обновлении AGP.

**Компромисс:** Hilt увеличивает время full build из-за kapt/KSP. Митигация: KSP вместо kapt везде где возможно.

---

### 6.2 SQLCipher — SupportSQLiteOpenHelper.Factory

**Решение:** `net.zetetic:android-database-sqlcipher` через `SupportFactory(passphrase)`.  
**Обоснование:**
- Room принимает кастомный `SupportSQLiteOpenHelper.Factory` — это официальная точка интеграции без форка Room.
- Ключ (32 байта) генерируется при первом запуске, хранится в `EncryptedSharedPreferences` (Jetpack Security), защищённых `AndroidKeyStore`. Passphrase передаётся как `ByteArray` и немедленно `fill(0)` после передачи в `SupportFactory`.
- **Версия:** SQLCipher for Android ≥ 4.5.x (совместимость с SQLite 3.39+, NDK r25+). AGP 8.x / minSdk 26 — нет конфликтов с ABI.
- **WAL-режим**: включён через `RoomDatabase.Builder.setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)` — не конфликтует с шифрованием.

**Риск совместимости:** см. §7.2.

---

### 6.3 Deduplication-ключ

**Решение:** `DeduplicationKey = SHA-256(source + packageName + payloadHash)` где `payloadHash` — SHA-256 от нормализованного `payloadJson` (без unstable полей: `postTime`, `when`).  
**Обоснование:**
- Уведомления с одинаковым содержимым, приходящие несколько раз (пересоздание sbn), должны быть идемпотентными.
- `payloadHash` исключает нестабильные timestamp-поля через whitelist-сериализацию: в JSON включаются только `title`, `text`, `subText`, `bigText`.
- `findDuplicate(key)` — indexed SELECT, выполняется до первого INSERT → минимальный overhead.
- Хранится как `TEXT` (hex-encoded) в `raw_event.hash` с `UNIQUE` constraint → БД сама является защитой второго уровня (если вызов `findDuplicate` гоняется параллельно).

---

### 6.4 Watchdog ↔ Listener

**Решение:** `IngestWatchdog` — отдельный компонент в `ingest:notification`, инициализируется при старте `App`.  
**Механизм:**
1. Watchdog вызывает `NotificationManagerCompat.getEnabledListenerPackages(ctx)` каждые 15 минут (WorkManager `PeriodicWorkRequest` с `FLEX` = 5 min).
2. Если пакет не в списке (пермиссия отозвана) → показывает `Notification` с deep-link на системные настройки.
3. Если пакет в списке, но `onListenerConnected` не вызывался > 30 минут (timestamp в `DataStore`) → `requestRebind(ComponentName)`.
4. `PipelineNotificationListenerService.onListenerConnected/Disconnected` обновляют `DataStore<Preferences>` атомарно.
5. `SourceHealth.lastError` обновляется при `onListenerDisconnected`.

**Watchdog НЕ использует Alarm:** WorkManager — правильный выбор для фоновой проверки (см. §6.7).

---

### 6.5 source_health thread-safety

**Решение:** Все операции `SourceHealth` проходят через `StorageGateway.upsertSourceHealth()` — единственный путь записи.  
**Обоснование:**
- Room + SQLite WAL гарантирует, что concurrent writes сериализуются на уровне SQLite write lock.
- `upsertSourceHealth` использует `INSERT OR REPLACE` (Room `@Upsert`) — атомарная операция, нет TOCTOU.
- `events24h` вычисляется не инкрементально (счётчик без lock), а через `SELECT COUNT(*) WHERE ts > now - 24h` — детерминировано, не требует mutex на стороне Kotlin.
- Несколько ingest-источников могут вызывать `upsertSourceHealth` параллельно для **разных** `SourceId` — нет конкуренции. Для одного `SourceId` параллельные вызовы безопасны благодаря `INSERT OR REPLACE`.

---

### 6.6 MediaSession — границы сессии

**Решение:** Сессия определяется как промежуток между `PlaybackState.STATE_PLAYING` → первым `STATE_NONE` / `STATE_STOPPED` или отсутствием heartbeat > 5 минут.  
**MediaSessionBoundaryDetector правила:**
1. `STATE_PLAYING` → открыть сессию (если не открыта): запомнить `sessionStartedAt`.
2. `STATE_PAUSED` → НЕ закрывать сессию (пользователь может продолжить).
3. `STATE_STOPPED` / `STATE_NONE` / контроллер отсоединился → закрыть сессию.
4. Heartbeat: `MediaController.Callback.onPlaybackStateChanged` должен приходить каждые ≤ 5 мин; если нет — закрыть сессию с пометкой `lastError = "heartbeat_timeout"`.
5. Сессия записывается в `RawEvent` только при закрытии (полный `MediaSessionContract` с `sessionEndedAt`).

**Пробел:** см. §7.3.

---

### 6.7 Алерт — WorkManager vs AlarmManager

**Решение:** `WorkManager` для всех периодических проверок (Watchdog, SmsPoller).  
**Обоснование:**
- `AlarmManager.setExactAndAllowWhileIdle` требует `SCHEDULE_EXACT_ALARM` permission (Android 12+) — лишняя политика для фоновой задачи без жёстких timing-требований.
- WorkManager интегрируется с Doze, App Standby, Battery Optimization автоматически — не нужно самостоятельно обрабатывать `ACTION_DEVICE_IDLE_MODE_CHANGED`.
- Минимальный интервал WorkManager — 15 мин (ограничение ОС), что достаточно для health-check.
- Watchdog alert (уведомление пользователю об отозванном разрешении) — `OneTimeWorkRequest` с `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)`.

**Исключение:** Если в фазе 1+ появится требование алерта с точностью < 5 мин — рассмотреть `setExactAndAllowWhileIdle` только для этого кейса.

---

## 7. Риски

### 7.1 HyperOS убивает Listener

**Описание:** Xiaomi HyperOS (и MIUI до него) агрессивно убивает `NotificationListenerService` при сворачивании приложения, несмотря на системный статус сервиса. Симптом: `onListenerDisconnected` → `onListenerConnected` цикл, или полное молчание сервиса.  
**Митигация:**
- `IngestWatchdog` детектирует разрыв (см. §6.4) и вызывает `requestRebind`.
- В `AndroidManifest.xml` сервис объявлен с `android:foregroundServiceType` не требуется (это системный сервис), но `App` держит `ForegroundService` (тип `dataSync`) с уведомлением — это сигнализирует HyperOS о том, что процесс «активен».
- Документировать для QA: тест на HyperOS-устройстве с отключённым автостартом.
- `SourceHealth.lastError` фиксирует факт разрыва; UI показывает предупреждение.  
**Неизвестно:** Эффективность `requestRebind` на конкретных версиях HyperOS — требует ручного тестирования.

---

### 7.2 SQLCipher совместимость

**Описание:** SQLCipher for Android имеет зависимость от NDK и конкретных версий OpenSSL. AGP 8.x меняет настройки ABI splits и packaging — возможны конфликты `libsqlcipher.so` и `libcrypto.so`.  
**Митигация:**
- Использовать `net.zetetic:android-database-sqlcipher:4.5.4` (последняя стабильная на момент архитектуры) — только ABI: `arm64-v8a`, `x86_64` (minSdk 26 исключает `armeabi-v7a` из необходимых).
- `packagingOptions { jniLibs { pickFirst "lib/*/libcrypto.so" } }` в `app/build.gradle.kts` — устранить конфликт если другая библиотека тащит OpenSSL.
- Smoke-test в CI: открыть зашифрованную БД, выполнить SELECT, убедиться в отсутствии `UnsatisfiedLinkError`.  
**Открытый вопрос:** Совместимость с Android 15 (API 35) изменёнными политиками загрузки нативных библиотек — проверить после выхода финального AGP 8.x для API 35.

---

### 7.3 MediaSession пробелы

**Описание:** `MediaController` может не уведомить о конце сессии если:
- Приложение-плеер упало без вызова `release()`.
- Процесс плеера убит ОС.
- Bluetooth-устройство отключилось без `STATE_STOPPED`.  
**Митигация:**
- Heartbeat timeout (§6.6, п. 4): закрыть сессию через 5 минут отсутствия событий.
- `MediaSessionManager.OnActiveSessionsChangedListener`: если сессия исчезла из активных — немедленно закрыть.
- `sessionEndedAt = null` в `MediaSessionContract` означает «незакрытая сессия»; при следующем старте приложения незакрытые сессии «закрываются» с `sessionEndedAt = App.startedAt`.  
**Риск:** Пробел в записи при очень коротких треках (< 1 с) — `STATE_PLAYING` → `STATE_STOPPED` быстрее чем обрабатывается callback. Приоритет: низкий.

---

### 7.4 Гонки в ingest-pipeline

**Описание:** Два события одного источника могут прийти одновременно (burst notification). Параллельные `launch(IO)` могут оба пройти `findDuplicate` до того как первый сделает INSERT.  
**Митигация:**
- `UNIQUE` constraint на `raw_event.hash` → второй INSERT получит `SQLiteConstraintException` (Room выбросит `SQLiteConstraintException`), который перехватывается в `StorageGatewayImpl` и тихо игнорируется (dedup на уровне БД).
- `SourceHealth.upsert` — атомарный `INSERT OR REPLACE`, гонки безопасны.
- Для `insertEvent` — выполняется только после успешного `insertRawEvent`; если `insertRawEvent` проиграл гонку — `insertEvent` не вызывается.  
**Открытый вопрос:** Нужен ли `Channel<RawNotificationPayload>` (single-consumer) вместо параллельных корутин для строгой упорядоченности? Компромисс: упорядоченность vs throughput. Рекомендация фазы 0: параллельные корутины + UNIQUE constraint достаточно.

---

## Открытые вопросы (для команды)

1. **HyperOS `requestRebind`**: нужен ли fallback через `AccessibilityService`? (повышает риск отклонения в Google Play)
2. **SQLCipher API 35**: подтвердить совместимость `libcrypto.so` с новой политикой загрузки нативных библиотек Android 15.
3. **`Channel` vs parallel coroutines**: нужна ли строгая упорядоченность событий одного источника? Если да — ввести `actor` (deprecated) или `Channel(UNLIMITED)` с единственным consumer.
4. **`EventNormalizer`** (нормализация текста, lang detect): выделить в `:core:nlp` модуль или оставить в `:core:storage`? Рекомендация: `:core:nlp` (фаза 0.5), чтобы не тащить ML-зависимости в storage.
5. **Embedding**: `embeddingRef` в `Event` указывает на внешнее векторное хранилище — его контракт (фаза 1+) влияет на схему БД фазы 0. Нужно зарезервировать колонку `embedding_ref TEXT NULL`.

---

*Документ актуален для Фазы 0. Фазы 1+ (embedding pipeline, NLP module, export, cloud sync) рассматриваются отдельно.*
