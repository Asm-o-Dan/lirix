# Архитектурный план: Pivot в Music & Lyrics Hub с аналитикой Wrapped

- **Документ:** Архитектурная спецификация системы (Architecture Plan)
- **TASK_ID:** ARCH-001
- **Статус:** APPROVED ARCHITECTURAL BASELINE
- **Дата:** 2026-10-01
- **Нормативная база:** Spec Driven Design (SDD), [decisions.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md) (ADR-001, ADR-002)
- **Целевая платформа:** Android 8.0+ (API 26..35), Poco M7 (HyperOS 2, Android 15), 100% On-Device / Local-First

---

## 1. Резюме и контекст архитектурного пивота

На основании решения **ADR-002 (Pivot and Descope to Pure Music & Lyrics Hub with Gamified Wrapped)** проект переходит из статуса многодоменного агрегатора уведомлений (финансы, дедлайны, сообщения, OTP) в специализированный **100% автономный Медиа-трекер, Хаб текстов песен и геймифицированную аналитику прослушиваний (Music Wrapped)**.

### Ключевые архитектурные драйверы:
1. **Zero-Overhead Media Ingress:** Уведомления, не относящиеся к воспроизведению медиаконтента, отсекаются на самом входе в `NotificationListener` без глубокого парсинга, нормализации и работы с базой данных.
2. **Удаление доменного балласта (~40% кодовой базы):** Полный демонтаж финансовых транзакций, парсеров банковских SMS/Push, банковских карт/счетов, парсеров Moodle/учёбы, классификатора на ONNX эмбеддингах (Jev/Prototypes) и декларативных правил автоматизации.
3. **Локальный кэш текстов с учётом типа сети:** Умная предзагрузка караоке (LRC) и аккордов (AmDm) по Wi-Fi, ленивая подгрузка по сотовой сети, поддержка ручных заметок офлайн.
4. **Конфиденциальный Wrapped и геймификация:** Локальный расчёт аналитики времени прослушивания, любимых жанров/артистов, паттернов дня и системы ачивок с возможностью экспорта графической карточки для соцсетей без обращения к облачным серверам.
5. **Чистый AMOLED интерфейс:** 4 сфокусированных таба вместо 5 перегруженных экранов.

---

## 2. Зонирование и границы ответственности (Зоны A–E)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             OS & HARDWARE INGRESS                           │
│     NotificationListenerService          MediaSessionManager (Controllers)  │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                          ЗОНА A: media-ingress                              │
│  - MediaIngressFilter (Быстрый отсев не-медиа событий, whitelist пакетов)   │
│  - MediaDebounceFilter (Схлопывание дребезга плееров 600ms)                 │
│  - CrossSourceCorrelator (Дедупликация StatusBar vs MediaSession)           │
│  - MusicTrackParser (Очистка Artist/Title/Album от мусора плееров)          │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │ Emits Clean MediaPlaybackSignal
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ЗОНА B: media-core                                │
│  - MusicFeatureEngine (Идентификация SHA-256 trackKey, сессии, playCount)   │
│  - Room Database (AppDatabase v6):                                          │
│      ├── music_tracks (Каталог треков, метаданные, счётчики)                │
│      ├── music_listening_sessions (Сессии непрерывного прослушивания)        │
│      ├── lyrics_cache (Тексты, LRC таймкоды, AmDm аккорды)                  │
│      └── achievements (Геймификационные бейджи и прогресс)                  │
└───────────────┬───────────────────────────────┬─────────────────────────────┘
                │                               │
                ▼                               ▼
