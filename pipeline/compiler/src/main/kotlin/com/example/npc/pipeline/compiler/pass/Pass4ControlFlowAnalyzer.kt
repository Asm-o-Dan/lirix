package com.example.npc.pipeline.compiler.pass

import com.example.npc.pipeline.compiler.diagnostic.CompilationDiagnostic
import com.example.npc.pipeline.compiler.diagnostic.DiagnosticSeverity
import com.example.npc.pipeline.compiler.diagnostic.SourceLocation
import com.example.npc.pipeline.compiler.diagnostic.Target
import com.example.npc.pipeline.dsl.ActionDefinition
import com.example.npc.pipeline.dsl.ConditionDefinition
import com.example.npc.pipeline.dsl.PipelineDefinition
import com.example.npc.pipeline.dsl.TransformDefinition

data class ControlFlowAnalysisResult(
    val diagnostics: List<CompilationDiagnostic>,
    val deadStageIds: Set<String>,
    val estimatedRefSlots: Int,
    val estimatedPrimSlots: Int
)

interface Pass4ControlFlowAnalyzer {
    fun analyze(definition: PipelineDefinition): ControlFlowAnalysisResult

    companion object {
        fun create(): Pass4ControlFlowAnalyzer = Pass4ControlFlowAnalyzerImpl()
        const val MAX_REF_SLOTS = 128
        const val MAX_PRIM_SLOTS = 64
    }
}

class Pass4ControlFlowAnalyzerImpl : Pass4ControlFlowAnalyzer {

