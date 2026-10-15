# Задача TASK-KMP-03: Миграция чистых движков ядра в :shared/commonMain

- **ID задачи:** `TASK-KMP-03`
- **Роль исполнителя:** Core Coder
- **Зона:** `media-core`
- **Файлы:**
  - Перенести из `app/src/main/java/com/lirix/app/` в `shared/src/commonMain/kotlin/com/lirix/app/`:
    1. `domain/*` (`Category.kt`, `Event.kt`, `EventSource.kt`, `EventType.kt`)
    2. `feature/lyrics/LrcSyncEngine.kt`
    3. `feature/lyrics/AmDmChordParser.kt`
    4. `feature/lyrics/MyChordsParser.kt`
    5. `feature/lyrics/ChordTransposer.kt`
    6. `feature/lyrics/ChordAutoscrollCalculator.kt`
    7. `feature/lyrics/GuitarChordDictionary.kt`
    8. `feature/blacklist/BlacklistMatcher.kt`
    9. `feature/music/MusicTrackParser.kt`
    10. `analytics/WrappedStatsEngine.kt`
    11. `analytics/GamificationEngine.kt`
  - Перенести тесты в `shared/src/commonTest/kotlin/com/lirix/app/`.
- **Зависит от:** `TASK-KMP-02`
- **Основание:** [ADR-020](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-020)
- **Приоритет:** HIGH

---

## 1. Цель задачи
Переместить чистые алгоритмы и структуры данных, не имеющие зависимостей от Android SDK (`android.*`, `Context`), в `shared/src/commonMain/kotlin/com/lirix/app/`.
Пакеты сохранить идентичными (`com.lirix.app.*`), чтобы не ломать импорты в основном приложении `:app`.

---

## 2. Критерии приемки (DoD)
- [ ] Все 11 файлов движков перенесены в `shared/src/commonMain`.
- [ ] Юнит-тесты на `LrcSyncEngine`, `BlacklistMatcher`, `AmDmChordParser`, `MyChordsParser`, `WrappedStatsEngine` выполняются в `:shared:allTests`.
- [ ] Модуль `:app` компилируется и использует перенесенные классы из `:shared`.
