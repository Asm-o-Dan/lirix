# Межзонный контракт: UI Features ↔ Domain UseCases & Storage Gateway

**Версия:** DRAFT v4 (Фаза 3)  
**Дата:** 2026-09-28  
**Статус:** REVIEW / PROPOSED  
**Стороны контракта:**
- Потребители Presentation-слоя: `:feature:editor`, `:feature:analytics`, `:feature:diagnostics`
- Провайдер бизнес-логики: `:domain`
- Провайдер хранилища: `zone/core-storage` (`:core:storage`)

---

### 1. Архитектурный контекст и границы

Контракт специфицирует MVI/UDF контракты для трех ключевых пользовательских экранов Фазы 3:
1. **One-Tap Template Editor:** Редактирование и утверждение шаблона уведомления прямо из `EventDetailsDialog`.
2. **Financial Analytics UI:** Дашборд расходов/доходов с разделением по валютам (MDL, RUP, USD, EUR, RUB).
3. **Orchestrator Diagnostics Screen:** Мониторинг очередей Ingest, предохранителей CircuitBreaker и трейсов TraceRing.

Feature-модули не обращаются напрямую к Room, SQLCipher или рантайму. Все взаимодействия осуществляются через иммутабельные модели UseCase'ов и реактивные потоки `StateFlow` / `Flow`.

```
┌────────────────────────────────────────────────────────┐
│                   Presentation Layer                   │
│   :feature:editor | :feature:analytics | :feature:diag │
└───────────────────────────┬────────────────────────────┘
                            │ MVI Intent / UiState
                            ▼
┌────────────────────────────────────────────────────────┐
│                      :domain                           │
│  - InduceTemplateUseCase, SaveTemplateUseCase          │
│  - FinancialAnalyticsUseCase, DiagnosticsProbeUseCase  │
└───────────────────────────┬────────────────────────────┘
                            │ Queries / DTO
                            ▼
┌────────────────────────────────────────────────────────┐
│             zone/core-storage & runtime                │
│  - StorageGateway: getAggregatedTotals, getDailySeries │
│  - OrchestratorProbe: lock-free snapshot               │
└────────────────────────────────────────────────────────┘
```

---

### 2. Спецификация контрактов Presentation

#### 2.1 One-Tap Template Editor
```kotlin
package com.example.npc.feature.editor.model

import com.example.npc.induction.SlotType
import com.example.npc.induction.TokenRole
import kotlinx.collections.immutable.ImmutableList

@androidx.compose.runtime.Immutable
data class EditorTokenUi(
    val index: Int,
    val text: String,
    val role: TokenRole,
    val assignedSlot: SlotType?,
    val isAnchorKeyword: Boolean
)

@androidx.compose.runtime.Immutable
data class EditorUiState(
    val eventId: Long,
    val packageName: String,
    val tokens: ImmutableList<EditorTokenUi>,
    val detectedOpType: String,
    val isRefund: Boolean,
    val isValidationInProgress: Boolean,
    val positiveMatchesCount: Int = 0,
    val conflictCount: Int = 0,
    val validationError: String? = null,
    val canSave: Boolean = false,
    val isSaving: Boolean = false
)

sealed interface EditorUiIntent {
    data class AssignTokenRole(val tokenIndex: Int, val role: TokenRole, val slot: SlotType?) : EditorUiIntent
    data class ToggleRefund(val isRefund: Boolean) : EditorUiIntent
    data class ChangeOpType(val opType: String) : EditorUiIntent
    data object SaveAndActivate : EditorUiIntent
}

sealed interface EditorUiEffect {
    data class SavedSuccessfully(val templateId: String, val affectedEventsCount: Int) : EditorUiEffect
    data class ShowToast(val message: String) : EditorUiEffect
}
```

