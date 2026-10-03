# Спецификация: zone/app-lifecycle

## Модуль: :app   Зона: zone/app-lifecycle   Версия спеки: v1   Статус: DRAFT

---

### Назначение

Модуль `:app` является главным исполняемым модулем Android-приложения (`com.android.application`) и реализует зону `zone/app-lifecycle`. Он отвечает за точку входа в процесс ОС Android, инициализацию контейнера внедрения зависимостей (Hilt), первичную конфигурацию криптографического окружения базы данных (SQLCipher), создание системных каналов уведомлений, планирование фоновых регламентных задач (`WorkManager`), обработку системных широковещательных событий перезагрузки устройства и обновления пакета, оркестрацию пошагового мастера первичной настройки (онбординга) с адаптацией под агрессивные механизмы энергосбережения Xiaomi HyperOS / MIUI (Poco M7), а также координацию запуска UI-экранов.

Ключевые функциональные обязанности зоны `zone/app-lifecycle`:
1. **Application-класс (`App`)**:
   - Точка входа в процесс приложения, аннотированная `@HiltAndroidApp`.
   - Загрузка нативной библиотеки шифрования `System.loadLibrary("sqlcipher")` в фазе холодного старта процесса до любых обращений к хранилищу (согласно ADR-007).
   - Создание системных каналов уведомлений приложения (`AppNotificationChannels`).
   - Инициализация фабрики рабочих задач `WorkManager` через интерфейс `Configuration.Provider` для поддержки `@HiltWorker`.
   - Первичное планирование периодических задач: сторожевого контроля листенера уведомлений (`NotificationWatchdogWorker`) и детектора длительного отсутствия событий (`AbsenceAlertWorker`).
2. **Контейнер внедрения зависимостей (`AppModule`)**:
   - Построение графа зависимостей Hilt для уровня приложения (`SingletonComponent`).
   - Предоставление синглтон-экземпляров `AppDatabase` и `StorageGateway` (гарантия единственного пула подключений к зашифрованной SQLite БД, ADR-001, ADR-007).
   - Предоставление системных сервисов: `Context`, `WorkManager`, `NotificationManagerCompat`, `PowerManager`, `PackageManager`.
3. **Системные каналы уведомлений (`AppNotificationChannels`)**:
   - Регистрация канала `"npc_service"` (`IMPORTANCE_LOW`, silent) для foreground-сервиса повышенной живучести (`LiveWatchdogForegroundService`).
   - Регистрация канала `"npc_alerts"` (`IMPORTANCE_HIGH`, sound + heads-up) для критических предупреждений сторожа и детектора отсутствия событий.
4. **Обработчик системной загрузки (`BootCompletedReceiver`)**:
   - Наследник `BroadcastReceiver` с аннотацией `@AndroidEntryPoint`.
   - Реакция на широковещательные интенты `Intent.ACTION_BOOT_COMPLETED` и `Intent.ACTION_MY_PACKAGE_REPLACED`.
   - Гарантированное перепланирование воркеров `WorkManager` (`ExistingPeriodicWorkPolicy.KEEP`).
   - Поднятие сервиса повышенной живучести при активной пользовательской настройке (ADR-003).
5. **Детектор длительного отсутствия событий (`AbsenceAlertWorker`, DoD критерий 9)**:
   - Периодическая проверка времени последнего зарегистрированного события (`lastEventAt`) через `StorageGateway`.
   - Если промежуток между текущим временем и последним событием превышает настраиваемый порог $N$ часов (по умолчанию 6 часов) — генерация высокоприоритетного системного уведомления в канал `"npc_alerts"`.
   - Автоматическая отмена активного предупреждения при возобновлении потока событий.
