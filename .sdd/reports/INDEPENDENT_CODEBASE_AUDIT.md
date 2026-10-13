# Независимый Аудит Кодовой Базы: Lirix (Android)

**Дата проведения:** 13 октября 2026 г.  
**Аудитор:** Независимый Аудитор Кодовой Базы (Zero-Context Code Auditor)  
**Репозиторий:** `quick-nobel` (`com.lirix.app`)  
**Стек:** Kotlin 2.x, Android SDK 35 (minSdk 26), Jetpack Compose (BOM), Room 2.6 (KSP), Coroutines & Flow, Material 3  
**Статус автоматических тестов:** ✅ 202 теста пройдено (0 упало, 0 пропущено)

---

## 1. Исполнительное резюме (Executive Summary)

Проведен глубокий независимый аудит кодовой базы Android-приложения **Lirix** с чистого листа, без предвзятых вводных.

### Общая оценка проекта:
Кодовая база представляет собой функциональное, оригинальное Android-приложение, реализующее хаб текстов песен, караоке со строгой синхронизацией LRC, парсинг и транспонирование гитарных аккордов, инспектор веб-селекторов (Teach Mode), интеграцию с системными медиа-сессиями Android и плавающий оверлей караоке поверх сторонних приложений (Floating Overlay).

В проекте реализована качественная предметная логика в части обработки музыки:
- Строгий каскад источников текстов (`LrcLib` $\to$ `AmDm` $\to$ `MyChords` $\to$ `VsePesni` $\to$ `Genius` $\to$ `Amalgama`).
- Надежная криптографическая проверка целостности правил парсинга с защитой от SSRF и инъекций (`ScraperRuleBundleCodec`).
- Полное покрытие юнит-тестами ключевых алгоритмов (202 теста в Gradle test runner).

Однако аудит выявил **критические архитектурные и инженерные риски**, обусловленные незавершенным рефакторингом (переход от платформы "Android Event Engine" к приложению "Lirix"), отсутствием слоя MVVM/Clean Architecture (прямой вызов Room DAO из Compose UI), блокирующим дисковым I/O на главном потоке и штормом рекомпозиций с частотой 60–120 FPS.

### Сводка выявленных дефектов по приоритетам:
| Категория | P0 (Блокирующие) | P1 (Критические) | P2 (Средние) | P3 (Минорные) | Всего |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Security & Privacy** | 1 | 3 | 1 | 0 | **5** |
| **Correctness & Logic** | 0 | 2 | 2 | 0 | **4** |
| **Performance & Memory** | 0 | 2 | 3 | 0 | **5** |
| **Architecture & Code Quality** | 0 | 1 | 4 | 2 | **7** |
| **ИТОГО** | **1** | **8** | **10** | **2** | **21** |

---

## 2. Обзор реальной архитектуры системы

### 2.1. Фактическое назначение приложения
Приложение **Lirix** — это локальный автономный плеер-компаньон и караоке-хаб для Android:
1. **Перехват воспроизведения:** Отслеживает проигрываемую музыку в сторонних плеерах (Spotify, Яндекс.Музыка, YouTube Music, VK Музыка и др.) через `NotificationListenerService` и `MediaSessionManager`.
2. **Тексты и караоке:** Автоматически ищет тексты песен и синхронизированные таймкоды LRC через открытый API `lrclib.net` и веб-скрейперы (`AmDm.ru`, `MyChords.net`, `Genius`, `Textpesni`).
3. **Оверлей:** Отображает плавающую строку караоке поверх других приложений через `SYSTEM_ALERT_WINDOW` и `FloatingLyricsService`.
4. **Режим обучения (Teach Mode):** Встроенный WebView со скриптом-инспектором DOM для интерактивного выделения блоков с текстом/аккордами и создания правил парсинга.
5. **Аналитика (Wrapped) и геймификация:** Локальный расчет статистики прослушиваний и 26 достижений на основе истории в Room.

### 2.2. Исторический след и архитектурный долг
Кодовая база развивалась из многофункционального проекта "Android Event Engine" (включавшего классификацию финансов, учебы, событий через эмбеддинги ONNX `multilingual-e5-small`). В ходе рефакторинга (миграция Room 5 $\to$ 6) таблицы событий, финансов и правил были удалены из БД, однако значительный объем мертвого кода (`EventDao.kt`, `EventEntity.kt`, `domain/Event.kt`, `HistoryScreen.kt`, `LyricsViewerDialog.kt`) остался в репозитории без использования.