┌───────────────────────────────┐ ┌───────────────────────────────────────────┐
│     ЗОНА C: lyrics-engine     │ │        ЗОНА D: wrapped-analytics          │
│  - NetworkConditionManager    │ │  - WrappedStatsEngine (Топ артистов,      │
│    (Wi-Fi auto / Cell lazy)   │ │    часы, распределение по времени суток)  │
│  - Provider Cascade:          │ │  - GamificationEngine (Проверка условий   │
│    1. Room lyrics_cache       │ │    разблокировки ачивок)                  │
│    2. LRCLIB (LRC synced)     │ │  - ListeningArchetypeClassifier           │
│    3. AmDm.ru (Chords)        │ │  - ShareCardGenerator (Офлайн рендер      │
│    4. Fallback (LyricFind...) │ │    Story-карточки в PNG)                  │
│    5. User Notes (Офлайн)     │ │                                           │
└───────────────┬───────────────┘ └─────────────────────┬─────────────────────┘
                │                                       │
                └───────────────────┬───────────────────┘
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            ЗОНА E: media-ui                                 │
│                   AMOLED Obsidian Pulse (Jetpack Compose)                   │
│                                                                             │
│  Tab 1: 🎵 «Сейчас играет»   (Hero-плеер, LRC караоке, аккорды AmDm)        │
│  Tab 2: 📜 «История»         (Хронологический поток с группировкой и поиском)│
│  Tab 3: 📚 «Библиотека»      (Офлайн кэш текстов, избранное, артисты)      │
│  Tab 4: 🏆 «Итоги & Ачивки»  (Music Wrapped дашборд, сетка ачивок, экспорт) │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### Зона A: `media-ingress` (Сбор и первичный отсев)

- **Назначение:** Приём сигналов от Android OS и изоляция приложения от любых не-медиа событий.
- **Компоненты:**
  - `NotificationListener`: В точке `onNotificationPosted` реализуется жесткий гейткипер:
    - Проверка принадлежности `packageName` к известным медиаплеерам либо наличие флагов `Notification.EXTRA_MEDIA_SESSION`, `android.mediaSession`, `Notification.CATEGORY_TRANSPORT` или шаблона `MediaStyle`.
    - Все остальные пакеты (банки, СМС, мессенджеры, почта, маркетплейсы, доставка) **немедленно отбрасываются** без чтения extras, без вызова нормализаторов и без записи в базу данных.
  - `MediaSessionCollector`: Мониторинг `MediaSessionManager.OnActiveSessionsChangedListener` и подписка на `MediaController.Callback` для плееров в фоне.
  - `MediaDebounceFilter`: 600 мс окно схлопывания транзитных статусов (BUFFERING -> PLAYING).
  - `CrossSourceCorrelator`: Устранение дублирования между `StatusBarNotification` и `MediaController`.
  - `MusicTrackParser`: Детерминированное извлечение пары `(Artist, Title, Album)` с очисткой от служебного мусора («Official Video», «Remix», «feat.», названий плееров).
- **Контракт на выходе:** Чистый `MediaPlaybackSignal(title, artist, album, packageName, state, timestamp)`.

---

### Зона B: `media-core` (Хранилище и учёт прослушиваний)

- **Назначение:** Персистентность локального состояния, агрегация сессий и аналитические выборки.
- **Компоненты:**
  - `AppDatabase` (версия 6, SQLite WAL): Полное удаление таблиц `events`, `financial_transactions`, `study_items`, `rule_entities`, `prototypes`.
  - `MusicDao`: Реактивные `Flow` и suspend-запросы для треков, сессий и библиотеки.
  - `LyricsDao`: Управление кэшем текстов песен, таймкодов караоке и аккордов.
  - `AchievementDao`: Хранение состояния прогресса и разблокированных достижений.
  - `MusicFeatureEngine`:
    - Расчёт детерминированного ключа трека: `trackKey = SHA256(lowercase(title)|lowercase(artist)|lowercase(album))`.
    - Учёт пауз: Паузы длительностью $\le 30$ секунд объединяются в единую сессию прослушивания.
    - Идемпотентность счётчика прослушиваний (`playCount`): защита от ложных накруток при частых сбросах метаданных плеером.
    - Автоматическое определение `isFavorite`: `playCount >= 5` или `totalDurationMs >= 15 минут`.

---

### Зона C: `lyrics-engine` (Сетевой кэш текстов и аккордов)