    override fun analyze(definition: PipelineDefinition): ControlFlowAnalysisResult {
        val diagnostics = mutableListOf<CompilationDiagnostic>()
        val deadStageIds = mutableSetOf<String>()

        var hasTerminated = false
        var terminatingStageId: String? = null

        // 1. Trace stages for reachability (Dead Stage Detection)
        definition.stages.forEachIndexed { index, stage ->
            val stagePath = "$['stages'][$index]"

            if (hasTerminated) {
                deadStageIds.add(stage.id)
                diagnostics.add(
                    CompilationDiagnostic(
                        code = "P4001",
                        severity = DiagnosticSeverity.WARNING,
                        message = "Stage '${stage.id}' is unreachable due to preceding unconditional termination at stage '$terminatingStageId'",
                        messageKey = "warning.stage.unreachable",
                        messageArgs = listOf(stage.id, terminatingStageId ?: ""),
                        location = SourceLocation(jsonPath = stagePath, target = Target.VALUE, stageId = stage.id)
                    )
                )
            } else {
                // Check if this stage unconditionally terminates
                val isUnconditional = stage.condition == null || stage.condition is ConditionDefinition.AlwaysTrue
                val dropsOrStops = stage.actions.any { it is ActionDefinition.DropEvent || it is ActionDefinition.StopProcessing }
                val terminatesOnMatch = stage.terminateOnMatch

                if (isUnconditional && (terminatesOnMatch || dropsOrStops)) {
                    hasTerminated = true
                    terminatingStageId = stage.id
                }
            }

            // 2. Check for trivial constant conditions
            stage.condition?.let { cond ->
                checkTrivialConditions(cond, "$stagePath['condition']", stage.id, diagnostics)
            }
        }

        // 3. Count estimated slots
        var refSlots = 4 // Base: input.title, text, sender, pkg
        var primSlots = 2 // Base: postTime, channelId

        definition.stages.forEach { stage ->
            stage.transforms.forEach { t ->
                when (t) {
                    is TransformDefinition.RegionalTextSanitize -> refSlots++
                    is TransformDefinition.FingerprintCompute -> refSlots++
                    is TransformDefinition.AmountParse -> primSlots++
                    is TransformDefinition.CurrencyResolve -> refSlots++
                    is TransformDefinition.FinanceExtract -> refSlots++
                }
            }
            stage.condition?.let { cond ->
                refSlots += countRegexPatterns(cond)
            }
        }

        if (refSlots > Pass4ControlFlowAnalyzer.MAX_REF_SLOTS || primSlots > Pass4ControlFlowAnalyzer.MAX_PRIM_SLOTS) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P4006",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Memory slot complexity limit exceeded. Ref slots: $refSlots (max ${Pass4ControlFlowAnalyzer.MAX_REF_SLOTS}), Prim slots: $primSlots (max ${Pass4ControlFlowAnalyzer.MAX_PRIM_SLOTS})",
                    messageKey = "error.memory.slot_limit_exceeded",
                    messageArgs = listOf(refSlots.toString(), primSlots.toString()),
                    location = SourceLocation(jsonPath = "$['stages']", target = Target.VALUE)
                )
            )
        }

        return ControlFlowAnalysisResult(
            diagnostics = diagnostics,
            deadStageIds = deadStageIds,
            estimatedRefSlots = refSlots,
            estimatedPrimSlots = primSlots
        )
    }

    private fun checkTrivialConditions(
        cond: ConditionDefinition,
        path: String,
        stageId: String,
        diagnostics: MutableList<CompilationDiagnostic>
    ) {
        when (cond) {
            is ConditionDefinition.LogicalAnd -> {
                if (cond.conditions.any { it is ConditionDefinition.AlwaysFalse }) {
                    diagnostics.add(
                        CompilationDiagnostic(
                            code = "P4002",
                            severity = DiagnosticSeverity.WARNING,
                            message = "Condition trivially evaluates to constant (AlwaysFalse) at compile time",
                            messageKey = "warning.condition.trivially_false",
                            messageArgs = listOf("AlwaysFalse"),
                            location = SourceLocation(jsonPath = path, target = Target.VALUE, stageId = stageId)
                        )
                    )
                }
                cond.conditions.forEachIndexed { i, c ->
                    checkTrivialConditions(c, "$path['conditions'][$i]", stageId, diagnostics)
                }
            }
            is ConditionDefinition.LogicalOr -> {
                if (cond.conditions.any { it is ConditionDefinition.AlwaysTrue }) {
                    diagnostics.add(
                        CompilationDiagnostic(
                            code = "P4002",
                            severity = DiagnosticSeverity.WARNING,
                            message = "Condition trivially evaluates to constant (AlwaysTrue) at compile time",
                            messageKey = "warning.condition.trivially_true",
                            messageArgs = listOf("AlwaysTrue"),
                            location = SourceLocation(jsonPath = path, target = Target.VALUE, stageId = stageId)
                        )
                    )
                }
                cond.conditions.forEachIndexed { i, c ->
                    checkTrivialConditions(c, "$path['conditions'][$i]", stageId, diagnostics)
                }
            }
            is ConditionDefinition.LogicalNot -> {
                if (cond.condition is ConditionDefinition.AlwaysTrue || cond.condition is ConditionDefinition.AlwaysFalse) {
                    val constVal = if (cond.condition is ConditionDefinition.AlwaysTrue) "AlwaysFalse" else "AlwaysTrue"
                    diagnostics.add(
                        CompilationDiagnostic(
                            code = "P4002",
                            severity = DiagnosticSeverity.WARNING,
                            message = "Condition trivially evaluates to constant ($constVal) at compile time",
                            messageKey = "warning.condition.trivial_not",
                            messageArgs = listOf(constVal),
                            location = SourceLocation(jsonPath = path, target = Target.VALUE, stageId = stageId)
                        )
                    )
                } else {
                    checkTrivialConditions(cond.condition, "$path['condition']", stageId, diagnostics)
                }
            }
            else -> {}
        }
    }

    private fun countRegexPatterns(cond: ConditionDefinition): Int {
        return when (cond) {
            is ConditionDefinition.TextRegexMatch -> 1
            is ConditionDefinition.LogicalAnd -> cond.conditions.sumOf { countRegexPatterns(it) }
            is ConditionDefinition.LogicalOr -> cond.conditions.sumOf { countRegexPatterns(it) }
            is ConditionDefinition.LogicalNot -> countRegexPatterns(cond.condition)
            else -> 0
        }
    }
}
