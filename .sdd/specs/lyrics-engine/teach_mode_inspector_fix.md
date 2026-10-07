# Спецификация SPEC-TEACH-03: Ремонт WebView Инспектора и Сквозная Привязка Текста

## 1. Контекст и Проблематика
Пользователь зафиксировал: «Не работает от слова совсем инспектор, я нажал сохранить правило текст песни не загружается».

### Корневые причины:
1. **Инспектор WebView (`teach_inspector.js`)**:
   - `click` на мобильных экранах эмулируется с задержкой или подавляется внутренними скриптами сайтов (`stopPropagation`/`pointer-events`).
   - Требуется перехват событий `touchend` и `pointerup` в capture-фазе с получением элемента через `document.elementFromPoint(touch.clientX, touch.clientY)` и предотвращением стандартных действий.
2. **Потеря целевого трека при открытии TeachMode**:
   - В `NowPlayingScreen.kt` коллбэк `onOpenTeachMode` имел сигнатуру `(() -> Unit)?` и не передавал `currentTrack`.
   - `AppScaffold.kt` передавал в `TeachModeScreen` параметр `viewingTrack`, который равен `null` во время прямого воспроизведения. Из-за этого `targetTrack == null`, заголовок трека пустой, кнопка «Привязать к треку» скрыта.
3. **Кнопка «Сохранить правило» не привязывает текст песни**:
   - При нажатии «Сохранить правило» создается сущность `CustomLyricsRuleEntity`, но текст песни не сохраняется в `music_tracks` и `lyrics_cache`, и экран не закрывается.
   - Пользователь ожидает, что после сохранения правила выбранный текст песни сразу же подгрузится в плеер для текущего трека!
4. **`AggregatedLyricsProvider` не знает про `lyricsDao`**:
   - В `NowPlayingScreen.kt` (строки 551, 660) `AggregatedLyricsProvider()` вызывается без аргументов (`lyricsDao = null`). Поэтому созданные кастомные правила вообще не используются каскадным загрузчиком.

---

## 2. Архитектурные Требования и Контракты

### Зона 1: Assets & Ingress (`app/src/main/assets/teach_inspector.js`)
- Добавить обработчик `touchend` и `pointerup`:
  ```javascript
  function handleTouchInspect(e) {
      if (!isInspecting) return;
      var touch = e.changedTouches ? e.changedTouches[0] : e;
      var target = document.elementFromPoint(touch.clientX, touch.clientY);
      if (target) {
          e.preventDefault();
          e.stopPropagation();
          handleElementClick(target);
      }
  }
  ```
- Зарегистрировать слушатели на `document` с `{ capture: true, passive: false }`.

### Зона 2: UI Flow (`NowPlayingScreen.kt` и `AppScaffold.kt`)
- `NowPlayingScreen`: сигнатура `onOpenTeachMode: ((TrackEntity?) -> Unit)? = null`.
- При клике на `onTeachModeClick` и `onSearchWeb` передавать `currentTrack`.
- При создании `AggregatedLyricsProvider` передавать `lyricsDao = db.lyricsDao()`.
- `AppScaffold`: объявить `var activeTeachTrack by remember { mutableStateOf<TrackEntity?>(null) }`.
- В `onOpenTeachMode`: `activeTeachTrack = track; isTeachModeOpen = true`.
- Передавать `targetTrack = activeTeachTrack ?: viewingTrack` в `TeachModeScreen`.

### Зона 3: TeachModeScreen Dual-Write (`TeachModeScreen.kt`)
- В кнопке «Сохранить правило» (`onSaveRule`):
  - Сохранить правило в базу: `db.lyricsDao().saveRule(rule)`.
  - Если `selectedText.isNotBlank()` и `targetTrack != null`:
    - Выполнить dual-write:
      ```kotlin
      db.musicDao().updateLyrics(targetTrack.trackKey, selectedText)
      db.lyricsDao().saveLyrics(LyricsCacheEntity(
          trackKey = targetTrack.trackKey,
          plainLyrics = selectedText,
          source = "CustomRule",
          fetchedAt = System.currentTimeMillis()
      ))
      onLyricsAttached(selectedText)
      onBack()
      ```
    - Показать уведомление «Правило сохранено и привязано к треку!».

### Зона 4: Engine (`MusicFeatureEngine.kt`)
- В конструкторе `MusicFeatureEngine` передавать `lyricsDao = lyricsDao` в дефолтный экземпляр `AggregatedLyricsProvider`.

---

## 3. Критерии приемки (DoD)
1. Нажатие пальцем в режиме инспектора мгновенно подсвечивает и выбирает блоки текста на мобильном устройстве.
2. При переходе в режим обучения из NowPlaying в верхней панели отображается название текущего играющего трека.
3. При нажатии «Сохранить правило» текст песни сохраняется в Room БД для этого трека, экран закрывается, и в плеере сразу виден текст песни.
4. Все unit-тесты проходят успешно (`./gradlew testDebugUnitTest`).
5. Release APK успешно собирается (`./gradlew assembleRelease`).
