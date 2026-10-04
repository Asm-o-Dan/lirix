# Задача TASK-BUG-03: Точный динамический расчет времени прослушивания и учет активной сессии в реальном времени

- **ID задачи:** `TASK-BUG-03`
- **Роль исполнителя:** Кодер
- **Зона:** `media-core` / `wrapped-analytics` / `media-ingress` / `media-ui`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/feature/MusicFeatureEngine.kt` (закрытие предыдущей сессии, метод `updateActiveSessionProgress`)
  2. `app/src/main/java/com/eventengine/app/ingestion/MediaSessionCollector.kt` (5-секундный фоновый heartbeat активной сессии)
  3. `app/src/main/java/com/eventengine/app/analytics/WrappedStatsEngine.kt` (экстраполяция активной сессии в `totalListeningTimeMs`)
  4. `app/src/main/java/com/eventengine/app/ui/WrappedScreen.kt` (передача `livePlayback` и живой тикер)
  5. `app/src/test/java/com/eventengine/app/WrappedStatsEngineTest.kt` (тесты экстраполяции времени активной сессии)
- **Спека:** [.sdd/specs/wrapped-analytics/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/wrapped-analytics/overview.md) (v2), [.sdd/specs/media-core/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/overview.md) (v1)
- **Приоритет:** CRITICAL (Дефект пользователя: *«Хреново считает общее время прослушивания: играет уже 5 минут, а написано 2 минуты»*)

---

## 1. Root Cause Analysis (RCA)

Пользователь выявил систематическое отставание времени прослушивания: при непрерывном воспроизведении трека в течение 5 минут отображается всего 2 минуты.

Глубокий аудит выявил 4 архитектурных дефекта:

1. **Дедупликационный барьер в `MediaSessionCollector.kt` (строки 213–217):**
   ```kotlin
   val trackKey = "$packageName|$title|$artist|$stateName"
   if (lastTrackMap[packageName] == trackKey) {
       return // Пропуск дублирующего идентичного состояния
   }
   lastTrackMap[packageName] = trackKey
   ```
   Пока трек играет 3–5–10 минут в одном и том же состоянии (`PLAYING`), метод `persistMediaEvent` **вообще больше не вызывается** из-за фильтра дедупликации!
2. **Сессия замораживается на `durationMs = 0L` при старте трека:**
   В `MusicFeatureEngine.kt` сессия для нового трека создается со значением `durationMs = 0L`. Поскольку из-за пункта 1 последующие события воспроизведения блокируются дедупликацией, сессия в базе данных Room **не получает обновлений времени в течение всего звучания песни**.
3. **Зависание сессий при смене трека (Orphaned Sessions Bug):**
   При переключении на новый трек (`isNewTrack == true`) блок обновления текущей сессии пропускался, а сессия предыдущего трека (`lastSession`) оставалась с нулевой или устаревшей длительностью, никогда не закрываясь. Время прослушивания предыдущего трека безвозвратно терялось.
4. **Статическое суммирование в `WrappedStatsEngine`:**
   В `WrappedStatsEngine.kt`:
   ```kotlin
   totalListeningTimeMs = validSessions.sumOf { it.durationMs }
   ```
   Движок вслепую складывал поле `durationMs` из базы. Так как текущая сессия имеет `durationMs = 0L`, все текущие минуты прослушивания трека **полностью отсутствуют в статистике**!


---

## 2. Спецификация архитектурных исправлений

### 2.1 Изменения в `MusicFeatureEngine.kt`

#### 1. Корректное закрытие сессии предыдущего трека при смене трека:
В методе `recordPlaybackSignal`:
```kotlin
if (isNewTrack && lastSession != null && !lastSession.isCompleted) {
    // Вычисляем реально прошедшее время звучания предыдущего трека
    val finalDuration = if (lastSession.durationMs > 0L) {
        val delta = (timestamp - lastSession.endTimeMs).coerceIn(0L, MAX_SINGLE_TRACK_GAP_MS)
        lastSession.durationMs + delta
    } else {
        (timestamp - lastSession.startTimeMs).coerceIn(0L, MAX_SINGLE_TRACK_GAP_MS)
    }

    val closedSession = lastSession.copy(
        endTimeMs = timestamp,
        durationMs = finalDuration,
        isCompleted = true
    )
    musicDao.updateSession(closedSession)

    // Кредитуем финальную дельту в totalDurationMs предыдущего трека
    val previousTrack = musicDao.getTrackByKey(lastSession.trackKey)
    if (previousTrack != null) {
        val durationDelta = (finalDuration - lastSession.durationMs).coerceAtLeast(0L)
        if (durationDelta > 0L) {
            musicDao.insertOrUpdateTrack(
                previousTrack.copy(
                    totalDurationMs = previousTrack.totalDurationMs + durationDelta,
                    lastPlayedAt = timestamp
                )
            )
        }
    }
}
```
*Константа:* `MAX_SINGLE_TRACK_GAP_MS = 30 * 60 * 1000L` (30 минут — защита от начисления времени, если плеер был убит системой без оповещения).

#### 2. Метод периодической синхронизации прогресса `updateActiveSessionProgress`:
```kotlin
suspend fun updateActiveSessionProgress(
    sourcePackage: String,
    currentPlaybackPositionMs: Long? = null,
    timestamp: Long = System.currentTimeMillis()
) {
    val lastSession = musicDao.getLastSessionForPackage(sourcePackage) ?: return
    if (lastSession.isCompleted) return

    // Определяем прогресс: либо точная позиция плеера, либо дельта времени от старта
    val effectiveDuration = if (currentPlaybackPositionMs != null && currentPlaybackPositionMs > 0L) {
        currentPlaybackPositionMs
    } else {
        (timestamp - lastSession.startTimeMs).coerceIn(0L, MAX_SINGLE_TRACK_GAP_MS)
    }

    if (effectiveDuration > lastSession.durationMs) {
        val durationDelta = effectiveDuration - lastSession.durationMs
        val updatedSession = lastSession.copy(
            endTimeMs = timestamp,
            durationMs = effectiveDuration
        )
        musicDao.updateSession(updatedSession)

        val track = musicDao.getTrackByKey(lastSession.trackKey)
        if (track != null) {
            musicDao.insertOrUpdateTrack(
                track.copy(
                    totalDurationMs = track.totalDurationMs + durationDelta,
                    lastPlayedAt = timestamp
                )
            )
        }
    }
}
```

---

### 2.2 Изменения в `MediaSessionCollector.kt` (Heartbeat Ticker)

Запуск периодического корутинного тикера каждые **5 секунд**, пока `livePlayback.isPlaying == true`:
```kotlin
private var heartbeatJob: kotlinx.coroutines.Job? = null

