# Задача TASK-KMP-01: Мультипроектная конфигурация Gradle (:shared и :desktop)

- **ID задачи:** `TASK-KMP-01`
- **Роль исполнителя:** Build Engineer
- **Зона:** `build-sys`
- **Файлы:**
  1. `settings.gradle.kts` (добавить `:shared`, `:desktop`)
  2. `gradle/libs.versions.toml` (добавить плагины KMP, Compose Multiplatform, JNA)
  3. `build.gradle.kts` (зарегистрировать плагины на уровне root)
- **Основание:** [ADR-020](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/.sdd/decisions.md#adr-020)
- **Приоритет:** CRITICAL (Блокирует TASK-KMP-02..13)

---

## 1. Цель задачи
Подготовить фундамент Gradle для перехода к Kotlin Multiplatform:
1. Зарегистрировать модули `:shared` и `:desktop` в `settings.gradle.kts`.
2. Добавить версии и плагины в `libs.versions.toml`:
   - `compose-multiplatform = "1.7.3"`
   - `kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }`
   - `compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }`
   - `compose-multiplatform = { id = "org.jetbrains.compose", version.ref = "composeMultiplatform" }`
   - `jna = "5.15.0"`
3. Зарегистрировать плагины в корневом `build.gradle.kts` с `apply false`.

---

## 2. Критерии приемки (DoD)
- [ ] `settings.gradle.kts` включает `:app`, `:shared`, `:desktop`.
- [ ] Корневой `./gradlew help` выполняется успешно без конфликтов версий плагинов.
- [ ] Существующая сборка Android `:app:assembleRelease` и `:app:testDebugUnitTest` не сломана и собирается без ошибок.
