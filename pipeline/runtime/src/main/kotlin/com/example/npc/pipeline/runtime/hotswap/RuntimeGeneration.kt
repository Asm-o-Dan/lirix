package com.example.npc.pipeline.runtime.hotswap

import com.example.npc.pipeline.compiler.CompiledPipeline
import com.google.re2j.Pattern

/**
 * Скомпилированное регулярное выражение шаблона с метаданными.
 * Поддерживает как классический монолитный шаблон (pattern),
 * так и конвейер слотовых регулярок (Slot-Decomposed Regex Pipeline, OPT-PIPE-001).
 */
data class CompiledTemplate(
    val id: String,
    val sourceKey: String,
    val priority: Int,
    val pattern: Pattern = Pattern.compile(".*"),
    val requiredLiterals: List<String> = emptyList(),
    val constants: Map<String, String> = emptyMap(),
    val bindingsJson: String = "{}",
    val anchorPattern: Pattern? = null,
    val slotRules: Map<String, Pattern> = emptyMap(),
    val slotPipeline: com.example.npc.pipeline.runtime.slot.SlotDecomposedPipeline? = null
) {
    val isDecomposed: Boolean
        get() = slotPipeline != null || slotRules.isNotEmpty() || anchorPattern != null

    val resolvedPipeline: com.example.npc.pipeline.runtime.slot.SlotDecomposedPipeline? by lazy {
        slotPipeline ?: if (isDecomposed) {
            com.example.npc.pipeline.runtime.slot.SlotDecomposedPipeline.fromRules(anchorPattern, slotRules)
        } else null
    }

    constructor(
        id: String,
        sourceKey: String,
        priority: Int,
        anchorPattern: String,
        slotRules: Map<String, String>,
        requiredLiterals: List<String> = emptyList(),
        constants: Map<String, String> = emptyMap(),
        bindingsJson: String = "{}"
    ) : this(
        id = id,
        sourceKey = sourceKey,
        priority = priority,
        pattern = Pattern.compile(anchorPattern),
        requiredLiterals = requiredLiterals,
        constants = constants,
        bindingsJson = bindingsJson,
        anchorPattern = Pattern.compile(anchorPattern),
        slotRules = slotRules.mapValues { Pattern.compile(it.value) }
    )
}

/**
 * Скомпилированный банк активных шаблонов с индексами по источникам для O(1) префильтрации.
 */
data class CompiledTemplateBank(
    val bankVersion: Long,
    val overrideTemplatesBySource: Map<String, List<CompiledTemplate>> = emptyMap(),
    val fallbackTemplatesBySource: Map<String, List<CompiledTemplate>> = emptyMap()
) {
    companion object {
        val Empty = CompiledTemplateBank(bankVersion = 0L)
    }
}

/**
 * Единый атомарный контейнер поколения рантайма (ADR-304).
 * Гарантирует синхронность версий конвейера и банка шаблонов для каждого обрабатываемого события.
 */
data class RuntimeGeneration(
    val pipeline: CompiledPipeline,
    val bank: CompiledTemplateBank,
    val generationId: Long,
    val activatedAtTimestamp: Long = System.currentTimeMillis()
)
