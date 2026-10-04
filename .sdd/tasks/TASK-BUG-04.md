# Задача TASK-BUG-04: Устранение аномального разгона времени Wrapped (242 часа за день) и строгая изоляция активной сессии

- **ID задачи:** `TASK-BUG-04`
- **Роль исполнителя:** Кодер
- **Зона:** `wrapped-analytics` / `media-core`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/analytics/WrappedStatsEngine.kt` (строгая изоляция активной сессии, устранение глобальной экстраполяции)
  2. `app/src/main/java/com/eventengine/app/feature/MusicFeatureEngine.kt` (ограничение длительности сессии длительностью трека)
  3. `app/src/test/java/com/eventengine/app/WrappedStatsEngineTest.kt` (тест на отсутствие разгона времени при 100 старых сессиях)
- **Спека:** [.sdd/specs/wrapped-analytics/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/wrapped-analytics/overview.md) (v2)
- **Приоритет:** BLOCKER / CRITICAL (Баг: 242 часа прослушивания за один день и прирост «секунды за минуты»)

---

## 1. Точный Root Cause Analysis (RCA)

Пользователь прислал скриншот: общее время за «Сегодня» показывает **242ч 15м**, и каждые 3 секунды добавляется по несколько минут.

### Механика дефекта в `WrappedStatsEngine.kt`:
В функции `calculateEffectiveSessionDuration`:
```kotlin
val isLiveMatching = livePlayback != null &&
    livePlayback.isPlaying &&
    (referenceTimestampMs - livePlayback.timestamp < 60_000L || livePlayback.timestamp == referenceTimestampMs)

val isRecentUnfinished = !session.isCompleted &&
    (referenceTimestampMs - session.endTimeMs < 60_000L)

if (isLiveMatching || isRecentUnfinished) {
    val livePos = ...
    val elapsedWallTime = (referenceTimestampMs - session.startTimeMs).coerceIn(0L, 4 * 3600 * 1000L)
    return maxOf(session.durationMs, livePos, elapsedWallTime)
}
```

1. **Глобальный флаг без привязки к конкретной сессии:**
   Пока играет любой трек, условие `isLiveMatching` становилось `true` **ДЛЯ КАЖДОЙ ИЗ СОТЕН СЕССИЙ** в базе данных!
2. **Экстраполяция сотен старых незавершенных сессий:**
   Все сессии, созданные до внедрения фикса (где `isCompleted == false` или `durationMs == 0`), попадали под ветку `isLiveMatching == true`.
3. **Мультипликация времени:**
   Для каждой из 60–100 сессий вычислялось `elapsedWallTime = referenceTimestampMs - session.startTimeMs` (до 4 часов на каждую сессию!). В результате сумма давала:
   $$60 \text{ сессий} \times 4 \text{ часа} \approx 240 \text{ часов!}$$
4. **Разгон в UI («секунды за минуты»):**
   В `WrappedScreen.kt` запущен тикер с интервалом 3 секунды. Каждые 3 секунды `referenceTimestampMs` сдвигался вперед на 3 секунды. Этот сдвиг умножался на 100 сессий:
   $$100 \times 3 \text{ сек} = 300 \text{ сек} = 5 \text{ минут прироста каждые 3 секунды!}$$

---

## 2. Спецификация архитектурного исправления

### 2.1 Строгая изоляция: только ОДНА активная сессия в `WrappedStatsEngine.kt`

Экстраполяция реального времени должна применяться **СТРОГО И ИСКЛЮЧИТЕЛЬНО К ОДНОЙ ЕДИНСТВЕННОЙ АКТИВНОЙ СЕССИИ**.

В методе `calculateStats`:
```kotlin
// 1. Находим ЕДИНСТВЕННУЮ действительно активную сессию (самую последнюю незавершенную)
val activeSession: MusicListeningSessionEntity? = if (livePlayback != null && livePlayback.isPlaying) {
    val liveTrackKey = MusicFeatureEngine.computeTrackKey(livePlayback.title, livePlayback.artist, livePlayback.album)
    // Ищем последнюю сессию, совпадающую по trackKey или packageName
    filteredSessions.lastOrNull { s ->
        !s.isCompleted && (s.trackKey == liveTrackKey || s.sourcePackage == livePlayback.packageName)
    } ?: filteredSessions.lastOrNull { !it.isCompleted && (referenceTimestampMs - it.endTimeMs < 60_000L) }
} else {
    // Если плеер на паузе/остановлен — только последняя незавершенная сессия не старше 30 секунд
    filteredSessions.lastOrNull { !it.isCompleted && (referenceTimestampMs - it.endTimeMs < 30_000L) }
}