#### 2.2 Financial Analytics UI
```kotlin
package com.example.npc.feature.analytics.model

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.TransactionType
import kotlinx.collections.immutable.ImmutableList
import java.time.Instant

enum class TimePeriodSelection {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    CUSTOM
}

enum class RefundCalculationMode {
    REDUCE_EXPENSE, // Возврат вычитается из расходов (дефолт)
    TREAT_AS_INCOME // Возврат прибавляется к доходам
}

data class CurrencyCardUi(
    val currency: CurrencyCode,
    val totalExpenseMinor: Long,
    val totalIncomeMinor: Long,
    val totalRefundMinor: Long,
    val netMinor: Long,
    val transactionCount: Int
)

@androidx.compose.runtime.Immutable
data class AnalyticsUiState(
    val period: TimePeriodSelection = TimePeriodSelection.THIS_MONTH,
    val customFrom: Instant? = null,
    val customTo: Instant? = null,
    val directionFilter: TransactionType? = null,
    val refundMode: RefundCalculationMode = RefundCalculationMode.REDUCE_EXPENSE,
    val includeSuggested: Boolean = false,
    val cards: ImmutableList<CurrencyCardUi> = kotlinx.collections.immutable.persistentListOf(),
    val isLoading: Boolean = false
)
```

#### 2.3 Orchestrator Diagnostics Screen
```kotlin
package com.example.npc.feature.diagnostics.model

import kotlinx.collections.immutable.ImmutableList

enum class BreakerUiState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

data class BreakerInfoUi(
    val nodeId: String,
    val state: BreakerUiState,
    val failureCount: Int,
    val lastFailureTimestamp: Long?
)

data class TraceRowUi(
    val seq: Long,
    val eventId: Long,
    val nodeId: String,
    val outcome: String,
    val durationUs: Long,
    val templateId: String?
)

@androidx.compose.runtime.Immutable
data class DiagnosticsUiState(
    val isLiveUpdateEnabled: Boolean = true,
    val ingestQueueSize: Int = 0,
    val ingestEventsPerMinute: Int = 0,
    val activePipelineRevision: Long = 0L,
    val activeBankVersion: Long = 0L,
    val activeTemplatesCount: Int = 0,
    val quarantinedTemplatesCount: Int = 0,
    val financeWithoutPayloadAlertCount: Int = 0,
    val breakers: ImmutableList<BreakerInfoUi> = kotlinx.collections.immutable.persistentListOf(),
    val recentTraces: ImmutableList<TraceRowUi> = kotlinx.collections.immutable.persistentListOf()
)
```

---

### 3. Расширенный контракт StorageGateway для аналитики

```kotlin
package com.example.npc.core.storage

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.feature.analytics.model.RefundCalculationMode
import kotlinx.coroutines.flow.Flow
import java.time.Instant

data class MultiCurrencyAggregates(
    val totalsByCurrency: Map<CurrencyCode, AggregatedSums>
)

data class AggregatedSums(
    val expenseMinor: Long,
    val incomeMinor: Long,
    val refundMinor: Long,
    val count: Int
)

interface FinancialAnalyticsStorage {
    /**
     * Возвращает агрегированные суммы по всем валютам за выбранный период.
     */
    fun observeAggregatedTotals(
        from: Instant,
        to: Instant,
        direction: TransactionType?,
        refundMode: RefundCalculationMode,
        includeSuggested: Boolean
    ): Flow<MultiCurrencyAggregates>

    /**
     * Постраничный список финансовых транзакций.
     */
    fun pagedTransactionsByPeriod(
        from: Instant,
        to: Instant,
        direction: TransactionType?,
        includeSuggested: Boolean
    ): Flow<androidx.paging.PagingData<FinancialTransaction>>
}
```

---

### 4. Инварианты производительности UI (Poco M7)
1. **Строгая иммутабельность:** Все поля состояний помечены `@Immutable`, списки используют `ImmutableList`.
2. **Частота опроса диагностики:** `DiagnosticsViewModel` выполняет сэмплирование снэпшота с частотой строго не более 2 Гц (интервал 500 мс) и останавливает сбор потока в состоянии `onStop`.
3. **Плавность анимации (60 FPS):** Отрисовка графиков аналитики выполняется исключительно через легковесный `androidx.compose.ui.graphics.drawscope.DrawScope` без аллокаций объектов на кадр.
