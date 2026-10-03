## Задача INFRA-001: инициализировать корневую инфраструктуру сборки Gradle KTS и version catalog

**Файл:** `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/` (создать)  
**Место в файле:** корневая директория проекта  
**Спека:** `.sdd/architecture.md#5-структура-файлов-проекта` и ADR-001  

**Поведение:**
1. Создать `gradle/libs.versions.toml` с объявлением версий и библиотек:
   - Kotlin 2.0.20+ (или совместимый 2.0.x)
   - AGP 8.7.0+
   - KSP 2.0.x
   - Room 2.6.1
   - SQLCipher `net.zetetic:sqlcipher-android:4.6.0`
   - Coroutines 1.9.0
   - Hilt 2.51.1+
   - Compose BOM 2024.09.00+
   - JUnit 4 / 5, Kotest / AssertJ / Turbine / Robolectric
2. Настроить `build-logic/` с convention-плагинами:
   - `KotlinJvmConventionPlugin`: pure-JVM модуль (JDK 17, kotlin jvm плагин).
   - `AndroidLibraryConventionPlugin`: minSdk 26, compileSdk/targetSdk 35, Java 17.
   - `AndroidApplicationConventionPlugin`: app модуль.
   - `RoomConventionPlugin`: подключение room-runtime, room-ktx, room.schemaLocation через KSP.
3. Настроить `settings.gradle.kts`:
   - `pluginManagement` + `includeBuild("build-logic")`
   - `dependencyResolutionManagement`
   - `include(":core:model", ":core:storage", ":ingest:notification", ":ingest:sms", ":ingest:media", ":ui:timeline", ":app")`
4. Настроить корневой `build.gradle.kts` (без лишнего кода).
5. Создать `core/model/build.gradle.kts` с подключением `kotlin-jvm` convention плагина.

**Запрещено:**
- Использовать устаревшие плагины Groovy.
- Добавлять Android-зависимости в `:core:model`.
- Слепой `pickFirst "lib/*/libcrypto.so"` (ADR-007).

**Критерий приёмки:**
- Вызов `./gradlew :core:model:tasks` или `./gradlew tasks` выполняется успешно без синтаксических ошибок сборки Gradle.