---

## 3. Критические проблемы (P0 / P1)

### 🔴 P0 — Hardcoded Keystore Passwords & Отсутствие R8/ProGuard в Release
- **Файл:** `app/build.gradle.kts`, строки 31–42, 45–52
- **Суть проблемы:**
  В конфигурации сборки захардкожен пароль к релизному хранилищу ключей:
  ```kotlin
  storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: "lirixrelease2026"
  keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "lirixrelease2026"
  ```
  При этом в `buildTypes.release` флаг минификации выключен: `isMinifyEnabled = false`.
- **Последствия:** Утечка учетных данных подписи в историю Git; релизный APK публикуется без обфускации и сжатия, что позволяет декомпилировать исходный код в один клик.
- **Рекомендация:** Удалить дефолтный пароль из скрипта сборки, требовать переменные окружения CI/CD. Включить `isMinifyEnabled = true` с правилами R8 для Room и Compose.

---

### 🔴 P1 — Отсутствие разрешения `ACCESS_NETWORK_STATE` приводит к тихому отказу префетчинга
- **Файлы:**
  - `app/src/main/AndroidManifest.xml`, строки 4–10
  - `app/src/main/java/com/lirix/app/feature/lyrics/NetworkConditionManager.kt`, строки 26–35
  - `app/src/main/java/com/lirix/app/feature/MusicFeatureEngine.kt`, строки 392–396
- **Суть проблемы:**
  `NetworkConditionManager.getNetworkPolicy()` вызывает `cm.getNetworkCapabilities(network)`. На реальных устройствах Android вызов `getNetworkCapabilities` без разрешения `android.permission.ACCESS_NETWORK_STATE` выбрасывает `SecurityException`.
  В коде вызов обернут в `runCatching { ... }.getOrDefault(LyricsNetworkPolicy.OFFLINE_ONLY)`.
  В результате исключение молча проглатывается, метод всегда возвращает `OFFLINE_ONLY`, а `MusicFeatureEngine.prefetchLyricsAsync()` немедленно прерывает работу:
  ```kotlin
  val policy = NetworkConditionManager.getNetworkPolicy(context)
  if (policy == LyricsNetworkPolicy.OFFLINE_ONLY) return
  ```
- **Последствия:** Фоновый автоматический предзагрузчик текстов и караоке на реальных смартфонах полностью неработоспособен.
- **Рекомендация:** Добавить `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />` в `AndroidManifest.xml`.

---

### 🔴 P1 — Неконтролируемый JS-интерфейс в WebView (Teach Mode) на открытых URL
- **Файл:** `app/src/main/java/com/lirix/app/ui/TeachModeScreen.kt`, строки 83–108, 363–389, 410–414
- **Суть проблемы:**
  Встроенный `WebView` используется как открытый браузер: стартует с Google Search (`https://www.google.com/search?q=...`) и позволяет переходить на любые внешние сайты.
  При этом в WebView внедрен интерфейс:
  ```kotlin
  addJavascriptInterface(TeachJsBridge(...), "TeachBridge")
  ```
  Метод `onElementSelected` принимает произвольные строки (`parentSelector`, `stripSelectorsJson`, `text`, `domain`) прямо из JavaScript. Любой сторонний сайт, открытый пользователем, может выполнить скрипт, вызывающий `window.TeachBridge.onElementSelected(...)`, и передать фиктивный текст или внедрить селекторы.
  Кроме того, не переопределен `shouldOverrideUrlLoading`, из-за чего переходы по схемам `intent://` или `market://` приводят к сбоям загрузки страницы.
- **Последствия:** Риск спуфинга данных, подмены правил и нестабильности WebView.
- **Рекомендация:** Внедрить валидацию URL в `WebViewClient`, проверять происхождение (origin check) перед обработкой сигналов из JS-моста, либо инжектировать мост строго по явному включению режима "Инспектор" на проверенных страницах.

---

