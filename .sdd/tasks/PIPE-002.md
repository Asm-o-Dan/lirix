# Задача PIPE-002: Реализовать DTO модели zone/app-pipeline

**Файлы:**
- `app/src/main/kotlin/com/example/npc/app/pipeline/model/OrchestratorState.kt` (создать)
- `app/src/main/kotlin/com/example/npc/app/pipeline/model/EventProcessingStatus.kt` (создать)
- `app/src/main/kotlin/com/example/npc/app/pipeline/model/EventProcessingTarget.kt` (создать)
- `app/src/main/kotlin/com/example/npc/app/pipeline/model/PipelineMetrics.kt` (создать)
- `app/src/main/kotlin/com/example/npc/app/pipeline/model/OrchestratorStatus.kt` (создать)
**Спека:** `.sdd/specs/app-pipeline/overview.md#секция-3`

## Сигнатуры (строго по спеке):
1. `OrchestratorState`: enum `IDLE`, `RUNNING`, `DRAINING`, `STOPPED`.
2. `EventProcessingStatus`: enum `COMPLETED`, `SKIPPED_ALREADY_CLAIMED`, `SKIPPED_NOT_FOUND`, `FAILED`.
3. `EventProcessingTarget`:
```kotlin
data class EventProcessingTarget(
    val event: Event,
    val packageName: String,
    val sourceId: SourceId,
    val rawPayloadJson: String
)
```
4. `PipelineMetrics`: data class с полями `totalSubmitted: Long`, `totalClaimed: Long`, `totalCompleted: Long`, `totalSkipped: Long`, `totalFailed: Long`, `totalPrototypeHits: Long`, `totalFinanceExtracted: Long`, `totalDeclinedTransactions: Long`, `currentQueueDepth: Int`, `averageProcessingDurationMs: Double`, `lastProcessedEventId: Long?`, `lastProcessedAt: Instant?`, `lastError: String?`.
5. `OrchestratorStatus`: data class с полями `state: OrchestratorState`, `isRecoveryActive: Boolean`, `metrics: PipelineMetrics`, `circuitBreakerStatuses: Map<String, Boolean>`.

## Критерий приёмки:
- Успешная компиляция: `./gradlew.bat :app:compileDebugKotlin`.
