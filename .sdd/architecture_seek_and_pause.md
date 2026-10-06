# Архитектурный проект: Разрешение SeekTo контроллеров и Жизненный цикл сессий при паузе

## 1. Введение и Контекст
В рамках эксплуатации приложения обнаружены две критические проблемы:
1. **Перемотка (SeekTo) не срабатывает для Telegram / AyuGram**:
   В ряде приложений (например, Telegram, AyuGram, некоторые версии VK) регистрируется несколько инстансов `MediaController` для одного пакета (например, фоновый вспомогательный сервис уведомлений `MediaSessionHelper` и реальный аудиоплеер `telegramAudioPlayer` / ExoPlayer). Метод `MediaSessionCollector.seekTo()` использовал `getActiveController()`, который возвращал первый попавшийся контроллер из отсортированного списка (часто вспомогательный без флага `PlaybackState.ACTION_SEEK_TO`), из-за чего вызов `transportControls.seekTo()` либо игнорировался, либо выбрасывал исключение.
2. **Пауза приводит к накрутке счетчика прослушиваний (playCount += 1)**:
   При переходе плеера в состояние `PAUSED` сессия помечалась как `isCompleted = true`. При последующем возобновлении воспроизведения (нажатие Play) условие `isTrackReplayAfterCompletion` оценивалось как `true` (если прошло время кулдауна или повторный запуск), что приводило к ложному инкременту `playCount`, искажая статистику прослушиваний и Wrapped.

---

## 2. Анализ и Архитектурное Решение Проблемы 1 (Media Ingress)

### 2.1 Текущая реализация
В `MediaSessionCollector.kt`:
```kotlin
fun seekTo(positionMs: Long): Boolean {
    val snapshot = _livePlaybackFlow.value
    val controller = getActiveController(snapshot?.packageName)
    ...
    return try {
        controller?.transportControls?.seekTo(clampedPosition)
        true
    } catch (e: Exception) { ... }
}
```
`getActiveController` просто берёт `getControllers(packageName).firstOrNull()`.
При наличии нескольких контроллеров у одного пакета, активным по воспроизведению может быть контроллер без `ACTION_SEEK_TO`, либо плеер разбит на 2 сессии (одна для уведомления, другая для воспроизведения медиа).

### 2.2 Целевая архитектура поиска контроллера перемотки
1. Для операции перемотки `seekTo` необходимо производить специализированный выбор целевого контроллера среди доступных для целевого пакета:
   - Получить список контроллеров через `getControllers(snapshot?.packageName)`.
   - Если контроллер единственный и поддерживает `ACTION_SEEK_TO` — использовать его.
   - Если контроллеров несколько — фильтровать или приоритизировать те, у которых в `playbackState?.actions` выставлен бит `PlaybackState.ACTION_SEEK_TO`.
   - Ввести специализированную функцию `resolveSeekController(controllers: List<MediaController>): MediaController?`.
2. **Алгоритм скоринга для `resolveSeekController`**:
   - Наличие флага `(actions and PlaybackState.ACTION_SEEK_TO) != 0L` — наивысший вес (+10000).
   - Текущее состояние `state == PlaybackState.STATE_PLAYING` — дополнительный вес (+1000).
   - Наличие `playbackState != null` (+100).
   - Наличие `metadata != null` (+10).
3. **Диспетчеризация**:
   - Если найден подходящий контроллер с флагом `ACTION_SEEK_TO`, отправляем вызов в него: `controller.transportControls.seekTo(clampedPosition)`.
   - Если ни у одного контроллера нет явного флага `ACTION_SEEK_TO` (некоторые кастомные плееры не выставляют флаги действий корректно), осуществляем fallback на `resolveTargetController(controllers)`.
   - Дополнительно: если `controller.transportControls.seekTo(...)` завершился ошибкой или не привел к результату, логировать и предотвращать краши.

---

## 3. Анализ и Архитектурное Решение Проблемы 2 (Media Core)