6. **Пошаговый онбординг с поддержкой HyperOS / Poco M7 (`OnboardingFlow`)**:
   - Обеспечение выполнения критериев живучести на целевом физическом устройстве Poco M7 под управлением HyperOS (ADR-002, ADR-003, ADR-008).
   - Пошаговая валидация и навигация пользователя:
     - **Шаг 1:** Разрешение на доступ к уведомлениям (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`).
     - **Шаг 2:** Разрешение автозапуска HyperOS / MIUI (`miui.intent.action.OP_AUTO_START` с безопасным fallback).
     - **Шаг 3:** Отключение оптимизации батареи (`Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
     - **Шаг 4:** Запрос критических runtime-разрешений (`POST_NOTIFICATIONS`, `READ_SMS`, `RECEIVE_SMS`, `READ_CALENDAR`).
     - **Шаг 5 (Руководство):** Инструкция по закреплению процесса в меню «Недавние» (Lock in Recents).
7. **Главная Activity (`MainActivity`)**:
   - Единственная Activity приложения (`singleActivity` паттерн), хост для Jetpack Compose.
   - Маршрутизация: отображение `OnboardingScreen` при неполных разрешениях, либо `TimelineScreen` при полной готовности системы.

Зона `zone/app-lifecycle` **НЕ содержит** бизнес-логики парсинга и фильтрации уведомлений (делегировано `:ingest:notification`), **НЕ осуществляет** опрос SMS (делегировано `:ingest:sms`), **НЕ отслеживает** медиа-сессии (делегировано `:ingest:media`), **НЕ выполняет** манипуляций с DOM/UI таймлайна (делегировано `:ui:timeline`) и **НЕ содержит** DAO-запросов напрямую в обход `StorageGateway`.

---

### Архитектурное окружение и структура пакетов

```
com.example.npc.app/
├── App.kt                                      # Application класс, Hilt root, loadLibrary, WorkManager Configuration.Provider
├── MainActivity.kt                             # Single Activity хост Compose, проверка готовности онбординга
├── AppNotificationChannels.kt                  # Декларация и регистрация каналов npc_service и npc_alerts
├── receiver/
│   └── BootCompletedReceiver.kt                # Приём BOOT_COMPLETED и MY_PACKAGE_REPLACED, решедулинг WorkManager
├── worker/
│   ├── AbsenceAlertWorker.kt                   # CoroutineWorker проверки затишья событий (DoD 9)
│   └── AbsenceAlertScheduler.kt                # Менеджер шедулинга AbsenceAlertWorker в WorkManager
├── onboarding/
│   ├── OnboardingStep.kt                       # Перечисление шагов настройки системы
│   ├── OnboardingState.kt                      # Состояние готовности разрешений и настроек
│   ├── OnboardingManager.kt                    # Логика проверки статуса шагов и генерации Intent-переходов
│   └── HyperOsIntegration.kt                   # Утилита безопасного вызова интентов автозапуска Xiaomi/HyperOS
└── di/
    ├── AppModule.kt                            # Hilt SingletonComponent провайдеры (Storage, DB, System Services)
    └── WorkManagerInitializer.kt               # Фабрика кастомной инициализации WorkManager без ContentProvider
```

---

### Типы данных, состояния и DTO (Data Structures & State Models)

Все классы моделей жизненного цикла размещаются в пакете `com.example.npc.app.onboarding` и `com.example.npc.app.worker`.

#### 1. `OnboardingStep`

```kotlin
package com.example.npc.app.onboarding

enum class OnboardingStep {
    NOTIFICATION_LISTENER_ACCESS,
    HYPEROS_AUTOSTART,
    BATTERY_OPTIMIZATION_EXCLUSION,
    RUNTIME_PERMISSIONS,
    RECENTS_LOCK_GUIDE
}
```

- **Назначение:** Идентификация конкретного этапа первичной настройки и калибровки живучести приложения на устройстве.
- **Значения:**
  - `NOTIFICATION_LISTENER_ACCESS` — предоставление системного доступа к чтению уведомлений (`NotificationListenerService`).
  - `HYPEROS_AUTOSTART` — включение разрешения на автозапуск в панели управления безопасностью Xiaomi HyperOS / MIUI.
  - `BATTERY_OPTIMIZATION_EXCLUSION` — перевод приложения в режим «Без ограничений» (отключение Doze/App Standby ограничений питания).
  - `RUNTIME_PERMISSIONS` — выдача опасных runtime-разрешений (`POST_NOTIFICATIONS`, `RECEIVE_SMS`, `READ_SMS`, `READ_CALENDAR`).
  - `RECENTS_LOCK_GUIDE` — визуальная памятка пользователю по фиксации приложения замком в шторке недавних задач HyperOS.

---

#### 2. `OnboardingState`

```kotlin
package com.example.npc.app.onboarding

data class OnboardingState(
    val isNotificationListenerGranted: Boolean,
    val isBatteryOptimizationIgnored: Boolean,
    val isPostNotificationsGranted: Boolean,
    val isSmsPermissionsGranted: Boolean,
    val isCalendarPermissionGranted: Boolean,
    val isHyperOsDevice: Boolean,
    val isHyperOsAutostartAcknowledged: Boolean,
    val isRecentsLockAcknowledged: Boolean
) {
    val isMandatorySetupComplete: Boolean
        get() = isNotificationListenerGranted &&
                isBatteryOptimizationIgnored &&
                isPostNotificationsGranted &&
                (!isHyperOsDevice || isHyperOsAutostartAcknowledged)
}
```

- **Назначение:** Неизменяемый снимок текущего состояния системных привилегий и пользовательских подтверждений.
- **Поля:**
  - `isNotificationListenerGranted: Boolean` — активно ли разрешение в `NotificationManagerCompat.getEnabledListenerPackages`.
  - `isBatteryOptimizationIgnored: Boolean` — входит ли приложение в белый список энергосбережения `PowerManager.isIgnoringBatteryOptimizations`.
  - `isPostNotificationsGranted: Boolean` — активно ли разрешение `POST_NOTIFICATIONS` (для Android 13+ / API 33+; всегда `true` для API < 33).
  - `isSmsPermissionsGranted: Boolean` — активны ли одновременно `READ_SMS` и `RECEIVE_SMS`.
  - `isCalendarPermissionGranted: Boolean` — активно ли разрешение `READ_CALENDAR`.
  - `isHyperOsDevice: Boolean` — идентифицировано ли устройство как Xiaomi / Poco под управлением HyperOS или MIUI.
  - `isHyperOsAutostartAcknowledged: Boolean` — подтвердил ли пользователь переход в экран автозапуска HyperOS.
  - `isRecentsLockAcknowledged: Boolean` — ознакомился ли пользователь с инструкцией закрепления в меню «Недавние».
- **Инварианты:**
  - `isMandatorySetupComplete` возвращает `true` только в случае, когда базовые функции приёма уведомлений и живучести гарантированы системой.

---

#### 3. `AbsenceAlertConfig`

```kotlin
package com.example.npc.app.worker

data class AbsenceAlertConfig(
    val thresholdHours: Long = 6L,
    val checkIntervalMinutes: Long = 30L,
    val flexIntervalMinutes: Long = 10L,
    val suppressAlertWindowHours: Long = 4L
) {
    init {
        require(thresholdHours >= 1L) { "Absence threshold must be at least 1 hour" }
        require(checkIntervalMinutes >= 15L) { "WorkManager periodic interval must be at least 15 minutes" }
        require(flexIntervalMinutes in 5L..checkIntervalMinutes) { "Flex interval must be between 5 min and checkInterval" }
        require(suppressAlertWindowHours >= 1L) { "Alert suppression window must be at least 1 hour" }
    }
}
```

- **Назначение:** Параметры конфигурации фонового сторожа тишины событий (DoD критерий 9).
- **Поля:**
  - `thresholdHours: Long` — интервал времени без единого события, по истечении которого генерируется предупреждение (по умолчанию 6 часов).
  - `checkIntervalMinutes: Long` — периодичность пробуждения `WorkManager` (по умолчанию 30 минут).
  - `flexIntervalMinutes: Long` — окно гибкости выполнения задачи `WorkManager` (10 минут).
  - `suppressAlertWindowHours: Long` — защитный интервал анти-спама (повторное уведомление не отправляется чаще раза в 4 часа, если ситуация не изменилась).

---

### Публичный API и компоненты зоны

В зону `zone/app-lifecycle` входят следующие ключевые классы и интерфейсы:

```kotlin
// 1. Точка входа приложения
@HiltAndroidApp
class App : Application(), androidx.work.Configuration.Provider {
    @Inject lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory
    @Inject lateinit var ingestWatchdog: IngestWatchdog
    @Inject lateinit var absenceAlertScheduler: AbsenceAlertScheduler

    override fun onCreate()
    override val workManagerConfiguration: androidx.work.Configuration
}

// 2. Декларатор каналов уведомлений
object AppNotificationChannels {
    const val CHANNEL_ID_SERVICE = "npc_service"
    const val CHANNEL_ID_ALERTS = "npc_alerts"

    fun createAll(context: Context)
}

// 3. DI-модуль верхнего уровня
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideSqlCipherSupportFactoryProvider(@ApplicationContext context: Context): SqlCipherSupportFactoryProvider

    @Provides @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context, factoryProvider: SqlCipherSupportFactoryProvider): AppDatabase

    @Provides @Singleton
    fun provideStorageGateway(database: AppDatabase): StorageGateway

    @Provides @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager

    @Provides @Singleton
    fun provideNotificationManagerCompat(@ApplicationContext context: Context): NotificationManagerCompat

    @Provides @Singleton
    fun providePowerManager(@ApplicationContext context: Context): PowerManager
}

// 4. Системный BroadcastReceiver перезагрузки и обновления
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    @Inject lateinit var ingestWatchdog: IngestWatchdog
    @Inject lateinit var absenceAlertScheduler: AbsenceAlertScheduler

    override fun onReceive(context: Context, intent: Intent?)
}

// 5. CoroutineWorker мониторинга затишья событий (DoD 9)
@HiltWorker
class AbsenceAlertWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val storageGateway: StorageGateway,
    private val notificationManager: NotificationManagerCompat
) : CoroutineWorker(context, workerParams) {
    override suspend fun doWork(): Result
    fun checkAbsence(lastEventEpochMs: Long?, currentEpochMs: Long, thresholdMs: Long): Boolean
    fun postAbsenceNotification(silenceDurationHours: Long)
    fun cancelAbsenceNotification()
}

// 6. Менеджер периодического шедулинга AbsenceAlertWorker
@Singleton
class AbsenceAlertScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    fun schedulePeriodicCheck(config: AbsenceAlertConfig = AbsenceAlertConfig())
    fun cancelPeriodicCheck()
}

// 7. Менеджер пошагового онбординга
@Singleton
class OnboardingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManagerCompat,
    private val powerManager: PowerManager
) {
    fun getOnboardingState(): OnboardingState
    fun isNotificationListenerGranted(): Boolean
    fun isBatteryOptimizationIgnored(): Boolean
    fun isHyperOsDevice(): Boolean
    fun createNotificationListenerSettingsIntent(): Intent
    fun createHyperOsAutostartIntent(): Intent
    fun createBatteryOptimizationIntent(): Intent
    fun getRequiredRuntimePermissions(): Array<String>
}

// 8. Утилита интеграции с MIUI / HyperOS
object HyperOsIntegration {
    fun isHyperOsOrMiui(): Boolean
    fun getAutostartIntent(context: Context): Intent
}

// 9. Главная Activity хоста
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var onboardingManager: OnboardingManager

    override fun onCreate(savedInstanceState: Bundle?)
}
```

---

### Детальная спецификация компонентов (по стандарту DoR)

#### 1. Класс `App` (Application)

- **Полное имя класса:** `com.example.npc.app.App`
- **Базовый класс:** `android.app.Application()`
- **Интерфейсы:** `androidx.work.Configuration.Provider`
- **Аннотации:** `@HiltAndroidApp`
- **Поля:**
  - `@Inject lateinit var workerFactory: androidx.hilt.work.HiltWorkerFactory` — фабрика создания воркеров Hilt.
  - `@Inject lateinit var ingestWatchdog: IngestWatchdog` — менеджер сторожа листенера уведомлений.
  - `@Inject lateinit var absenceAlertScheduler: AbsenceAlertScheduler` — планировщик детектора отсутствия событий.
  - `private var isSqlCipherLoaded: Boolean = false` — внутренний флаг успешной загрузки нативной библиотеки.

##### 1.1 Метод `onCreate`

- **Сигнатура:**
  ```kotlin
  override fun onCreate()
  ```
- **Предусловия:**
  - Процесс операционной системы инициализирован. Вызывается Android Runtime на главном потоке (`main thread`) приложения перед запуском любых Activity, Service или Receiver (за исключением ContentProvider, если они не отключены).
- **Постусловия:**
  - Нативная библиотека `libsqlcipher.so` успешно загружена в память JVM через вызов `System.loadLibrary("sqlcipher")`. Флаг `isSqlCipherLoaded == true`.
  - Системные каналы уведомлений (`npc_service`, `npc_alerts`) созданы в `NotificationManager`.
  - Периодическая задача `NotificationWatchdogWorker` зарегистрирована в `WorkManager` с политикой `ExistingPeriodicWorkPolicy.KEEP`.
  - Периодическая задача `AbsenceAlertWorker` зарегистрирована в `WorkManager` с политикой `ExistingPeriodicWorkPolicy.KEEP`.
- **Пошаговое поведение:**
  1. Вызвать родительскую реализацию: `super.onCreate()`.
  2. Загрузить нативную библиотеку SQLCipher:
     ```kotlin
     try {
         System.loadLibrary("sqlcipher")
         isSqlCipherLoaded = true
     } catch (e: UnsatisfiedLinkError) {
         Log.e("NPC_App", "Fatal: Failed to load libsqlcipher.so", e)
         throw e
     }
     ```
  3. Инициализировать каналы уведомлений:
     ```kotlin
     AppNotificationChannels.createAll(this)
     ```
  4. Запланировать периодические регламентные воркеры через внедрённые координаторы:
     ```kotlin
     ingestWatchdog.schedulePeriodicCheck()
     absenceAlertScheduler.schedulePeriodicCheck()
     ```
- **Ошибки и обработка исключений:**
  - `UnsatisfiedLinkError`: если APK не содержит библиотеку `libsqlcipher.so` для ABI текущего устройства (`arm64-v8a`, `x86_64`). Исключение логируется как фатальное и пробрасывается дальше, так как продолжение работы приложения без зашифрованной базы данных невозможно.
  - `IllegalStateException`: при сбое создания каналов уведомлений на Android 8.0+.
- **Граничные случаи:**
  - Повторный запуск процесса после аварийного завершения: `ExistingPeriodicWorkPolicy.KEEP` предотвращает сброс существующих таймеров в WorkManager.
  - Устройство с кастомной прошивкой (HyperOS): каналы регистрируются корректно до старта системного листенера.
- **Примеры:**
  1. *Пример 1 (Холодный старт приложения пользователем):*
     - Вход: устройство Poco M7, первый запуск процесса.
     - Поведение: загрузка `libsqlcipher.so` успешна, каналы `npc_service` и `npc_alerts` созданы, `NotificationWatchdogWorker` и `AbsenceAlertWorker` поставлены в очередь WorkManager.
     - Выход: процесс переходит к запуску `MainActivity`.
  2. *Пример 2 (Старт процесса системой в фоне по Intent BOOT_COMPLETED):*
     - Вход: перезагрузка смартфона, интент `BOOT_COMPLETED`.
     - Поведение: `App.onCreate()` отрабатывает, библиотека загружена, каналы созданы, воркеры поставлены с флагом `KEEP` (дублирование исключено).
     - Выход: управление передаётся в `BootCompletedReceiver.onReceive`.
  3. *Пример 3 (Несовместимая архитектура процессора / отсутствие сошки):*
     - Вход: повреждённая сборка без `arm64-v8a`.
     - Поведение: `System.loadLibrary("sqlcipher")` выбрасывает `UnsatisfiedLinkError`.
     - Выход: краш процесса с понятным логом до попытки открытия SQLite БД.

##### 1.2 Свойство `workManagerConfiguration`

- **Сигнатура:**
  ```kotlin
  override val workManagerConfiguration: androidx.work.Configuration
  ```
- **Предусловия:**
  - В `AndroidManifest.xml` отключён стандартный `androidx.work.WorkManagerInitializer` (через `tools:node="remove"`).
- **Постусловия:**
  - Возвращает экземпляр `Configuration`, настроенный на использование `@Inject lateinit var workerFactory: HiltWorkerFactory`.
- **Пошаговое поведение:**
  1. Сконструировать `Configuration.Builder()`.
  2. Установить кастомную фабрику: `.setWorkerFactory(workerFactory)`.
  3. Сконфигурировать минимальный уровень логирования: `.setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)`.
  4. Вызвать `.build()` и вернуть результат.
- **Примеры:**
  1. *Пример 1 (Инициализация воркера с зависимостями):*
     - Вход: WorkManager инициирует создание `AbsenceAlertWorker`.
     - Выход: `HiltWorkerFactory` разрешает `@AssistedInject` конструктор с внедрением `StorageGateway` и `NotificationManagerCompat`.

---

#### 2. Компонент `AppNotificationChannels`

- **Полное имя класса:** `com.example.npc.app.AppNotificationChannels`
- **Тип:** Kotlin `object` (синглтон без внутреннего состояния)
- **Константы:**
  - `const val CHANNEL_ID_SERVICE = "npc_service"`
  - `const val CHANNEL_ID_ALERTS = "npc_alerts"`
  - `const val NOTIFICATION_ID_LIVE_FGS = 1001`
  - `const val NOTIFICATION_ID_ABSENCE_ALERT = 2001`
  - `const val NOTIFICATION_ID_WATCHDOG_PERMISSION = 3001`

##### 2.1 Метод `createAll`

- **Сигнатура:**
  ```kotlin
  fun createAll(context: Context)
  ```
- **Предусловия:**
  - `context != null`.
- **Постусловия:**
  - Для Android API $\ge 26$ (`Build.VERSION.SDK_INT >= Build.VERSION_CODES.O`) в системе зарегистрированы два канала уведомлений.
  - Для Android API $< 26$ метод завершается без вызовов API каналов (no-op).
- **Пошаговое поведение:**
  1. Проверить `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O`. Если `false` — выйти из метода.
  2. Получить системный сервис `NotificationManager` через `context.getSystemService(NotificationManager::class.java)`. Если `null` — выбросить `IllegalStateException`.
  3. Сформировать канал службы повышенной живучести:
     - ID: `"npc_service"`.
     - Name: `"NPC Background Service"` (строковый ресурс `R.string.channel_service_name`).
     - Importance: `NotificationManager.IMPORTANCE_LOW` (низкая важность: без всплывающих окон, без звука, не раздражает пользователя).
     - Description: `"Foreground service keeping notification ingestion active"` (ресурс `R.string.channel_service_desc`).
     - `setShowBadge(false)`.
     - `enableLights(false)`.
     - `enableVibration(false)`.
     - `setSound(null, null)`.
  4. Сформировать канал системных предупреждений:
     - ID: `"npc_alerts"`.
     - Name: `"NPC System Alerts"` (строковый ресурс `R.string.channel_alerts_name`).
     - Importance: `NotificationManager.IMPORTANCE_HIGH` (высокая важность: heads-up баннер, привлекающий внимание звук и вибрация).
     - Description: `"Critical alerts: missing permissions, watchdog issues, and event absence warnings"` (ресурс `R.string.channel_alerts_desc`).
     - `setShowBadge(true)`.
     - `enableLights(true)`.
     - `enableVibration(true)`.
  5. Передать список каналов `listOf(serviceChannel, alertsChannel)` в `notificationManager.createNotificationChannels(...)`.
- **Ошибки:**
  - `IllegalStateException`: если `NotificationManager` недоступен в контексте системы.
- **Граничные случаи:**
  - Повторный вызов метода (например, при каждом `App.onCreate`): метод идемпотентен, существующие каналы обновляются системой без сброса пользовательских настроек звука.
- **Примеры:**
  1. *Пример 1 (Запуск на Android 14 / HyperOS):*
     - Вход: `Build.VERSION.SDK_INT = 34`.
     - Выход: оба канала созданы в системе с правильными `importance` флагами.
  2. *Пример 2 (Вызов на эмуляторе Android 7.1 / API 25):*
     - Вход: `Build.VERSION.SDK_INT = 25`.
     - Выход: метод завершается без исключений, каналы не создаются.
  3. *Пример 3 (Обновление названия канала):*
     - Вход: пользователь сменил язык системы, повторный вызов `createAll`.
     - Выход: имена каналов обновлены в настройках ОС на локализованные.

---

#### 3. Модуль внедрения зависимостей `AppModule`

- **Полное имя класса:** `com.example.npc.app.di.AppModule`
- **Тип:** Kotlin `object`
- **Аннотации:** `@Module`, `@InstallIn(SingletonComponent::class)`
- **Назначение:** Конфигурация корневого графа Hilt для управления жизненным циклом синглтон-компонентов.

##### 3.1 Провайдер `provideSqlCipherSupportFactoryProvider`

- **Сигнатура:**
  ```kotlin
  @Provides
  @Singleton
  fun provideSqlCipherSupportFactoryProvider(
      @ApplicationContext context: Context
  ): SqlCipherSupportFactoryProvider
  ```
- **Предусловия:**
  - Передан валидный контекст приложения `@ApplicationContext`.
- **Постусловия:**
  - Возвращает синглтон-экземпляр `SqlCipherSupportFactoryProvider`, настроенный на файл ключа `db.key` в защищённом каталоге `context.filesDir`.
- **Примеры:**
  1. *Пример 1:*
     - Вход: Application context.
     - Выход: экземпляр `SqlCipherSupportFactoryProvider(keyStorageFile = File(context.filesDir, "db.key"))`.

##### 3.2 Провайдер `provideAppDatabase`

- **Сигнатура:**
  ```kotlin
  @Provides
  @Singleton
  fun provideAppDatabase(
      @ApplicationContext context: Context,
      factoryProvider: SqlCipherSupportFactoryProvider
  ): AppDatabase
  ```
- **Предусловия:**
  - `System.loadLibrary("sqlcipher")` успешно выполнен в `App.onCreate()`.
  - Android Keystore доступен для извлечения/генерации мастер-ключа.
- **Постусловия:**
  - Возвращает единственный синглтон-инстанс `AppDatabase`.
  - База данных сконфигурирована с именем `"npc_events.db"`.
  - К `RoomDatabase.Builder` подключена фабрика `openHelperFactory = factoryProvider.createOpenHelperFactory(passphrase)`.
  - `passphrase` немедленно зануляется через `factoryProvider.wipePassphrase(passphrase)`.
  - Включён режим WAL: `.setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)`.
- **Пошаговое поведение:**
  1. Получить 32-байтный ключ через `factoryProvider.getOrCreateDatabaseKey()`.
  2. Создать фабрику `openHelperFactory = factoryProvider.createOpenHelperFactory(passphrase)`.
  3. Немедленно вызвать `factoryProvider.wipePassphrase(passphrase)`.
  4. Собрать базу данных:
     ```kotlin
     Room.databaseBuilder(context, AppDatabase::class.java, "npc_events.db")
         .openHelperFactory(openHelperFactory)
         .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
         .fallbackToDestructiveMigration(false)
         .build()
     ```
- **Ошибки:**
  - `GeneralSecurityException`: ошибка AndroidKeyStore при извлечении ключа.
- **Примеры:**
  1. *Пример 1 (Корректная инициализация):*
     - Вход: валидный Keystore.
     - Выход: синглтон `AppDatabase`, готовый к параллельным чтению и записи.

##### 3.3 Провайдер `provideStorageGateway`

- **Сигнатура:**
  ```kotlin
  @Provides
  @Singleton
  fun provideStorageGateway(database: AppDatabase): StorageGateway
  ```
- **Постусловия:**
  - Возвращает экземпляр `StorageGatewayImpl(database.rawEventDao(), database.eventDao(), database.sourceHealthDao())`.
- **Примеры:**
  1. *Пример 1:*
     - Вход: `AppDatabase`.
     - Выход: интерфейс `StorageGateway` с областью видимости `@Singleton`.

##### 3.4 Провайдеры системных сервисов

- **Сигнатуры:**
  ```kotlin
  @Provides
  @Singleton
  fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
      WorkManager.getInstance(context)

  @Provides
  @Singleton
  fun provideNotificationManagerCompat(@ApplicationContext context: Context): NotificationManagerCompat =
      NotificationManagerCompat.from(context)

  @Provides
  @Singleton
  fun providePowerManager(@ApplicationContext context: Context): PowerManager =
      context.getSystemService(Context.POWER_SERVICE) as PowerManager
  ```
- **Постусловия:**
  - Предоставляют потокобезопасные системные адаптеры Android в граф Hilt.

---

#### 4. Компонент `BootCompletedReceiver`

- **Полное имя класса:** `com.example.npc.app.receiver.BootCompletedReceiver`
- **Базовый класс:** `android.content.BroadcastReceiver()`
- **Аннотации:** `@AndroidEntryPoint`
- **Зависимости:**
  - `@Inject lateinit var ingestWatchdog: IngestWatchdog`
  - `@Inject lateinit var absenceAlertScheduler: AbsenceAlertScheduler`
  - `@Inject lateinit var preferencesDataStore: DataStore<Preferences>`

##### 4.1 Метод `onReceive`

- **Сигнатура:**
  ```kotlin
  override fun onReceive(context: Context, intent: Intent?)
  ```
- **Предусловия:**
  - BroadcastReceiver зарегистрирован в `AndroidManifest.xml` с intent-filter `android.intent.action.BOOT_COMPLETED` и `android.intent.action.MY_PACKAGE_REPLACED`.
  - `intent != null`.
- **Постусловия:**
  - Проверено соответствие `intent.action`. Нецелевые интенты безопасно игнорируются.
  - В `WorkManager` подтверждена регистрация `NotificationWatchdogWorker` и `AbsenceAlertWorker` (политика `KEEP`).
  - Если в пользовательских настройках активен флаг `high_survivability_mode` (ADR-003) — инициирован запуск `LiveWatchdogForegroundService`.
  - Время выполнения метода на `main thread` составляет $< 100$ мс (предотвращение риска ANR на старте устройства).
- **Пошаговое поведение:**
  1. Проверить `intent?.action`:
     - Если action НЕ входит в набор `[Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED]` -> выйти из метода.
  2. Выполнить логирование старта: `Log.i("NPC_Boot", "System boot or package replacement detected: ${intent.action}")`.
  3. Запустить планирование регламентных воркеров:
     ```kotlin
     ingestWatchdog.schedulePeriodicCheck()
     absenceAlertScheduler.schedulePeriodicCheck()
     ```
  4. Проверить необходимость запуска FGS режима повышенной живучести:
     - Асинхронно или через блокирующий быстрый преф-ридер проверить настройку `KEY_HIGH_SURVIVABILITY_ENABLED`.
     - Если включено:
       ```kotlin
       val fgsIntent = Intent(context, LiveWatchdogForegroundService::class.java)
       ContextCompat.startForegroundService(context, fgsIntent)
       ```
- **Ошибки:**
  - `ForegroundServiceStartNotAllowedException` (Android 12+): может возникнуть, если система наложила временные ограничения. Обрабатывается в `try-catch` с записью ошибки в лог, так как `WorkManager` компенсирует запуск при первой возможности.
- **Граничные случаи:**
  - `intent == null` -> немедленный выход.
  - Обновление приложения без перезагрузки телефона (`ACTION_MY_PACKAGE_REPLACED`) -> воркеры и сервисы перезапускаются бесшовно.
- **Примеры:**
  1. *Пример 1 (Холодная перезагрузка Poco M7):*
     - Вход: `intent.action = Intent.ACTION_BOOT_COMPLETED`.
     - Поведение: `schedulePeriodicCheck()` вызывается для обоих координаторов.
     - Результат: периодические воркеры активны в WorkManager, процесс запущен.
  2. *Пример 2 (Обновление APK поверх установленного):*
     - Вход: `intent.action = Intent.ACTION_MY_PACKAGE_REPLACED`.
     - Результат: воркеры перепланированы с политикой `KEEP`, предотвращая засыпание листенера.
  3. *Пример 3 (Левый интент, отправленный сторонним приложением):*
     - Вход: `intent.action = "com.malicious.ACTION"`.
     - Результат: интент отброшен на первой строке проверки, никаких действий не выполнено.

---

#### 5. Компоненты мониторинга затишья `AbsenceAlertScheduler` и `AbsenceAlertWorker` (DoD критерий 9)

Компоненты реализуют требование DoD Фазы 0 (критерий 9): оповещение пользователя о том, что в пайплайн не поступало событий в течение $N$ часов, что свидетельствует о возможном скрытом убийстве системных слушателей энергосбережением HyperOS.

##### 5.1 Класс `AbsenceAlertScheduler`

- **Полное имя:** `com.example.npc.app.worker.AbsenceAlertScheduler`
- **Область видимости:** `@Singleton`
- **Зависимости:** `WorkManager`
- **Константы:**
  - `const val UNIQUE_WORK_NAME = "npc_absence_alert_work"`

###### Метод `schedulePeriodicCheck`
- **Сигнатура:**
  ```kotlin
  fun schedulePeriodicCheck(config: AbsenceAlertConfig = AbsenceAlertConfig())
  ```
- **Предусловия:**
  - `config` валиден (интервал $\ge 15$ минут).
- **Постусловия:**
  - В `WorkManager` зарегистрирован `PeriodicWorkRequest` для `AbsenceAlertWorker` с политикой `ExistingPeriodicWorkPolicy.KEEP`.
- **Пошаговое поведение:**
  1. Сформировать `Constraints`:
     - `setRequiresBatteryNotLow(false)` (мониторинг критичен даже при низком заряде).
     - `setRequiresDeviceIdle(false)` (проверка должна выполняться во время бодрствования).
  2. Создать `PeriodicWorkRequestBuilder<AbsenceAlertWorker>(config.checkIntervalMinutes, TimeUnit.MINUTES, config.flexIntervalMinutes, TimeUnit.MINUTES)`:
     - Добавить тег `"absence_monitor"`.
     - Установить `setConstraints(constraints)`.
  3. Вызвать:
     ```kotlin
     workManager.enqueueUniquePeriodicWork(
         UNIQUE_WORK_NAME,
         ExistingPeriodicWorkPolicy.KEEP,
         workRequest.build()
     )
     ```
- **Примеры:**
  1. *Пример 1 (Первичное планирование):*
     - Вход: вызов при старте приложения.
     - Выход: задача добавлена в очередь WorkManager с интервалом 30 минут.
  2. *Пример 2 (Повторный вызов):*
     - Вход: вызов при перезагрузке устройства.
     - Выход: благодаря `ExistingPeriodicWorkPolicy.KEEP` таймер существующей задачи не сбрасывается.

##### 5.2 Класс `AbsenceAlertWorker`

- **Полное имя:** `com.example.npc.app.worker.AbsenceAlertWorker`
- **Базовый класс:** `androidx.work.CoroutineWorker`
- **Аннотации:** `@HiltWorker`
- **Конструктор:** `@AssistedInject constructor(...)` с внедрением `StorageGateway`, `NotificationManagerCompat`.

###### Метод `doWork`
- **Сигнатура:**
  ```kotlin
  override suspend fun doWork(): Result
  ```
- **Постусловия:**
  - Проверено состояние последнего события из всех источников.
  - Если затишье превышает пороговое значение -> отправлено heads-up уведомление в канал `"npc_alerts"`.
  - Если события появились после предупреждения -> активное уведомление снято.
  - Возвращает `Result.success()`.
- **Пошаговое поведение:**
  1. Получить текущую временную метку: `val currentEpochMs = System.currentTimeMillis()`.
  2. Получить список состояний источников:
     - `val healthList = storageGateway.observeSourceHealth().first()` (чтение первого снимка `Flow`).
  3. Вычислить максимальную метку последнего события:
     - `val latestEventEpochMs = healthList.mapNotNull { it.lastEventAt?.toEpochMilli() }.maxOrNull()`
  4. Прочитать конфигурацию порога: `val thresholdMs = 6L * 3600L * 1000L` (6 часов).
  5. Проверить критерий отсутствия:
     - Если `latestEventEpochMs == null` (событий не было вообще с момента установки приложения):
       - Проверить время первого запуска приложения (хранится в SharedPreferences/DataStore). Если с момента установки прошло $> 6$ часов -> `postAbsenceNotification(silenceHours = 6)`.
     - Если `(currentEpochMs - latestEventEpochMs) > thresholdMs`:
       - Вычислить длительность затишья в часах: `val silenceHours = (currentEpochMs - latestEventEpochMs) / (3600L * 1000L)`.
       - Вызвать `postAbsenceNotification(silenceHours)`.
     - Иначе (`currentEpochMs - latestEventEpochMs <= thresholdMs`):
       - Вызвать `cancelAbsenceNotification()`.
  6. Вернуть `Result.success()`.
- **Ошибки:**
  - При возникновении исключений доступа к БД — вернуть `Result.retry()`.
- **Граничные случаи:**
  - Пользователь вручную перевёл время устройства вперёд -> корректно обнаруживается превышение дельты.
  - Свежеустановленное приложение (первые 30 минут) -> не показывать ложный алерт.
- **Примеры:**
  1. *Пример 1 (Штатная работа, события идут):*
     - Вход: `latestEventEpochMs = now - 15 минут`.
     - Выход: уведомление не генерируется, `Result.success()`.
  2. *Пример 2 (Сервис убит HyperOS 7 часов назад):*
     - Вход: `latestEventEpochMs = now - 7 часов`.
     - Поведение: обнаружено превышение порога в 6 часов, вызывается `postAbsenceNotification(7)`.
     - Выход: в шторке появляется высокоприоритетное уведомление: *"Внимание: нет новых событий уже 7 ч. Проверьте разрешения автозапуска."*.
  3. *Пример 3 (Пользователь открыл приложение, пришло новое уведомление):*
     - Вход: при следующем тике воркера `latestEventEpochMs = now - 2 минуты`.
     - Поведение: вызывается `cancelAbsenceNotification()`.
     - Выход: устаревшее предупреждение автоматически убирается из шторки.

###### Метод `postAbsenceNotification`
- **Сигнатура:**
  ```kotlin
  fun postAbsenceNotification(silenceDurationHours: Long)
  ```
- **Предусловия:**
  - Разрешение `POST_NOTIFICATIONS` активно.
- **Постусловия:**
  - В системный трей отправлено `Notification` с ID `NOTIFICATION_ID_ABSENCE_ALERT` в канал `CHANNEL_ID_ALERTS`.
  - Уведомление содержит `PendingIntent` на запуск `MainActivity`.

###### Метод `cancelAbsenceNotification`
- **Сигнатура:**
  ```kotlin
  fun cancelAbsenceNotification()
  ```
- **Постусловия:**
  - Вызван `notificationManager.cancel(AppNotificationChannels.NOTIFICATION_ID_ABSENCE_ALERT)`.

---

#### 6. Компоненты онбординга и адаптации HyperOS (`OnboardingManager`, `HyperOsIntegration`)

Обеспечивают пошаговый проход настроек для закрытия DoD-критериев 1 и 5 на физическом Poco M7 под управлением HyperOS (ADR-002, ADR-003, ADR-008).

##### 6.1 Утилита `HyperOsIntegration`

- **Полное имя:** `com.example.npc.app.onboarding.HyperOsIntegration`
- **Тип:** Kotlin `object`

###### Метод `isHyperOsOrMiui`
- **Сигнатура:**
  ```kotlin
  fun isHyperOsOrMiui(): Boolean
  ```
- **Постусловия:**
  - Возвращает `true`, если устройство произведено Xiaomi/Redmi/POCO, либо определены системные свойства MIUI/HyperOS (`ro.miui.ui.version.name`, `ro.mi.os.version.name`).
- **Пошаговое поведение:**
  1. Проверить `Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)`.
  2. Проверить системные свойства через вызов рефлексии `android.os.SystemProperties.get("ro.miui.ui.version.name")` и `"ro.mi.os.version.name"`.
  3. Если хотя бы один маркер не пуст -> вернуть `true`, иначе `false`.

###### Метод `getAutostartIntent`
- **Сигнатура:**
  ```kotlin
  fun getAutostartIntent(context: Context): Intent
  ```
- **Постусловия:**
  - Возвращает `Intent`, ведущий напрямую в меню «Автозапуск» (Autostart) HyperOS/MIUI Security Center.
  - Если компонент отсутствует в системе — возвращает безопасный fallback на системный экран настроек текущего приложения (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`).
- **Пошаговое поведение:**
  1. Сформировать список потенциальных Intent'ов автозапуска MIUI/HyperOS:
     - `Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))`
     - `Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT)`
     - `Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"))`
  2. Проверить каждый Intent через `context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)`.
  3. Если найден валидный обработчик — вернуть данный Intent с флагом `Intent.FLAG_ACTIVITY_NEW_TASK`.
  4. Если ни один не подошёл — сформировать fallback Intent:
     ```kotlin
     Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
         data = Uri.fromParts("package", context.packageName, null)
         flags = Intent.FLAG_ACTIVITY_NEW_TASK
     }
     ```