- **Назначение:** Офлайн-ориентированное извлечение текстов, караоке-таймкодов (LRC) и гитарных табулатур.
- **Компоненты:**
  - `NetworkConditionManager`: Обнаружение текущего типа подключения через `ConnectivityManager` (`NET_CAPABILITY_NOT_METERED`, `TRANSPORT_WIFI` vs `TRANSPORT_CELLULAR`).
  - **Политика загрузки:**
    - **Wi-Fi:** Автоматическая фоновая предзагрузка текстов при фиксации нового трека в сессии (добавление в очередь предзагрузки).
    - **Сотовая сеть (Cellular / Metered):** Ленивая загрузка (On-Demand) только при открытии пользователем экрана «Сейчас играет» или ручном нажатии кнопки загрузки.
    - **Офлайн:** Моментальная отдача из локальной таблицы `lyrics_cache` либо показ личных заметок к песне.
  - **Каскадный провайдер (`LyricsProviderCascade`):**
    1. `RoomLyricsCache`: Проверка таблицы `lyrics_cache`.
    2. `LrcLibProvider`: Бесплатный открытый API с синхронизированным караоке (`syncedLyrics` вида `[01:23.45]`) и `plainLyrics`.
    3. `AmDmScraper`: Русскоязычные песни, рок, инди, гитарные подборы и аккорды.
    4. `LyricFind / FallbackScraper`: Резервный поиск зарубежных и редких композиций.
    5. `UserNotesAdapter`: Пользовательский офлайн-текст/заметки, если в сети ничего не найдено.

---

### Зона D: `wrapped-analytics` (Локальный Wrapped и геймификация)

- **Назначение:** 100% приватная локальная аналитика прослушиваний без передачи данных наружу.
- **Компоненты:**
  - `WrappedStatsEngine`:
    - Временные диапазоны: Всё время, последние 30 дней, текущий календарный год (Yearly Wrapped).
    - Метрики: Суммарное время (часы, минуты), общее число треков, топ-5 артистов с процентом от общего времени, распределение прослушиваний по часам суток (утро, день, вечер, ночь).
    - Определение музыкального архетипа:
      - «Ночной странник» (Night Owl) — пик прослушиваний с 01:00 до 05:00.
      - «Преданный слушатель» (Loyal Repeater) — топ-1 артист занимает $> 50\%$ всего времени.
      - «Музыкальный исследователь» (Genre Nomad) — большое количество уникальных артистов с низким числом повторов.
      - «Акустический бард» (Acoustic Bard) — частое открытие треков с аккордами AmDm.
  - `GamificationEngine`: Автоматическая проверка триггеров ачивок при завершении сессии или просмотре текста:
    - *«Первый бит»* (First Groove): Первое зарегистрированное прослушивание.
    - *«Сотник»* (Centurion): Прослушано 100 уникальных треков.
    - *«Марафонец»* (Marathoner): Непрерывное прослушивание музыки $> 5$ часов за сутки.
    - *«Мастер караоке»* (Karaoke Star): Просмотрено 10 треков с синхронизированным LRC-текстом.
    - *«Песни у костра»* (Campfire Singer): Открыто 5 песен с аккордами AmDm.
    - *«Музыкальная одержимость»* (Obsession): Один трек прослушан $\ge 25$ раз.
  - `ShareCardGenerator`: Рендеринг Jetpack Compose макета карточки Wrapped в растровый `Bitmap` (Android Canvas) с сохранением в локальный кэш через `FileProvider` для нативной системной шторки «Поделиться».

---

### Зона E: `media-ui` (4 таба AMOLED интерфейса)

- **Назначение:** Визуальное представление в дизайн-системе Obsidian Pulse (глубокий чёрный цвет `#000000`, акценты `#7C4DFF` и `#B388FF`, поддержка 120 Гц).
- **Структура экранов (4 таба):**
  1. 🎵 **«Сейчас играет» (Now Playing):**
     - Hero-секция с анимированным винилом / обложкой, название трека, исполнитель, источник (Spotify, Яндекс Музыка, VK, YouTube).
     - Синхронизированный караоке-скроллер LRC: автоскролл активной строки песни с подсветкой.
     - Вкладка-свитчер: «Текст» / «Аккорды (AmDm)» / «Заметки».
     - Быстрые действия: Избранное (сердечко), ручной поиск текста, добавление личной заметки к песне.
  2. 📜 **«История» (History):**
     - Хронологический список сессий прослушивания с группировкой (Сегодня, Вчера, На этой неделе).
     - Отображение длительности сессии, количества повторов и значка источника.
     - Быстрый текстовый поиск по истории.
  3. 📚 **«Библиотека» (Library):**
     - Каталог треков с сохранённым офлайн-кэшем текстов.
     - Вкладки-фильтры: «Все сохранённые», «Избранное», «С аккордами».
     - Группировка по артистам и альбомам.
  4. 🏆 **«Итоги & Ачивки» (Wrapped & Badges):**
     - Музыкальный Wrapped: карточки статистики, топ артистов, график активности по часам.
     - Сетка достижений (Achievements): разблокированные бейджи с золотым свечением, прогресс-бары для заблокированных.
     - Кнопка «Поделиться карточкой года/месяца» (генерация красивого инфографического постера).