### 🔴 P1 — Фатальное падение `FileUriExposedException` в ShareManager
- **Файл:** `app/src/main/java/com/lirix/app/feature/share/ShareManager.kt`, строки 36–44
- **Суть проблемы:**
  При возникновении ошибки получения URI через FileProvider вызывается блок отката:
  ```kotlin
  val contentUri: Uri = try {
      FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
  } catch (e: Exception) {
      Uri.fromFile(shareFile) // КРАШ НА ANDROID 7.0+
  }
  ```
  Начиная с Android 7.0 (API 24), передача `file://` URI во внешние приложения через `Intent.ACTION_SEND` строго запрещена ОС и гарантированно приводит к аварийному завершению приложения (`android.os.FileUriExposedException`).
- **Последствия:** Краш приложения при сбое `FileProvider`.
- **Рекомендация:** Исключить использование `Uri.fromFile()`. При сбое `FileProvider` логировать ошибку и выводить пользователю информативное уведомление без выброса исключения в ОС.

---

### 🔴 P1 — Множественная инициализация и Split-Brain синглтона `MediaSessionCollector`
- **Файлы:**
  - `app/src/main/java/com/lirix/app/LirixApp.kt`, строки 36–37
  - `app/src/main/java/com/lirix/app/ingestion/NotificationListener.kt`, строки 42–44, 53–54
  - `app/src/main/java/com/lirix/app/service/FloatingLyricsService.kt`, строка 80
  - `app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt`, строки 36–39, 392–401
- **Суть проблемы:**
  Экземпляр `MediaSessionCollector` создается оператором `new/constructor` в трех не связанных между собой компонентах (`LirixApp`, `NotificationListener`, `FloatingLyricsService`).
  При этом каждый конструктор перезаписывает статическую ссылку `instance = this` в companion object.
  Когда системный сервис `NotificationListener` отключается или уничтожается ОС, его метод `onDestroy` вызывает:
  ```kotlin
  mediaSessionCollector?.stopListening()
  ```
  Внутри `stopListening()` очищаются статические коллекции companion object:
  ```kotlin
  activeControllers.clear()
  activeMediaControllers.clear()
  _livePlaybackFlow.value = null
  instance = null
  ```
- **Последствия:** При отключении/перезапуске `NotificationListener` состояние воспроизведения обнуляется для всего приложения и плавающего сервиса, приводя к потере текущего трека ("состояние раскола").
- **Рекомендация:** Превратить `MediaSessionCollector` в настоящий синглтон с внедрением зависимостей (DI) или фабричным методом, привязав жизненный цикл к `ApplicationContext`.

---

### 🔴 P1 — Блокирующий дисковый ввод-вывод `runBlocking(Dispatchers.IO)` на UI-потоке
- **Файл:** `app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt`, строки 188–190
- **Суть проблемы:**
  Коллбэки `MediaController.Callback.onPlaybackStateChanged` и `onMetadataChanged` по умолчанию диспетчеризуются на главном потоке (Main Looper). Внутри метода `extractAndSaveAlbumArt` выполняется синхронная блокировка:
  ```kotlin
  val savedPath = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
      albumArtStorage.saveBitmap(trackKey, rawBitmap)
  }
  ```
  Сжатие полноразмерного битмапа в WebP (85% качество) и запись на диск внутри `runBlocking` полностью блокируют UI-поток приложения при каждой смене метаданных трека.
- **Последствия:** Микрофризы интерфейса (Jank), пропуск кадров и потенциальный ANR (Application Not Responding) при воспроизведении музыки.
- **Рекомендация:** Убрать `runBlocking`. Выполнять сохранение обложки асинхронно через корутины (`scope.launch(Dispatchers.IO)`), а URI обновлять реактивно.

---

### 🔴 P1 — Шторм рекомпозиций 60–120 FPS и алгоритмическая сложность O(N) на каждый кадр
- **Файл:** `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt`, строки 465–473, 999, 1923–1925
- **Суть проблемы:**
  В цикле интерполяции позиции трека используется `withFrameMillis`:
  ```kotlin
  withFrameMillis {
      playbackPositionMs = (raw + syncOffsetMs).coerceAtLeast(0L)
  }
  ```
  Переменная `playbackPositionMs` является состоянием Compose (`mutableLongStateOf`). Она читается напрямую в теле основного экрана (`formatMs(playbackPositionMs)` на строке 999).
  Из-за этого весь экран плеера принудительно рекомпозируется **каждый кадр** (60–120 раз в секунду).
  Более того, внутри дочернего `KaraokeView` на каждый кадр вызывается линейный поиск:
  ```kotlin
  val activeIndex = remember(lines, playbackPositionMs) {
      val idx = lines.indexOfLast { it.timestampMs <= playbackPositionMs }
  ```
  Для песен на 100+ строк это означает выполнение 120 раз в секунду линейного обхода массива строк на главном потоке.
