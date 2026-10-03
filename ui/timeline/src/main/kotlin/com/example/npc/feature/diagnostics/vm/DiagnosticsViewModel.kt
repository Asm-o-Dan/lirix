package com.example.npc.feature.diagnostics.vm

import com.example.npc.core.storage.bank.DynamicTemplateDraft
import com.example.npc.core.storage.bank.TemplateBankManager
import com.example.npc.core.storage.bank.TemplateState
import com.example.npc.core.storage.dao.DynamicTemplateDao
import com.example.npc.domain.mvi.BaseViewModel
import com.example.npc.feature.diagnostics.model.BreakerInfoUi
import com.example.npc.feature.diagnostics.model.BreakerUiState
import com.example.npc.feature.diagnostics.model.DiagnosticsUiEffect
import com.example.npc.feature.diagnostics.model.DiagnosticsUiIntent
import com.example.npc.feature.diagnostics.model.DiagnosticsUiState
import com.example.npc.feature.diagnostics.model.QuarantinedTemplateUi
import com.example.npc.feature.diagnostics.model.TraceRowUi
import com.example.npc.pipeline.runtime.diagnostics.OrchestratorProbe
import com.example.npc.pipeline.runtime.diagnostics.OrchestratorProbeImpl
import com.example.npc.pipeline.runtime.resilience.CircuitState
import com.example.npc.pipeline.runtime.trace.TraceRecord
import com.example.npc.pipeline.runtime.trace.TraceRing
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * MVI ViewModel экрана диагностики конвейера.
 * Выполняет периодический сэмплинг рантайма через OrchestratorProbe с частотой 2 Гц (500 мс)
 * с автоматической остановкой при уходе экрана в фон (onStop / DisposableEffect).
 */
