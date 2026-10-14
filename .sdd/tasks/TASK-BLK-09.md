# Задача TASK-BLK-09: Реактивный предпросмотр конкретных удаляемых треков в конструкторе

- **ID задачи:** `TASK-BLK-09`
- **Роль исполнителя:** Кодер (Core & UI)
- **Зона:** `media-core` & `media-ui`
- **Файлы:**
  1. `app/src/main/java/com/lirix/app/feature/blacklist/BlacklistPurgeEngine.kt`
  2. `app/src/main/java/com/lirix/app/ui/blacklist/BlacklistViewModel.kt`
  3. `app/src/main/java/com/lirix/app/ui/blacklist/BlacklistSettingsScreen.kt`
  4. `app/src/main/java/com/lirix/app/ui/blacklist/BlacklistRuleConstructorSheet.kt`
- **Спека / Решение:** [ADR-019 в .sdd/decisions.md](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md)
- **Приоритет:** HIGH

---

## 1. Цель задачи
При вводе текста в поле значения или выборе подстроки реактивно фильтровать фонотеку и отображать конкретные треки, которые будут удалены этим правилом.

---

## 2. Инструкции по реализации

### 1. `BlacklistPurgeEngine.kt`
Добавить метод:
```kotlin
suspend fun findMatchingTracks(rule: BlacklistRuleEntity, limit: Int = 20): List<com.lirix.app.storage.TrackEntity> = withContext(Dispatchers.IO) {
    val allTracks = db.musicDao().getAllTracks()
    allTracks.filter { track ->
        BlacklistMatcher.matches(
            title = track.title,
            artist = track.artist,
            packageName = track.sourcePackage,
            rule = rule
        )
    }.take(limit)
}
```

### 2. `BlacklistViewModel.kt`
- В `BlacklistUiState` добавить:
  `val previewMatchingTracks: List<com.lirix.app.storage.TrackEntity> = emptyList()`
- В `recalculatePreview()`:
  ```kotlin
  if (currentPattern.isEmpty()) {
      _uiState.update { it.copy(draftPurgePreviewCount = 0, previewMatchingTracks = emptyList(), isCalculatingPreview = false) }
      return
  }
  // В корутине после debounce:
  val matches = purgeEngine.findMatchingTracks(tempRule, limit = 20)
  val count = purgeEngine.previewPurgeCount(tempRule)
  _uiState.update { it.copy(draftPurgePreviewCount = count, previewMatchingTracks = matches, isCalculatingPreview = false) }
  ```

### 3. `BlacklistSettingsScreen.kt`
Пробросить `previewMatchingTracks = uiState.previewMatchingTracks` в `BlacklistRuleConstructorSheet`.

### 4. `BlacklistRuleConstructorSheet.kt`
- Добавить параметр: `previewMatchingTracks: List<com.lirix.app.storage.TrackEntity> = emptyList()`.
- В блоке отображения подсказок/удалений:
  - **Если `draftPattern.isBlank()`:**
    Показывать `suggestions` из фонотеки (как сейчас: "Треки / Исполнители / Приложения из вашей фонотеки:").
  - **Если `draftPattern.isNotBlank()`:**
    Показывать секцию предпросмотра удалений:
    - Заголовок: `Будет удалено из фонотеки ($previewCount):`
    - Если `previewMatchingTracks.isNotEmpty()`:
      Горизонтальная прокручиваемая лента чипов:
      `[ ✕ ${track.title} • ${track.artist} ]`
      Стилизованная с акцентной/warning подсветкой (`tokens.accentPrimary` или danger стили, скругление `tokens.pillCornerRadius`, высота $\ge 48\,\text{dp}$).
    - Если `previewMatchingTracks.isEmpty()` и `!isCalculatingPreview`:
      Текстовая плашка `✓ Ни один трек в фонотеке не совпадает с этим правилом` приглушенным цветом `tokens.textSecondary`.

---

## 3. Критерии приемки (DoD)
- [ ] При вводе в поле значения (например, буквы "а") список чипов динамически показывает конкретные треки из базы, которые совпадают с фильтром.
- [ ] Счетчик удалений и список удаляемых треков синхронизированы.
- [ ] При очистке поля ввода возвращаются исходные пресеты фонотеки.
- [ ] Unit-тесты и `./gradlew assembleRelease` проходят успешно.
