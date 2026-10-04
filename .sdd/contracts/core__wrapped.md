# Межзонный контракт: core__wrapped

- **Зоны:** `media-core` (поставщик истории и сессий) ──> `wrapped-analytics` (движок аналитики и ачивок)
- **Версия:** FROZEN v1
- **Дата фиксации:** 2026-10-01
- **Статус:** FROZEN
- **Нормативная база:** [architecture.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture.md), ADR-002, ADR-003

---

## 1. Назначение контракта

Контракт определяет формат чтения агрегированных данных прослушивания из `media-core`, расчет локальных метрик Music Wrapped и условия детерминированной разблокировки 7 геймификационных достижений.

---

## 2. Типы данных

### 2.1 Диапазон выборки `WrappedTimeRange`
```kotlin
enum class WrappedTimeRange {
    LAST_30_DAYS,
    CURRENT_YEAR,
    ALL_TIME
}
```

### 2.2 Архетипы слушателя `ListeningArchetype`
```kotlin
enum class ListeningArchetype(val titleRu: String, val descriptionRu: String) {
    NIGHT_OWL("Ночной странник", "Большинство треков звучат глубокой ночью"),
    LOYAL_REPEATER("Преданный слушатель", "Один любимый артист звучит чаще всех остальных вместе взятых"),
    GENRE_NOMAD("Музыкальный исследователь", "Сотни разных исполнителей и постоянный поиск нового"),
    ACOUSTIC_BARD("Акустический бард", "Частый просмотр гитарных аккордов и текстов под гитару"),
    DAY_WORKER("Ритмичный труженик", "Музыка сопровождает рабочие дневные часы")
}
```

### 2.3 Сводка аналитики `WrappedStats`
```kotlin
data class WrappedStats(
    val timeRange: WrappedTimeRange,
    val totalListeningTimeMs: Long,
    val totalTracksPlayed: Int,
    val uniqueArtistsCount: Int,
    val topArtists: List<ArtistStat>,
    val topTracks: List<TrackStat>,
    val peakHourOfDay: Int,          // 0..23
    val archetype: ListeningArchetype,
    val calculatedAtMs: Long = System.currentTimeMillis()
)

data class ArtistStat(
    val artistName: String,
    val playCount: Int,
    val durationMs: Long,
    val percentageOfTotalTime: Float // 0.0 .. 100.0
)

data class TrackStat(
    val trackKey: String,
    val title: String,
    val artist: String,
    val playCount: Int,
    val durationMs: Long
)
```

### 2.4 Спецификация 7 достижений (`AchievementSpec`)

| ID ачивки | Название | Описание | Триггер / Условие разблокировки | Макс. прогресс |
|---|---|---|---|---|
| `ach_first_track` | «Первый бит» | Зафиксировано первое прослушивание трека | `COUNT(music_listening_sessions) >= 1` | 1 |
| `ach_centurion` | «Сотник» | Прослушано 100 уникальных треков | `COUNT(music_tracks WHERE playCount >= 1) >= 100` | 100 |
| `ach_marathoner` | «Марафонец» | Непрерывное прослушивание музыки $> 5$ часов за календарные сутки | `SUM(durationMs в пределах одних суток) >= 18_000_000 мс` | 18000000 |
| `ach_karaoke_star` | «Мастер караоке» | Просмотрено 10 треков с синхронизированным LRC-караоке | Открытие экрана плеера с активным `syncedLyrics != null` для 10 уникальных треков | 10 |
| `ach_campfire_singer` | «Песни у костра» | Просмотрено 5 песен с аккордами AmDm | Открытие экрана аккордов для 5 уникальных треков | 5 |
| `ach_night_owl` | «Ночной странник» | Прослушано 10 треков в интервале 01:00 – 05:00 ночи | `COUNT(сессий с startTimeMs между 01:00 и 05:00) >= 10` | 10 |
| `ach_loyal_repeater` | «Преданный слушатель» | Один трек воспроизведён $\ge 25$ раз | `MAX(music_tracks.playCount) >= 25` | 25 |

---

## 3. Гарантии провайдера аналитики (`wrapped-analytics`)

1. **100% On-Device Privacy Guarantee:**
   - Ни один байт аналитики, названий треков, времени или статистики не отправляется по сети. Все расчеты производятся локально в фоновом потоке базы данных SQLite.
2. **Изоляция сессий от внешних сбоев:**
   - Если сессия была прервана выключением устройства, расчет защищен: сессии без `endTimeMs` нормализуются ограничением по тайм-ауту 5 минут.
3. **Детерминизм вычисления архетипа:**
   - Если $> 40\%$ времени приходится на 01:00–05:00 $\to$ `NIGHT_OWL`.
   - Иначе если топ-1 артист $> 45\%$ всего времени $\to$ `LOYAL_REPEATER`.
   - Иначе если просмотрено $\ge 5$ треков с аккордами $\to$ `ACOUSTIC_BARD`.
   - Иначе если уникальных артистов $> 30$ при среднем `playCount < 2` $\to$ `GENRE_NOMAD`.
   - Иначе $\to$ `DAY_WORKER`.

---

## 4. Публичный API интеграции

```kotlin
interface WrappedAnalyticsContract {
    /**
     * Вычисление полного отчёта Wrapped за выбранный период.
     */
    suspend fun getWrappedStats(range: WrappedTimeRange): WrappedStats

    /**
     * Оценка текущего прогресса всех 7 ачивок и обновление таблицы achievements.
     * Возвращает список только что разблокированных достижений (для показа snackbar/toast).
     */
    suspend fun evaluateAchievements(): List<AchievementEntity>

    /**
     * Реактивный поток всех достижений с текущим прогрессом.
     */
    fun observeAchievements(): Flow<List<AchievementEntity>>
}
```

---

## 5. Примеры вызовов и результатов

### Пример 1 (Оценка первого трека):
- Состояние БД: вставлена первая запись в `music_listening_sessions`.
- Вызов: `evaluateAchievements()`
- Результат: возвращает `listOf(AchievementEntity(id="ach_first_track", isUnlocked=true, currentProgress=1))`

### Пример 2 (Расчет Wrapped за 30 дней для активного слушателя):
- Вызов: `getWrappedStats(WrappedTimeRange.LAST_30_DAYS)`
- Результат: `WrappedStats(totalListeningTimeMs=36000000, totalTracksPlayed=150, uniqueArtistsCount=12, topArtists=[ArtistStat("Кино", 80, 20000000, 55.5f)], peakHourOfDay=23, archetype=ListeningArchetype.LOYAL_REPEATER)`
