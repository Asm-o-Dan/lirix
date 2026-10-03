package com.example.npc.pipeline.compiler.diagnostic

enum class DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}

enum class Target {
    VALUE,
    KEY
}

data class TextSpan(
    val start: Int,
    val end: Int
) {
    init {
        require(start >= 0) { "Start must be non-negative: $start" }
        require(end >= start) { "End ($end) must be >= start ($start)" }
    }
}

data class SourceLocation(
    val jsonPath: String,
    val target: Target = Target.VALUE,
    val stageId: String? = null,
    val span: TextSpan? = null
)

data class QuickFix(
    val id: String,
    val title: String,
    val targetPath: String,
    val replacementJson: String
)

data class CompilationDiagnostic(
    val code: String,
    val severity: DiagnosticSeverity,
    val message: String,
    val messageKey: String,
    val messageArgs: List<String> = emptyList(),
    val location: SourceLocation,
    val relatedLocations: List<SourceLocation> = emptyList(),
    val hint: String? = null,
    val quickFixes: List<QuickFix> = emptyList()
) {
    init {
        require(code.isNotBlank()) { "Diagnostic code cannot be blank" }
        require(message.isNotBlank()) { "Diagnostic message cannot be blank" }
    }
}
