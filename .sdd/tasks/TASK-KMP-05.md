# Задача TASK-KMP-05: Платформенные швы (LirixLog, MediaIngressProvider, ThemePreferences)

- **ID задачи:** `TASK-KMP-05`
- **Роль исполнителя:** Core Coder
- **Зона:** `media-core`
- **Файлы:**
  1. `shared/src/commonMain/kotlin/com/lirix/app/platform/LirixLog.kt` (expect/actual)
  2. `shared/src/commonMain/kotlin/com/lirix/app/platform/MediaIngressProvider.kt`
  3. `shared/src/commonMain/kotlin/com/lirix/app/platform/ThemePreferencesStore.kt`
  4. Реализации в `shared/src/androidMain/kotlin/...` и `shared/src/desktopMain/kotlin/...`
- **Зависит от:** `TASK-KMP-03`
- **Основание:** [ADR-020](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-020)
- **Приоритет:** HIGH

---

## 1. Цель задачи
Снять прямые зависимости от `android.content.Context` и `timber.log.Timber` в оставшихся сетевых и сервисных классах:
- `LirixLog`: на Android делегирует в Timber, на Desktop пишет в ротируемый файл `%APPDATA%\Lirix\logs\lirix.log` и `System.err`.
- `MediaIngressProvider`: абстракция над источником воспроизведения.
- `ThemePreferencesStore`: абстракция над SharedPreferences (Android) и `java.util.prefs.Preferences` (Desktop).

---

## 2. Критерии приемки (DoD)
- [ ] Абстракции `LirixLog` и `MediaIngressProvider` скомпилированы для обоих таргетов.
- [ ] Никаких вызовов Timber в модуле `:shared`.
