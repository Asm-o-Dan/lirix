# Спецификация Зоны Media Ingress: Разрешение Контроллера Перемотки (SeekTo)

## 1. Назначение и Контекст
В Android одно приложение (пакет `packageName`) может объявлять несколько объектов `MediaSession` и порождать несколько `MediaController`.
В Telegram / AyuGram часто одновременно активны:
1. Контроллер сервиса уведомлений / чата (не поддерживающий перемотку, `actions and ACTION_SEEK_TO == 0L`).
2. Контроллер аудиоплеера / голосовых сообщений (поддерживающий перемотку, `(actions and ACTION_SEEK_TO) != 0L`).

Метод `seekTo(positionMs: Long)` в `MediaSessionCollector` должен однозначно определять контроллер, поддерживающий `ACTION_SEEK_TO`, и направлять команду перемотки именно в него.

---

## 2. Контракт функции `resolveSeekController`

### Сигнатура:
```kotlin
fun resolveSeekController(controllers: List<MediaController>): MediaController?
```

### Алгоритм ранжирования:
Для каждого `MediaController` вычисляется скор:
1. `val hasSeekToAction = (controller.playbackState?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO != 0L`
   - Если `hasSeekToAction == true`: `+10000` баллов.
2. `val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING`
   - Если `isPlaying == true`: `+1000` баллов.
3. `val hasPlaybackState = controller.playbackState != null`
   - Если `hasPlaybackState == true`: `+100` баллов.
4. `val hasMetadata = controller.metadata != null`
   - Если `hasMetadata == true`: `+10` баллов.

Выбирается контроллер с максимальным скором. Если список пуст, возвращается `null`.

---

## 3. Контракт функции `seekTo`

### Сигнатура:
```kotlin
fun seekTo(positionMs: Long): Boolean
```

### Шаги выполнения:
1. Получить текущий снэпшот `val snapshot = _livePlaybackFlow.value`.
2. Получить список контроллеров для целевого пакета:
   ```kotlin
   val controllers = getControllers(snapshot?.packageName)
   ```
3. Выбрать целевой контроллер:
   ```kotlin
   val controller = resolveSeekController(controllers) ?: resolveTargetController(controllers)
   ```
4. Вычислить и ограничить позицию `clampedPosition` с учетом `validDuration`:
   - Если `validDuration > 0L`, использовать `positionMs.coerceIn(0L, validDuration)`.
   - Иначе `positionMs.coerceAtLeast(0L)`.
5. Оптимистично обновить локальный стейт `_livePlaybackFlow`:
   ```kotlin
   if (snapshot != null) {
       _livePlaybackFlow.value = snapshot.copy(
           basePositionMs = clampedPosition,
           lastPositionUpdateTimeMs = android.os.SystemClock.elapsedRealtime()
       )
   }
   ```
6. Выполнить команду через транспортный контроллер выбранного контроллера:
   ```kotlin
   return try {
       controller?.transportControls?.seekTo(clampedPosition)
       controller != null
   } catch (e: Exception) {
       Timber.tag(TAG).e(e, "Failed to seekTo %d on controller %s", clampedPosition, controller?.packageName)
       false
   }
   ```

---

## 4. Граничные случаи (Edge Cases)
- **Нет активных контроллеров**: Метод возвращает `false`, не падает с NPE.
- **Ни один контроллер не имеет флага `ACTION_SEEK_TO`**: Фолбэк на `resolveTargetController(controllers)`, что сохраняет обратную совместимость с кастомными плеерами.
- **Длительность трека неизвестна или равна 0**: Позиция `clampedPosition` ограничивается `0L..Long.MAX_VALUE` (`coerceAtLeast(0L)`).
- **Ошибка IPC при вызове `seekTo`**: Перехватывается блоком `catch (e: Exception)`, в лог пишется ошибка, метод возвращает `false`.
