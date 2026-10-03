package com.example.npc.feature.diagnostics.model

import androidx.compose.runtime.Immutable
import com.example.npc.domain.mvi.UiEffect
import com.example.npc.domain.mvi.UiIntent
import com.example.npc.domain.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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

data class QuarantinedTemplateUi(
    val id: String,
    val sourceKey: String,
    val pattern: String,
    val reason: String?,
    val updatedAt: Long
)

@Immutable
data class DiagnosticsUiState(
    val isLiveUpdateEnabled: Boolean = true,
    val ingestQueueSize: Int = 0,
    val ingestEventsPerMinute: Int = 0,
    val activePipelineRevision: Long = 0L,
    val activeBankVersion: Long = 0L,
    val activeTemplatesCount: Int = 0,
    val shadowTemplatesCount: Int = 0,
    val quarantinedTemplatesCount: Int = 0,
    val financeWithoutPayloadAlertCount: Int = 0,
    val breakers: ImmutableList<BreakerInfoUi> = persistentListOf(),
    val recentTraces: ImmutableList<TraceRowUi> = persistentListOf(),
    val quarantinedTemplates: ImmutableList<QuarantinedTemplateUi> = persistentListOf(),
    val selectedNodeFilter: String? = null,
    val availableNodeFilters: ImmutableList<String> = persistentListOf(),
    val isPollingActive: Boolean = false
) : UiState

sealed interface DiagnosticsUiIntent : UiIntent {
    data object StartPolling : DiagnosticsUiIntent
    data object StopPolling : DiagnosticsUiIntent
    data class ToggleLiveUpdate(val enabled: Boolean) : DiagnosticsUiIntent
    data class ResetCircuitBreaker(val nodeId: String) : DiagnosticsUiIntent
    data class UnquarantineTemplate(val templateId: String) : DiagnosticsUiIntent
    data class FilterTracesByNode(val nodeId: String?) : DiagnosticsUiIntent
    data object RefreshManual : DiagnosticsUiIntent
}

sealed interface DiagnosticsUiEffect : UiEffect {
    data class ShowToast(val message: String) : DiagnosticsUiEffect
    data class BreakerResetSuccess(val nodeId: String) : DiagnosticsUiEffect
    data class TemplateUnquarantined(val templateId: String) : DiagnosticsUiEffect
    data class OpenTemplateInEditor(val templateId: String) : DiagnosticsUiEffect
}
