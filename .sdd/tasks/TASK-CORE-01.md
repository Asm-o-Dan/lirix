# TASK-CORE-01: Устранение критических уязвимостей ядра (Security, FileProvider, MediaSession Singleton)

**Зона:** `media-core` & `media-ingress`  
**Спецификация:** [`.sdd/specs/media-core/core_security_and_stability_spec.md`](../specs/media-core/core_security_and_stability_spec.md)  
**Исполнитель:** Android Core Engineer (Subagent)  
**Статус:** `READY`

---

## Контекст и цель задачи
По результатам независимого аудита [`.sdd/reports/INDEPENDENT_CODEBASE_AUDIT.md`](../reports/INDEPENDENT_CODEBASE_AUDIT.md) выявлены дефекты ядра, влияющие на безопасность, краши и стабильность фонового сервиса. Цель — устранить их строго по утвержденной спецификации.

---

## Список файлов для модификации
1. `app/src/main/AndroidManifest.xml` — добавить разрешение `ACCESS_NETWORK_STATE`.
2. `app/src/main/java/com/lirix/app/feature/share/ShareManager.kt` — устранить `Uri.fromFile()`.
3. `app/src/main/java/com/lirix/app/ingestion/MediaSessionCollector.kt` — реализовать потокобезопасный `getInstance(context)`.
4. `app/src/main/java/com/lirix/app/LirixApp.kt`, `app/src/main/java/com/lirix/app/ingestion/NotificationListener.kt`, `app/src/main/java/com/lirix/app/service/FloatingLyricsService.kt` — переход на синглтон `MediaSessionCollector.getInstance(context)`.
5. `app/build.gradle.kts` — очистка открытых паролей подписи.

---

## Критерии готовности (DoD)
- Все тесты `./gradlew testDebugUnitTest` зеленые.
- Нет предупреждений линтера по `FileUriExposedException`.
- Создан отчет в `.sdd/reports/CORE_01_IMPLEMENTATION_REPORT.md`.