---

## 3. Точный список удаляемых файлов

Для реализации архитектурного плана без поломки сборки и появления циклических зависимостей, удаление производится строго после создания ветки архива `legacy-full-engine`.

### Категория 1: Продуктовые движки старых фичей (Feature Engines)
1. `app/src/main/java/com/eventengine/app/feature/FinanceFeatureEngine.kt`
2. `app/src/main/java/com/eventengine/app/feature/StudyFeatureEngine.kt`

### Категория 2: База данных Room (старые сущности и DAO)
3. `app/src/main/java/com/eventengine/app/storage/FinancialTransactionEntity.kt`
4. `app/src/main/java/com/eventengine/app/storage/FinancialTransactionDao.kt`
5. `app/src/main/java/com/eventengine/app/storage/StudyItemEntity.kt`
6. `app/src/main/java/com/eventengine/app/storage/StudyItemDao.kt`
7. `app/src/main/java/com/eventengine/app/storage/RuleEntity.kt`
8. `app/src/main/java/com/eventengine/app/storage/RuleDao.kt`
9. `app/src/main/java/com/eventengine/app/storage/PrototypeEntity.kt`
10. `app/src/main/java/com/eventengine/app/storage/PrototypeDao.kt`
11. `app/src/main/java/com/eventengine/app/storage/SeedPrototypeBank.kt`
12. `app/src/main/java/com/eventengine/app/storage/EventEntity.kt` *(заменяется специализированными `music_tracks` и `music_listening_sessions`)*
13. `app/src/main/java/com/eventengine/app/storage/EventDao.kt` *(заменяется `MusicDao`)*

### Категория 3: Старый доменный слой (Domain Enums & Models)
14. `app/src/main/java/com/eventengine/app/domain/FinancialDirection.kt`
15. `app/src/main/java/com/eventengine/app/domain/StudyItemEnums.kt`
16. `app/src/main/java/com/eventengine/app/domain/PrototypeScope.kt`
17. `app/src/main/java/com/eventengine/app/domain/ClassificationEvidence.kt`
18. `app/src/main/java/com/eventengine/app/domain/Event.kt` *(заменяется внутренними моделями медиа)*
19. `app/src/main/java/com/eventengine/app/domain/EventSource.kt`
20. `app/src/main/java/com/eventengine/app/domain/EventType.kt`
21. `app/src/main/java/com/eventengine/app/domain/Category.kt`

### Категория 4: Банковские регулярки, парсеры и ML-классификация
22. `app/src/main/java/com/eventengine/app/classification/RuleClassifier.kt` *(банковские, смс, otp и moodle правила)*
23. `app/src/main/java/com/eventengine/app/classification/CompositeConfidenceEngine.kt`
24. `app/src/main/java/com/eventengine/app/classification/JevClassifier.kt`
25. `app/src/main/java/com/eventengine/app/classification/MockJevAdapter.kt`
26. `app/src/main/java/com/eventengine/app/normalization/EventNormalizer.kt` *(банковские карты, PII, валюты RUP/MDL/PRB)*
27. `app/src/main/java/com/eventengine/app/rules/DeclarativeRulesEngine.kt`
28. `app/src/main/java/com/eventengine/app/search/HybridRetrievalEngine.kt`
29. `app/src/main/java/com/eventengine/app/ml/SimilarityEngine.kt`
30. `app/src/main/java/com/eventengine/app/ml/VectorUtils.kt`
31. `app/src/main/java/com/eventengine/app/analytics/LocalHypothesisTracker.kt` *(H1-H8 гипотезы мультидоменного движка)*