private fun syncHeartbeat(isPlaying: Boolean, packageName: String) {
    heartbeatJob?.cancel()
    if (!isPlaying) return

    heartbeatJob = scope.launch {
        while (isActive) {
            delay(5000L)
            try {
                val snapshot = _livePlaybackFlow.value
                if (snapshot != null && snapshot.isPlaying && snapshot.packageName == packageName) {
                    val currentPos = snapshot.currentPositionMs()
                    val db = AppDatabase.getInstance(context)
                    val engine = MusicFeatureEngine(db.musicDao(), db.lyricsDao())
                    engine.updateActiveSessionProgress(
                        sourcePackage = packageName,
                        currentPlaybackPositionMs = currentPos,
                        timestamp = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Error updating heartbeat session progress")
            }
        }
    }
}
```
Вызывать `syncHeartbeat(isPlaying, controller.packageName)` в:
- `handlePlaybackChange`
- `handleMetadataChange`
- `togglePlayPause`

---

### 2.3 Изменения в `WrappedStatsEngine.kt` (Динамическая экстраполяция активной сессии)

Расширить метод `calculateStats`:
```kotlin
fun calculateStats(
    tracks: List<MusicTrackEntity>,
    sessions: List<MusicListeningSessionEntity>,
    timeframe: AnalyticsTimeframe = AnalyticsTimeframe.ALL_TIME,
    referenceTimestampMs: Long = System.currentTimeMillis(),
    livePlayback: LivePlaybackSnapshot? = null
): WrappedStats
```

#### Вспомогательная функция вычисления эффективной длительности сессии:
```kotlin
fun calculateEffectiveSessionDuration(
    session: MusicListeningSessionEntity,
    referenceTimestampMs: Long,
    livePlayback: LivePlaybackSnapshot? = null
): Long {
    // Если сессия завершена и имеет положительную длительность — используем ее
    if (session.isCompleted && session.durationMs > 0L) {
        return session.durationMs
    }

    // Проверяем, является ли сессия активной прямо сейчас
    val isLiveMatching = livePlayback != null &&
        livePlayback.isPlaying &&
        (referenceTimestampMs - livePlayback.timestamp < 60_000L)

    val isRecentUnfinished = !session.isCompleted &&
        (referenceTimestampMs - session.endTimeMs < 60_000L)

    if (isLiveMatching || isRecentUnfinished) {
        val livePos = if (isLiveMatching) livePlayback?.currentPositionMs(android.os.SystemClock.elapsedRealtime()) ?: 0L else 0L
        val elapsedWallTime = (referenceTimestampMs - session.startTimeMs).coerceIn(0L, 4 * 3600 * 1000L)
        return maxOf(session.durationMs, livePos, elapsedWallTime)
    }

    return session.durationMs
}
```

#### Применение при расчете метрик:
1. `totalListeningTimeMs`:
```kotlin
totalListeningTimeMs = filteredSessions.sumOf {
    calculateEffectiveSessionDuration(it, referenceTimestampMs, livePlayback)
}
```
2. `sessionDurationsByTrack`:
```kotlin
val sessionDurationsByTrack = filteredSessions.groupBy { it.trackKey }
    .mapValues { (_, list) ->
        list.sumOf { calculateEffectiveSessionDuration(it, referenceTimestampMs, livePlayback) }
    }
```
3. `topObsession` и `topTracks`: длительность также агрегируется с учетом экстраполированных миллисекунд.

---

### 2.4 Изменения в `WrappedScreen.kt`

1. Подписка на живой поток медиа-сессии:
```kotlin
val livePlayback by MediaSessionCollector.livePlaybackFlow.collectAsStateWithLifecycle(null)
```
2. Передача `livePlayback` в `WrappedStatsEngine.calculateStats`:
```kotlin
LaunchedEffect(tracks, sessions, rawAchievements, selectedTimeframe, livePlayback?.isPlaying) {
    withContext(Dispatchers.Default) {
        val calculated = WrappedStatsEngine.calculateStats(
            tracks = tracks,
            sessions = sessions,
            timeframe = selectedTimeframe,
            referenceTimestampMs = System.currentTimeMillis(),
            livePlayback = livePlayback
        )
        stats = calculated
        ...
    }
}
```
3. Живой тикер обновления (каждые 3 секунды, когда `livePlayback?.isPlaying == true`), чтобы общее время на экране Wrapped увеличивалось прямо на глазах у пользователя, пока играет музыка:
```kotlin
LaunchedEffect(livePlayback?.isPlaying) {
    if (livePlayback?.isPlaying == true) {
        while (isActive) {
            delay(3000L)
            withContext(Dispatchers.Default) {
                stats = WrappedStatsEngine.calculateStats(
                    tracks = tracks,
                    sessions = sessions,
                    timeframe = selectedTimeframe,
                    referenceTimestampMs = System.currentTimeMillis(),
                    livePlayback = livePlayback
                )
            }
        }
    }
}
```

---

## 3. Критерии приемки (DoD)

1. **Unit-тесты в `WrappedStatsEngineTest.kt`:**
   - `testCalculateStats_extrapolatesActiveUnfinishedSession`: проверяет, что незавершенная сессия с `durationMs = 0L`, начатая 5 минут назад, добавляет ровно 300 000 мс в `totalListeningTimeMs`.
   - `testCalculateStats_usesLivePlaybackPositionForCurrentSession`: проверяет приоритет реальной позиции `livePlayback.currentPositionMs()`.
   - Все предыдущие 80+ тестов проходят без регрессий.
2. **Интеграционное поведение:**
   - При воспроизведении трека в течение 5 минут общее время на экране Wrapped показывает актуальные 5 минут (а не старые 2 минуты).
   - При смене трека A на B длительность трека A сохраняется в Room полностью, а не сбрасывается в 0.
   - Во время непрерывного воспроизведения `MediaSessionCollector` каждые 5 секунд коммитит актуальный прогресс в Room.
