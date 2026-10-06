# Карточка Задачи: TASK-ING-03

- **ID**: `TASK-ING-03`
- **Зона**: `media-ingress`
- **Зависит от**: `SPEC-ING-03` (`.sdd/specs/media-ingress/target_session_arbitration.md`)
- **Целевой файл**: `app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt`, `app/src/main/java/com/lirix/app/ingestion/NotificationListener.kt`
- **Исполнитель**: Coder (Media Ingress)
- **Статус**: READY

---

## 1. Контекст и Проблема
При открытии приложения Lirix отображает сессию YouTube вместо реально играющего Telegram, несмотря на то, что у YouTube нет плеера в шторке (зомби-сессия).

### Причины:
1. `updateControllers` в цикле `for (controller in controllers)` дергает `handleMetadataChange(controller)` для каждого контроллера, поэтому побеждает тот, кто попал в конец списка.
2. Неиграющие контроллеры затирают `_livePlaybackFlow.value`, даже если текущий трек играет (`current.isPlaying == true`).

---

## 2. Требования к Реализации
1. В `MediaSessionCollector.kt`:
   - В `updateControllers(controllers)`:
     ```kotlin
     if (controllers.isNullOrEmpty()) return
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
     // Выбираем ОДИН наилучший контроллер через resolveTargetController
     val bestTarget = resolveTargetController(controllers)
     if (bestTarget != null) {
         handleMetadataChange(bestTarget, bestTarget.metadata)
     }
     ```
   - В начале `handlePlaybackChange(controller, state)`:
     ```kotlin
     val current = _livePlaybackFlow.value
     val isPlaying = state?.state == PlaybackState.STATE_PLAYING
     if (current != null && current.isPlaying && current.packageName != controller.packageName && !isPlaying) {
         // Защита активного воспроизведения: фоновая неиграющая сессия не может сбить текущий плеер
         return
     }
     ```
   - В начале `handleMetadataChange(controller, metadata)`:
     ```kotlin
     val current = _livePlaybackFlow.value
     val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
     if (current != null && current.isPlaying && current.packageName != controller.packageName && !isPlaying) {
         // Защита активного воспроизведения
         return
     }
     ```
   - Добавить метод очистки при смахивании уведомления:
     ```kotlin
     fun onNotificationDismissed(packageName: String) {
         val current = _livePlaybackFlow.value
         if (current != null && current.packageName == packageName && !current.isPlaying) {
             val remaining = activeMediaControllers.values.filter { it.packageName != packageName }
             val best = resolveTargetController(remaining)
             if (best != null && best.playbackState?.state == PlaybackState.STATE_PLAYING) {
                 handleMetadataChange(best, best.metadata)
             } else {
                 _livePlaybackFlow.value = null
             }
         }
     }
     ```
2. В `NotificationListener.kt`:
   - В `onNotificationRemoved(sbn)`:
     ```kotlin
     mediaSessionCollector?.onNotificationDismissed(pkg)
     ```

---

## 3. Критерии Приемки (Definition of Done)
1. Код компилируется без ошибок.
2. При наличии нескольких зарегистрированных сессий при старте приложения выбирается сессия со статусом `STATE_PLAYING`.
3. Зомби-сессия на паузе без уведомления не может перебить играющий трек Telegram.
4. `./gradlew testDebugUnitTest` и `./gradlew assembleRelease` проходят успешно.
