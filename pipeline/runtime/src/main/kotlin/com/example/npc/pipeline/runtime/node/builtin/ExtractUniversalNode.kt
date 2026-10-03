package com.example.npc.pipeline.runtime.node.builtin

import com.example.npc.extract.universal.node.UniversalExtractorNodeExecutor
import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.executor.NodeExecutor
import com.example.npc.pipeline.nodes.api.executor.StepResult
import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.runtime.resilience.NodeCircuitBreaker

/**
 * Обертка узла extract.universal с предохранителем NodeCircuitBreaker.
 */
class ExtractUniversalNode(
    val delegate: UniversalExtractorNodeExecutor,
    val breaker: NodeCircuitBreaker = NodeCircuitBreaker(stageId = "extract.universal")
) : NodeExecutor {

    override fun execute(frame: Frame, buffer: EffectBuffer): StepResult {
        val nowMs = System.currentTimeMillis()
        if (breaker.shouldBypass(nowMs)) {
            // Предохранитель открыт из-за сбоев, пропускаем узел
            return StepResult.jump(delegate.nextPc)
        }

        return try {
            val res = delegate.execute(frame, buffer)
            breaker.recordSuccess()
            res
        } catch (t: Throwable) {
            breaker.recordFailure(nowMs)
            StepResult.jump(delegate.nextPc)
        }
    }
}
