# Задача PIPE-005: Реализовать EventProcessingOrchestrator и EventProcessingOrchestratorImpl

**Файлы:**
- `app/src/main/kotlin/com/example/npc/app/pipeline/EventProcessingOrchestrator.kt` (создать)
- `app/src/main/kotlin/com/example/npc/app/pipeline/EventProcessingOrchestratorImpl.kt` (создать)
**Спека:** `.sdd/specs/app-pipeline/overview.md#секция-4`

## Требования:
1. `EventProcessingOrchestrator`:
   - `fun start()`
   - `fun submit(eventId: Long): Boolean`
   - `fun stop()`
   - `fun getStatus(): OrchestratorStatus`
   - `fun triggerRecoverySweep(): Job`
2. `EventProcessingOrchestratorImpl`:
   - Принимает `StorageGateway`, `SemanticClassifier`, `PackageGatedRouter`, `IsolatedExtractorRunner`, `CircuitBreaker`, dispatchers.
   - `Channel<Long>(Channel.UNLIMITED)` для ID событий.
   - `consumeLoop()`: читает eventId, замеряет время, вызывает `processSingleEvent(eventId)`.
   - `processSingleEvent`:
     * Атомарный `tryClaimEvent(eventId)` (при false -> SKIPPED).
     * Загрузка события и `packageName`.
     * `Fingerprinter.calculateFingerprint(...)`.
     * Поиск прототипа: `storageGateway.findMatchingPrototype(packageName, fingerprint)`.
     * Классификация: `semanticClassifier.classify(event, matchingPrototype)`.
     * Если `category == FINANCE` и `packageGatedRouter.isFinanceAllowed(packageName, ...)` -> `extractorRunner.runExtraction(event, packageName)` под `withTimeoutOrNull(50)`.
     * Транзакционный коммит: `storageGateway.completeEventProcessing(eventId, classification, transaction)`.
   - `triggerRecoverySweep()`: сканирует `getPendingUnprocessedEventIds()` и подаёт их в `submit(id)`.

## Критерий приёмки:
- Успешная компиляция: `./gradlew.bat :app:compileDebugKotlin`.
