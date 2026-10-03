package com.example.npc.pipeline.runtime.diagnostics

import com.example.npc.pipeline.runtime.hotswap.GenerationSnapshotInfo
import com.example.npc.pipeline.runtime.resilience.CircuitState
import com.example.npc.pipeline.runtime.trace.TraceRecord

data class QueueDiagnostics(
    val queueSize: Int = 0,
    val capacity: Int = 1000,
    val throughputEventsPerSec: Double = 0.0
)

data class DiagnosticsSnapshot(
    val generation: GenerationSnapshotInfo,
    val circuitBreakers: Map<String, CircuitState>,
    val queueStats: QueueDiagnostics,
    val recentTraces: List<TraceRecord>,
    val timestampNanos: Long = System.nanoTime(),
    val financeWithoutPayload: Long = 0L
)

interface OrchestratorProbe {
    /**
     * Считывает консистентный снэпшот рантайма за O(128).
     */
    fun takeSnapshot(): DiagnosticsSnapshot

    /**
     * Ручной сброс состояния предохранителя узла.
     */
    fun resetCircuitBreaker(nodeId: String): Boolean

    /**
     * Возвращает количество событий FINANCE с уверенностью >= 0.85, для которых ни один экстрактор не смог извлечь payload.
     */
    fun getFinanceWithoutPayloadCount(): Long = 0L
}
