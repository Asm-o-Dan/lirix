package com.example.npc.pipeline.compiler.result

import com.example.npc.pipeline.compiler.CompiledPipeline
import com.example.npc.pipeline.compiler.diagnostic.CompilationDiagnostic
import com.example.npc.pipeline.compiler.diagnostic.DiagnosticSeverity

sealed interface CompilationResult {
    val diagnostics: List<CompilationDiagnostic>

    val hasErrors: Boolean
        get() = diagnostics.any { it.severity == DiagnosticSeverity.ERROR }

    val hasWarnings: Boolean
        get() = diagnostics.any { it.severity == DiagnosticSeverity.WARNING }

    val errors: List<CompilationDiagnostic>
        get() = diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }

    val warnings: List<CompilationDiagnostic>
        get() = diagnostics.filter { it.severity == DiagnosticSeverity.WARNING }

    data class Success(
        val pipeline: CompiledPipeline,
        override val diagnostics: List<CompilationDiagnostic> = emptyList()
    ) : CompilationResult {
        init {
            require(diagnostics.none { it.severity == DiagnosticSeverity.ERROR }) {
                "CompilationResult.Success cannot contain ERROR diagnostics, but found: ${diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }}"
            }
        }
    }

    data class Failure(
        override val diagnostics: List<CompilationDiagnostic>
    ) : CompilationResult {
        init {
            require(diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) {
                "CompilationResult.Failure must contain at least one ERROR diagnostic, but got none"
            }
        }
    }
}
