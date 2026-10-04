# Задача TASK-LYR-03-B: Каскадный опрос источников с фильтрацией отклонений в LyricsEngine

- **ID задачи:** `TASK-LYR-03-B`
- **Роль исполнителя:** Кодер
- **Зона:** `lyrics-engine` / `media-core`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/feature/LyricsProvider.kt` (канонический `sourceId`, фильтрация `rejectedSourceIds` в каскаде)
  2. `app/src/main/java/com/eventengine/app/feature/MusicFeatureEngine.kt` (методы `rejectCurrentLyrics`, `undoLyricsRejection`, `clearAllRejections`, учет в prefetch)
  3. `app/src/test/java/com/eventengine/app/LyricsRejectionCascadeTest.kt` (unit-тесты каскадного перехода и отмены)
- **Спека:** [.sdd/architecture_lyr_community.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/architecture_lyr_community.md) (Раздел 3), [.sdd/contracts/core__lyrics.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/core__lyrics.md)
- **Приоритет:** HIGH (Логика каскадного переключения и защиты от повторного показа)

---

## 1. Назначение и контекст

При отклонении пользователем текущего текста («Не тот текст») движок текстов должен:
1. Зафиксировать `sourceId` текущего источника в списке отклонений.
2. Очистить неверный текст из кэша и карточки трека.
3. Опросить **следующий** источник в цепочке (LRCLIB $\to$ AmDm $\to$ VsePesni $\to$ Scrapers), пропуская все ранее отклоненные источники для данного `trackKey`.
4. При исчерпании всех источников — вернуть статус `hasLyrics = false` с кодом `sourceId = "none"`.
5. Предоставить методы быстрой отмены (`undoLyricsRejection`) и полного сброса отклонений (`clearAllRejections`).
6. Защитить фоновый `prefetchLyricsAsync` от повторной загрузки отклонённого провайдера.

---

## 2. Спецификация изменений

### 2.1 Изменения в `feature/LyricsProvider.kt`

1. **Добавление `sourceId` в `LyricsResult`:**
   ```kotlin
   data class LyricsResult(
       val hasLyrics: Boolean,
       val lyricsText: String,
       val source: String,
       val isUserNote: Boolean = false,
       val syncedLyrics: String? = null,
       val plainLyrics: String? = null,
       val sourceId: String = "" // Канонический ID: "builtin:lrclib", "builtin:amdm", "note:user", etc.
   )
   ```

2. **Стабильные константы идентификаторов источников:**
   ```kotlin
   object LyricsSourceIds {
       const val USER_NOTE = "note:user"
       const val ROOM_CACHE = "cache:room"
       const val LRCLIB = "builtin:lrclib"
       const val AMDM = "builtin:amdm"
       const val VSE_PESNI = "builtin:vse-pesni"
       const val NONE = "none"
   }
   ```

3. **Обновление провайдеров:**
   - `OfflineLyricsAdapter`: при наличии заметок возвращает `sourceId = LyricsSourceIds.USER_NOTE`, иначе `LyricsSourceIds.NONE`.
   - `LrcLibLyricsProvider`: при успешном поиске возвращает `sourceId = LyricsSourceIds.LRCLIB`.
   - `FallbackLyricsScraper`:
     - AmDm ветка возвращает `sourceId = LyricsSourceIds.AMDM`.
     - VsePesni ветка возвращает `sourceId = LyricsSourceIds.VSE_PESNI`.

4. **Сигнатура и логика `AggregatedLyricsProvider.getLyrics`:**
   ```kotlin
   suspend fun getLyrics(
       track: TrackEntity,
       rejectedSourceIds: Set<String> = emptySet(),
       forceNetwork: Boolean = false
   ): LyricsResult
   ```
   **Поведение каскада:**
   - Шаг 1: Проверка кэша Room. Если кэш есть, но его `provider` совпадает с любым из `rejectedSourceIds` — кэш считается инвалидированным и пропускается.
   - Шаг 2: Проверка пользовательских заметок (`OfflineLyricsAdapter`). Если `LyricsSourceIds.USER_NOTE in rejectedSourceIds` — заметки пропускаются.
   - Шаг 3: LRCLIB (`LrcLibLyricsProvider`). Если `LyricsSourceIds.LRCLIB !in rejectedSourceIds` — выполнить запрос. Если текст найден — вернуть.
   - Шаг 4: AmDm скрапер. Если `LyricsSourceIds.AMDM !in rejectedSourceIds` — выполнить запрос. Если найден — вернуть.
   - Шаг 5: Резервные скраперы (VsePesni и др.). Пропускать источники, входящие в `rejectedSourceIds`.
   - Шаг 6: Если все источники отклонены или не дали результата — вернуть:
     ```kotlin
     LyricsResult(
         hasLyrics = false,
         lyricsText = "Текст не найден в доступных базах",
         source = "Система",
         sourceId = LyricsSourceIds.NONE
     )
     ```

### 2.2 Изменения в `feature/MusicFeatureEngine.kt`

1. **Метод `rejectCurrentLyrics`:**
   ```kotlin
   suspend fun rejectCurrentLyrics(trackKey: String, currentSourceId: String): LyricsResult {
       if (currentSourceId.isNotBlank() && currentSourceId != LyricsSourceIds.NONE) {
           lyricsDao?.insertRejection(
               LyricsRejectionEntity(
                   trackKey = trackKey,
                   sourceId = currentSourceId,
                   rejectedAt = System.currentTimeMillis()
               )
           )
       }
       // 1. Очищаем неверный текст из кэша
       musicDao.updateLyrics(trackKey, null, null)
       lyricsDao?.deleteCachedLyrics(trackKey) // удаление или перезапись пустым

       // 2. Ищем следующий доступный источник
       val track = musicDao.getTrackByKey(trackKey)
           ?: return LyricsResult(false, "Трек не найден", "Система", sourceId = LyricsSourceIds.NONE)

       val rejected = lyricsDao?.getRejectedSourceIds(trackKey)?.toSet().orEmpty()
       val nextResult = lyricsAdapter.getLyrics(track, rejectedSourceIds = rejected, forceNetwork = true)

       // 3. Если следующий источник найден — сохраняем его в кэш
       if (nextResult.hasLyrics) {
           val plain = nextResult.plainLyrics ?: nextResult.lyricsText
           val synced = nextResult.syncedLyrics
           lyricsDao?.saveLyrics(
               LyricsCacheEntity(
                   trackKey = trackKey,
                   plainLyrics = plain,
                   syncedLyricsLrc = synced,
                   chordsAmDm = AmDmChordParser.parseAmDmHtml(plain),
                   userNotes = track.userNotes,
                   provider = nextResult.sourceId
               )
           )
           musicDao.updateLyrics(trackKey, plain, synced)
       }
       return nextResult
   }
   ```

2. **Метод `undoLyricsRejection`:**
   ```kotlin
   suspend fun undoLyricsRejection(trackKey: String, sourceId: String): LyricsResult {
       lyricsDao?.deleteRejection(trackKey, sourceId)
       return getLyrics(trackKey)
   }
   ```

3. **Метод `clearAllRejections`:**
   ```kotlin
   suspend fun clearAllRejections(trackKey: String): LyricsResult {
       lyricsDao?.clearRejectionsForTrack(trackKey)
       return getLyrics(trackKey)
   }
   ```

4. **Защита в `prefetchLyricsAsync`:**
   Перед сетевым запросом вызвать `val rejected = lyricsDao?.getRejectedSourceIds(track.trackKey)?.toSet().orEmpty()`. Передать `rejected` в `getLyrics`. Если найденный результат принадлежит отклоненному источнику, не сохранять его.

---

## 3. Критерии приемки (DoD)

1. **Unit-тесты в `LyricsRejectionCascadeTest.kt`:**
   - `test_rejection_advances_to_next_source`: если `LRCLIB` возвращает неверный текст, вызов `rejectCurrentLyrics` сохраняет отклонение и переключается на `AmDm`.
   - `test_all_sources_rejected_yields_none`: если все источники отклонены, возвращается `hasLyrics = false` и `sourceId = "none"`.
   - `test_undo_restores_previous_source`: после отклонения вызов `undoLyricsRejection` снимает блокировку с источника и восстанавливает его результат.
   - `test_prefetch_respects_rejections`: фоновый prefetch для трека с отклоненным `builtin:lrclib` не восстанавливает старый текст из LRCLIB.
2. Все тесты (`.\gradlew.bat test`) завершаются с кодом 0 (SUCCESS).
