# Задача PIPE-001: Добавить зависимости :classify:rules и :extract:finance в :app

**Файл:** `app/build.gradle.kts` (изменить)
**Спека:** `.sdd/specs/app-pipeline/overview.md#секция-6`

## Сигнатура / Содержимое:
В блок `dependencies { ... }`:
```kotlin
    implementation(project(":classify:rules"))
    implementation(project(":extract:finance"))
```

## Поведение:
1. Открыть `app/build.gradle.kts`.
2. В секцию `dependencies` после `implementation(project(":ui:timeline"))` добавить зависимости на `:classify:rules` и `:extract:finance`.
3. Сохранить файл.

## Критерий приёмки:
- Команда `./gradlew.bat :app:compileDebugKotlin` успешно компилируется без ошибок графа зависимостей.

## Запрещено:
- Менять другие зависимости или плагины.
