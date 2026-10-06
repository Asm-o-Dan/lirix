# Задача TASK-TG-01: Детерминированное управление Play/Pause и выбор единого MediaController (BUG-TG-01)

- **ID задачи:** `TASK-TG-01`
- **Роль исполнителя:** Кодер
- **Зона:** `media-ingress`
- **Приоритет:** CRITICAL (Блокер корректного управления воспроизведением Telegram / AyuGram)
- **Файл:** [`app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt) (изменить)
- **Место в файле:** внутри `companion object` класса `MediaSessionCollector` (заменить блок строк 415–487 между методами `getActiveController` и `seekTo`)
- **Спека:** [.sdd/specs/media-ingress/telegram_pause_fix.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/specs/media-ingress/telegram_pause_fix.md) (v1)

---

## 1. Контекст дефекта

В текущей реализации `MediaSessionCollector.kt:450-464`:
1. Метод `sendMediaButtonFallback()` вынесен за пределы `catch` и вызывается **безусловно** поверх успешного `transportControls.pause()`.
2. Внутри `sendMediaButtonFallback()` при `!directHandled` отправляется двухпозиционный тумблер `KEYCODE_MEDIA_PLAY_PAUSE`, что немедленно перезапускает только что остановленное воспроизведение в Telegram.
3. Команда рассылается в цикле `for (controller in targetControllers)` по всем сессиям Telegram (`MediaSessionHelper` и `telegramAudioPlayer`), вызывая гонку потоков (Race Condition) в сервисе `AudioPlayerService`.

---

## 2. Сигнатуры (НЕ МЕНЯТЬ)

```kotlin
fun resolveTargetController(controllers: List<MediaController>): MediaController?

fun togglePlayPause(): Boolean

internal fun sendMediaButtonFallback(controller: MediaController, wasPlaying: Boolean): Boolean
```

---

## 3. Пошаговое поведение (Детерминированный алгоритм)

### 3.1 Реализация `resolveTargetController(controllers: List<MediaController>): MediaController?`

Разместить метод в `companion object` непосредственно перед `togglePlayPause()`.

1. **Проверка базового условия:**
   Если `controllers.isEmpty()`, вернуть `null`.
2. **Линейный скоринг контроллеров:**
   Найти контроллер с максимальным баллом, используя следующий алгоритм:
   ```kotlin
   var bestController: MediaController? = null
   var maxScore = -1

   for (controller in controllers) {
       val state = controller.playbackState
       val actions = state?.actions ?: 0L

       val isPlaying = state?.state == PlaybackState.STATE_PLAYING
       val hasPlayPauseAction = (actions and (PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE)) != 0L
       val hasPositiveActions = actions > 0L
       val hasSeekToAction = (actions and PlaybackState.ACTION_SEEK_TO) != 0L
       val hasMetadata = controller.metadata != null

       val score = (if (isPlaying) 10000 else 0) +
                   (if (hasPlayPauseAction) 1000 else 0) +
                   (if (hasPositiveActions) 100 else 0) +
                   (if (hasSeekToAction) 50 else 0) +
                   (if (hasMetadata) 10 else 0)

       if (score > maxScore) {
           maxScore = score
           bestController = controller
       }
   }

   return bestController
   ```

### 3.2 Реализация `togglePlayPause(): Boolean`

Заменить существующий метод `togglePlayPause()` (строки 415–466):

1. **Извлечение текущего состояния:**
   ```kotlin
   val snapshot = _livePlaybackFlow.value
   val targetPackage = snapshot?.packageName
   val controllers = getControllers(targetPackage)
   if (controllers.isEmpty()) {
       Timber.tag(TAG).w("togglePlayPause: no controllers found for package %s", targetPackage)
       return false
   }
   ```
2. **Выбор ЕДИНСТВЕННОГО целевого контроллера:**
   ```kotlin
   val targetController = resolveTargetController(controllers) ?: return false
   ```
3. **Определение направления переключения:**
   ```kotlin
   val isCurrentlyPlaying = (targetController.playbackState?.state == PlaybackState.STATE_PLAYING) ||
                            (snapshot?.isPlaying == true)
   val nextPlaying = !isCurrentlyPlaying
   ```
4. **Оптимистичное обновление UI (Zero-Latency UI Feedback):**
   ```kotlin
   val currentPos = snapshot?.currentPositionMs()
       ?: targetController.playbackState?.position
       ?: 0L

   if (snapshot != null) {
       _livePlaybackFlow.value = snapshot.copy(
           isPlaying = nextPlaying,
           basePositionMs = currentPos,
           lastPositionUpdateTimeMs = android.os.SystemClock.elapsedRealtime()
       )
   }
   instance?.syncHeartbeat(nextPlaying, targetController.packageName)
   ```
5. **Отправка команды первого эшелона (Primary IPC):**
   ```kotlin
   var transportSuccess = false
   try {
       if (isCurrentlyPlaying) {
           targetController.transportControls.pause()
       } else {
           targetController.transportControls.play()
       }
       transportSuccess = true
   } catch (e: Exception) {
       Timber.tag(TAG).w(e, "transportControls failed on %s", targetController.packageName)
   }
   ```
6. **Строгая иерархия Fail-Safe:**
   - Если `transportSuccess == true` $\implies$ вернуть `true` (**фоллбэк НЕ вызывается**).
   - Если `transportSuccess == false` $\implies$ вернуть результат `sendMediaButtonFallback(targetController, isCurrentlyPlaying)`.

### 3.3 Реализация `sendMediaButtonFallback(controller: MediaController, wasPlaying: Boolean): Boolean`

Заменить существующий метод `sendMediaButtonFallback()` (строки 468–487):

```kotlin
internal fun sendMediaButtonFallback(controller: MediaController, wasPlaying: Boolean): Boolean {
    return try {
        val primaryKeyCode = if (wasPlaying) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, primaryKeyCode)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, primaryKeyCode)
        val downHandled = controller.dispatchMediaButtonEvent(downEvent)
        val upHandled = controller.dispatchMediaButtonEvent(upEvent)
        val directHandled = downHandled || upHandled

        if (!directHandled) {
            if (wasPlaying) {
                // ПАУЗА: разрешен ТОЛЬКО идемпотентный код STOP.
                // Отправка KEYCODE_MEDIA_PLAY_PAUSE КАТЕГОРИЧЕСКИ ЗАПРЕЩЕНА!
                val stopDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP)
                val stopUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP)
                controller.dispatchMediaButtonEvent(stopDown) || controller.dispatchMediaButtonEvent(stopUp)
            } else {
                // ВОСПРОИЗВЕДЕНИЕ: переключатель допустим как резерв
                val toggleDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                val toggleUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                controller.dispatchMediaButtonEvent(toggleDown) || controller.dispatchMediaButtonEvent(toggleUp)
            }
        } else true
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "dispatchMediaButtonEvent failed completely")
        false
    }
}
```

---

## 4. Ошибки и обработка исключений

1. Исключения при вызове `targetController.transportControls.pause()` / `play()`:
   - Перехватываются блоком `catch (e: Exception)`.
   - Логируются через `Timber.tag(TAG).w(e, ...)`.
   - Активируют вызов метода второго эшелона `sendMediaButtonFallback`.
2. Исключения при вызове `controller.dispatchMediaButtonEvent`:
   - Перехватываются блоком `catch (e: Exception)`.
   - Логируются через `Timber.tag(TAG).e(e, "dispatchMediaButtonEvent failed completely")`.
   - Возвращают `false`. Никаких падений приложения.

---

## 5. Граничные случаи

| Входные условия | Ожидаемое поведение | Результат |
|---|---|---|
| `controllers.isEmpty()` | Выход без отправки IPC и без модификации snapshot | `return false` |
| `controllers` содержит 2 контроллера Telegram: `MediaSessionHelper` (actions=567) и `telegramAudioPlayer` (actions=822, metadata!=null) | `resolveTargetController` выбирает `telegramAudioPlayer` (балл 11160 против 11100). Команда отправляется только ему. | `return true`, нет гонки потоков |
| `transportControls.pause()` выполнился без исключений | `sendMediaButtonFallback` **не вызывается** | `return true` |
| `transportControls.pause()` выбросил `RemoteException` | Вызывается `sendMediaButtonFallback(targetController, true)`. Отправляется `KEYCODE_MEDIA_PAUSE`. При `!directHandled` отправляется `KEYCODE_MEDIA_STOP`. `KEYCODE_MEDIA_PLAY_PAUSE` **не отправляется ни при каких условиях**. | `return true` |
| Возобновление (`wasPlaying == false`), `transportControls.play()` выбросил исключение | Вызывается `sendMediaButtonFallback(targetController, false)`. Отправляется `KEYCODE_MEDIA_PLAY`, при неудаче — `KEYCODE_MEDIA_PLAY_PAUSE`. | `return true` |

---

## 6. Можно использовать (уже реализовано)

- `getControllers(packageName: String?): List<MediaController>` (в `MediaSessionCollector.Companion`)
- `_livePlaybackFlow: MutableStateFlow<LivePlaybackSnapshot?>` (в `MediaSessionCollector.Companion`)
- `instance?.syncHeartbeat(isPlaying: Boolean, packageName: String)` (в `MediaSessionCollector.Companion`)
- `Timber` (`com.jakewharton.timber:timber:5.0.1`)
- Константы `PlaybackState` (`STATE_PLAYING`, `ACTION_PLAY`, `ACTION_PAUSE`, `ACTION_SEEK_TO`)
- Константы `KeyEvent` (`ACTION_DOWN`, `ACTION_UP`, `KEYCODE_MEDIA_PAUSE`, `KEYCODE_MEDIA_PLAY`, `KEYCODE_MEDIA_STOP`, `KEYCODE_MEDIA_PLAY_PAUSE`)

---

## 7. Запрещено

1. Менять любые файлы, кроме `app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt`.
2. Менять сигнатуры публичных методов `togglePlayPause()`, `getActiveController()`, `getControllers()`, `seekTo()`, `seekRelative()`.
3. Запускать цикл рассылки команд по нескольким контроллерам.
4. Вызывать `sendMediaButtonFallback()`, если вызов `transportControls` завершился успешно (без `Exception`).
5. Отправлять `KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE` при операции паузы (`wasPlaying == true`).
6. Добавлять внешние библиотеки в `build.gradle.kts`.

---

## 8. Критерии приёмки (DoD для TASK-TG-01)

1. [ ] В файле `app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt` реализован метод `resolveTargetController`.
2. [ ] Метод `togglePlayPause()` отправляет команду **строго в один** контроллер, возвращенный `resolveTargetController`.
3. [ ] Вызов `sendMediaButtonFallback` происходит **только** в блоке при `transportSuccess == false` (при исключении в `transportControls`).
4. [ ] В `sendMediaButtonFallback` при `wasPlaying == true` отправка `KEYCODE_MEDIA_PLAY_PAUSE` физически отсутствует в ветке кода.
5. [ ] Проект успешно компилируется (`./gradlew testDebugUnitTest`).
6. [ ] Все существующие 31 юнит-тестов остаются зелеными.

---

## 9. Если что-то неясно

Не угадывать. Немедленно вернуть отчёт со статусом `BLOCKED` и конкретным вопросом Координатору.