- **Примеры:**
  1. *Пример 1 (Физический Poco M7 с HyperOS):*
     - Выход: Intent на `com.miui.permcenter.autostart.AutoStartManagementActivity`.
  2. *Пример 2 (Google Pixel / Чистый Android):*
     - Выход: Fallback Intent на `ACTION_APPLICATION_DETAILS_SETTINGS`.

##### 6.2 Класс `OnboardingManager`

- **Полное имя:** `com.example.npc.app.onboarding.OnboardingManager`
- **Область видимости:** `@Singleton`
- **Зависимости:** `@ApplicationContext Context`, `NotificationManagerCompat`, `PowerManager`.

###### Метод `getOnboardingState`
- **Сигнатура:**
  ```kotlin
  fun getOnboardingState(): OnboardingState
  ```
- **Постусловия:**
  - Возвращает актуальный объект `OnboardingState` с опросом текущих системных разрешений.
- **Пошаговое поведение:**
  1. Проверить `isNotificationListenerGranted()`:
     - `NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)`
  2. Проверить `isBatteryOptimizationIgnored()`:
     - `powerManager.isIgnoringBatteryOptimizations(context.packageName)`
  3. Проверить `isPostNotificationsGranted()`:
     - Если `Build.VERSION.SDK_INT >= 33`: `ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PERMISSION_GRANTED`
     - Иначе: `true`.
  4. Проверить SMS permissions:
     - `checkSelfPermission(READ_SMS) == GRANTED && checkSelfPermission(RECEIVE_SMS) == GRANTED`
  5. Проверить календарь:
     - `checkSelfPermission(READ_CALENDAR) == GRANTED`
  6. Проверить флаг вендора через `HyperOsIntegration.isHyperOsOrMiui()`.
  7. Собрать и вернуть `OnboardingState`.

