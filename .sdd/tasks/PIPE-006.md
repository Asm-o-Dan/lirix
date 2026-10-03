# Задача PIPE-006: Связать Orchestrator с PipelineNotificationListenerService и App.kt

**Файлы:**
- `app/src/main/kotlin/com/example/npc/app/App.kt` (изменить)
- `ingest/notification/src/main/kotlin/com/example/npc/ingest/notification/service/PipelineNotificationListenerService.kt` (изменить)
**Спека:** `.sdd/specs/app-pipeline/overview.md#секция-7`

## Требования:
1. `App.kt`:
   - Реализовать `OrchestratorProvider`.
   - Внедрить `@Inject lateinit var orchestrator: EventProcessingOrchestrator`.
   - В `onCreate()` вызвать `orchestrator.start()`.
2. `PipelineNotificationListenerService.kt`:
   - Добавить свойство `var orchestrator: EventProcessingOrchestrator? = null`.
   - В `ensureDependencies()`: разрешать оркестратор через `applicationContext as? OrchestratorProvider`.
   - В `onListenerConnected()`: вызывать `orchestrator?.triggerRecoverySweep()`.
   - В `processSinglePayload()` (после шага 4 `val savedEventId = storageGateway.insertEvent(domainEvent)`):
     * Вызывать `orchestrator?.submit(savedEventId)`.

## Критерий приёмки:
- Успешная компиляция: `./gradlew.bat :app:compileDebugKotlin :ingest:notification:compileDebugKotlin`.
