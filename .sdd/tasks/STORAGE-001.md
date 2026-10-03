## Задача STORAGE-001: настроить build.gradle.kts для модуля :core:storage

**Файл:** `core/storage/build.gradle.kts` (создать)  
**Спека:** `.sdd/specs/core-storage/overview.md#конфигурация-сборки-и-экспорта-схемы` (v1)  
**Зависит от:** `INFRA-001`, `MODEL-ALL`  

**Поведение:**
1. Подключить плагины:
   - `convention.kotlin.jvm` (или `com.example.npc.kotlin-jvm`)
   - `com.google.devtools.ksp`
2. Подключить зависимости:
   - `implementation(project(":core:model"))`
   - `implementation(libs.androidx.room.runtime)`
   - `implementation(libs.androidx.room.ktx)`
   - `implementation(libs.sqlcipher.android)`
   - `implementation(libs.kotlinx.coroutines.core)`
   - `ksp(libs.androidx.room.compiler)`
   - `testImplementation(libs.junit.jupiter)`
   - `testImplementation(libs.androidx.room.testing)`
   - `testImplementation(libs.kotlinx.coroutines.test)`
   - `testImplementation(libs.turbine)`
3. Настроить KSP аргументы для экспорта схемы Room:
   ```kotlin
   ksp {
       arg("room.schemaLocation", "$projectDir/schemas")
       arg("room.incremental", "true")
       arg("room.expandProjection", "true")
   }
   ```

**Запрещено:**
- Использовать плагины `com.android.library` (модуль pure-JVM).
- Добавлять зависимости от `ingest:*` или `ui:*`.

**Критерий приёмки:**
- Вызов `./gradlew.bat :core:storage:tasks` завершается успешно без ошибок.
