# Задача TASK-BUG-01: Восстановление цепочки персистентности медиа в Room v6 (BUG-001)

- **ID задачи:** `TASK-BUG-01`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ingress` / `media-core`
- **Приоритет:** CRITICAL (Блокер работы Истории, Библиотеки и Wrapped)
- **Спека:** [.sdd/specs/media-core/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/overview.md) (v1), [.sdd/contracts/ingress__core.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/ingress__core.md) (FROZEN v1)

---

## 1. Диагноз и точка обрыва (RCA)

После удаления устаревших классов (TASK-PURGE-01) вызовы сохранения в базу данных Room оказались полностью разорваны в трёх точках:

1. **`app/src/main/java/com/eventengine/app/ingestion/MediaSessionCollector.kt`:**
   Метод `persistMediaEvent(...)` (строки 124–173) после схлопывания дебаунса 600 мс создавал устаревший объект `Event`, логировал его в `Timber.i` и вызывал `MusicLyricsNotificationManager.showLyricsPrompt(...)`, **но нигде не обращался к `MusicDao` или `MusicFeatureEngine`**. Старый вызов `db.eventDao().insertEvent()` был удален, а вызов записи в `music_tracks` и `music_listening_sessions` не был подключен.
2. **`app/src/main/java/com/eventengine/app/ingestion/NotificationListener.kt`:**
   Метод `processNotification(...)` (строки 67–118) фильтровал не-медиа уведомления и парсил метаданные, но **вообще не имел кода записи в базу данных Room**.
3. **`app/src/main/java/com/eventengine/app/feature/MusicFeatureEngine.kt`:**
   Существующий метод `processMusicEvent(event: Event, ...)` завязан на удаляемую модель `Event` и нигде в кодовой базе не вызывался (`0` вызовов).
4. **`app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt`:**
   При переходе по нотификации генерировал неканонический `trackKey = "$artist||$title"` вместо канонического SHA-256 хэша, и не сохранял трек в `musicDao`.

---

## 2. Спецификация решения

### 2.1 Изменение 1: Добавление канонического метода в `MusicFeatureEngine.kt`

**Файл:** `app/src/main/java/com/eventengine/app/feature/MusicFeatureEngine.kt`
**Место в файле:** добавить публичный метод после конструктора класса

**Сигнатура:**
```kotlin
suspend fun recordPlaybackSignal(
    title: String,
    artist: String,
    album: String = "",
    sourcePackage: String,
    playbackState: String = "PLAYING",
    durationDeltaMs: Long = 0L,
    timestamp: Long = System.currentTimeMillis()
): TrackEntity?
```

**Поведение:**
1. Если `title.isBlank()` $\to$ вернуть `null`.
2. Очистить `cleanArtist = artist.ifBlank { "Unknown Artist" }`.
3. Вычислить канонический `trackKey = computeTrackKey(title, cleanArtist, album)`.
4. Запросить существующий трек: `val existingTrack = musicDao.getTrackByKey(trackKey)`.
5. Запросить последнюю сессию для пакета: `val lastSession = musicDao.getLastSessionForPackage(sourcePackage)`.
6. Проверить интервал:
   - `isNewTrack = (lastSession == null || lastSession.trackKey != trackKey)`.
   - `isGapLarge = (lastSession != null && (timestamp - lastSession.endTimeMs) > SESSION_GAP_THRESHOLD_MS)`.
7. Рассчитать приращение `effectiveDuration`:
   - Если `durationDeltaMs > 0L` $\to durationDeltaMs$.
   - Иначе если `lastSession != null && !isNewTrack && !isGapLarge` $\to \max(0L, timestamp - lastSession.endTimeMs)$.
   - Иначе $\to 0L$.
8. Обновить или создать сессию:
   - Если `lastSession != null && !isNewTrack && !isGapLarge`:
     ```kotlin
     val updated = lastSession.copy(
         endTimeMs = timestamp,
         durationMs = lastSession.durationMs + effectiveDuration,
         isCompleted = (playbackState == "PAUSED" || playbackState == "STOPPED")
     )
     musicDao.updateSession(updated)
     ```
   - Иначе:
     ```kotlin
     val newSession = ListeningSessionEntity(
         trackKey = trackKey,
         sourcePackage = sourcePackage,
         startTimeMs = timestamp,
         endTimeMs = timestamp + effectiveDuration,
         durationMs = effectiveDuration,
         isCompleted = false
     )
     musicDao.insertSession(newSession)
     ```
9. Рассчитать счетчики:
   - `shouldIncrement = (existingTrack == null) || isNewTrack || isGapLarge`.
   - `updatedPlayCount = (existingTrack?.playCount ?: 0) + (if (shouldIncrement) 1 else 0)`.
   - `updatedTotalDuration = (existingTrack?.totalDurationMs ?: 0L) + effectiveDuration`.
   - `isFavorite = (existingTrack?.isFavorite == true) || (updatedPlayCount >= FAVORITE_MIN_PLAY_COUNT) || (updatedTotalDuration >= FAVORITE_MIN_DURATION_MS)`.
10. Собрать и сохранить `TrackEntity`:
    ```kotlin
    val track = TrackEntity(
        trackKey = trackKey,
        title = title,
        artist = cleanArtist,
        album = album,
        sourcePackage = sourcePackage,
        playCount = updatedPlayCount,
        totalDurationMs = updatedTotalDuration,
        firstPlayedAt = existingTrack?.firstPlayedAt ?: timestamp,
        lastPlayedAt = timestamp,
        userNotes = existingTrack?.userNotes.orEmpty(),
        isFavorite = isFavorite
    )
    musicDao.insertOrUpdateTrack(track)
    ```
11. Вернуть `track`.

---

### 2.2 Изменение 2: Встраивание вызова в `MediaSessionCollector.kt`

**Файл:** `app/src/main/java/com/eventengine/app/ingestion/MediaSessionCollector.kt`
**Место в файле:** внутри `persistMediaEvent(...)` (после проверки дедупликации `lastTrackMap`)

**Код вызова:**
```kotlin
val db = AppDatabase.getInstance(context)
val musicEngine = com.lirix.app.feature.MusicFeatureEngine(db.musicDao())

