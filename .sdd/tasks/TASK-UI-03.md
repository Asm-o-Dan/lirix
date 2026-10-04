# Задача TASK-UI-03: Расширенная аналитика (WrappedStatsEngine) и редизайн WrappedScreen

**Файлы:**
- `app/src/main/java/com/eventengine/app/analytics/WrappedStatsEngine.kt` (таймфреймы, одержимость, компас)
- `app/src/main/java/com/eventengine/app/ui/WrappedScreen.kt` (полный редизайн интерфейса)
- `app/src/test/java/com/eventengine/app/WrappedStatsEngineTest.kt` (тесты таймфреймов и одержимости)

**Спецификация:** [.sdd/specs/wrapped-analytics/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/wrapped-analytics/overview.md) (v2), дизайн-система `UI_DESIGN_SPEC.md`.

---

## 1. Контекст и архитектурная цель
Экран «Итоги & Достижения» (Wrapped) переводится на глубокую интерактивную аналитику с временными срезами (`TODAY`, `WEEK`, `MONTH`, `ALL_TIME`), выявлением супер-зацикленного трека («Главная одержимость»), почасовым/недельным компасом активности и премиальной витриной 26 достижений с фильтрацией по тирам и статусу.

---

## 2. Модель данных аналитики (`WrappedStatsEngine.kt`)

### 2.1. Временные периоды
```kotlin
enum class AnalyticsTimeframe(val labelRu: String) {
    TODAY("Сегодня"),
    WEEK("7 дней"),
    MONTH("Месяц"),
    ALL_TIME("Всё время")
}
```

### 2.2. Расширенные структуры данных
```kotlin
data class TopObsessionItem(
    val trackKey: String,
    val title: String,
    val artist: String,
    val albumArtUri: String?,
    val playCountInPeriod: Int,
    val durationMsInPeriod: Long
)

data class DayOfWeekActivity(
    val dayName: String,     // "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"
    val sessionCount: Int,
    val totalDurationMs: Long
)

data class WrappedStats(
    val totalListeningTimeMs: Long,
    val uniqueTracksCount: Int,
    val uniqueArtistsCount: Int,
    val repeatRatio: Float,            // playCount / uniqueTracks
    val lyricsCoverageRatio: Float,    // tracksWithLyrics / totalTracks
    val topArtists: List<TopArtistItem>,
    val topTracks: List<TopTrackItem>,
    val topObsession: TopObsessionItem?,
    val timeOfDayDistribution: Map<TimeOfDaySlot, Float>,
    val weeklyActivity: List<DayOfWeekActivity>,
    val archetype: ListeningArchetype
)
```

### 2.3. Алгоритм фильтрации и расчета
```kotlin
fun calculateStats(
    tracks: List<MusicTrackEntity>,
    sessions: List<MusicListeningSessionEntity>,
    timeframe: AnalyticsTimeframe = AnalyticsTimeframe.ALL_TIME,
    referenceTimestampMs: Long = System.currentTimeMillis()
): WrappedStats
```
1. **Фильтрация сессий:**
   - `TODAY`: сессии с полуночи текущего дня (`startOfDayMs(referenceTimestampMs)`).
   - `WEEK`: сессии за последние 7 суток (`referenceTimestampMs - 7 * 86_400_000L`).
   - `MONTH`: сессии за последние 30 суток (`referenceTimestampMs - 30 * 86_400_000L`).
   - `ALL_TIME`: все доступные сессии.
2. **Агрегация по трекам периода:**
   - Если список сессий периода не пуст: вычислять `playCountInPeriod` и `durationMsInPeriod` путем группировки сессий по `trackKey`.
   - Если сессий нет, но `timeframe == ALL_TIME`: использовать совокупные поля `playCount` и `totalDurationMs` из `MusicTrackEntity`.
3. **Определение «Главной одержимости» (`topObsession`):**
   - Трек с максимальным количеством воспроизведений (`playCountInPeriod >= 3`) в выбранном периоде. Если таких нет — возвращать `null`.
4. **Недельная активность (`weeklyActivity`):**
   - Массив из 7 элементов: Понедельник — Воскресенье с количеством сессий и суммарной длительностью.
5. **Архетипы слушателя (`ListeningArchetype`):**
   - `NIGHT_OWL` ("Ночной странник"): >35% сессий ночью (00:00-06:00).
   - `LOYAL_REPEATER` ("Верный фанат"): среднее число повторов трека > 3.0.
   - `GENRE_NOMAD` ("Кочевник жанров"): отношение уникальных артистов к трекам > 0.7.
   - `ACOUSTIC_BARD` ("Акустический бард"): >50% прослушанных треков имеют тексты/аккорды.
   - `DAY_WORKER` ("Дневной труженик"): пик прослушивания с 12:00 до 18:00.
   - `CASUAL_LISTENER` ("Меломан"): базовый сбалансированный профиль.

