## Задача MEDIA-006: MediaSessionRecoveryManager и DI модуль

**Файлы:**
- `ingest/media/src/main/kotlin/com/example/npc/ingest/media/recovery/MediaSessionRecoveryManager.kt`
- `ingest/media/src/main/kotlin/com/example/npc/ingest/media/di/MediaIngestModule.kt`
(создать)

**Спека:**
- `.sdd/specs/ingest-media/overview.md#класс-mediasessionrecoverymanager` (v1)

**Зависит от:** `MEDIA-005`

**Поведение:**
1. `MediaSessionRecoveryManager`:
   - Сохранение снимков активных сессий в `DataStore<Preferences>` при каждом существенном изменении.
   - При старте приложения (`checkAndRecoverDanglingSessions(appStartedAt: Instant)`):
     - Вычитывает сохраненные незакрытые сессии.
     - Для каждой незакрытой сессии: закрывает с `MediaSessionEndReason.APP_RESTART`, вычисляет финальную длительность, записывает событие в `StorageGateway`.
     - Очищает персистентное хранилище снимков.
2. `MediaIngestModule`:
   - Предоставление синглтонов компонентов для Hilt или центрального DI приложения.

**Критерий приёмки:**
- Перезапуск приложения корректно закрывает висячие сессии с `APP_RESTART`.