### 3.1 Текущая реализация
В `MusicFeatureEngine.kt` в методах `recordPlaybackSignal` и `processMusicEvent`:
```kotlin
if (lastSession != null && !isNewTrack && !isGapLarge) {
    val updated = lastSession.copy(
        endTimeMs = timestamp,
        durationMs = lastSession.durationMs + effectiveDuration,
        isCompleted = (playbackState == "PAUSED" || playbackState == "STOPPED")
    )
    musicDao.updateSession(updated)
}
```
И далее:
```kotlin
val isTrackReplayAfterCompletion = (lastSession != null && lastSession.trackKey == trackKey && lastSession.isCompleted && isCooldownPassed)
val shouldIncrement = isPlayingSignal && isCooldownPassed && (isFirstTrackOccurrence || isTrackSwitch || isTrackReplayAfterCompletion)
```
Когда трек ставится на паузу (`playbackState == "PAUSED"`):
1. Сессия получает `isCompleted = true`.
2. Пользователь слушает трек, ставит на паузу на 65 секунд (`timestamp - lastIncrement >= 60000ms`), затем нажимает Play.
3. Приходит событие `PLAYING`.
4. Срабатывает проверка: `lastSession.trackKey == trackKey && lastSession.isCompleted (true) && isCooldownPassed (true)`.
5. `isTrackReplayAfterCompletion` становится `true`, и `playCount` инкрементируется на +1!
Это противоречит здравому смыслу: трек просто был на паузе, он не завершался и не запускался заново.

### 3.2 Целевая модель состояний сессии
1. **Пауза — это НЕ завершение сессии (`PAUSED != isCompleted`)**:
   - `isCompleted` должен устанавливаться в `true` **ТОЛЬКО** при:
     а) Явном событии остановки `playbackState == "STOPPED"`.
     б) Переключении на другой трек (`isNewTrack == true`, закрытие предыдущей незавершенной сессии).
     в) Истечении таймаута разрыва (`isGapLarge == true`).
   - При `playbackState == "PAUSED"` сессия остается открытой (`isCompleted = false`), лишь обновляется `endTimeMs` и аккумулированная длительность `durationMs`.
2. **Семантика повторного воспроизведения (`isTrackReplayAfterCompletion`)**:
   - Повтор трека засчитывается только тогда, когда предыдущая сессия этого трека была действительно завершена (`isCompleted == true`, например при явном STOP или перемотке в начало после полного прослушивания), либо при возврате к треку после переключения на другой трек (`isTrackSwitch`).
   - Возобновление после паузы (`PAUSED -> PLAYING`) на том же треке не закрывает и не начинает заново сессию с нуля, а продолжает существующую сессию, и `playCount` **НЕ** должен увеличиваться.
3. **Учет в тестах**:
   - В `MusicDatabaseTest.kt` был старый тест `testRecordPlaybackSignalSessionCollapsing`, в строке 241 которого проверялось `assertTrue("Session must be marked completed on PAUSED", pausedSession.isCompleted)`. Это утверждение фиксировало ошибочное поведение и подлежит обновлению в тестах для отражения нового контракта (`assertFalse(pausedSession.isCompleted)`).

---

## 4. Схема взаимодействия компонентов

```
+-------------------------------------------------------------+
|                      UI / User Action                       |
|           (Seek Slider in NowPlaying / Play-Pause)          |
+-------------------------------------------------------------+
                               |
                               v
+-------------------------------------------------------------+
|               Zone: media-ingress                           |
|           MediaSessionCollector.kt                          |
|                                                             |
|  1. seekTo(positionMs)                                      |
|     -> getControllers(packageName)                          |
|     -> resolveSeekController(controllers)                   |
|        [PRIORITY: actions & ACTION_SEEK_TO != 0L]           |
|     -> target.transportControls.seekTo(positionMs)          |
+-------------------------------------------------------------+
                               |
                               v (Playback Callback / Ingress)
+-------------------------------------------------------------+
|               Zone: media-core                              |
|             MusicFeatureEngine.kt                           |
|                                                             |
|  2. recordPlaybackSignal / processMusicEvent                |
|     - playbackState == "PAUSED":                            |
|         isCompleted = (playbackState == "STOPPED")          |
|         --> isCompleted остается FALSE!                     |
|     - subsequent PLAYING:                                   |
|         isTrackReplayAfterCompletion == FALSE               |
|         playCount НЕ инкрементируется!                      |
+-------------------------------------------------------------+
```

---

## 5. Границы задач (Decomposition)
- **TASK-BUG-09A**: Зона `media-ingress` (`MediaSessionCollector.kt`).
  - Добавление `resolveSeekController`.
  - Модификация `seekTo(positionMs)`.
- **TASK-BUG-09B**: Зона `media-core` (`MusicFeatureEngine.kt`).
  - Изменение условий `isCompleted` в `recordPlaybackSignal` и `processMusicEvent`.
  - Защита `playCount` от повторного инкремента при паузе.
- **TASK-QA-09**: Зона `QA` (`MusicDatabaseTest.kt`).
  - Обновление теста `testRecordPlaybackSignalSessionCollapsing` (проверка `assertFalse(pausedSession.isCompleted)`).
  - Добавление unit-теста на паузу > 60 секунд без инкремента `playCount`.
