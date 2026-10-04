# Задача TASK-BUG-05: Дедупликация и защита от x2/x3 инкремента playCount

- **ID задачи:** `TASK-BUG-05`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ingress` / `media-core`
- **Файлы:**
  1. `app/src/main/java/com/eventengine/app/feature/MusicFeatureEngine.kt` (кулдаун 60с, устранение ложного инкремента при паузах)
  2. `app/src/main/java/com/eventengine/app/ingestion/MediaSessionCollector.kt` (дедупликация событий без суффикса состояния)
  3. `app/src/main/java/com/eventengine/app/ingestion/NotificationListener.kt` (согласование ключей треков)
  4. `app/src/test/java/com/eventengine/app/MusicDatabaseTest.kt` (тесты защиты от x2/x3 инкремента)
- **Спека:** [.sdd/specs/media-core/overview.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-core/overview.md) (v1), [.sdd/contracts/ingress__core.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/contracts/ingress__core.md)
- **Приоритет:** CRITICAL (Дефект: дублирование и утроение счетчика воспроизведений трека)

---

## 1. Механика дефекта и цели

Пользователь выявил, что при воспроизведении одной песни счетчик `playCount` в базе увеличивается на +2 или +3:
1. `MediaSessionCollector` отправляет `UPDATE` и `PLAYING` с разницей в несколько десятков миллисекунд. Так как ключ дедупликации содержал суффикс `$stateName`, оба события доходили до `recordPlaybackSignal`.
2. В `recordPlaybackSignal` условие `shouldIncrement = (existingTrack == null) || isNewTrack || isGapLarge` срабатывало повторно, если `lastSession` еще не зафиксировалась в транзакции Room.
3. Обычная пауза более 30 секунд (`SESSION_GAP_THRESHOLD_MS = 30_000L`) приводила к тому, что продолжение прослушивания той же самой песни считалось за новое воспроизведение (`isGapLarge == true`) и накручивало счетчик.
4. Различие в наличии названия альбома между `NotificationListener` (альбом пуст `""`) и `MediaSessionCollector` (альбом из метаданных) приводило к генерации двух разных `trackKey` для одной и той же композиции.

---

## 2. Спецификация решения

### 2.1 Кулдаун и строгие правила инкремента в `MusicFeatureEngine.kt`

1. **Кулдаун инкремента (60 секунд):**
   Добавить в `MusicFeatureEngine` потокобезопасный реестр недавних инкрементов:
   ```kotlin
   private val lastIncrementTimeMap = ConcurrentHashMap<String, Long>()
   private const val PLAY_INCREMENT_COOLDOWN_MS = 60_000L
   ```
2. **Строгое правило инкремента:**
   Счетчик `playCount` трека увеличивается **строго на +1** только при выполнении совокупности условий:
   ```kotlin
   val lastIncrement = lastIncrementTimeMap[trackKey] ?: 0L
   val isCooldownPassed = (timestamp - lastIncrement) >= PLAY_INCREMENT_COOLDOWN_MS

   val isFirstTrackOccurrence = (existingTrack == null)
   val isTrackSwitch = (lastSession != null && lastSession.trackKey != trackKey)
   val isTrackReplayAfterCompletion = (lastSession != null && lastSession.trackKey == trackKey && lastSession.isCompleted && isCooldownPassed)

   val shouldIncrement = isCooldownPassed && (isFirstTrackOccurrence || isTrackSwitch || isTrackReplayAfterCompletion)

   if (shouldIncrement) {
       lastIncrementTimeMap[trackKey] = timestamp
   }

   val updatedPlayCount = (existingTrack?.playCount ?: 0) + (if (shouldIncrement) 1 else 0)
   ```
3. **Паузы НЕ инкрементируют `playCount`:**
   Пауза любой длительности (`isGapLarge`) на одном и том же треке продлевает или возобновляет сессию, но **никогда не увеличивает** `playCount`.

### 2.2 Дедупликация в `MediaSessionCollector.kt`

В методе `persistMediaEvent`:
Убрать `$stateName` из ключа дедупликации коротких всплесков:
```kotlin
// Дедуплицируем трек независимо от того, пришел ли сначала UPDATE или PLAYING
val stateAgnosticKey = "$packageName|$title|$artist"
val lastSeenTime = lastTrackMap[stateAgnosticKey]
if (lastSeenTime != null && (now - lastSeenTime) < 1500L && stateName == "UPDATE") {
    return // Подавляем промежуточный UPDATE сразу после или перед PLAYING
}
lastTrackMap[stateAgnosticKey] = now
```

### 2.3 Каноническая нормализация `trackKey`

При вычислении `computeTrackKey`:
Если в одном источнике альбом пуст, а артист и название полностью совпадают, использовать канонический ключ на основе `normalizedTitle` и `normalizedArtist`:
```kotlin
fun computeCanonicalTrackKey(title: String, artist: String): String {
    val normalized = "${title.trim().lowercase()}|${artist.trim().lowercase()}"
    val md = MessageDigest.getInstance("SHA-256")
    return md.digest(normalized.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
```
В `MusicDao`:
Перед созданием нового трека с непустым альбомом проверять, нет ли уже в базе трека с таким же `title` и `artist` без альбома. Если есть — обновлять существующую запись, не создавая дубликат.

---

## 3. Критерии приемки (DoD)

1. **Unit-тесты в `MusicDatabaseTest.kt`:**
   - `test_rapid_consecutive_signals_do_not_duplicate_playCount`: 5 подряд вызовов `recordPlaybackSignal` с интервалом 100 мс (смена `PLAYING` -> `UPDATE` -> `BUFFERING`) увеличивают `playCount` строго на 1.
   - `test_pause_and_resume_preserves_single_playCount`: воспроизведение, пауза на 40 секунд и возобновление сохраняют `playCount = 1`.
   - `test_replay_after_cooldown_increments_playCount`: воспроизведение после завершения предыдущей сессии и прошествии 60 секунд увеличивает `playCount` до 2.
2. Все тесты (`./gradlew test`) завершаются со статусом SUCCESS.