### Категория 5: Старые экраны UI
32. `app/src/main/java/com/eventengine/app/ui/FinanceScreen.kt`
33. `app/src/main/java/com/eventengine/app/ui/StudyScreen.kt`
34. `app/src/main/java/com/eventengine/app/ui/RulesScreen.kt`
35. `app/src/main/java/com/eventengine/app/ui/SearchScreen.kt` *(поиск интегрируется в Библиотеку и Историю)*
36. `app/src/main/java/com/eventengine/app/ui/AnalyticsScreen.kt` *(заменяется экраном Wrapped & Achievements)*
37. `app/src/main/java/com/eventengine/app/ui/CategoryPickerDialog.kt`
38. `app/src/main/java/com/eventengine/app/ui/EventCard.kt`

### Категория 6: Устаревшие Unit-тесты (подлежат удалению либо замене на тесты Зон A–D)
39. `app/src/test/java/com/eventengine/app/FeatureEnginesTest.kt`
40. `app/src/test/java/com/eventengine/app/CompositeConfidenceEngineTest.kt`
41. `app/src/test/java/com/eventengine/app/DeclarativeRulesEngineTest.kt`
42. `app/src/test/java/com/eventengine/app/EventModelTest.kt`
43. `app/src/test/java/com/eventengine/app/LocalHypothesisTrackerTest.kt`
44. `app/src/test/java/com/eventengine/app/MockJevAdapterTest.kt`
45. `app/src/test/java/com/eventengine/app/NormalizerAndClassifierTest.kt`
46. `app/src/test/java/com/eventengine/app/SimilarityEngineDecayAndConfigTest.kt`
47. `app/src/test/java/com/eventengine/app/SimilarityEngineTest.kt`
48. `app/src/test/java/com/eventengine/app/Sprint4EnginesAndRulesTest.kt`
49. `app/src/test/java/com/eventengine/app/Sprint5UiNavigationTest.kt`

### Не подлежат удалению (сохраняются и развиваются):
- `MusicFeatureEngine.kt`
- `LyricsProvider.kt`
- `MusicTrackParser.kt`
- `MusicLyricsNotificationManager.kt`
- `MediaSessionCollector.kt`
- `MediaDebounceFilter.kt`
- `CrossSourceCorrelator.kt`
- `NotificationListener.kt` *(переводится на строгий `MediaIngressFilter`)*
- `AppDatabase.kt` *(обновляется схема)*
- `Converters.kt`
- `MainActivity.kt`, `LirixApp.kt`, `Theme.kt`, `AppColors.kt`
- `LyricsViewerDialog.kt` *(переиспользуется в плеере)*
- `LyricsAndTrackParserTest.kt`, `MediaDebounceTest.kt`, `CrossSourceCorrelatorTest.kt`

---

## 4. Обновленная схема Room (DDL и Kotlin Entities)

Схема базы данных `AppDatabase` упрощается до 4 целевых таблиц. Версия повышается с `5` до `6`.

```sql
-- 1. Каталог уникальных треков
CREATE TABLE IF NOT EXISTS `music_tracks` (
    `trackKey` TEXT NOT NULL PRIMARY KEY,
    `title` TEXT NOT NULL,
    `artist` TEXT NOT NULL,
    `album` TEXT NOT NULL DEFAULT '',
    `sourcePackage` TEXT NOT NULL,
    `playCount` INTEGER NOT NULL DEFAULT 1,
    `totalDurationMs` INTEGER NOT NULL DEFAULT 0,
    `firstPlayedAt` INTEGER NOT NULL,
    `lastPlayedAt` INTEGER NOT NULL,
    `userNotes` TEXT NOT NULL DEFAULT '',
    `isFavorite` INTEGER NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS `index_music_tracks_isFavorite` ON `music_tracks` (`isFavorite`);
CREATE INDEX IF NOT EXISTS `index_music_tracks_playCount` ON `music_tracks` (`playCount`);
CREATE INDEX IF NOT EXISTS `index_music_tracks_lastPlayedAt` ON `music_tracks` (`lastPlayedAt`);
CREATE INDEX IF NOT EXISTS `index_music_tracks_artist` ON `music_tracks` (`artist`);

-- 2. Сессии прослушивания (таймлайн и статистика)
CREATE TABLE IF NOT EXISTS `music_listening_sessions` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `trackKey` TEXT NOT NULL,
    `sourcePackage` TEXT NOT NULL,
    `startTimeMs` INTEGER NOT NULL,
    `endTimeMs` INTEGER NOT NULL,
    `durationMs` INTEGER NOT NULL,
    `isCompleted` INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (`trackKey`) REFERENCES `music_tracks` (`trackKey`) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_music_listening_sessions_trackKey` ON `music_listening_sessions` (`trackKey`);
