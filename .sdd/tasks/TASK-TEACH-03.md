# Задача TASK-TEACH-03: Исправление мобильного инспектора WebView и сквозная загрузка текстов при сохранении правила

**Статус**: READY  
**Зона**: `lyrics-engine` / `media-ui`  
**Спецификация**: `.sdd/specs/lyrics-engine/teach_mode_inspector_fix.md`  
**Исполнитель**: Кодер Media Ingress & UI  

## Описание
Пользователь сообщил о поломке: инспектор не реагирует на тапы, а после нажатия «Сохранить правило» текст песни не появляется в плеере.

## Чек-лист реализации

### 1. `app/src/main/assets/teach_inspector.js`
- Добавить обработчик touch/pointer событий (`touchend`, `pointerup`) на фазе capture (`true`) для надежного перехвата нажатий пальцем.
- Использовать `document.elementFromPoint(clientX, clientY)` для точного определения целевого элемента.
- Предотвращать `default` и `stopPropagation`, если включен `isInspecting`.

### 2. `app/src/main/java/com/lirix/app/ui/NowPlayingScreen.kt`
- Изменить тип коллбэка: `onOpenTeachMode: ((TrackEntity?) -> Unit)? = null`.
- В вызовах меню передавать `currentTrack` (например, `onTeachModeClick = { onOpenTeachMode?.invoke(currentTrack) }`, и в `onSearchWeb` также передавать `currentTrack`).
- При инициализации `AggregatedLyricsProvider()` в `LaunchedEffect(currentTrack?.trackKey)` (строка ~551) и в кнопке `onRetryDefault` (строка ~660) передавать параметр: `AggregatedLyricsProvider(lyricsDao = db.lyricsDao())`.

### 3. `app/src/main/java/com/lirix/app/ui/AppScaffold.kt`
- Ввести состояние `var activeTeachTrack by remember { mutableStateOf<TrackEntity?>(null) }`.
- В лямбде `onOpenTeachMode`:
  ```kotlin
  onOpenTeachMode = { track ->
      activeTeachTrack = track
      activeTeachUrl = null
      isTeachModeOpen = true
  }
  ```
- В вызове `TeachModeScreen` передавать `targetTrack = activeTeachTrack ?: viewingTrack`.

### 4. `app/src/main/java/com/lirix/app/feature/MusicFeatureEngine.kt`
- В конструкторе `MusicFeatureEngine` по умолчанию передавать `lyricsDao`:
  `lyricsAdapter: LyricsProvider = AggregatedLyricsProvider(lyricsDao = lyricsDao)`

### 5. `app/src/main/java/com/lirix/app/ui/TeachModeScreen.kt`
- В обработчике кнопки «Сохранить правило»:
  - Сохранить сущность правила в `db.lyricsDao().saveRule(...)`.
  - Если `selectedText.isNotBlank()` и `targetTrack != null`:
    - Выполнить сохранение текста в БД:
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
    - Уведомить пользователя Toast: «Правило сохранено и текст привязан к песне!».

---

## Верификация
1. `./gradlew testDebugUnitTest` — должен завершиться `BUILD SUCCESSFUL` без ошибок.
2. `./gradlew assembleRelease` — успешная сборка APK.
3. Отчет о выполненной работе предоставить Координатору.