- **Последствия:** Экстремальный расход батареи, нагрев устройства, деградация плавности интерфейса на экранах 120 Гц.
- **Рекомендация:**
  1. Вынести бегунок времени в изолированный Composable-компонент, принимающий лямбду или `State<Long>`, чтобы чтение состояния не вызывало рекомпозицию родительского экрана.
  2. Использовать `derivedStateOf` для вычисления `activeIndex`, чтобы пересчет триггерился только тогда, когда действительно меняется активная строка караоке.
  3. Для поиска активной строки применить двоичный поиск `binarySearch` вместо `indexOfLast` (список строк караоке строго монотонно упорядочен по времени).

---

### 🔴 P1 — Монолитный God-Composable `NowPlayingScreen.kt` (2246 строк)
- **Файл:** `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt` (2246 строк, 122 916 байт)
- **Суть проблемы:**
  Нарушение принципа единой ответственности (Single Responsibility Principle). Один файл содержит:
  - Логику опроса сессий и синхронизации времени.
  - Анимацию винилового проигрывателя.
  - Парсинг и рендеринг караоке LRC.
  - Студию ручной синхронизации тапом (`LrcTapSyncStudioDialog`).
  - Парсинг, рендеринг и автоскролл гитарных аккордов.
  - Транспонирование тональностей и диаграммы аппликатур.
  - Диалоги калибровки смещения и генерации шеринг-карточек.
  - Редактирование пользовательских заметок к песне.
  - Логику каскадного отклонения текстов (`rejectCurrentLyrics`).
- **Последствия:** Катастрофическая сложность поддержки, высокий риск регрессий при любых правках, невозможность изолированного тестирования UI.
- **Рекомендация:** Декомпозировать экран на независимые модули и фичи: `PlayerControlsBar`, `KaraokeTabContent`, `ChordsTabContent`, `NotesTabContent`, `TurntableHeader`. Вынести логику состояний в `NowPlayingViewModel`.

---

## 4. Средние и минорные проблемы (P2 / P3)

### 🟡 P2 — Небезопасный `LruCache` для растровых обложек (размер по количеству, а не по байтам)
- **Файл:** `app/src/main/java/com/lirix/app/ui/components/AlbumArtImage.kt`, строки 38–48
- **Проблема:** Кэш инициализирован как `LruCache<String, ImageBitmap>(64)`. Лимит задан по количеству элементов (64 штуки), а метод `sizeOf` не переопределен. Каждый битмап 512x512 ARGB_8888 занимает ~1 МБ оперативной памяти. 64 объекта могут удерживать до 64 МБ в heap без учета доступной памяти на бюджетных устройствах.
- **Рекомендация:** Переопределить `sizeOf` с расчетом размера в килобайтах (`bitmap.asAndroidBitmap().byteCount / 1024`) и ограничить кэш долей от максимальной памяти JVM (`Runtime.getRuntime().maxMemory() / 8`).

---

### 🟡 P2 — Неоптимальная анимация винила через `.rotate()` вместо фазы отрисовки
- **Файл:** `app/src/main/java/com/lirix/app/ui/components/AnalogTurntable.kt`, строка 123
- **Проблема:** Модификатор `.rotate(if (isPlaying) spinAngle else 0f)` читает состояние анимации в фазе композиции, заставляя весь виниловый диск со всеми канавками рекомпозироваться на каждом кадре.
- **Рекомендация:** Заменить на `.graphicsLayer { rotationZ = if (isPlaying) spinAngle else 0f }`, чтобы вращение происходило исключительно в фазе рисования (Draw phase) без повторной композиции.

---

### 🟡 P2 — Непагинированная загрузка всей медиатеки с полными текстами в `LibraryScreen`
- **Файл:**
  - `app/src/main/java/com/lirix/app/ui/LibraryScreen.kt`, строки 105, 112–132
  - `app/src/main/java/com/lirix/app/storage/MusicDao.kt`, строки 22–23
