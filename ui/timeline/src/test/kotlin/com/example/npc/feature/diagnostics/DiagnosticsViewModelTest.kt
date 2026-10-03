package com.example.npc.feature.diagnostics

import app.cash.turbine.test
import com.example.npc.core.storage.bank.BankTransitionResult
import com.example.npc.core.storage.bank.DynamicTemplateDraft
import com.example.npc.core.storage.bank.TemplateBankInfo
import com.example.npc.core.storage.bank.TemplateBankManager
import com.example.npc.core.storage.bank.TemplateState
import com.example.npc.core.storage.dao.DynamicTemplateDao
import com.example.npc.core.storage.entity.DynamicTemplateEntity
import com.example.npc.feature.diagnostics.model.BreakerUiState
import com.example.npc.feature.diagnostics.model.DiagnosticsUiEffect
import com.example.npc.feature.diagnostics.model.DiagnosticsUiIntent
import com.example.npc.feature.diagnostics.vm.DiagnosticsViewModel
import com.example.npc.pipeline.runtime.diagnostics.DiagnosticsSnapshot
import com.example.npc.pipeline.runtime.diagnostics.OrchestratorProbe
import com.example.npc.pipeline.runtime.diagnostics.QueueDiagnostics
import com.example.npc.pipeline.runtime.hotswap.GenerationSnapshotInfo
import com.example.npc.pipeline.runtime.resilience.CircuitState
import com.example.npc.pipeline.runtime.trace.TraceRecord
import com.example.npc.pipeline.runtime.trace.TraceRing
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private class FakeOrchestratorProbe(
        var snapshot: DiagnosticsSnapshot
    ) : OrchestratorProbe {
        var resetCalledWith: String? = null

        override fun takeSnapshot(): DiagnosticsSnapshot = snapshot

        override fun resetCircuitBreaker(nodeId: String): Boolean {
            resetCalledWith = nodeId
            val updated = snapshot.circuitBreakers.toMutableMap()
            updated[nodeId] = CircuitState.CLOSED
            snapshot = snapshot.copy(circuitBreakers = updated)
            return true
        }
    }

    private class FakeTemplateBankManager : TemplateBankManager {
        val bankFlow = MutableStateFlow(
            TemplateBankInfo(
                version = 12L,
                activeCount = 25,
                shadowCount = 3,
                quarantinedCount = 2,
                lastUpdatedAt = 1000L
            )
        )
        override val currentBankFlow: StateFlow<TemplateBankInfo> = bankFlow
        var activatedId: String? = null

        override suspend fun registerDraft(draft: DynamicTemplateDraft, initialState: TemplateState?): String = "id"
        override suspend fun validateTemplate(templateId: String): BankTransitionResult =
            BankTransitionResult(templateId, TemplateState.DRAFT, TemplateState.VALIDATED, 1L)

        override suspend fun activateTemplate(templateId: String): BankTransitionResult {
            activatedId = templateId
            bankFlow.value = bankFlow.value.copy(
                quarantinedCount = (bankFlow.value.quarantinedCount - 1).coerceAtLeast(0),
                activeCount = bankFlow.value.activeCount + 1,
                version = bankFlow.value.version + 1
            )
            return BankTransitionResult(templateId, TemplateState.QUARANTINED, TemplateState.ACTIVE, bankFlow.value.version)
        }

        override suspend fun transitionToShadow(templateId: String): BankTransitionResult =
            BankTransitionResult(templateId, TemplateState.ACTIVE, TemplateState.SHADOW, 1L)

        override suspend fun quarantineTemplate(templateId: String, reason: String): BankTransitionResult =
            BankTransitionResult(templateId, TemplateState.ACTIVE, TemplateState.QUARANTINED, 1L)

        override suspend fun disableTemplate(templateId: String): BankTransitionResult =
            BankTransitionResult(templateId, TemplateState.ACTIVE, TemplateState.DISABLED, 1L)

        override suspend fun promoteShadowTemplate(templateId: String): BankTransitionResult = activateTemplate(templateId)
        override suspend fun getTemplate(templateId: String): DynamicTemplateEntity? = null
        override suspend fun getBankInfo(): TemplateBankInfo = bankFlow.value
        override suspend fun getLatestBankVersion(): Long = bankFlow.value.version
    }

    private fun createInitialSnapshot(): DiagnosticsSnapshot {
        return DiagnosticsSnapshot(
            generation = GenerationSnapshotInfo(
                generationId = 100L,
                pipelineRevision = 5L,
                bankVersion = 12L,
                activatedAtTimestamp = 1_700_000_000L
            ),
            circuitBreakers = mapOf(
                "extract.universal" to CircuitState.OPEN,
                "classify.rules" to CircuitState.CLOSED
            ),
            queueStats = QueueDiagnostics(
                queueSize = 42,
                capacity = 1000,
                throughputEventsPerSec = 10.0 // 600 events / min
            ),
            recentTraces = listOf(
                TraceRecord(
                    timestampNanos = 100_000L,
                    type = TraceRing.TYPE_STAGE_PASS,
                    stageId = 2, // classify.rules
                    revision = 5,
                    arg = 201
                ),
                TraceRecord(
                    timestampNanos = 200_000L,
                    type = TraceRing.TYPE_BREAKER_TRIP,
                    stageId = 4, // extract.universal
                    revision = 5,
                    arg = 202
                )
            ),
            timestampNanos = 500_000_000L
        )
    }

    @Test
    fun `initialization loads runtime snapshot and updates state correctly`() = testScope.runTest {
        val fakeProbe = FakeOrchestratorProbe(createInitialSnapshot())
        val fakeBank = FakeTemplateBankManager()

        val viewModel = DiagnosticsViewModel(
            orchestratorProbe = fakeProbe,
            templateBankManager = fakeBank,
            financeWithoutPayloadProvider = { 7 },
            samplingIntervalMs = 500L,
            coroutineScope = this
        )

        try {
            advanceUntilIdle()

            val state = viewModel.currentState
            state.activePipelineRevision shouldBe 5L
            state.activeBankVersion shouldBe 12L
            state.activeTemplatesCount shouldBe 25
            state.shadowTemplatesCount shouldBe 3
            state.quarantinedTemplatesCount shouldBe 2
            state.ingestQueueSize shouldBe 42
            state.ingestEventsPerMinute shouldBe 600
            state.financeWithoutPayloadAlertCount shouldBe 7

            state.breakers shouldHaveSize 2
            val openBreaker = state.breakers.first { it.nodeId == "extract.universal" }
            openBreaker.state shouldBe BreakerUiState.OPEN
            openBreaker.failureCount shouldBe 1

            val closedBreaker = state.breakers.first { it.nodeId == "classify.rules" }
            closedBreaker.state shouldBe BreakerUiState.CLOSED
            closedBreaker.failureCount shouldBe 0

            state.recentTraces shouldHaveSize 2
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun `periodic sampling runs at 2 Hz and updates queue and breaker metrics`() = testScope.runTest {
        val fakeProbe = FakeOrchestratorProbe(createInitialSnapshot())
        val viewModel = DiagnosticsViewModel(
            orchestratorProbe = fakeProbe,
            samplingIntervalMs = 500L,
            coroutineScope = this
        )

        try {
            viewModel.dispatch(DiagnosticsUiIntent.StartPolling)
            advanceTimeBy(100L)
            viewModel.currentState.isPollingActive shouldBe true

            // Меняем метрики в рантайме
            fakeProbe.snapshot = fakeProbe.snapshot.copy(
                queueStats = QueueDiagnostics(queueSize = 88, throughputEventsPerSec = 20.0)
            )

            // Прокручиваем время на 500 мс (частота 2 Гц)
            advanceTimeBy(500L)

            viewModel.currentState.ingestQueueSize shouldBe 88
            viewModel.currentState.ingestEventsPerMinute shouldBe 1200
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun `stopPolling stops sampling and prevents background execution on onStop`() = testScope.runTest {
        val fakeProbe = FakeOrchestratorProbe(createInitialSnapshot())
        val viewModel = DiagnosticsViewModel(
            orchestratorProbe = fakeProbe,
            samplingIntervalMs = 500L,
            coroutineScope = this
        )

        try {
            viewModel.dispatch(DiagnosticsUiIntent.StartPolling)
            advanceTimeBy(100L)
            viewModel.currentState.isPollingActive shouldBe true

            // Уход экрана в фон (onStop)
            viewModel.dispatch(DiagnosticsUiIntent.StopPolling)
            viewModel.currentState.isPollingActive shouldBe false

            // Изменение в рантайме во время нахождения в фоне
            fakeProbe.snapshot = fakeProbe.snapshot.copy(
                queueStats = QueueDiagnostics(queueSize = 999, throughputEventsPerSec = 50.0)
            )

            // Прокручиваем время на несколько интервалов
            advanceTimeBy(2000L)

            // Значения НЕ должны были обновиться, так как опрос остановлен
            viewModel.currentState.ingestQueueSize shouldBe 42

            // Возврат на экран (onStart)
            viewModel.dispatch(DiagnosticsUiIntent.StartPolling)
            advanceTimeBy(500L)

            viewModel.currentState.ingestQueueSize shouldBe 999
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun `toggleLiveUpdate controls whether sampling is applied`() = testScope.runTest {
        val fakeProbe = FakeOrchestratorProbe(createInitialSnapshot())
        val viewModel = DiagnosticsViewModel(
            orchestratorProbe = fakeProbe,
            samplingIntervalMs = 500L,
            coroutineScope = this
        )

        try {
            viewModel.dispatch(DiagnosticsUiIntent.StartPolling)
            advanceTimeBy(100L)

            viewModel.dispatch(DiagnosticsUiIntent.ToggleLiveUpdate(false))
            viewModel.currentState.isLiveUpdateEnabled shouldBe false

            fakeProbe.snapshot = fakeProbe.snapshot.copy(
                queueStats = QueueDiagnostics(queueSize = 123)
            )
            advanceTimeBy(1000L)
            viewModel.currentState.ingestQueueSize shouldBe 42 // Заморожено

            viewModel.dispatch(DiagnosticsUiIntent.ToggleLiveUpdate(true))
            viewModel.currentState.isLiveUpdateEnabled shouldBe true
            advanceTimeBy(500L)
            viewModel.currentState.ingestQueueSize shouldBe 123
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun `resetCircuitBreaker returns breaker to CLOSED state and emits effect`() = testScope.runTest {
        val fakeProbe = FakeOrchestratorProbe(createInitialSnapshot())
        val viewModel = DiagnosticsViewModel(
            orchestratorProbe = fakeProbe,
            samplingIntervalMs = 500L,
            coroutineScope = this
        )

        try {
            advanceUntilIdle()

            // Проверяем, что предохранитель изначально OPEN
            val initialBreaker = viewModel.currentState.breakers.first { it.nodeId == "extract.universal" }
            initialBreaker.state shouldBe BreakerUiState.OPEN

            viewModel.effects.test {
                viewModel.dispatch(DiagnosticsUiIntent.ResetCircuitBreaker("extract.universal"))
                advanceUntilIdle()

                fakeProbe.resetCalledWith shouldBe "extract.universal"

                // Проверяем, что состояние предохранителя стало CLOSED (DoD)
                val updatedBreaker = viewModel.currentState.breakers.first { it.nodeId == "extract.universal" }
                updatedBreaker.state shouldBe BreakerUiState.CLOSED
                updatedBreaker.failureCount shouldBe 0

                val effect = awaitItem()
                effect shouldBe DiagnosticsUiEffect.BreakerResetSuccess("extract.universal")
            }
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun `unquarantineTemplate activates template and increments active count`() = testScope.runTest {
        val fakeProbe = FakeOrchestratorProbe(createInitialSnapshot())
        val fakeBank = FakeTemplateBankManager()
        val viewModel = DiagnosticsViewModel(
            orchestratorProbe = fakeProbe,
            templateBankManager = fakeBank,
            samplingIntervalMs = 500L,
            coroutineScope = this
        )

        try {
            advanceUntilIdle()
            viewModel.currentState.quarantinedTemplatesCount shouldBe 2
            viewModel.currentState.activeTemplatesCount shouldBe 25

            viewModel.effects.test {
                viewModel.dispatch(DiagnosticsUiIntent.UnquarantineTemplate("tmpl-maib-001"))
                advanceUntilIdle()

                fakeBank.activatedId shouldBe "tmpl-maib-001"
                viewModel.currentState.quarantinedTemplatesCount shouldBe 1
                viewModel.currentState.activeTemplatesCount shouldBe 26

                val effect = awaitItem()
                effect shouldBe DiagnosticsUiEffect.TemplateUnquarantined("tmpl-maib-001")
            }
        } finally {
            viewModel.close()
        }
    }

    @Test
    fun `filtering traces by nodeId filters recentTraces list`() = testScope.runTest {
        val fakeProbe = FakeOrchestratorProbe(createInitialSnapshot())
        val viewModel = DiagnosticsViewModel(
            orchestratorProbe = fakeProbe,
            coroutineScope = this
        )

        try {
            advanceUntilIdle()
            viewModel.currentState.recentTraces shouldHaveSize 2

            // Фильтруем по "classify.rules"
            viewModel.dispatch(DiagnosticsUiIntent.FilterTracesByNode("classify.rules"))
            advanceUntilIdle()

            viewModel.currentState.selectedNodeFilter shouldBe "classify.rules"
            viewModel.currentState.recentTraces shouldHaveSize 1
            viewModel.currentState.recentTraces.first().nodeId shouldBe "classify.rules"

            // Сбрасываем фильтр в null ("Все")
            viewModel.dispatch(DiagnosticsUiIntent.FilterTracesByNode(null))
            advanceUntilIdle()

            viewModel.currentState.selectedNodeFilter shouldBe null
            viewModel.currentState.recentTraces shouldHaveSize 2
        } finally {
            viewModel.close()
        }
    }
}
