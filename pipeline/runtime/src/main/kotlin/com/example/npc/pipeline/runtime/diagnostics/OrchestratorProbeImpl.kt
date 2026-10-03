package com.example.npc.pipeline.runtime.diagnostics

import com.example.npc.pipeline.runtime.hotswap.ActiveGenerationProvider
import com.example.npc.pipeline.runtime.hotswap.GenerationSnapshotInfo
import com.example.npc.pipeline.runtime.resilience.CircuitState
import com.example.npc.pipeline.runtime.resilience.NodeCircuitBreaker
import com.example.npc.pipeline.runtime.trace.TraceRing
import java.util.concurrent.ConcurrentHashMap

class OrchestratorProbeImpl(
    private val traceRing: TraceRing,
    private val generationProvider: ActiveGenerationProvider? = null,
    initialBreakers: Map<String, NodeCircuitBreaker> = emptyMap(),
    private val queueDiagnosticsProvider: (() -> QueueDiagnostics)? = null,
    private val financeWithoutPayloadProvider: (() -> Long)? = null
) : OrchestratorProbe {

    private val seqlockReader = SeqlockTraceReader(traceRing)
    private val breakers = ConcurrentHashMap<String, NodeCircuitBreaker>(initialBreakers)
    private val internalFinanceWithoutPayload = java.util.concurrent.atomic.AtomicLong(0L)

    fun registerCircuitBreaker(breaker: NodeCircuitBreaker) {
        breakers[breaker.stageId] = breaker
    }

    fun incrementFinanceWithoutPayload() {
        internalFinanceWithoutPayload.incrementAndGet()
    }

    override fun getFinanceWithoutPayloadCount(): Long {
        return financeWithoutPayloadProvider?.invoke() ?: internalFinanceWithoutPayload.get()
    }

    override fun takeSnapshot(): DiagnosticsSnapshot {
        val traces = seqlockReader.readSnapshot()
        val nowMs = System.currentTimeMillis()

        val cbStates = breakers.mapValues { (_, breaker) ->
            breaker.getState(nowMs)
        }

        val genInfo = generationProvider?.let { provider ->
            provider.activeGenerationFlow.value
        } ?: GenerationSnapshotInfo(
            generationId = 0L,
            pipelineRevision = 1L,
            bankVersion = 0L,
            activatedAtTimestamp = nowMs
        )

        val queueStats = queueDiagnosticsProvider?.invoke() ?: QueueDiagnostics()
        val financeWithoutPayload = getFinanceWithoutPayloadCount()

        return DiagnosticsSnapshot(
            generation = genInfo,
            circuitBreakers = cbStates,
            queueStats = queueStats,
            recentTraces = traces,
            timestampNanos = System.nanoTime(),
            financeWithoutPayload = financeWithoutPayload
        )
    }

    override fun resetCircuitBreaker(nodeId: String): Boolean {
        val breaker = breakers[nodeId] ?: return false
        breaker.reset()
        return true
    }
}
