package com.example.npc.pipeline.compiler

import com.example.npc.pipeline.compiler.diagnostic.CompilationDiagnostic
import com.example.npc.pipeline.compiler.diagnostic.DiagnosticSeverity
import com.example.npc.pipeline.compiler.diagnostic.SourceLocation
import com.example.npc.pipeline.compiler.diagnostic.Target
import com.example.npc.pipeline.compiler.pass.Pass1StructuralValidator
import com.example.npc.pipeline.compiler.pass.Pass2RegexValidator
import com.example.npc.pipeline.compiler.pass.Pass3DataflowAnalyzer
import com.example.npc.pipeline.compiler.pass.Pass4ControlFlowAnalyzer
import com.example.npc.pipeline.compiler.result.CompilationResult
import com.example.npc.pipeline.dsl.ActionDefinition
import com.example.npc.pipeline.dsl.ConditionDefinition
import com.example.npc.pipeline.dsl.PipelineDefinition
import com.example.npc.pipeline.dsl.codec.PipelineJsonCodec
import com.example.npc.pipeline.nodes.api.frame.Frame
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import com.google.re2j.Pattern

interface PipelineCompiler {
    fun compile(definition: PipelineDefinition): CompilationResult

    companion object {
        fun create(
            pass1: Pass1StructuralValidator = Pass1StructuralValidator.create(),
            pass2: Pass2RegexValidator = Pass2RegexValidator.create(),
            pass3: Pass3DataflowAnalyzer = Pass3DataflowAnalyzer.create(),
            pass4: Pass4ControlFlowAnalyzer = Pass4ControlFlowAnalyzer.create()
        ): PipelineCompiler {
            return PipelineCompilerImpl(pass1, pass2, pass3, pass4)
        }
    }
}

class PipelineCompilerImpl(
    private val pass1: Pass1StructuralValidator,
    private val pass2: Pass2RegexValidator,
    private val pass3: Pass3DataflowAnalyzer,
    private val pass4: Pass4ControlFlowAnalyzer
) : PipelineCompiler {

    override fun compile(definition: PipelineDefinition): CompilationResult {
        return try {
            val p1Diags = pass1.validate(definition)
            val p2Result = pass2.validate(definition)
            val p3Result = pass3.analyze(definition)
            val p4Result = pass4.analyze(definition)

            val allDiags = p1Diags + p2Result.diagnostics + p3Result.diagnostics + p4Result.diagnostics

            if (allDiags.any { it.severity == DiagnosticSeverity.ERROR }) {
                val sorted = allDiags.sortedWith(
                    compareBy<CompilationDiagnostic> { it.location.jsonPath }
                        .thenBy { it.severity }
                        .thenBy { it.code }
                )
                return CompilationResult.Failure(sorted)
            }

            // Lowering phase
            val hash = PipelineJsonCodec.calculateCanonicalHash(definition)

            val packageWhitelist = mutableSetOf<String>()
            definition.stages.forEach { stage ->
                collectPackages(stage.condition, packageWhitelist)
            }

            val layout = FrameLayout(
                longSlots = maxOf(8, p4Result.estimatedPrimSlots),
                doubleSlots = 4,
                refSlots = maxOf(8, p4Result.estimatedRefSlots),
                textSlots = 8,
                textCapacity = 1024,
                requiredInputMask = p3Result.requiredInputMask
            )

            val stageCount = definition.stages.size
            val compiledStages = Array<CompiledStage>(stageCount) { i ->
                val stageDef = definition.stages[i]
                DefaultCompiledStage(
                    stageId = stageDef.id,
                    stageIndex = i,
                    totalStages = stageCount,
                    terminateOnMatch = stageDef.terminateOnMatch,
                    isDrop = stageDef.actions.any { it is ActionDefinition.DropEvent },
                    hasCondition = stageDef.condition != null
                )
            }

            val patternArray = p2Result.compiledPatterns.values.toTypedArray()

            val debugInfo = DebugInfo(
                stageNames = definition.stages.associate { it.id to it.name },
                variableNames = p3Result.symbolTable.values.associate { it.slotIndex to it.name }
            )

            val compiledPipeline = CompiledPipeline(
                pipelineId = definition.id,
                revision = definition.revision,
                canonicalHash = hash,
                compiledAtTimestamp = System.currentTimeMillis(),
                dslVersion = definition.schemaVersion,
                compilerVersion = 1,
                packageWhitelistSet = packageWhitelist,
                requiredInputMask = p3Result.requiredInputMask,
                layout = layout,
                stages = compiledStages,
                patterns = patternArray,
                debugInfo = debugInfo
            )

            val nonErrors = allDiags.filter { it.severity != DiagnosticSeverity.ERROR }
            CompilationResult.Success(compiledPipeline, nonErrors)
        } catch (t: Throwable) {
            val fatalDiag = CompilationDiagnostic(
                code = "E0000",
                severity = DiagnosticSeverity.ERROR,
                message = "Internal compiler error during processing: ${t.message ?: t.javaClass.simpleName}",
                messageKey = "error.compiler.internal",
                messageArgs = listOf(t.message ?: t.javaClass.simpleName),
                location = SourceLocation(jsonPath = "$", target = Target.VALUE)
            )
            CompilationResult.Failure(listOf(fatalDiag))
        }
    }

    private fun collectPackages(condition: ConditionDefinition?, out: MutableSet<String>) {
        if (condition == null) return
        when (condition) {
            is ConditionDefinition.PackageMatch -> out.addAll(condition.packages)
            is ConditionDefinition.LogicalAnd -> condition.conditions.forEach { collectPackages(it, out) }
            is ConditionDefinition.LogicalOr -> condition.conditions.forEach { collectPackages(it, out) }
            is ConditionDefinition.LogicalNot -> collectPackages(condition.condition, out)
            else -> {}
        }
    }

    private class DefaultCompiledStage(
        override val stageId: String,
        override val stageIndex: Int,
        private val totalStages: Int,
        private val terminateOnMatch: Boolean,
        private val isDrop: Boolean,
        private val hasCondition: Boolean
    ) : CompiledStage {
        override fun execute(frame: Frame): Int {
            if (isDrop) return Signal.DROP
            if (terminateOnMatch) return Signal.PASS
            val next = stageIndex + 1
            return if (next >= totalStages) Signal.PASS else next
        }
    }
}