---

## 3. Архитектура и компоненты `WrappedScreen.kt`

Экран строится на чистом `AmoledBlack` (`#000000`) без белых подложек, с неоновыми акцентами `HyperViolet` (`#B388FF`), `CyberCyan` (`#00E5FF`), `ElectricMint` (`#00F5A0`), `AmberGold` (`#FFD700`).

### 3.1. Реактивные потоки
```kotlin
val tracks by db.musicDao().observeRecentTracks().collectAsStateWithLifecycle(emptyList())
val sessions by db.musicDao().observeAllSessions().collectAsStateWithLifecycle(emptyList())
val rawAchievements by db.achievementDao().observeAchievements().collectAsStateWithLifecycle(emptyList())
```
При изменении `timeframe` расчет `stats` и `GamificationEngine.checkAchievements` производится асинхронно в корутине `Dispatchers.Default` / `Dispatchers.IO`.

### 3.2. Компоновка экрана (блоки LazyColumn):
1. **Заголовок страницы:**
   - Текст "Музыкальный Wrapped" с градиентным бейджем периода.
2. **Сегментированный переключатель таймфреймов:**
   - 4 вкладки: `[Сегодня] [7 дней] [Месяц] [Всё время]`.
   - Активная вкладка: фон `SurfaceLevel3` с неоновой каймой `Border(1.dp, HyperViolet)` и текстом `TextPrimary`.
   - Неактивные вкладки: `SurfaceLevel1`, текст `TextSecondary`.
3. **Голографическая карточка Архетипа:**
   - Градиентная подложка (`Brush.linearGradient(HyperVioletDark, AmoledBlack)`).
   - Иконка/бейдж архетипа, крупное название ("Ночной странник", "Верный фанат" и т.д.).
   - Пояснительное описание поведения слушателя.
4. **Сетка KPI (4 карточки 2x2):**
   - Общее время: в формате `Xч Yмин` (акцент `CyberCyan`).
   - Уникальных треков: число (акцент `ElectricMint`).
   - Топ-артист периода: имя исполнителя и количество треков (акцент `HyperViolet`).
   - Покрытие караоке: процент треков с текстами (акцент `AmberGold`).
5. **Карточка «Главная одержимость» (если есть повторы):**
   - Заголовок с неоновой вспышкой "🔥 Главная одержимость периода".
   - `AlbumArtThumbnail` размером 56dp со скруглением 12dp.
   - Название трека, артист, бейдж "xN повторов" и общее время зацикливания.
6. **Суточный и недельный пульс (Компас):**
   - 4 слота времени суток (Ночь, Утро, День, Вечер) в виде горизонтальных цветных индикаторов с процентами.
   - 7 вертикальных микро-столбиков активности (Пн - Вс).
7. **Топ-5 треков и Топ-5 артистов:**
   - Ранжированный список с пропорциональными прогресс-барами частоты воспроизведения.
   - В списке треков отображается мини-обложка альбома (`AlbumArtThumbnail` 36dp).
8. **Витрина достижений (26 бейджей):**
   - Сводный индикатор общего прогресса: "Открыто X из 26 (Y%)" с `LinearProgressIndicator(ElectricMint)`.
   - Чипы фильтрации статуса: `Все (26)`, `Открытые`, `В процессе`.
   - Чипы фильтрации по тирам: `Все`, `🥉 Бронза`, `🥈 Серебро`, `🥇 Золото`, `💎 Платина`.
   - Карточки достижений:
     - Окантовка цветом тира (`#CD7F32`, `#C0C0C0`, `#FFD700`, `#00E5FF`).
     - Иконка разблокировки / замок.
     - Название, описание, прогресс-бар `currentProgress / maxProgress`.
     - Дата открытия для полученных ачивок.

---

## 4. Критерии приемки (DoD)
1. Команда `./gradlew test` завершается успешно:
   - Добавлены тесты в `WrappedStatsEngineTest.kt` для всех 4 таймфреймов (`TODAY`, `WEEK`, `MONTH`, `ALL_TIME`).
   - Проверено корректное определение `topObsession` и распределения времени.
2. В интерфейсе `WrappedScreen.kt`:
   - Мгновенное переключение между таймфреймами без лагов и зависаний интерфейса.
   - Отображение реальных сессий из базы данных (без хардкода `emptyList()`).
   - Фильтры витрины достижений корректно фильтруют список по статусу и по 4 тирам.
   - Использование `AlbumArtThumbnail` для визуала обложек.