CREATE INDEX IF NOT EXISTS `index_music_listening_sessions_startTimeMs` ON `music_listening_sessions` (`startTimeMs`);

-- 3. Выделенный кэш текстов песен, караоке и аккордов
CREATE TABLE IF NOT EXISTS `lyrics_cache` (
    `trackKey` TEXT NOT NULL PRIMARY KEY,
    `plainLyrics` TEXT,
    `syncedLyrics` TEXT,
    `chords` TEXT,
    `sourceProvider` TEXT NOT NULL,
    `cachedAtMs` INTEGER NOT NULL,
    `syncOffsetMs` INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (`trackKey`) REFERENCES `music_tracks` (`trackKey`) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS `index_lyrics_cache_cachedAtMs` ON `lyrics_cache` (`cachedAtMs`);

-- 4. Геймификация и достижения (Wrapped & Achievements)
CREATE TABLE IF NOT EXISTS `achievements` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `title` TEXT NOT NULL,
    `description` TEXT NOT NULL,
    `category` TEXT NOT NULL,
    `iconName` TEXT NOT NULL,
    `isUnlocked` INTEGER NOT NULL DEFAULT 0,
    `unlockedAtMs` INTEGER,
    `currentProgress` INTEGER NOT NULL DEFAULT 0,
    `maxProgress` INTEGER NOT NULL DEFAULT 1,
    `isSecret` INTEGER NOT NULL DEFAULT 0
);
```

### Kotlin Data Classes (Зона B)

```kotlin
@Entity(
    tableName = "music_tracks",
    indices = [
        Index("isFavorite"),
        Index("playCount"),
        Index("lastPlayedAt"),
        Index("artist")
    ]
)
data class TrackEntity(
    @PrimaryKey val trackKey: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val sourcePackage: String,
    val playCount: Int = 1,
    val totalDurationMs: Long = 0L,
    val firstPlayedAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val userNotes: String = "",
    val isFavorite: Boolean = false
)

@Entity(
    tableName = "music_listening_sessions",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackKey"],
            childColumns = ["trackKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("trackKey"),
        Index("startTimeMs")
    ]
)
data class ListeningSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackKey: String,
    val sourcePackage: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean = false
)