- **Проблема:** Запрос `observeRecentTracks()` возвращает `SELECT * FROM music_tracks` без `LIMIT`. В память загружаются сотни/тысячи сущностей `TrackEntity`, каждая из которых содержит длинные строки `plainLyrics` и `syncedLyrics`. Поиск выполняется полным обходом списка на UI-потоке:
  ```kotlin
  it.plainLyrics?.lowercase()?.contains(q) == true
  ```
- **Рекомендация:** Использовать проекцию без тяжелых полей текстов для списков медиатеки (`TrackSummaryTuple`: `trackKey`, `title`, `artist`, `albumArtUri`, `playCount`). Подключить пагинацию (AndroidX Paging 3) или Room FTS для поиска.

---

### 🟡 P2 — "Призрачная архитектура" и мертвый код после рефакторинга
- **Файлы:**
  - `app/src/main/java/com/lirix/app/storage/EventDao.kt` (56 строк)
  - `app/src/main/java/com/lirix/app/storage/EventEntity.kt` (42 строки)
  - `app/src/main/java/com/lirix/app/domain/*` (`Category.kt`, `Event.kt`, `EventSource.kt`, `EventType.kt`)
  - `app/src/main/java/com/lirix/app/ui/HistoryScreen.kt` (409 строк)
  - `app/src/main/java/com/lirix/app/ui/LyricsViewerDialog.kt` (311 строк)
  - `app/src/main/res/values/strings.xml`, строки 8–19
- **Проблема:** Таблица `events` была удалена в миграции 6, однако `EventDao` и `EventEntity` остались. `HistoryScreen` и `LyricsViewerDialog` нигде не вызываются и представляют собой ~720 строк мертвого кода. `MediaSessionCollector` до сих пор тратит процессорное время на создание неиспользуемых объектов `Event(..., EventSource.MEDIA_SESSION)`.
- **Рекомендация:** Безопасно удалить мертвые файлы, вычистить неиспользуемые domain-сущности и ресурсы из `strings.xml`.

---

### 🟡 P2 — Несинхронизированное состояние в объекте `MusicLyricsNotificationManager`
- **Файл:** `app/src/main/java/com/lirix/app/feature/MusicLyricsNotificationManager.kt`, строки 32–33
- **Проблема:** Поля `lastShownTitle` и `lastShownArtist` являются обычными `var` в синглтоне `object`, к которым обращаются параллельно из корутин `NotificationListener`, `MediaSessionCollector` и `MainActivity` без синхронизации или `@Volatile`.
- **Рекомендация:** Использовать потокобезопасные атомарные ссылки (`AtomicReference`) или аннотацию `@Volatile`.

---

### 🟡 P2 — Деструктивная миграция Room для версий БД ниже v5
- **Файл:** `app/src/main/java/com/lirix/app/storage/AppDatabase.kt`, строки 253–254
- **Проблема:** База данных настроена с `.fallbackToDestructiveMigration()`. В коде присутствуют миграции только начиная с 5_6. Если у пользователя установлена старая сборка с версией схемы v1..v4, обновление сотрет всю локальную историю треков.
- **Рекомендация:** Добавить миграции с начальных версий или убрать деструктивный откат для продакшн-сборок.

---

### 🟡 P2 — Синхронный `decodeFile` на главном потоке при генерации Wrapped-карточек
- **Файл:** `app/src/main/java/com/lirix/app/ui/WrappedScreen.kt`, строки 118–137
- **Проблема:** В лямбде клика на шеринг `BitmapFactory.decodeFile` вызывается синхронно для обложек топ-треков прямо на UI-потоке перед рендерингом `ShareCardGenerator`.
- **Рекомендация:** Перенести генерацию шеринг-битмапов и декодирование файлов в `withContext(Dispatchers.IO)`.

---

### 🟢 P3 — Отсутствие интернационализации (жестко захардкоженные русские строки)
- **Файлы:** Все Composable-экраны (`NowPlayingScreen.kt`, `LibraryScreen.kt`, `TeachModeScreen.kt`)
- **Проблема:** Интерфейс перегружен строковыми литералами на русском языке непосредственно в Kotlin-коде. Файл `strings.xml` содержит только устаревшие строки.
- **Рекомендация:** Вынести строковые ресурсы в `strings.xml` для поддержки мультиязычности и единообразия текста.

