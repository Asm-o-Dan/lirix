# Карточка Задачи: TASK-BUG-09A

- **ID**: `TASK-BUG-09A`
- **Зона**: `media-ingress`
- **Зависит от**: `SPEC-BUG-09A` (`.sdd/specs/media-ingress/seek_controller_resolution.md`)
- **Целевой файл**: `app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt`
- **Исполнитель**: Coder (Android / Media Ingress)
- **Статус**: READY

---

## 1. Контекст и Описание Задачи
В плеере Telegram / AyuGram команда перемотки не срабатывает из-за того, что `MediaSessionCollector.seekTo` выбирает первый попавшийся контроллер из списка сессий приложения, который часто является контроллером фонового сервиса уведомлений без поддержки действия `PlaybackState.ACTION_SEEK_TO`.

Необходимо:
1. Добавить функцию `resolveSeekController(controllers: List<MediaController>): MediaController?` в `companion object` класса `MediaSessionCollector`.
2. Обновить реализацию `seekTo(positionMs: Long)`, чтобы она производила выбор контроллера через `resolveSeekController(controllers)` с фолбэком на `resolveTargetController(controllers)`.

---

## 2. Детальные Инструкции по Реализации

### 2.1 Добавление `resolveSeekController`
В `MediaSessionCollector.kt` внутри `companion object`:
```kotlin
fun resolveSeekController(controllers: List<MediaController>): MediaController? {
    if (controllers.isEmpty()) return null

    var bestController: MediaController? = null
    var maxScore = -1

    for (controller in controllers) {
        val state = controller.playbackState
        val actions = state?.actions ?: 0L

        val hasSeekToAction = (actions and PlaybackState.ACTION_SEEK_TO) != 0L
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val hasPlaybackState = state != null
        val hasMetadata = controller.metadata != null

        val score = (if (hasSeekToAction) 10000 else 0) +
                (if (isPlaying) 1000 else 0) +
                (if (hasPlaybackState) 100 else 0) +
                (if (hasMetadata) 10 else 0)

        if (score > maxScore) {
            maxScore = score
            bestController = controller
        }
    }

    return bestController
}
```

### 2.2 Модификация `seekTo(positionMs: Long)`
В `MediaSessionCollector.kt` внутри `companion object`:
```kotlin
fun seekTo(positionMs: Long): Boolean {
    val snapshot = _livePlaybackFlow.value
    val targetPackage = snapshot?.packageName
    val controllers = getControllers(targetPackage)
    val controller = resolveSeekController(controllers) ?: resolveTargetController(controllers)

    val validDuration = snapshot?.durationMs ?: controller?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    val clampedPosition = if (validDuration > 0L) {
        positionMs.coerceIn(0L, validDuration)
    } else {
        positionMs.coerceAtLeast(0L)
    }

    // 1. ОПТИМИСТИЧНОЕ ОБНОВЛЕНИЕ: замораживаем позицию в snapshot на новом месте
    if (snapshot != null) {
        _livePlaybackFlow.value = snapshot.copy(
            basePositionMs = clampedPosition,
            lastPositionUpdateTimeMs = android.os.SystemClock.elapsedRealtime()
        )
    }

    // 2. ОТПРАВКА ВО ВНЕШНИЙ ПЛЕЕР
    return try {
        controller?.transportControls?.seekTo(clampedPosition)
        controller != null
    } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to seekTo %d on controller", clampedPosition)
        false
    }
}
```

---

## 3. Критерии Приемки (Definition of Done)
1. Код компилируется без ошибок.
2. Существующий функционал `getControllers`, `resolveTargetController` и других методов не нарушен.
3. При вызове `seekTo` выбор контроллера отдает абсолютный приоритет сессии, содержащей флаг `ACTION_SEEK_TO`.
