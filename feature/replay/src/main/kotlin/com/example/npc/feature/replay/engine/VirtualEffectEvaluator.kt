package com.example.npc.feature.replay.engine

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.pipeline.compiler.Signal
import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.effect.EffectKindId
import com.example.npc.pipeline.nodes.api.effect.EffectView

data class EvaluationOutcome(
    val signal: Int,
    val category: Category,
    val confidence: Double,
    val transaction: FinancialTransaction?,
    val executionDurationNanos: Long
) {
    val isDropped: Boolean get() = signal == Signal.DROP
    val isFault: Boolean get() = signal == Signal.FAULT
    val isPassed: Boolean get() = signal == Signal.PASS
}

class VirtualEffectEvaluator {

    /**
     * Преобразует сырое содержимое буфера побочных эффектов в виртуальный результат в памяти
     * без вызова StorageGateway и без мутаций базы данных SQLite.
     *
     * @param signal Терминальный сигнал выполнения конвейера (Signal.PASS, Signal.DROP, Signal.FAULT).
     * @param effectBuffer Предвыделенный плоский буфер эффектов узлов конвейера.
     * @param durationNanos Измеренное время выполнения конвейера в наносекундах.
     * @return [EvaluationOutcome] с извлеченной категорией, уверенностью и транзакцией.
     */
    fun evaluate(
        signal: Int,
        effectBuffer: EffectBuffer,
        durationNanos: Long
    ): EvaluationOutcome {
        if (signal == Signal.DROP) {
            return EvaluationOutcome(
                signal = Signal.DROP,
                category = Category.UNCLASSIFIED,
                confidence = 0.0,
                transaction = null,
                executionDurationNanos = durationNanos
            )
        }

        var resolvedCategory = Category.UNCLASSIFIED
        var resolvedConfidence = 0.0
        var resolvedTransaction: FinancialTransaction? = null

        val view = effectBuffer.view()

        while (view.hasNext()) {
            view.next()
            when (view.kindId) {
                EffectKindId.SET_CATEGORY -> {
                    val categoryOrdinal = view.getIntArg(0)
                    resolvedCategory = Category.entries.getOrElse(categoryOrdinal) { Category.UNCLASSIFIED }
                    resolvedConfidence = view.getDoubleArg(1)
                }
                EffectKindId.CREATE_FINANCIAL_TRANSACTION -> {
                    resolvedTransaction = view.getRefArg(0) as? FinancialTransaction
                }
            }
        }

        return EvaluationOutcome(
            signal = signal,
            category = resolvedCategory,
            confidence = resolvedConfidence,
            transaction = resolvedTransaction,
            executionDurationNanos = durationNanos
        )
    }
}
