# Карточка Задачи: TASK-ING-04

- **ID**: `TASK-ING-04`
- **Зона**: `media-ingress`
- **Зависит от**: `SPEC-BUG-11` (`.sdd/specs/media-ingress/skip_and_lifecycle_cleanup.md`)
- **Целевой файл**: `app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt`
- **Исполнитель**: Coder (Media Ingress)
- **Статус**: READY

---

## 1. Контекст и Проблема
1. В приложениях с несколькими медиасессиями (Telegram / AyuGram / VK) `skipToNext()` и `skipToPrevious()` выбирали первый попавшийся контроллер через `resolveTargetController()`, не проверяя поддержку действий `ACTION_SKIP_TO_NEXT` и `ACTION_SKIP_TO_PREVIOUS`.
2. При закрытии всех плееров (`controllers.isNullOrEmpty()`) метод `updateControllers()` просто выходил, оставляя старые контроллеры в `activeMediaControllers`, а колбэки в `activeControllers` не разрегистрировались. Это вызывало утечки памяти и зависание зомби-треков в интерфейсе.

---

## 2. Детальные Инструкции по Реализации
В файле `MediaSessionCollector.kt`:

1. **Добавить функцию `resolveSkipController` в companion object**:
```kotlin
fun resolveSkipController(controllers: List<MediaController>, isNext: Boolean): MediaController? {
    if (controllers.isEmpty()) return null

    val targetAction = if (isNext) PlaybackState.ACTION_SKIP_TO_NEXT else PlaybackState.ACTION_SKIP_TO_PREVIOUS
    var bestController: MediaController? = null
    var maxScore = -1

    for (controller in controllers) {
        val state = controller.playbackState
        val actions = state?.actions ?: 0L

        val hasSkipAction = (actions and targetAction) != 0L
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val hasPlaybackState = state != null
        val hasMetadata = controller.metadata != null

        val score = (if (hasSkipAction) 10000 else 0) +
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

2. **Обновить `skipToNext()` и `skipToPrevious()`**:
- В `skipToNext()`:
```kotlin
val controllers = getControllers(targetPackage)
val controller = resolveSkipController(controllers, isNext = true) ?: resolveTargetController(controllers) ?: return false
```
- В `skipToPrevious()`:
```kotlin
val controllers = getControllers(targetPackage)
val controller = resolveSkipController(controllers, isNext = false) ?: resolveTargetController(controllers) ?: return false
```

3. **Обновить `updateControllers(controllers: List<MediaController>?)`**:
```kotlin
    private fun updateControllers(controllers: List<MediaController>?) {
        if (controllers.isNullOrEmpty()) {
            // Все сессии закрыты: разрегистрируем колбэки и переходим в IDLE
            for ((pkg, callback) in activeControllers) {
                try {
                    activeMediaControllers[pkg]?.unregisterCallback(callback)
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Error unregistering callback for %s", pkg)
                }
            }
            activeControllers.clear()
            activeMediaControllers.clear()
            _livePlaybackFlow.value = null
            return
        }

        // Очистка сессий, которые пропали из системы
        val currentPkgs = controllers.map { it.packageName }.toSet()
        val removedPkgs = activeMediaControllers.keys - currentPkgs
        for (pkg in removedPkgs) {
            val cb = activeControllers.remove(pkg)
            if (cb != null) {
                try {
                    activeMediaControllers[pkg]?.unregisterCallback(cb)
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Error unregistering removed session for %s", pkg)
                }
            }
            activeMediaControllers.remove(pkg)
        }

        for (controller in controllers) {
            val pkg = controller.packageName
            activeMediaControllers[pkg] = controller
            if (activeControllers.containsKey(pkg)) continue

            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    handlePlaybackChange(controller, state)
                }

                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    handleMetadataChange(controller, metadata)
                }
            }

            controller.registerCallback(callback)
            activeControllers[pkg] = callback
        }

        // Если текущий отображаемый трек принадлежал удаленному пакету, переключаем на живой
        val currentSnapshot = _livePlaybackFlow.value
        if (currentSnapshot == null || currentSnapshot.packageName in removedPkgs || !currentSnapshot.isPlaying) {
            val bestTarget = resolveTargetController(controllers)
            if (bestTarget != null) {
                handleMetadataChange(bestTarget, bestTarget.metadata)
            }
        }
    }
```

---

## 3. Критерии Приемки (Definition of Done)
1. Код компилируется без ошибок.
2. При вызове `skipToNext()` и `skipToPrevious()` выбирается сессия с соответствующим флагом действия.
3. При закрытии всех сессий коллектор корректно освобождает колбэки и переводит UI в режим IDLE.
4. `./gradlew testDebugUnitTest` проходит на 100%.
