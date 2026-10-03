# Межзонный контракт: Pipeline Replay ↔ Pipeline Runtime & Compiler

**Версия:** FROZEN v3  
**Дата заморозки:** 2026-09-28  
**Статус:** FROZEN (GATE 3 PASSED)  
**Стороны контракта:**
- Провайдер симулятора и ретроспективного анализа: `zone/pipeline-replay` (`:feature:replay`)
- Провайдер модели скомпилированного конвейера: `zone/pipeline-compiler` (`:pipeline:compiler`)
- Поставщик исторического корпуса уведомлений (ReadOnly): `zone/core-storage` (`:core:storage`)
- Потребитель отчетов симуляции: визуальный редактор конвейера (`:feature:editor`)

---

### 1. Архитектурный контекст и песочница (Zero Side Effects)

Контракт гарантирует безопасный прогон черновика конвейера (`draftPipeline`) на накопленном корпусе реальных исторических уведомлений (база догфудинга за 7+ дней).
Ключевые инварианты:
1. **Zero SQLite Mutations:** Ни при каких обстоятельствах симуляция не выполняет `INSERT`, `UPDATE` или `DELETE` в рабочей базе данных SQLite Room.
2. **In-Memory Effect Interception:** Все побочные эффекты узлов перехватываются виртуальным интерпретатором `VirtualEffectEvaluator` в памяти и сопоставляются с эталоном.
3. **Кооперативная многозадачность (Cancellation & Yielding):** Каждые $N$ обработанных событий симулятор вызывает `kotlinx.coroutines.yield()`, предотвращая зависание UI потока и утилизируя фоновые ресурсы `Dispatchers.Default`.

```
┌────────────────────────────────────────────────────────┐
│               UI Редактора (:feature:editor)           │
│  - Запускает ReplayEngine.runSimulation(...)           │
│  - Отображает Flow<ReplayStatus> прогресс и Diff-отчет │
└───────────────────────────┬────────────────────────────┘
                            │ Flow<ReplayStatus>
                            ▼
┌────────────────────────────────────────────────────────┐
│           zone/pipeline-replay (:feature:replay)       │
│  - ReplayEngine: Keyset Pagination, Dual-Run In-Memory │
│  - VirtualEffectEvaluator (эффекты без мутаций БД)     │
│  - ReplayDiffReport, DiffMetrics, Quantile Latency     │
└───────────────────────────┬────────────────────────────┘
                            │ ReadOnly выборка событий
                            ▼
┌────────────────────────────────────────────────────────┐
│            zone/core-storage (:core:storage)           │
│  - ReplayEventSourceDao (чистое чтение raw_event/event)│
└────────────────────────────────────────────────────────┘
```

---

### 2. Публичный контракт `ReplayEngine`

```kotlin
package com.example.npc.feature.replay.engine

import com.example.npc.pipeline.compiler.CompiledPipeline
import kotlinx.coroutines.flow.Flow

interface ReplayEngine {

    /**
     * Запускает симуляцию черновика конвейера на исторических данных.
     *
     * @param draftPipeline Скомпилированный конвейер-черновик.
     * @param activePipeline Текущий активный конвейер (для парного сравнения) либо null (сравнение с историей в БД).
     * @param criteria Критерии выборки событий (временной диапазон, фильтр пакетов, лимит).
     * @return Холодный [Flow], транслирующий статус выполнения и итоговый [ReplayDiffReport].
     */
    fun runSimulation(
        draftPipeline: CompiledPipeline,
        activePipeline: CompiledPipeline? = null,
        criteria: ReplayCriteria = ReplayCriteria.Default
    ): Flow<ReplayStatus>
}
```

---

### 3. Модели критериев выборки и статуса исполнения

