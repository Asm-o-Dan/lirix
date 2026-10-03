package com.example.npc.pipeline.runtime.hotswap

import com.example.npc.pipeline.compiler.CompiledPipeline
import kotlinx.coroutines.flow.StateFlow

data class GenerationSnapshotInfo(
    val generationId: Long,
    val pipelineRevision: Long,
    val bankVersion: Long,
    val activatedAtTimestamp: Long
)

/**
 * Потокобезопасный провайдер активного поколения рантайма с поддержкой горячей подмены CAS (ADR-304).
 */
interface ActiveGenerationProvider {

    /**
     * Возвращает текущее активное поколение рантайма.
     * Выполняется за O(1), строго 0 аллокаций.
     */
    fun current(): RuntimeGeneration

    /**
     * Атомарная замена поколения рантайма через CAS с проверкой монотонности generationId.
     */
    fun swap(next: RuntimeGeneration): RuntimeGeneration

    /**
     * Атомарное обновление банка шаблонов с сохранением активного конвейера.
     */
    fun updateBank(newBank: CompiledTemplateBank): RuntimeGeneration

    /**
     * Атомарное обновление конвейера с сохранением активного банка шаблонов.
     */
    fun updatePipeline(newPipeline: CompiledPipeline): RuntimeGeneration

    val activeGenerationFlow: StateFlow<GenerationSnapshotInfo>

    companion object {
        fun create(initial: RuntimeGeneration): ActiveGenerationProvider =
            ActiveGenerationProviderImpl(initial)
    }
}