if (!title.isNullOrBlank()) {
    val cleanArtist = artist.orEmpty().ifBlank { "Unknown Artist" }
    musicEngine.recordPlaybackSignal(
        title = title,
        artist = cleanArtist,
        album = album.orEmpty(),
        sourcePackage = packageName,
        playbackState = stateName,
        timestamp = now
    )
}
```

---

### 2.3 Изменение 3: Встраивание вызова в `NotificationListener.kt`

**Файл:** `app/src/main/java/com/eventengine/app/ingestion/NotificationListener.kt`
**Место в файле:** внутри `processNotification(...)` в блоке `if (isOngoing)`

**Код вызова:**
```kotlin
if (isOngoing) {
    val parsed = com.lirix.app.feature.MusicTrackParser.parse(
        mediaTrack = null,
        mediaArtist = null,
        title = title,
        text = text
    )
    if (parsed.title.isNotBlank()) {
        val db = AppDatabase.getInstance(applicationContext)
        val musicEngine = com.lirix.app.feature.MusicFeatureEngine(db.musicDao())
        musicEngine.recordPlaybackSignal(
            title = parsed.title,
            artist = parsed.artist.ifBlank { "Unknown Artist" },
            album = parsed.album,
            sourcePackage = packageName,
            playbackState = "PLAYING",
            timestamp = now
        )

        com.lirix.app.feature.MusicLyricsNotificationManager.showLyricsPrompt(
            context = applicationContext,
            title = parsed.title,
            artist = parsed.artist,
            album = parsed.album
        )
    }
}
```

---

### 2.4 Изменение 4: Фоновое сохранение текста в `LyricsDao` при обнаружении

**Файл:** `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt`
**Место в файле:** в блоке `LaunchedEffect(currentTrack?.trackKey)` при получении `fetched.hasLyrics`

**Требование:**
Убедиться, что перед сохранением в `db.lyricsDao().saveLyrics(entity)` родительский `TrackEntity` гарантированно существует в `db.musicDao()` (для целостности внешнего ключа), и что `currentTrack` использует канонический `MusicFeatureEngine.computeTrackKey(title, artist, album)`.

---

## 3. Критерии приёмки (DoD для TASK-BUG-01)

1. [ ] Метод `recordPlaybackSignal` в `MusicFeatureEngine` реализован и протестирован Unit-тестом.
2. [ ] В `MediaSessionCollector` и `NotificationListener` восстановлена передача сигналов в `MusicFeatureEngine`.
3. [ ] На реальном устройстве / эмуляторе при воспроизведении любого трека (Spotify, Яндекс.Музыка, VK, YouTube Music):
   - `SELECT COUNT(*) FROM music_tracks` возвращает $\ge 1$.
   - `SELECT COUNT(*) FROM music_listening_sessions` возвращает $\ge 1$.
4. [ ] Экран «История» сразу отображает зарегистрированную сессию в хронологической ленте.
5. [ ] Экран «Библиотека» отображает сохраненный трек.
6. [ ] При открытии экрана текстов и успешной загрузке с LRCLIB/AmDm запись сохраняется в `lyrics_cache`:
   - `SELECT COUNT(*) FROM lyrics_cache` возвращает $\ge 1$.
7. [ ] Все unit-тесты проекта проходят успешно (`./gradlew testDebugUnitTest`).