###### Методы создания Intent-переходов для шагов онбординга

- **Шаг 1: Доступ к слушателю уведомлений:**
  ```kotlin
  fun createNotificationListenerSettingsIntent(): Intent
  ```
  - Возвращает `Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)`.
  - Fallback: `Intent(Settings.ACTION_SETTINGS)`.

- **Шаг 2: Автозапуск HyperOS:**
  ```kotlin
  fun createHyperOsAutostartIntent(): Intent
  ```
  - Делегирует вызов в `HyperOsIntegration.getAutostartIntent(context)`.

- **Шаг 3: Энергосбережение без ограничений:**
  ```kotlin
  fun createBatteryOptimizationIntent(): Intent
  ```
  - Возвращает:
    ```kotlin
    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ```
  - Fallback: `Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)`.

- **Шаг 4: Список запрашиваемых runtime-разрешений:**
  ```kotlin
  fun getRequiredRuntimePermissions(): Array<String>
  ```
  - Возвращает массив разрешений:
    - Для API $\ge 33$: `[Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CALENDAR]`.
    - Для API $< 33$: `[Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CALENDAR]`.

---

#### 7. Компонент `MainActivity`

- **Полное имя:** `com.example.npc.app.MainActivity`
- **Базовый класс:** `androidx.activity.ComponentActivity`
- **Аннотации:** `@AndroidEntryPoint`
- **Зависимости:** `@Inject lateinit var onboardingManager: OnboardingManager`

