# Задача TASK-WRP-01: реализовать `calculateStats`

**Файл:** `app/src/main/java/com/eventengine/app/analytics/WrappedStatsEngine.kt` (создать)
**Спека:** [.sdd/specs/wrapped-analytics/overview.md#calculateStats](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/wrapped-analytics/overview.md) (v1), контракт `contracts/core__wrapped.md`

**Сигнатура (НЕ МЕНЯТЬ):**
```kotlin
fun calculateStats(
    tracks: List<MusicTrackEntity>,
    sessions: List<MusicListeningSessionEntity>
): WrappedStats
```

**Модель данных:**
```kotlin
data class WrappedStats(
    val totalListeningTimeMs: Long,
    val uniqueTracksCount: Int,
    val uniqueArtistsCount: Int,
    val topArtists: List<TopArtistItem>,
    val topTracks: List<TopTrackItem>,
    val timeOfDayDistribution: Map<TimeOfDaySlot, Float>, // NIGHT, MORNING, AFTERNOON, EVENING
    val archetype: ListeningArchetype
)

data class TopArtistItem(val artist: String, val playCount: Int, val durationMs: Long)
data class TopTrackItem(val title: String, val artist: String, val playCount: Int, val durationMs: Long)
enum class TimeOfDaySlot { NIGHT, MORNING, AFTERNOON, EVENING }
enum class ListeningArchetype { NIGHT_DREAMER, MARATHON_RUNNER, DISCOVERY_HUNTER, CASUAL_LISTENER }
```

**Поведение:**
1. Если `tracks` и `sessions` пусты:
   - Вернуть пустую модель со всеми нулевыми значениями и архетипом `CASUAL_LISTENER`.
2. `totalListeningTimeMs`: сумма `durationMs` по всем сессиям из `sessions`.
3. `uniqueTracksCount`: количество уникальных `trackKey` в `tracks`.
4. `uniqueArtistsCount`: количество уникальных нормализованных `artist` в `tracks`.
5. `topArtists`: агрегация `playCount` и длительности по `artist`, сортировка по убыванию, выбор топ-5.
6. `topTracks`: агрегация по трекам, сортировка по `playCount` убывающе, выбор топ-5.
7. `timeOfDayDistribution`: классификация времени старта каждой сессии (`startedAt`):
   - 00:00 .. 05:59 $\to$ `NIGHT`
   - 06:00 .. 11:59 $\to$ `MORNING`
   - 12:00 .. 17:59 $\to$ `AFTERNOON`
   - 18:00 .. 23:59 $\to$ `EVENING`
   Вычисление доли каждой части дня (0.0 .. 1.0).
8. `archetype`:
   - Если доля `NIGHT` > 40% $\to$ `NIGHT_DREAMER`.
   - Если средняя длина сессии > 45 минут $\to$ `MARATHON_RUNNER`.
   - Если отношение `uniqueTracksCount / totalSessions` > 0.8 $\to$ `DISCOVERY_HUNTER`.
   - Иначе $\to$ `CASUAL_LISTENER`.

**Критерий приёмки:**
- Проходят unit-тесты `WrappedStatsEngineTest::test_calculate_stats_*`.
