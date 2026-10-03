package com.example.npc.core.model.pipeline

data class OrchestratorStatus(
    val state: OrchestratorState,
    val isRecoveryActive: Boolean,
    val metrics: PipelineMetrics,
    val circuitBreakerStatuses: Map<String, Boolean> // extractorId -> isOpen
)
