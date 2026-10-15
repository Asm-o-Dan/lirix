# Задача TASK-KMP-04: Перенос дизайн-токенов, тем и AnalogTurntable в :shared

- **ID задачи:** `TASK-KMP-04`
- **Роль исполнителя:** UI Coder
- **Зона:** `media-ui`
- **Файлы:**
  - Перенести из `app/src/main/java/com/lirix/app/` в `shared/src/commonMain/kotlin/com/lirix/app/`:
    1. `ui/theme/AppColors.kt`
    2. `ui/theme/AppThemeType.kt`
    3. `ui/theme/ColorPalettes.kt`
    4. `ui/theme/LirixColors.kt`
    5. `ui/theme/Theme.kt` (LocalLirixColors, LirixThemeTokens)
    6. `ui/components/AnalogTurntable.kt` (винил, кинетический тонарм)
    7. `ui/components/ThemeSelectionDialog.kt`
    8. `feature/lyrics/GuitarFretboardDiagram.kt`
    9. `feature/lyrics/GuitarChordDialog.kt`
- **Зависит от:** `TASK-KMP-03`
- **Основание:** [ADR-020](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-020)
- **Приоритет:** HIGH

---

## 1. Цель задачи
Обеспечить доступность всех 4 тем (`Spotify`, `Yandex`, `Obsidian`, `Pastel`) и визуального проигрывателя винила `AnalogTurntable` как на Android, так и на Windows Desktop через `org.jetbrains.compose`.

---

## 2. Критерии приемки (DoD)
- [ ] Все компоненты тем и верстки винила перенесены в `shared/src/commonMain`.
- [ ] Замена андроид-специфичных зависимостей на `org.jetbrains.compose.*`.
- [ ] Сборка `:shared:assemble` и `:app:assembleRelease` проходит успешно.