class DiagnosticsViewModel(
    private val orchestratorProbe: OrchestratorProbe = OrchestratorProbeImpl(TraceRing(128)),
    private val templateBankManager: TemplateBankManager? = null,
    private val dynamicTemplateDao: DynamicTemplateDao? = null,
    private val financeWithoutPayloadProvider: (suspend () -> Int)? = null,
    private val samplingIntervalMs: Long = 500L,
    coroutineScope: CoroutineScope? = null
) : BaseViewModel<DiagnosticsUiState, DiagnosticsUiIntent, DiagnosticsUiEffect>(
    initialState = DiagnosticsUiState(),
    coroutineScope = coroutineScope
) {

    private var pollingJob: Job? = null
    private val failureCounts = ConcurrentHashMap<String, Int>()
    private var allTracesRaw: List<TraceRowUi> = emptyList()

    init {
        // Первоначальный снимок рантайма
        viewModelScope.launch {
            sampleRuntime()
        }
    }

    override fun handleIntent(intent: DiagnosticsUiIntent) {
        when (intent) {
            is DiagnosticsUiIntent.StartPolling -> startPolling()
            is DiagnosticsUiIntent.StopPolling -> stopPolling()
            is DiagnosticsUiIntent.ToggleLiveUpdate -> toggleLiveUpdate(intent.enabled)
            is DiagnosticsUiIntent.ResetCircuitBreaker -> resetBreaker(intent.nodeId)
            is DiagnosticsUiIntent.UnquarantineTemplate -> unquarantineTemplate(intent.templateId)
            is DiagnosticsUiIntent.FilterTracesByNode -> filterTraces(intent.nodeId)
            is DiagnosticsUiIntent.RefreshManual -> {
                viewModelScope.launch { sampleRuntime() }
            }
        }
    }

    private fun startPolling() {
        if (pollingJob?.isActive == true) return
        updateState { it.copy(isPollingActive = true) }
        pollingJob = viewModelScope.launch {
            while (isActive) {
                if (currentState.isLiveUpdateEnabled) {
                    sampleRuntime()
                }
                delay(samplingIntervalMs)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        updateState { it.copy(isPollingActive = false) }
    }

    private fun toggleLiveUpdate(enabled: Boolean) {
        updateState { it.copy(isLiveUpdateEnabled = enabled) }
        if (enabled && pollingJob?.isActive != true && currentState.isPollingActive) {
            startPolling()
        }
    }

    private fun resetBreaker(nodeId: String) {
        viewModelScope.launch {
            val resetOk = orchestratorProbe.resetCircuitBreaker(nodeId)
            failureCounts[nodeId] = 0
            sampleRuntime()
            if (resetOk) {
                emitEffect(DiagnosticsUiEffect.BreakerResetSuccess(nodeId))
            }
        }
    }

    private fun unquarantineTemplate(templateId: String) {
        viewModelScope.launch {
            try {
                if (templateBankManager != null) {
                    templateBankManager.activateTemplate(templateId)
                } else if (dynamicTemplateDao != null) {
                    dynamicTemplateDao.updateState(templateId, TemplateState.ACTIVE.name, "Unquarantined by operator")
                }
                emitEffect(DiagnosticsUiEffect.TemplateUnquarantined(templateId))
                sampleRuntime()
            } catch (e: Exception) {
                emitEffect(DiagnosticsUiEffect.ShowToast("Ошибка разблокировки шаблона: ${e.message}"))
            }
        }
    }

    private fun filterTraces(nodeId: String?) {
        updateState { state ->
            val filtered = if (nodeId.isNullOrBlank()) {
                allTracesRaw
            } else {
                allTracesRaw.filter { it.nodeId == nodeId }
            }
            state.copy(
                selectedNodeFilter = nodeId,
                recentTraces = filtered.take(128).toPersistentList()
            )
        }
    }

    internal suspend fun sampleRuntime() {
        val snapshot = orchestratorProbe.takeSnapshot()

        // 1. Статус предохранителей
        val breakersList = snapshot.circuitBreakers.map { (nodeId, state) ->
            val uiState = when (state) {
                CircuitState.CLOSED -> BreakerUiState.CLOSED
                CircuitState.OPEN -> BreakerUiState.OPEN
                CircuitState.HALF_OPEN -> BreakerUiState.HALF_OPEN
            }
            val count = if (uiState == BreakerUiState.CLOSED) {
                0
            } else {
                failureCounts.compute(nodeId) { _, curr -> (curr ?: 0) + 1 } ?: 1
            }
            BreakerInfoUi(
                nodeId = nodeId,
                state = uiState,
                failureCount = count,
                lastFailureTimestamp = if (uiState != BreakerUiState.CLOSED) System.currentTimeMillis() else null
            )
        }.sortedBy { it.nodeId }

        // 2. Список последних трейсов
        val mappedTraces = snapshot.recentTraces.takeLast(128).mapIndexed { idx, record ->
            mapTraceRecord(idx.toLong(), record)
        }
        allTracesRaw = mappedTraces

        val availableNodes = mappedTraces.map { it.nodeId }.distinct().sorted()
        val currentFilter = currentState.selectedNodeFilter
        val displayTraces = if (currentFilter.isNullOrBlank()) {
            mappedTraces
        } else {
            mappedTraces.filter { it.nodeId == currentFilter }
        }.toPersistentList()

        // 3. Банк шаблонов и статистика
        var activeCount = currentState.activeTemplatesCount
        var shadowCount = currentState.shadowTemplatesCount
        var quarantinedCount = currentState.quarantinedTemplatesCount
        var bankVersion = snapshot.generation.bankVersion

        if (templateBankManager != null) {
            val bankInfo = templateBankManager.currentBankFlow.value
            activeCount = bankInfo.activeCount
            shadowCount = bankInfo.shadowCount
            quarantinedCount = bankInfo.quarantinedCount
            bankVersion = bankInfo.version
        }

        // 4. Список шаблонов в карантине
        val quarantinedList = if (dynamicTemplateDao != null) {
            val entities = dynamicTemplateDao.getByState(TemplateState.QUARANTINED.name)
            entities.map { entity ->
                QuarantinedTemplateUi(
                    id = entity.id,
                    sourceKey = entity.sourceKey,
                    pattern = entity.pattern,
                    reason = entity.stateReason,
                    updatedAt = entity.updatedAt
                )
            }.toPersistentList()
        } else {
            currentState.quarantinedTemplates
        }

        if (dynamicTemplateDao != null && templateBankManager == null) {
            quarantinedCount = quarantinedList.size
        }

        // 5. Алерт пушей Finance без полезной нагрузки
        val financeAlertCount = financeWithoutPayloadProvider?.invoke()
            ?: snapshot.financeWithoutPayload.toInt()

        // 6. Метрики очереди
        val queueSize = snapshot.queueStats.queueSize
        val eventsPerMin = (snapshot.queueStats.throughputEventsPerSec * 60).toInt()

        updateState { state ->
            state.copy(
                ingestQueueSize = queueSize,
                ingestEventsPerMinute = eventsPerMin,
                activePipelineRevision = snapshot.generation.pipelineRevision,
                activeBankVersion = bankVersion,
                activeTemplatesCount = activeCount,
                shadowTemplatesCount = shadowCount,
                quarantinedTemplatesCount = quarantinedCount,
                financeWithoutPayloadAlertCount = financeAlertCount,
                breakers = breakersList.toPersistentList(),
                recentTraces = displayTraces,
                quarantinedTemplates = quarantinedList,
                availableNodeFilters = availableNodes.toPersistentList()
            )
        }
    }

    private fun mapTraceRecord(seq: Long, record: TraceRecord): TraceRowUi {
        val outcome = when (record.type) {
            TraceRing.TYPE_EVENT_START -> "START"
            TraceRing.TYPE_STAGE_PASS -> "PASS"
            TraceRing.TYPE_STAGE_FAIL -> "FAIL"
            TraceRing.TYPE_STAGE_BYPASS -> "BYPASS"
            TraceRing.TYPE_EVENT_COMMIT -> "COMMIT"
            TraceRing.TYPE_EVENT_DROP -> "DROP"
            TraceRing.TYPE_BREAKER_TRIP -> "BREAKER_TRIP"
            else -> "CODE_${record.type}"
        }

        val nodeName = when (record.stageId) {
            1 -> "ingest.filter"
            2 -> "classify.rules"
            3 -> "extract.finance"
            4 -> "extract.universal"
            5 -> "storage.sink"
            else -> "stage-${record.stageId}"
        }

        return TraceRowUi(
            seq = seq,
            eventId = record.arg.toLong(),
            nodeId = nodeName,
            outcome = outcome,
            durationUs = (record.timestampNanos / 1000L).coerceAtLeast(0L),
            templateId = if (record.stageId in 3..4 && record.revision > 0) "tmpl-r${record.revision}" else null
        )
    }

    override fun close() {
        stopPolling()
        super.close()
    }
}
