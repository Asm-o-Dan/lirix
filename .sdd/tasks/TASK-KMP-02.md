# Задача TASK-KMP-02: Конфигурация KMP-модуля :shared

- **ID задачи:** `TASK-KMP-02`
- **Роль исполнителя:** Build Engineer
- **Зона:** `build-sys`
- **Файлы:**
  1. `shared/build.gradle.kts` (создать)
  2. `app/build.gradle.kts` (добавить `implementation(project(":shared"))`)
- **Зависит от:** `TASK-KMP-01`
- **Основание:** [ADR-020](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-020)
- **Приоритет:** CRITICAL (Блокирует перенос кода)

---

## 1. Цель задачи
Создать модуль `:shared` с поддержкой Kotlin Multiplatform и Compose Multiplatform:
- Таргеты:
  - `androidTarget()`
  - `jvm("desktop")`
- SourceSets:
  - `commonMain`: зависимости Compose Runtime, Foundation, Material3, Coroutines Core.
  - Промежуточный `jvmShared`: общий для Android и Desktop (для сетевых вызовов, JSON и SQLite).
  - `androidMain`: андроид-специфичные расширения.
  - `desktopMain`: десктоп-специфичные расширения.
- Подключить `:shared` как зависимость в `app/build.gradle.kts`.

---

## 2. Критерии приемки (DoD)
- [ ] Модуль `:shared` успешно компилируется `./gradlew :shared:assemble`.
- [ ] Модуль `:app` собирается с зависимостью на `:shared` (`./gradlew :app:assembleRelease`).
