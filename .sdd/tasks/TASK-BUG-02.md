# Задача TASK-BUG-02: Автоматический фоновый prefetch и синхронизация текстов песен (lyrics_cache <-> music_tracks)

- **ID задачи:** `TASK-BUG-02`
- **Роль исполнителя:** Кодер
- **Зона:** `media-core` / `lyrics-engine` / `media-ingress` / `media-ui`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/feature/MusicFeatureEngine.kt` (исправить затирание lyrics, гидрация из кэша)
  2. `app/src/main/java/com/eventengine/app/ingestion/MediaSessionCollector.kt` (фоновый prefetch при захвате трека)
  3. `app/src/main/java/com/eventengine/app/ingestion/NotificationListener.kt` (фоновый prefetch при захвате трека)
  4. `app/src/main/java/com/eventengine/app/ui/NowPlayingScreen.kt` (dual-write в `lyrics_cache` и `music_tracks`)
  5. `app/src/test/java/com/eventengine/app/MusicDatabaseTest.kt` (тест сохранения и сохранения текстов)
- **Спека:** [.sdd/specs/media-core/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/overview.md) (v1), [.sdd/contracts/core__lyrics.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/core__lyrics.md)
- **Приоритет:** CRITICAL (Дефект: история и библиотека сохраняются без текста песни)

---

## 1. Root Cause Analysis (RCA)

Пользователь сообщил: *«История треков и библиотека сохраняются, но они сохраняются без текста.»*

Аудит кодовой базы выявил 4 взаимосвязанных дефекта:

1. **Деструктивное перезаписывание в `MusicFeatureEngine` (Data Loss Bug):**
   В методах `recordPlaybackSignal`, `processMusicEvent` и `recordPlayback` при сборке объекта `TrackEntity` поля `plainLyrics` и `syncedLyrics` не передавались и принимали значение по умолчанию `null`:
   ```kotlin
   val track = TrackEntity(
       trackKey = trackKey,
       ...
       albumArtUri = effectiveAlbumArtUri
       // plainLyrics и syncedLyrics НЕ передаются -> null!
   )
   musicDao.insertOrUpdateTrack(track)
   ```
   **Последствие:** Любое повторное воспроизведение трека (или обновление счетчика прослушиваний/длительности) безвозвратно затирало ранее загруженные тексты в таблице `music_tracks` значениями `null`.
2. **Отсутствие фонового Prefetch при первичном обнаружении трека:**
   Ни `MediaSessionCollector`, ни `NotificationListener` не запускали фоновую загрузку текстов через `AggregatedLyricsProvider`. Текст запрашивался **исключительно** если пользователь вручную переходил на экран `NowPlayingScreen`. Если трек играл в фоне или экран приложения был закрыт, текст никогда не загружался.
3. **Отсутствие Dual-Write в `NowPlayingScreen.kt`:**
   В `NowPlayingScreen.kt` при успешной загрузке текста:
   ```kotlin
   val entity = LyricsCacheEntity(...)
   db.lyricsDao().saveLyrics(entity)
   ```
   Запись производилась **только** в таблицу `lyrics_cache`, но метод `db.musicDao().updateLyrics(track.trackKey, plain, fetched.syncedLyrics)` **не вызывался**. В результате в таблице `music_tracks` поля оставались пустыми, и экраны «История» и «Библиотека» (фильтр `WITH_LYRICS`) не видели наличия текста.
4. **Отсутствие гидрации из `lyrics_cache`:**
   Если в `lyrics_cache` уже сохранен текст, `MusicFeatureEngine` при записи сигнала не проверял кэш и не заполнял `TrackEntity.plainLyrics` / `syncedLyrics`.

---

## 2. Спецификация архитектурных изменений

### 2.1 Изменения в `MusicFeatureEngine.kt`

1. **Сохранение существующих текстов и гидрация из `lyrics_cache`:**
   В конструктор `MusicFeatureEngine` передавать `db: AppDatabase` (или `lyricsDao: LyricsDao`):
   ```kotlin
   class MusicFeatureEngine(
       private val musicDao: MusicDao,
       private val lyricsDao: LyricsDao? = null,
       private val lyricsAdapter: LyricsProvider = AggregatedLyricsProvider(...)
   )
   ```
   В методе `recordPlaybackSignal`:
   ```kotlin
   // 1. Проверяем наличие текстов в существующем треке или в lyrics_cache
   val cachedLyrics = if (existingTrack?.plainLyrics == null && existingTrack?.syncedLyrics == null) {
       lyricsDao?.getLyrics(trackKey)
   } else {
       null
   }

   val effectivePlainLyrics = existingTrack?.plainLyrics 
       ?: cachedLyrics?.plainLyrics
   val effectiveSyncedLyrics = existingTrack?.syncedLyrics 
       ?: cachedLyrics?.syncedLyricsLrc

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
       isFavorite = isFavorite,
       albumArtUri = effectiveAlbumArtUri,
       plainLyrics = effectivePlainLyrics,
       syncedLyrics = effectiveSyncedLyrics
   )
   musicDao.insertOrUpdateTrack(track)
   ```
   Аналогично сохранить `plainLyrics` и `syncedLyrics` в `processMusicEvent` и статическом `recordPlayback`.

2. **Метод фонового Prefetch текста `prefetchLyricsInBackground`:**
   ```kotlin
   fun prefetchLyricsAsync(
       context: Context,
       scope: CoroutineScope,
       track: TrackEntity
   ) {
       // Если текст уже есть — повторный запрос не нужен
       if (!track.plainLyrics.isNullOrBlank() || !track.syncedLyrics.isNullOrBlank()) {
           return
       }

       val policy = NetworkConditionManager.getNetworkPolicy(context)
       if (policy == LyricsNetworkPolicy.OFFLINE_ONLY) {
           Timber.tag(TAG).d("Skipping lyrics prefetch: OFFLINE_ONLY")
           return
       }

       scope.launch(Dispatchers.IO) {
           try {
               // Проверяем кэш Room
               val cached = lyricsDao?.getLyrics(track.trackKey)
               if (cached != null && (!cached.plainLyrics.isNullOrBlank() || !cached.syncedLyricsLrc.isNullOrBlank())) {
                   musicDao.updateLyrics(track.trackKey, cached.plainLyrics, cached.syncedLyricsLrc)
                   return@launch
               }

               // Загружаем через агрегатор (LrcLib -> Scrapers)
               val fetched = lyricsAdapter.getLyrics(track)
               if (fetched.hasLyrics) {
                   val plain = fetched.plainLyrics ?: fetched.lyricsText
                   val synced = fetched.syncedLyrics

                   // 1. Dual-write в lyrics_cache
                   lyricsDao?.saveLyrics(
                       LyricsCacheEntity(
                           trackKey = track.trackKey,
                           plainLyrics = plain,
                           syncedLyricsLrc = synced,
                           chordsAmDm = AmDmChordParser.parseAmDmHtml(plain),
                           userNotes = "",
                           provider = fetched.source
                       )
                   )

                   // 2. Dual-write в music_tracks
                   musicDao.updateLyrics(track.trackKey, plain, synced)
                   Timber.tag(TAG).i("Successfully prefetched lyrics for: %s", track.title)
               }
           } catch (e: Exception) {
               Timber.tag(TAG).w(e, "Background lyrics prefetch failed for %s", track.title)
           }
       }
   }
   ```

### 2.2 Изменения в `MediaSessionCollector.kt` и `NotificationListener.kt`

В методе `persistMediaEvent` (`MediaSessionCollector.kt`):
```kotlin
val recordedTrack = musicEngine.recordPlaybackSignal(...)

