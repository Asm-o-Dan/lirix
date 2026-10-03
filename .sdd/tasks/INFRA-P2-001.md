## Задача INFRA-P2-001: Создать каркасы модулей Фазы 2 в Gradle

**Модули:** `:pipeline:dsl`, `:pipeline:nodes-api`, `:pipeline:compiler`, `:pipeline:runtime`, `:feature:replay`  
**Целевые файлы:**  
- `settings.gradle.kts` (добавить include)
- `pipeline/dsl/build.gradle.kts`
- `pipeline/nodes-api/build.gradle.kts`
- `pipeline/compiler/build.gradle.kts`
- `pipeline/runtime/build.gradle.kts`
- `feature/replay/build.gradle.kts`
**Спецификация:** `.sdd/architecture_phase2.md#4-карта-модулей-и-границы-ответственности`  
**Контракт:** `.sdd/contracts/pipeline-dsl__compiler.md`  

### Входные контракты / сигнатуры:
1. `settings.gradle.kts`:
   ```kotlin
   include(":pipeline:dsl")
   include(":pipeline:nodes-api")
   include(":pipeline:compiler")
   include(":pipeline:runtime")
   include(":feature:replay")
   ```
2. `pipeline/dsl/build.gradle.kts`:
   - Чистый Kotlin JVM: `plugins { id("convention.kotlin.jvm"); kotlin("plugin.serialization") }`
   - Зависимости: `project(":core:model")`, `kotlinx.serialization.json`
3. `pipeline/nodes-api/build.gradle.kts`:
   - Чистый Kotlin JVM: `plugins { id("convention.kotlin.jvm") }`
   - Зависимости: `project(":core:model")` (Zero Android SDK, Zero Room)
4. `pipeline/compiler/build.gradle.kts`:
   - Чистый Kotlin JVM: `plugins { id("convention.kotlin.jvm") }`
   - Зависимости: `project(":pipeline:dsl")`, `project(":pipeline:nodes-api")`, `com.google.re2j:re2j:1.8`
5. `pipeline/runtime/build.gradle.kts`:
   - Чистый Kotlin JVM (или Android Library без UI): `plugins { id("convention.kotlin.jvm") }`
   - Зависимости: `project(":pipeline:compiler")`, `project(":pipeline:nodes-api")`, `project(":core:model")`, `kotlinx.coroutines.core`
6. `feature/replay/build.gradle.kts`:
   - Kotlin/Android: `plugins { id("convention.android.library") }`
   - Зависимости: `project(":pipeline:compiler")`, `project(":pipeline:nodes-api")`, `project(":core:model")`, `project(":core:storage")`, `kotlinx.coroutines.core`

### Критерии приемки (DoD):
- [ ] Все 5 модулей подключены в `settings.gradle.kts`.
- [ ] `./gradlew projects` отображает добавленные модули.
- [ ] `./gradlew assemble` успешно компилирует пустые каркасы модулей.
