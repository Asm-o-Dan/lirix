# Отчет о Реализации: Устранение критических уязвимостей ядра (TASK-CORE-01)

**Зона:** `media-core` & `media-ingress`  
**Спецификация:** [core_security_and_stability_spec.md](../specs/media-core/core_security_and_stability_spec.md)  
**Статус:** `COMPLETED & VERIFIED` (Build & Unit Tests: Green)  
**Дата:** 2026-10-13  

---

## 1. Выполненные Задачи

### 1.1 Сетевое разрешение `ACCESS_NETWORK_STATE` (P1)
- **Файл:** [`AndroidManifest.xml`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/AndroidManifest.xml)
- **Изменение:** Добавлено `<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />`.
- **Эффект:** Устранен тихий отказ `NetworkConditionManager`, из-за которого `cm.getNetworkCapabilities(network)` бросал `SecurityException`, блокируя автоматический фоновый префетч текстов и караоке на реальных смартфонах.

### 1.2 Защита от фатального краша `FileUriExposedException` (P1)
- **Файл:** [`ShareManager.kt`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/java/com/lirix/app/feature/share/ShareManager.kt)
- **Изменение:** Полностью удален опасный fallback `Uri.fromFile(shareFile)`. При сбое `FileProvider` операция безопасно логирует ошибку и завершается с возвратом `false` без выброса `FileUriExposedException` в ОС на Android 7.0+.

### 1.3 Устранение Split-Brain синглтона `MediaSessionCollector` (P1)
- **Файлы:**
  - [`MediaSessionCollector.kt`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt)
  - [`LirixApp.kt`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/java/com/lirix/app/LirixApp.kt)
  - [`NotificationListener.kt`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/java/com/lirix/app/ingestion/NotificationListener.kt)
  - [`FloatingLyricsService.kt`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/main/java/com/lirix/app/service/FloatingLyricsService.kt)
- **Изменение:**
  - Внедрен потокобезопасный синглтон `MediaSessionCollector.getInstance(context: Context)` на базе двойной проверки (`Double-Checked Locking`) с привязкой к `context.applicationContext`.
  - При `onDestroy()` сервиса `NotificationListener` больше не зануляются статические коллекции сессий и текущий трек `_livePlaybackFlow`. Приложение сохраняет непрерывное воспроизведение даже при перезапуске системного сервиса слушателя уведомлений.

### 1.4 Безопасность сборки `build.gradle.kts` (P0)
- **Файл:** [`app/build.gradle.kts`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/build.gradle.kts)
- **Изменение:** Удалены захардкоженные дефолтные пароли (`lirixrelease2026`) в открытом виде из репозитория. Пароли теперь считываются строго из защищенных переменных окружения CI/CD.

---

## 2. Верификация и Тестирование

- Разработан верификационный набор тестов: [`CoreSecurityAndStabilityTest.kt`](file:///c:/Users/DaniilTuT/Documents/antigravity/quick-nobel/app/src/test/java/com/lirix/app/CoreSecurityAndStabilityTest.kt).
  1. `test_manifest_declaresAccessNetworkState`: подтверждает наличие `ACCESS_NETWORK_STATE` в манифесте.
  2. `test_mediaSessionCollector_isThreadSafeSingleton`: подтверждает идентичность экземпляра синглтона в многопоточной среде (8 потоков, 50 одновременных запросов).
  3. `test_notificationListener_destroyDoesNotWipeMediaSessionCollector`: подтверждает сохранение коллектора при уничтожении сервиса уведомлений.
  4. `test_shareManager_doesNotThrowFileUriExposedException`: проверяет безопасный экспорт и шеринг без краша.
  5. `test_buildGradle_doesNotContainPlaintextPasswords`: исключает регрессию открытых паролей в коде сборки.

- **Результат Gradle Test Runner:**
  ```bash
  ./gradlew testDebugUnitTest --tests "com.lirix.app.CoreSecurityAndStabilityTest"
  BUILD SUCCESSFUL in 26s
  ```
  Все 5 тестов пройдены успешно!