@Entity(
    tableName = "lyrics_cache",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["trackKey"],
            childColumns = ["trackKey"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("cachedAtMs")
    ]
)
data class LyricsCacheEntity(
    @PrimaryKey val trackKey: String,
    val plainLyrics: String? = null,
    val syncedLyrics: String? = null,
    val chords: String? = null,
    val sourceProvider: String,
    val cachedAtMs: Long = System.currentTimeMillis(),
    val syncOffsetMs: Long = 0L
)

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: String, // "LISTENING", "LYRICS", "EXPLORATION"
    val iconName: String,
    val isUnlocked: Boolean = false,
    val unlockedAtMs: Long? = null,
    val currentProgress: Int = 0,
    val maxProgress: Int = 1,
    val isSecret: Boolean = false
)
```

---

## 5. Риски и порядок миграции БД (Database Migration Plan)

### Анализ рисков:
1. **Риск потери накопленной истории музыки при `fallbackToDestructiveMigration`:**
   Если инкрементировать версию базы с 5 до 6 без явного `Migration(5, 6)`, пользователи потеряют уже зафиксированные треки `music_tracks` и сессии `music_listening_sessions`.
2. **Риск сбоя SQLite из-за наличия данных текстов в старой таблице `music_tracks`:**
   В версии 5 поля `plainLyrics` и `syncedLyrics` находились прямо в `music_tracks`. Перенос в `lyrics_cache` требует аккуратной SQL-миграции данных.
3. **Риск падения при обращении к старым DAO из фоновых сервисов:**
   Любой остаточный вызов `financialTransactionDao()` или `studyItemDao()` приведет к моментальному крашу `IllegalStateException`.

### Порядок выполнения миграции:

```
[Шаг 1: Git Freeze] ──> [Шаг 2: Migration(5,6) Script] ──> [Шаг 3: Seeding Achievements] ──> [Шаг 4: Smoke Test]
```

1. **Создание архивной ветки (Git Freeze):**
   ```bash
   git checkout -b legacy-full-engine
   git push origin legacy-full-engine
   git checkout master
   ```
2. **Имплементация `MIGRATION_5_6`:**
   Вместо деструктивного сброса реализуется детерминированная миграция:
   - Создаётся новая таблица `lyrics_cache`.
   - Если в старой таблице `music_tracks` были тексты (`plainLyrics IS NOT NULL OR syncedLyrics IS NOT NULL`), выполняется их копирование в `lyrics_cache`:
     ```sql
     INSERT OR IGNORE INTO lyrics_cache (trackKey, plainLyrics, syncedLyrics, chords, sourceProvider, cachedAtMs, syncOffsetMs)
     SELECT trackKey, plainLyrics, syncedLyrics, NULL, 'LegacyMigration', lastPlayedAt, 0
     FROM music_tracks WHERE plainLyrics IS NOT NULL OR syncedLyrics IS NOT NULL;
     ```
   - Создаётся таблица `achievements`.
   - Удаляются устаревшие таблицы:
     ```sql
     DROP TABLE IF EXISTS financial_transactions;
     DROP TABLE IF EXISTS study_items;
     DROP TABLE IF EXISTS rule_entities;
     DROP TABLE IF EXISTS prototypes;
     DROP TABLE IF EXISTS events;
     ```
   - Создаются новые индексы.
3. **Предустановка (Seeding) дефолтных достижений:**
   В `AppDatabase.Callback.onCreate` и в миграции регистрируются 7 базовых ачивок:
   - `ach_first_track` («Первый бит»)
   - `ach_centurion` («Сотник»)
   - `ach_marathoner` («Марафонец»)
   - `ach_karaoke_star` («Мастер караоке»)
   - `ach_campfire_singer` («Песни у костра»)
   - `ach_night_owl` («Ночной странник»)
   - `ach_loyal_repeater` («Преданный слушатель»)

---

## 6. Точки интеграции и сквозные контракты

### 6.1 Контракт между Зонами A и B (`media-ingress` -> `media-core`)
```kotlin
data class MediaPlaybackSignal(
    val title: String,
    val artist: String,
    val album: String = "",
    val packageName: String,
    val playbackState: String, // "PLAYING", "PAUSED", "STOPPED"
    val timestamp: Long = System.currentTimeMillis()
)
```
Движок `MusicFeatureEngine` принимает `MediaPlaybackSignal`, вычисляет SHA-256 `trackKey`, сопоставляет время с предыдущей сессией и обновляет `music_tracks` и `music_listening_sessions`.

### 6.2 Контракт между Зонами B и C (`media-core` -> `lyrics-engine`)
```kotlin
interface LyricsEngineContract {
    suspend fun getLyrics(trackKey: String, forceNetwork: Boolean = false): LyricsDataResult
    fun observeLyrics(trackKey: String): Flow<LyricsCacheEntity?>
    suspend fun prefetchIfWifi(trackKey: String, title: String, artist: String)
}

data class LyricsDataResult(
    val trackKey: String,
    val plainLyrics: String?,
    val syncedLyrics: String?,
    val chords: String?,
    val source: String,
    val isOfflineCache: Boolean
)
```

### 6.3 Контракт между Зонами B и D (`media-core` -> `wrapped-analytics`)
```kotlin
interface WrappedAnalyticsContract {
    suspend fun calculateWrapped(timeRange: WrappedTimeRange): WrappedReport
    suspend fun checkAchievements(): List<AchievementEntity>
    suspend fun renderShareCard(report: WrappedReport): Bitmap
}