```kotlin
package com.example.npc.feature.replay.engine

import java.time.Instant

sealed interface TimeRange {
    data object Last24Hours : TimeRange
    data object Last7Days : TimeRange
    data object AllTime : TimeRange
    data class Custom(val start: Instant, val end: Instant) : TimeRange
}

data class ReplayCriteria(
    val timeRange: TimeRange = TimeRange.Last7Days,
    val packageFilter: Set<String>? = null,
    val maxEventsLimit: Int = 1000,
    val chunkSize: Int = 150
) {
    companion object {
        val Default = ReplayCriteria()
    }
}

sealed interface ReplayStatus {
    data object Idle : ReplayStatus

    data class Running(
        val processedCount: Int,
        val totalCount: Int,
        val percentProgress: Float,
        val currentEventId: Long
    ) : ReplayStatus

    data class Completed(
        val report: ReplayDiffReport
    ) : ReplayStatus

    data class Failed(
        val throwable: Throwable,
        val processedBeforeFailure: Int
    ) : ReplayStatus
}
```

---

### 4. Дифференциальный отчет (`ReplayDiffReport`)

```kotlin
package com.example.npc.feature.replay.report

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.finance.FinancialTransaction

enum class DiscrepancyType {
    CATEGORY_MISMATCH,
    CONFIDENCE_DROP,
    TRANSACTION_EXTRACTED_VS_NONE,
    TRANSACTION_MISSED,
    TRANSACTION_AMOUNT_DIFF,
    TRANSACTION_CURRENCY_DIFF,
    EXECUTION_FAILED,
    DROPPED_VS_PASSED
}

data class ConfidenceShift(
    val averageDraft: Double,
    val averageActive: Double,
    val delta: Double
)

data class EventDiscrepancy(
    val eventId: Long,
    val packageName: String,
    val notificationTitle: String,
    val notificationText: String,
    val discrepancyType: DiscrepancyType,
    val baselineCategory: Category,
    val draftCategory: Category,
    val baselineTransaction: FinancialTransaction?,
    val draftTransaction: FinancialTransaction?,
    val baselineLatencyUs: Long,
    val draftLatencyUs: Long
)

data class DiffMetrics(
    val totalProcessedEvents: Int,
    val identicalOutcomesCount: Int,
    val discrepancyCount: Int,
    val matchRatePercent: Float,
    val avgDraftLatencyUs: Long,
    val avgActiveLatencyUs: Long,
    val p95DraftLatencyUs: Long,
    val p95ActiveLatencyUs: Long,
    val confidenceShift: ConfidenceShift
)

data class ReplayDiffReport(
    val metrics: DiffMetrics,
    val discrepancies: List<EventDiscrepancy>,
    val executionDurationMs: Long,
    val isTruncated: Boolean = false
)
```

---

### 5. Контракт чтения исторического корпуса (`ReplayEventSourceDao`)

```kotlin
package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Query

data class ReplayHistoricalEvent(
    val eventId: Long,
    val rawId: Long,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val historicalCategory: String,
    val historicalConfidence: Double,
    val historicalTransactionJson: String?
)

@Dao
interface ReplayEventSourceDao {

    /**
     * Постраничная выборка (Keyset Pagination) событий без блокировки базы.
     */
    @Query("""
        SELECT e.id AS eventId, e.raw_id AS rawId, r.source_package AS packageName,
               r.title AS title, r.content AS text, r.posted_at AS postTime,
               e.category AS historicalCategory, e.confidence AS historicalConfidence,
               ft.raw_amount AS historicalTransactionJson
        FROM event e
        JOIN raw_event r ON e.raw_id = r.id
        LEFT JOIN financial_transaction ft ON ft.event_id = e.id
        WHERE e.id > :afterId AND r.posted_at >= :startTime AND r.posted_at <= :endTime
        ORDER BY e.id ASC
        LIMIT :limit
    """)
    suspend fun getEventsAfterId(
        afterId: Long,
        startTime: Long,
        endTime: Long,
        limit: Int
    ): List<ReplayHistoricalEvent>
}
```