val activeSessionId: Long? = activeSession?.id
```

### 2.2 Новая детерминированная функция `calculateEffectiveSessionDuration`

```kotlin
fun calculateEffectiveSessionDuration(
    session: MusicListeningSessionEntity,
    referenceTimestampMs: Long,
    livePlayback: LivePlaybackSnapshot? = null,
    activeSessionId: Long? = null,
    trackDurationMs: Long? = null
): Long {
    // ПРАВИЛО 1: Если сессия НЕ является текущей активной — возвращаем СТРОГО сохраненный durationMs!
    if (activeSessionId == null || session.id != activeSessionId) {
        return session.durationMs
    }

    // ПРАВИЛО 2: Для ЕДИНСТВЕННОЙ активной сессии вычисляем реальный прогресс
    val livePos = if (livePlayback != null && livePlayback.isPlaying) {
        if (livePlayback.lastPositionUpdateTimeMs > 0L) {
            livePlayback.currentPositionMs()
        } else {
            livePlayback.basePositionMs
        }
    } else {
        0L
    }

    // Ограничиваем максимальную длительность одной песни (не больше длительности трека или 15 минут)
    val maxTrackCap = trackDurationMs?.takeIf { it > 0L } 
        ?: (livePlayback?.durationMs?.takeIf { it > 0L } ?: (15 * 60 * 1000L))

    val elapsedWallTime = (referenceTimestampMs - session.startTimeMs).coerceIn(0L, maxTrackCap)
    val effective = maxOf(session.durationMs, livePos, elapsedWallTime)

    return minOf(effective, maxTrackCap)
}
```

### 2.3 Применение при расчетах и математическая защита

1. **Расчет `totalListeningTimeMs`:**
```kotlin
val rawTotalListeningTimeMs = filteredSessions.sumOf { session ->
    val trackDuration = trackMap[session.trackKey]?.totalDurationMs
    calculateEffectiveSessionDuration(
        session = session,
        referenceTimestampMs = referenceTimestampMs,
        livePlayback = livePlayback,
        activeSessionId = activeSessionId,
        trackDurationMs = trackDuration
    )
}

// Защита от математического абсурда: за TODAY не может быть больше 24 часов
totalListeningTimeMs = if (timeframe == AnalyticsTimeframe.TODAY) {
    rawTotalListeningTimeMs.coerceAtMost(24 * 3600 * 1000L)
} else {
    rawTotalListeningTimeMs
}
```

2. **Расчет `sessionDurationsByTrack`:**
```kotlin
val sessionDurationsByTrack = filteredSessions.groupBy { it.trackKey }
    .mapValues { (key, list) ->
        val trackDuration = trackMap[key]?.totalDurationMs
        list.sumOf { session ->
            calculateEffectiveSessionDuration(
                session = session,
                referenceTimestampMs = referenceTimestampMs,
                livePlayback = livePlayback,
                activeSessionId = activeSessionId,
                trackDurationMs = trackDuration
            )
        }
    }
```

3. **Расчет `weeklyActivity`:**
Аналогично передавать `activeSessionId` при суммировании дневных длительностей `dayDurations[idx]`.

---

## 3. Критерии приемки (DoD)

1. **Unit-тесты в `WrappedStatsEngineTest.kt`:**
   - `testCalculateStats_withManyUnfinishedOldSessions_doesNotInflateTime`:
     Создать 100 старых незавершенных сессий (`isCompleted = false`, `durationMs = 0L`, `startTimeMs = refTime - 2 hours`).
     Запустить `calculateStats` с `livePlayback(isPlaying = true)`.
     **Утверждение:** Общее время должно учитывать ТОЛЬКО 1 активную сессию (несколько минут), а НЕ 400 часов!
   - `testCalculateStats_todayTimeframe_doesNotExceed24Hours`: проверка верхнего предела 24 часов.
2. **Интеграционная проверка:**
   - Время за «Сегодня» на реальном устройстве возвращается к адекватным значениям (например, 15-30 минут вместо 242 часов).
   - Каждые 3 секунды время прибавляется строго на 3 секунды (1 секунда реального времени = 1 секунда прослушивания), без эффекта «секунды за минуты».
   - Все unit-тесты (`./gradlew test`) завершаются со статусом SUCCESS.