enum class WrappedTimeRange { ALL_TIME, LAST_30_DAYS, YEAR_TO_DATE }
```

### 6.4 Контракт между Зонами D и E (`wrapped-analytics` -> `media-ui`)
Экран `WrappedScreen` подписывается на реактивные состояния:
- `Flow<WrappedReport>` для рендера интерактивной статистики и графиков.
- `Flow<List<AchievementEntity>>` для отображения бейджей.
- Callback `onExportStoryClicked` передаёт сгенерированный `Uri` картинки в `Intent(Intent.ACTION_SEND)`.

---

## 7. Декомпозиция задач по методологии SDD (Roadmap)

В соответствии с правилом атомарности SDD (1 функция / 1 атомарный шаг на задачу), последующая реализация разбивается на цепочку фаз:

| Фаза | Идентификатор | Зона | Описание задачи |
|---|---|---|---|
| **Phase 1: Git & Safety** | `TASK-SAFE-01` | Core | Создание ветки `legacy-full-engine`, фиксация legacy состояния |
| **Phase 2: Ingress Pivot** | `TASK-ING-01` | Зона A | Реализация строгого `MediaIngressFilter` в `NotificationListener` |
| | `TASK-ING-02` | Зона A | Очистка `CrossSourceCorrelator` от не-медиа проверок |
| **Phase 3: DB Migration** | `TASK-DB-01` | Зона B | Создание сущностей `LyricsCacheEntity` и `AchievementEntity` |
| | `TASK-DB-02` | Зона B | Реализация `MIGRATION_5_6` и очистка `AppDatabase` от старых DAO |
| **Phase 4: Dead Code Removal** | `TASK-PURGE-01` | Clean | Удаление файлов категорий 1–5 без поломки сборки |
| **Phase 5: Lyrics & Network** | `TASK-LYR-01` | Зона C | `NetworkConditionManager` (Wi-Fi prefetch / Cellular lazy) |
| | `TASK-LYR-02` | Зона C | Интеграция парсера аккордов AmDm в `LyricsProvider` |
| **Phase 6: Wrapped & Badges** | `TASK-WRP-01` | Зона D | `WrappedStatsEngine` (расчёт времени, топов, архетипов) |
| | `TASK-WRP-02` | Зона D | `GamificationEngine` (логика проверки и разблокировки ачивок) |
| | `TASK-WRP-03` | Зона D | Офлайн-генератор Story-карточки в Bitmap |
| **Phase 7: AMOLED UI 4-Tabs** | `TASK-UI-01` | Зона E | Рефакторинг `AppScaffold` на 4 таба (Сейчас играет, История, Библиотека, Итоги) |
| | `TASK-UI-02` | Зона E | Разработка экрана «Сейчас играет» с LRC караоке и аккордами |
| | `TASK-UI-03` | Зона E | Разработка экрана «Библиотека» (офлайн-тексты, избранное) |
| | `TASK-UI-04` | Зона E | Разработка экрана «Итоги & Ачивки» с кнопкой шаринга |
| **Phase 8: Verification** | `TASK-TEST-01` | QA | Написание unit-тестов для Зон A, B, C, D |

---

## 8. Критерии готовности (Definition of Done для ARCH-001)

1. [x] Документ `.sdd/architecture.md` полностью сформирован и содержит все обязательные разделы.
2. [x] Выделены и специфицированы 5 четких зон ответственности (`media-ingress`, `media-core`, `lyrics-engine`, `wrapped-analytics`, `media-ui`).
3. [x] Сформирован исчерпывающий реестр из 49 удаляемых файлов и устаревших тестов с обоснованием сохранения сборки.
4. [x] Приведена обновленная схема Room (DDL + Kotlin Entities) с выделенной таблицей `lyrics_cache` и таблицей `achievements`.
5. [x] Зафиксированы риски миграции, стратегия переноса данных с версии 5 на версию 6 и предустановка ачивок.
6. [x] Зафиксированы сквозные контракты между всеми зонами ответственности.