##### Метод `onCreate`
- **Сигнатура:**
  ```kotlin
  override fun onCreate(savedInstanceState: Bundle?)
  ```
- **Предусловия:**
  - Процесс запущен, Hilt-граф разрешён.
- **Постусловия:**
  - Произведён вызов `setContent { ... }` Jetpack Compose.
  - Проверено состояние `val state = onboardingManager.getOnboardingState()`.
  - Маршрутизация UI:
    - Если `!state.isMandatorySetupComplete`: отображается граф экрана онбординга `OnboardingScreen(state)`.
    - Если `state.isMandatorySetupComplete`: отображается главный рабочий экран таймлайна `TimelineScreen()`.
- **Поведение при возврате в приложение (`onResume`):**
  - Обновление `onboardingState`: если пользователь вернулся из системных настроек и выдал разрешение — UI реактивно переключается на следующий шаг либо на `TimelineScreen`.

---

### Спецификация манифеста `AndroidManifest.xml` модуля `:app`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    package="com.example.npc.app">

    <!-- Системные разрешения Фазы 0 (согласно ADR-001) -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.READ_SMS" />
    <uses-permission android:name="android.permission.RECEIVE_SMS" />
    <uses-permission android:name="android.permission.READ_CALENDAR" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <!-- Разрешения Foreground Service (согласно ADR-003) -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />

    <application
        android:name=".App"
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.NPC">

        <!-- Отключение стандартного WorkManagerInitializer для кастомного HiltWorkerFactory -->
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>

        <!-- Главная Activity -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.NPC">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Системный приёмник перезагрузки и обновления -->
        <receiver
            android:name=".receiver.BootCompletedReceiver"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

