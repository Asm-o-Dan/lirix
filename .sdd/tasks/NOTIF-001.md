## Задача NOTIF-001: Настройка build.gradle.kts и AndroidManifest для :ingest:notification

**Файлы:**
- `ingest/notification/build.gradle.kts`
- `ingest/notification/src/main/AndroidManifest.xml`
(создать)

**Спека:** `.sdd/specs/ingest-notification/overview.md` (v1)  
**Зависит от:** `STORAGE-ALL`  

**Поведение:**
1. Подключить плагины:
   - `convention.android.library`
2. Сконфигурировать namespace: `com.example.npc.ingest.notification`.
3. Подключить зависимости:
   - `implementation(project(":core:model"))`
   - `implementation(project(":core:storage"))`
   - `implementation(libs.androidx.core.ktx)`
   - `implementation(libs.androidx.work.runtime.ktx)`
   - `implementation(libs.kotlinx.coroutines.core)`
   - `implementation(libs.kotlinx.coroutines.android)`
   - `testImplementation(libs.junit.jupiter)`
   - `testImplementation(libs.kotest.assertions.core)`
   - `testImplementation(libs.kotlinx.coroutines.test)`
   - `testImplementation(libs.turbine)`
   - `testImplementation(libs.mockk)`
4. В `AndroidManifest.xml` объявить:
   - `<service android:name=".service.PipelineNotificationListenerService" android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" android:exported="true">` с фильтром `android.service.notification.NotificationListenerService`.
   - Опциональный сервис: `<service android:name=".service.LiveWatchdogForegroundService" android:foregroundServiceType="specialUse" android:exported="false">`.
   - Разрешения: `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`.

**Критерий приёмки:**
- Вызов `./gradlew.bat :ingest:notification:tasks` завершается успешно без ошибок.
