# Спецификация: Арбитраж Активных Медиасессий и Подавление Зомби-Сессий

## Модуль: MediaSessionCollector   Зона: media-ingress   Версия: v1   Статус: FROZEN

### 1. Назначение
Обеспечить детерминированный выбор истинно активной медиасессии при запуске приложения и во время работы, исключить вытеснение играющего плеера (например, Telegram) неактивными/закрытыми зомби-сессиями (например, YouTube без уведомления в шторке).

---

### 2. Проблема и Инварианты
1. **Инвариант 1 (Приоритет воспроизведения)**:
   Если хоть один контроллер находится в состоянии `PlaybackState.STATE_PLAYING`, он обладает абсолютным приоритетом над любыми сессиями в `STATE_PAUSED`, `STATE_STOPPED` или `STATE_NONE`.
2. **Инвариант 2 (Защита живого воспроизведения)**:
   Если текущий `_livePlaybackFlow.value` имеет `isPlaying == true`, обновление от контроллера другого пакета с `isPlaying == false` **НЕ МОЖЕТ** перезаписать `_livePlaybackFlow.value`.
3. **Инвариант 3 (Детерминированный старт)**:
   При первичном сканировании сессий в `updateControllers(controllers)` колбэки регистрируются для всех сессий, но снимок `_livePlaybackFlow.value` вычисляется ровно один раз через функцию скоринга `resolveTargetController(controllers)`.
4. **Инвариант 4 (Синхронизация удаления)**:
   При удалении уведомления или завершении сессии пакета, если этот пакет отображается в `_livePlaybackFlow` и не играет, фокус автоматически переключается на другой доступный активный контроллер.

---

### 3. Детали Реализации
В `MediaSessionCollector.kt`:
1. В `updateControllers(controllers: List<MediaController>?)`:
   - Зарегистрировать колбэки для всех контроллеров.
   - Выбрать единый целевой контроллер: `val target = resolveTargetController(controllers)`.
   - Если `target != null`, обновить `_livePlaybackFlow.value` через `updateSnapshotForController(target)`.
2. В `handlePlaybackChange(controller, state)` и `handleMetadataChange(controller, metadata)`:
   - Проверять:
     ```kotlin
     val current = _livePlaybackFlow.value
     val isPlaying = state?.state == PlaybackState.STATE_PLAYING
     if (current != null && current.isPlaying && current.packageName != controller.packageName && !isPlaying) {
         // Игнорируем обновление неактивного контроллера, пока играет другой плеер
         return
     }
     ```
3. Метод `onNotificationDismissed(packageName: String)`:
   - Если `_livePlaybackFlow.value?.packageName == packageName && !_livePlaybackFlow.value?.isPlaying`:
     Очистить или переключить на оставшиеся сессии через `resolveTargetController`.

---

### 4. Критерии Приемки
1. При старте Lirix, если в фоне играет Telegram, а YouTube остановлен, плеер Lirix показывает сессию Telegram.
2. Никакая сессия на паузе без шторки не может сбить отображение играющего трека.
3. Unit-тесты проверяют выбор играющего контроллера при старте из списка смешанных сессий.