---

### Конфигурация сборки модуля (`app/build.gradle.kts`)

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt) // или KSP для Hilt
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.example.npc.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.npc"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            // Согласно ADR-007: исключение дублей crypto без слепого pickFirst
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    implementation(project(":ingest:notification"))
    implementation(project(":ingest:sms"))
    implementation(project(":ingest:media"))
    implementation(project(":ui:timeline"))

    // AndroidX & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // WorkManager + Hilt integration
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    kapt(libs.androidx.hilt.compiler)

    // Hilt DI
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // SQLCipher (согласно ADR-007)
    implementation(libs.sqlcipher.android)

    // Compose UI
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
```

---

### Зависимости и правила изоляции

- **Входящие зависимости модуля `:app`:**
  - Имеет зависимости ко всем подмодулям проекта (`:core:model`, `:core:storage`, `:ingest:notification`, `:ingest:sms`, `:ingest:media`, `:ui:timeline`).
  - Является единственной сборочной вершиной графа.
- **Правила изоляции:**
  - Модуль `:app` **НЕ содержит** низкоуровневых алгоритмов парсинга `StatusBarNotification`, SMS PDU или метаданных медиасессий.
  - Модуль `:app` **НЕ содержит** собственных Room Entity и DAO (все они инкапсулированы в `:core:storage`).
  - Доступ к персистентным данным в Activity и Worker'ах осуществляется строго через интерфейс `StorageGateway`.

---

### Вне скоупа (Out of Scope)

В модуль `:app` и зону `zone/app-lifecycle` явно **НЕ входят**:

1. **Обработка и маппинг уведомлений:**
   - Чтение `NotificationExtrasData`, фильтрация спама, определение `ThreadKey` находятся в `:ingest:notification`.
2. **Низкоуровневое криптографическое хранение:**
   - Алгоритмы генерации AES-GCM ключа в `AndroidKeyStore` и работа с `SupportOpenHelperFactory` инкапсулированы в `:core:storage`.
3. **Прямое отображение списков событий:**
   - Спецификация элементов таймлайна, виртуализация списков, пагинация находятся в `:ui:timeline`.
4. **Машинное обучение и векторный поиск:**
   - Анализ текста, генерация эмбеддингов вынесены в будущие фазы развития системы.
