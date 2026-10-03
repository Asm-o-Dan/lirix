## Задача MEDIA-001: Каркас модуля :ingest:media и AndroidManifest

**Файлы:**
- `ingest/media/build.gradle.kts`
- `ingest/media/src/main/AndroidManifest.xml`
(создать)

**Спека:**
- `.sdd/specs/ingest-media/overview.md` (v1)

**Зависит от:** `STORAGE-ALL` (или `SMS-ALL`)

**Поведение:**
1. `ingest/media/build.gradle.kts`:
   - Плагин `id("convention.android.library")`.
   - `namespace = "com.example.npc.ingest.media"`.
   - Зависимости:
     - `project(":core:model")`
     - `project(":core:storage")`
     - `libs.androidx.core.ktx`
     - `libs.androidx.media` (MediaSessionManager, MediaControllerCompat)
     - `libs.androidx.datastore.preferences`
     - `libs.kotlinx.coroutines.core`
     - `libs.kotlinx.coroutines.android`
     - Тестовые: junit, kotest, coroutines-test, turbine, mockk
2. `AndroidManifest.xml`:
   - Permissions / queries (если требуются для MediaSession).
   - Package name declaration.

**Критерий приёмки:**
- Модуль успешно регистрируется в Gradle и компилируется: `.\gradlew.bat :ingest:media:assembleDebug`.
