package com.example.npc.pipeline.compiler

import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import com.google.re2j.Pattern

class CompiledPipeline(
    val pipelineId: String,
    val revision: Long,
    val canonicalHash: String,
    val compiledAtTimestamp: Long,
    val dslVersion: Int,
    val compilerVersion: Int,
    val packageWhitelistSet: Set<String>,
    val requiredInputMask: Long,
    val layout: FrameLayout,
    @JvmField internal val stages: Array<CompiledStage>,
    @JvmField internal val patterns: Array<Pattern>,
    val debugInfo: DebugInfo
) {
    init {
        require(stages.isNotEmpty()) { "CompiledPipeline must contain at least one stage" }
        require(revision >= 1L) { "Revision must be >= 1, got $revision" }
        require(canonicalHash.isNotBlank()) { "Canonical hash must not be blank" }
    }

    val stagesCount: Int get() = stages.size

    fun execute(frame: Frame): Int {
        val stageArray = stages
        var pc = 0
        while (pc >= 0) {
            pc = stageArray[pc].execute(frame)
        }
        return pc
    }
}