// Фоновый запуск prefetch текстов при обнаружении нового трека
if (recordedTrack != null) {
    musicEngine.prefetchLyricsAsync(
        context = context,
        scope = scope,
        track = recordedTrack
    )
}
```

Аналогично в `NotificationListener.kt`:
```kotlin
val recordedTrack = musicEngine.recordPlaybackSignal(...)
if (recordedTrack != null) {
    musicEngine.prefetchLyricsAsync(
        context = applicationContext,
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        track = recordedTrack
    )
}
```

### 2.3 Изменения в `NowPlayingScreen.kt`

В блоке `LaunchedEffect(currentTrack?.trackKey)`:
```kotlin
val cached = db.lyricsDao().getLyrics(track.trackKey)
if (cached != null) {
    lyricsCache = cached
    // Синхронизируем с music_tracks, если там было пусто
    if (track.plainLyrics == null && track.syncedLyrics == null) {
        db.musicDao().updateLyrics(track.trackKey, cached.plainLyrics, cached.syncedLyricsLrc)
    }
    isLoadingLyrics = false
} else {
    val fetched = AggregatedLyricsProvider().getLyrics(track)
    if (fetched.hasLyrics) {
        val plain = fetched.plainLyrics ?: fetched.lyricsText
        val synced = fetched.syncedLyrics
        val entity = LyricsCacheEntity(
            trackKey = track.trackKey,
            plainLyrics = plain,
            syncedLyricsLrc = synced,
            chordsAmDm = AmDmChordParser.parseAmDmHtml(plain),
            userNotes = "",
            provider = fetched.source
        )
        // 1. Сохраняем в lyrics_cache
        db.lyricsDao().saveLyrics(entity)
        // 2. Dual-Write: сохраняем в music_tracks!
        db.musicDao().updateLyrics(track.trackKey, plain, synced)
        lyricsCache = entity
    }
    isLoadingLyrics = false
}
```

---

## 3. Критерии приемки (DoD)

1. **Сохранение текстов при воспроизведении:**
   - Последующие вызовы `recordPlaybackSignal` для того же трека **не затирают** `plainLyrics` и `syncedLyrics` в `music_tracks`.
2. **Фоновая загрузка:**
   - При появлении нового трека в MediaSession в фоне (даже если пользователь не заходил в NowPlaying), инициируется сетевой запрос к `AggregatedLyricsProvider` (при наличии Wi-Fi/Cellular).
   - Загруженные тексты сразу попадают в `lyrics_cache` и `music_tracks`.
3. **Отображение в списках:**
   - В экранах `HistoryScreen` и `LibraryScreen` треки с текстами корректно помечаются бейджами "LRC" / "Текст", и фильтр «С текстами» (`WITH_LYRICS`) возвращает все найденные треки.
4. **Тесты:**
   - Все 80+ unit-тестов проходят (`./gradlew test`).
   - Добавлен тест в `MusicDatabaseTest.kt`, проверяющий, что повторная запись трека через `recordPlaybackSignal` сохраняет имеющиеся `plainLyrics` и `syncedLyrics`.