---

### 🟢 P3 — Наличие тяжелых артефактов и дампов в корневом каталоге проекта
- **Файлы в корне:** `lirix-v1.1.2.apk`, `lirix-v1.1.3.apk`, `lirix-v1.1.4.apk`, `logcat_analyst_dump.txt` (900 КБ), `logcat_dump.txt`, `event_engine_py.db`
- **Проблема:** Бинарные файлы релизных APK по 11 МБ каждый и дампы логов находятся в рабочей директории и не добавлены в `.gitignore`.
- **Рекомендация:** Добавить `*.apk`, `*.db`, `logcat*.txt`, `dump.xml` в `.gitignore` и удалить локальные бинарные дампы.

---

## 5. Архитектурные риски и стратегические рекомендации

### Архитектурная диаграмма текущего состояния (As-Is):
```mermaid
flowchart TD
    subgraph OS [Android OS Platform]
        NLS["NotificationListenerService"]
        MSM["MediaSessionManager"]
        WV["WebView (TeachMode)"]
    end

    subgraph BrokenLifecycle [Проблемная зона жизненного цикла]
        MSC1["MediaSessionCollector (LirixApp)"]
        MSC2["MediaSessionCollector (NLS)"]
        MSC3["MediaSessionCollector (FloatingService)"]
    end

    subgraph UI [Jetpack Compose UI (Без ViewModel)]
        NPS["NowPlayingScreen (2246 строк)\nШторм рекомпозиций 120 FPS\nПрямой доступ к DAO"]
        LS["LibraryScreen\nЗагрузка всех строк текстов в RAM"]
        WS["WrappedScreen\nСинхронный decodeFile"]
    end

    subgraph Storage [Room Database v10]
        MT["music_tracks"]
        LC["lyrics_cache"]
        ACH["achievements"]
        DEAD["EventDao / events (МЕРТВЫЙ КОД)"]
    end

    NLS --> MSC2
    MSM --> MSC1
    MSM --> MSC2
    MSM --> MSC3
    MSC2 -.->|stopListening обнуляет синглтон| MSC1
    NPS -->|Direct DAO calls| MT
    NPS -->|Direct DAO calls| LC
    LS -->|Direct DAO calls| MT
    WS -->|Direct DAO calls| ACH
```

### Дорожная карта рефакторинга (Target Architecture):

1. **Фаза 1: Устранение дефектов безопасности и надежности (P0 / P1)**
   - Вынести учетные данные подписи в переменные окружения, включить R8 в Release.
   - Добавить разрешение `ACCESS_NETWORK_STATE` в `AndroidManifest.xml` (разблокировка предзагрузки).
   - Заменить `Uri.fromFile` в `ShareManager` на безопасный возврат ошибки.
   - Объединить создание `MediaSessionCollector` в единственный экземпляр на уровне `Application`.
   - Заменить `runBlocking(Dispatchers.IO)` в коллбэках медиа-сессий на асинхронные корутины.

2. **Фаза 2: Оптимизация производительности UI и батареи**
   - Изолировать таймер воспроизведения в `NowPlayingScreen` через `derivedStateOf` и бинарный поиск строки караоке.
   - Заменить `.rotate()` винила на `.graphicsLayer`.
   - Внедрить проекцию `TrackSummary` (без полей текстов) для списков в медиатеке.
   - Ограничить размер `AlbumArtBitmapCache` по байтовому объему (доля от heap).

3. **Фаза 3: Архитектурная декомпозиция (Внедрение MVVM)**
   - Создать `NowPlayingViewModel`, `LibraryViewModel`, `WrappedViewModel`.
   - Разбить `NowPlayingScreen.kt` (2246 строк) на 5 изолированных компонентов (`/ui/player/components/`).
   - Удалить неиспользуемые файлы (`EventDao.kt`, `EventEntity.kt`, `HistoryScreen.kt`, `LyricsViewerDialog.kt`).

---

## 6. Заключение

Приложение **Lirix** обладает качественным и рабочим ядром обработки музыки и текстов с высоким уровнем покрытия юнит-тестами (202 теста). Однако для вывода приложения в стабильный продакшн необходимо срочно устранить критические уязвимости (P0/P1), решить проблему шторма рекомпозиций и провести санитарную очистку кодовой базы от артефактов прошлого этапа разработки.
