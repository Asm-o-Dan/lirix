package com.example.npc.pipeline.compiler.result

import com.example.npc.pipeline.compiler.CompiledPipeline
import com.example.npc.pipeline.compiler.DebugInfo
import com.example.npc.pipeline.compiler.diagnostic.CompilationDiagnostic
import com.example.npc.pipeline.compiler.diagnostic.DiagnosticSeverity
import com.example.npc.pipeline.compiler.diagnostic.SourceLocation
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompilationResultTest {

    private fun dummyPipeline(): CompiledPipeline {
        return CompiledPipeline(
            pipelineId = "dummy-id",
            revision = 1L,
            canonicalHash = "abc",
            compiledAtTimestamp = 12345L,
            dslVersion = 1,
            compilerVersion = 1,
            packageWhitelistSet = emptySet(),
            requiredInputMask = 0L,
            layout = FrameLayout(1, 1, 1, 1, 100),
            stages = arrayOf(object : com.example.npc.pipeline.compiler.CompiledStage {
                override val stageId: String = "s1"
                override val stageIndex: Int = 0
                override fun execute(frame: com.example.npc.pipeline.nodes.api.frame.Frame): Int = -1
            }),
            patterns = emptyArray(),
            debugInfo = DebugInfo(emptyMap(), emptyMap())
        )
    }

    private fun errorDiag(code: String = "P1001"): CompilationDiagnostic =
        CompilationDiagnostic(
            code = code,
            severity = DiagnosticSeverity.ERROR,
            message = "Error",
            messageKey = "err.key",
            location = SourceLocation("$")
        )

    private fun warningDiag(code: String = "P4001"): CompilationDiagnostic =
        CompilationDiagnostic(
            code = code,
            severity = DiagnosticSeverity.WARNING,
            message = "Warning",
            messageKey = "warn.key",
            location = SourceLocation("$")
        )

    @Test
    fun success_creationWithNoErrors() {
        val success = CompilationResult.Success(dummyPipeline(), listOf(warningDiag()))
        assertFalse(success.hasErrors)
        assertTrue(success.hasWarnings)
        assertEquals(1, success.warnings.size)
        assertEquals(0, success.errors.size)
    }

    @Test
    fun success_throwsWhenContainsError() {
        assertThrows(IllegalArgumentException::class.java) {
            CompilationResult.Success(dummyPipeline(), listOf(errorDiag()))
        }
    }

    @Test
    fun failure_creationWithErrors() {
        val failure = CompilationResult.Failure(listOf(errorDiag(), warningDiag()))
        assertTrue(failure.hasErrors)
        assertTrue(failure.hasWarnings)
        assertEquals(1, failure.errors.size)
        assertEquals(1, failure.warnings.size)
    }

    @Test
    fun failure_throwsWhenNoErrors() {
        assertThrows(IllegalArgumentException::class.java) {
            CompilationResult.Failure(listOf(warningDiag()))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CompilationResult.Failure(emptyList())
        }
    }
}
