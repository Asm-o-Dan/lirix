package com.example.npc.pipeline.compiler

import com.example.npc.pipeline.compiler.diagnostic.CompilationDiagnostic
import com.example.npc.pipeline.compiler.diagnostic.DiagnosticSeverity
import com.example.npc.pipeline.compiler.pass.Pass1StructuralValidator
import com.example.npc.pipeline.compiler.pass.Pass2RegexValidator
import com.example.npc.pipeline.compiler.pass.Pass3DataflowAnalyzer
import com.example.npc.pipeline.compiler.pass.Pass4ControlFlowAnalyzer
import com.example.npc.pipeline.compiler.result.CompilationResult
import com.example.npc.pipeline.dsl.ActionDefinition
import com.example.npc.pipeline.dsl.PipelineDefinition
import com.example.npc.pipeline.dsl.StageDefinition
import com.example.npc.pipeline.dsl.preset.LegacyPipelinePreset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis

class PipelineCompilerTest {

    private val compiler = PipelineCompiler.create()

    @Test
    fun goldenTest_canonicalPresetCompilesToSuccess() {
        val preset = LegacyPipelinePreset.canonicalDefinition

        // Warmup to avoid classloader / JIT cold-start flakiness
        compiler.compile(preset)

        var result: CompilationResult? = null
        val timeMs = measureTimeMillis {
            result = compiler.compile(preset)
        }

        assertTrue(result is CompilationResult.Success, "Expected Success but got: $result")
        val success = result as CompilationResult.Success
        assertFalse(success.hasErrors)
        assertEquals(preset.id, success.pipeline.pipelineId)
        assertEquals(preset.stages.size, success.pipeline.stagesCount)
        assertTrue(timeMs < 100, "Compilation should be fast, took ${timeMs}ms")
    }

    @Test
    fun invalidPipeline_returnsFailure() {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val invalidDef = unsafe.allocateInstance(PipelineDefinition::class.java) as PipelineDefinition

        fun set(name: String, v: Any?) {
            val f = PipelineDefinition::class.java.getDeclaredField(name)
            f.isAccessible = true
            f.set(invalidDef, v)
        }
        set("id", "bad id with spaces")
        set("name", "")
        set("schemaVersion", 999)
        set("revision", 1L)
        set("enabled", true)
        set("priority", 100)
        set("packageWhitelist", emptyList<String>())
        set("triggers", emptyList<com.example.npc.pipeline.dsl.TriggerDefinition>())
        set("stages", emptyList<com.example.npc.pipeline.dsl.StageDefinition>())
        set("metadata", emptyMap<String, String>())

        val result = compiler.compile(invalidDef)
        assertTrue(result is CompilationResult.Failure)
        val failure = result as CompilationResult.Failure
        assertTrue(failure.hasErrors)
        assertTrue(failure.errors.size >= 3)
    }

    @Test
    fun exceptionIsolation_passThrowsException_returnsE0000Failure() {
        val throwingPass1 = object : Pass1StructuralValidator {
            override fun validate(definition: PipelineDefinition): List<CompilationDiagnostic> {
                throw RuntimeException("Simulated catastrophic crash in Pass 1")
            }
        }

        val safeCompiler = PipelineCompilerImpl(
            pass1 = throwingPass1,
            pass2 = Pass2RegexValidator.create(),
            pass3 = Pass3DataflowAnalyzer.create(),
            pass4 = Pass4ControlFlowAnalyzer.create()
        )

        val result = safeCompiler.compile(LegacyPipelinePreset.canonicalDefinition)
        assertTrue(result is CompilationResult.Failure)
        val failure = result as CompilationResult.Failure
        val fatalDiag = failure.errors.find { it.code == "E0000" }
        assertTrue(fatalDiag != null)
        assertTrue(fatalDiag!!.message.contains("Simulated catastrophic crash in Pass 1"))
    }
}
