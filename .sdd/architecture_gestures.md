# Архитектурный проект: Система естественных жестов (Natural Gestures System)

- **ID задачи:** `ARCH-GST-01`
- **Зоны:** `media-ingress`, `media-ui`
- **Статус:** COMPLETED (Gate 1 Passed)
- **Целевые файлы:**
  - `app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt` (ingress: skipToNext, skipToPrevious)
  - `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt` (ui: HorizontalPager, double-tap, tab-sync)
  - `app/src/main/java/com/lirix/app/ui/components/AnalogTurntable.kt` (ui: swipe gestures on vinyl)

---

## 1. Архитектура транспортного контроля (Зона media-ingress)

В текущем `MediaSessionCollector.kt` реализованы только `togglePlayPause()`, `seekTo()` и `seekRelative()`.
Для поддержки переключения треков жестами требуется расширить API следующими детерминированными методами:

```kotlin
fun skipToNext(): Boolean
fun skipToPrevious(): Boolean
```

### Принцип единого контроллера (Single Target Controller)
1. Контроллер выбирается через существующий скоринг `resolveTargetController(getControllers(targetPackage))`.
2. Команда первого эшелона отправляется через `targetController.transportControls.skipToNext()` / `skipToPrevious()`.
3. В случае исключения или отсутствия флага срабатывает fail-safe отправка через `dispatchMediaButtonEvent`:
   - Для следующего трека: `KeyEvent.KEYCODE_MEDIA_NEXT`
   - Для предыдущего трека: `KeyEvent.KEYCODE_MEDIA_PREVIOUS`

---

## 2. Архитектура горизонтального пейджера вкладок (Зона media-ui)

### 2.1 Замена Crossfade на HorizontalPager
- Используется Compose Foundation `HorizontalPager` с `rememberPagerState(initialPage = 0) { NowPlayingMode.entries.size }`.
- Двусторонняя реактивная синхронизация:
  1. При свайпе пейджера пользователем:
     - Отслеживается `pagerState.currentPage`.
     - При смене индекса обновляется `selectedMode = NowPlayingMode.entries[page]`.
     - Срабатывает тактильный отклик `haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)`.
  2. При тапе по кнопке режима в верхней плашке:
     - Запускается корутина: `scope.launch { pagerState.animateScrollToPage(mode.ordinal) }`.

### 2.2 Структура страниц Pager
- Страница 0: `NowPlayingMode.KARAOKE` — `HighFidelityKaraokePlayer` / `ExhaustedLyricsPlaceholder`
- Страница 1: `NowPlayingMode.LYRICS` — `PlainLyricsReadingView` / `ExhaustedLyricsPlaceholder`
- Страница 2: `NowPlayingMode.CHORDS` — Аккорды AmDm с умным автоскроллом и транспонированием
- Страница 3: `NowPlayingMode.NOTES` — Персональные заметки к треку

---

## 3. Архитектура жестов винила и воспроизведения

### 3.1 Свайпы по винилу (Track Skip)
В `AnalogTurntable.kt` (или контейнер винила в `NowPlayingScreen.kt`) добавляется модификатор обработки горизонтальных свайпов:
- `Modifier.pointerInput(Unit) { detectHorizontalDragGestures { ... } }`
- Порог срабатывания свайпа: `dragAmount > 60.dp`
  - Свайп влево (`dragAmount < -threshold`) $\implies$ `MediaSessionCollector.skipToNext()` + виброотклик `LongPress`.
  - Свайп вправо (`dragAmount > threshold`) $\implies$ `MediaSessionCollector.skipToPrevious()` + виброотклик `LongPress`.

### 3.2 Двойной тап по карточке текста (Play/Pause)
На контейнер карточки текста навешивается:
- `Modifier.pointerInput(Unit) { detectTapGestures(onDoubleTap = { ... }) }`
- Быстрый дабл-тап вызывает `MediaSessionCollector.togglePlayPause()` с мгновенным тактильным щелчком.
- Одиночный тап сохраняется для управления видимостью капсулы или фокуса на строке.
