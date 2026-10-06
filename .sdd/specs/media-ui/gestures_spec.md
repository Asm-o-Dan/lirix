# Спецификация: Система естественных жестов (Natural Gestures System)

- **ID спеки:** `SPEC-GST-01`
- **Зоны:** `media-ingress`, `media-ui`
- **Версия:** v1 (FROZEN)
- **Статус:** APPROVED (Gate 2 Passed)

---

## 1. Спецификация MediaSessionCollector (Зона media-ingress)

### 1.1 Публичный API
```kotlin
fun skipToNext(): Boolean
fun skipToPrevious(): Boolean
```

### 1.2 Поведение `skipToNext(): Boolean`
1. Получить текущий snapshot `_livePlaybackFlow.value`.
2. Найти целевой контроллер: `val controller = resolveTargetController(getControllers(snapshot?.packageName)) ?: return false`.
3. Попробовать вызвать `controller.transportControls.skipToNext()`.
4. При возникновении исключения или неудаче отправить резервный KeyCode через `controller.dispatchMediaButtonEvent`:
   - `KeyEvent.KEYCODE_MEDIA_NEXT` (ACTION_DOWN + ACTION_UP).
5. Вернуть результат успешности доставки IPC.

### 1.3 Поведение `skipToPrevious(): Boolean`
1. Получить текущий snapshot `_livePlaybackFlow.value`.
2. Найти целевой контроллер: `val controller = resolveTargetController(getControllers(snapshot?.packageName)) ?: return false`.
3. Попробовать вызвать `controller.transportControls.skipToPrevious()`.
4. При возникновении исключения или неудаче отправить резервный KeyCode через `controller.dispatchMediaButtonEvent`:
   - `KeyEvent.KEYCODE_MEDIA_PREVIOUS` (ACTION_DOWN + ACTION_UP).
5. Вернуть результат успешности доставки IPC.

---

## 2. Спецификация UI жестов (Зона media-ui)

### 2.1 HorizontalPager
- `val pagerState = rememberPagerState(initialPage = selectedMode.ordinal) { NowPlayingMode.entries.size }`
- Сегментированный переключатель вкладок при клике вызывает `pagerState.animateScrollToPage(mode.ordinal)`.
- `LaunchedEffect(pagerState.currentPage)` синхронизирует `selectedMode` с активной страницей и вызывает haptic feedback.

### 2.2 Свайпы по винилу
- Контейнер `AnalogTurntable` оборачивается в обработчик `pointerInput`:
  - Накопленный свайп влево > 60dp $\to$ `MediaSessionCollector.skipToNext()`
  - Накопленный свайп вправо > 60dp $\to$ `MediaSessionCollector.skipToPrevious()`

### 2.3 Дабл-тап по карточке
- Карточка контента обрабатывает `detectTapGestures(onDoubleTap = { MediaSessionCollector.togglePlayPause() })`.
