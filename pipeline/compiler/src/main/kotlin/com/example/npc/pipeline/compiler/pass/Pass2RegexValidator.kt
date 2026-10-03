package com.example.npc.pipeline.compiler.pass

import com.example.npc.pipeline.compiler.diagnostic.CompilationDiagnostic
import com.example.npc.pipeline.compiler.diagnostic.DiagnosticSeverity
import com.example.npc.pipeline.compiler.diagnostic.SourceLocation
import com.example.npc.pipeline.compiler.diagnostic.Target
import com.example.npc.pipeline.compiler.diagnostic.TextSpan
import com.example.npc.pipeline.dsl.ConditionDefinition
import com.example.npc.pipeline.dsl.PipelineDefinition
import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException

data class RegexValidationResult(
    val diagnostics: List<CompilationDiagnostic>,
    val compiledPatterns: Map<String, Pattern>,
    val literalLoweringCandidates: Set<String>
)

interface Pass2RegexValidator {
    fun validate(definition: PipelineDefinition): RegexValidationResult

    companion object {
        fun create(): Pass2RegexValidator = Pass2RegexValidatorImpl()
        const val MAX_REGEX_LENGTH = 256
        const val MAX_REPETITION_EXPANSION = 1000
    }
}

class Pass2RegexValidatorImpl : Pass2RegexValidator {

    private val regexMetaChars = setOf('.', '*', '+', '?', '^', '$', '(', ')', '[', ']', '{', '}', '|', '\\')

    override fun validate(definition: PipelineDefinition): RegexValidationResult {
        val diagnostics = mutableListOf<CompilationDiagnostic>()
        val compiledPatterns = mutableMapOf<String, Pattern>()
        val literalLoweringCandidates = mutableSetOf<String>()

        definition.stages.forEachIndexed { stageIndex, stage ->
            val stagePath = "$['stages'][$stageIndex]"
            stage.condition?.let { cond ->
                collectAndValidateRegexes(
                    condition = cond,
                    currentPath = "$stagePath['condition']",
                    stageId = stage.id,
                    diagnostics = diagnostics,
                    compiledPatterns = compiledPatterns,
                    literalLoweringCandidates = literalLoweringCandidates
                )
            }
        }

        return RegexValidationResult(
            diagnostics = diagnostics,
            compiledPatterns = compiledPatterns,
            literalLoweringCandidates = literalLoweringCandidates
        )
    }

    private fun collectAndValidateRegexes(
        condition: ConditionDefinition,
        currentPath: String,
        stageId: String,
        diagnostics: MutableList<CompilationDiagnostic>,
        compiledPatterns: MutableMap<String, Pattern>,
        literalLoweringCandidates: MutableSet<String>
    ) {
        when (condition) {
            is ConditionDefinition.TextRegexMatch -> {
                val path = "$currentPath['pattern']"
                validateSingleRegex(
                    patternStr = condition.pattern,
                    caseSensitive = condition.caseSensitive,
                    path = path,
                    stageId = stageId,
                    diagnostics = diagnostics,
                    compiledPatterns = compiledPatterns,
                    literalLoweringCandidates = literalLoweringCandidates
                )
            }
            is ConditionDefinition.LogicalAnd -> {
                condition.conditions.forEachIndexed { index, child ->
                    collectAndValidateRegexes(
                        child,
                        "$currentPath['conditions'][$index]",
                        stageId,
                        diagnostics,
                        compiledPatterns,
                        literalLoweringCandidates
                    )
                }
            }
            is ConditionDefinition.LogicalOr -> {
                condition.conditions.forEachIndexed { index, child ->
                    collectAndValidateRegexes(
                        child,
                        "$currentPath['conditions'][$index]",
                        stageId,
                        diagnostics,
                        compiledPatterns,
                        literalLoweringCandidates
                    )
                }
            }
            is ConditionDefinition.LogicalNot -> {
                collectAndValidateRegexes(
                    condition.condition,
                    "$currentPath['condition']",
                    stageId,
                    diagnostics,
                    compiledPatterns,
                    literalLoweringCandidates
                )
            }
            else -> {}
        }
    }

    private fun validateSingleRegex(
        patternStr: String,
        caseSensitive: Boolean,
        path: String,
        stageId: String,
        diagnostics: MutableList<CompilationDiagnostic>,
        compiledPatterns: MutableMap<String, Pattern>,
        literalLoweringCandidates: MutableSet<String>
    ) {
        // 1. Length check
        if (patternStr.length > Pass2RegexValidator.MAX_REGEX_LENGTH) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P2001",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Regex pattern length (${patternStr.length}) exceeds maximum allowed ${Pass2RegexValidator.MAX_REGEX_LENGTH} characters",
                    messageKey = "error.regex.length_exceeded",
                    messageArgs = listOf(patternStr.length.toString(), Pass2RegexValidator.MAX_REGEX_LENGTH.toString()),
                    location = SourceLocation(
                        jsonPath = path,
                        target = Target.VALUE,
                        stageId = stageId,
                        span = TextSpan(0, patternStr.length)
                    )
                )
            )
            return
        }

        // 2. Pre-scan for unsupported backtracking features: lookarounds, possessive quantifiers
        val lookaroundTokens = listOf("(?=", "(?!", "(?<=", "(?<!")
        for (token in lookaroundTokens) {
            val idx = patternStr.indexOf(token)
            if (idx >= 0) {
                diagnostics.add(
                    CompilationDiagnostic(
                        code = "P2003",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Unsupported backtracking regex syntax '$token' (lookaround or possessive quantifier). RE2 requires linear-time constructs",
                        messageKey = "error.regex.backtracking_unsupported",
                        messageArgs = listOf(token),
                        location = SourceLocation(
                            jsonPath = path,
                            target = Target.VALUE,
                            stageId = stageId,
                            span = TextSpan(idx, idx + token.length)
                        )
                    )
                )
                return
            }
        }

        val possessiveTokens = listOf("*+", "++", "?+")
        for (token in possessiveTokens) {
            val idx = patternStr.indexOf(token)
            if (idx >= 0) {
                diagnostics.add(
                    CompilationDiagnostic(
                        code = "P2003",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Unsupported backtracking regex syntax '$token' (lookaround or possessive quantifier). RE2 requires linear-time constructs",
                        messageKey = "error.regex.backtracking_unsupported",
                        messageArgs = listOf(token),
                        location = SourceLocation(
                            jsonPath = path,
                            target = Target.VALUE,
                            stageId = stageId,
                            span = TextSpan(idx, idx + token.length)
                        )
                    )
                )
                return
            }
        }

        // 3. Backreferences (\1..\9)
        var i = 0
        while (i < patternStr.length - 1) {
            if (patternStr[i] == '\\' && patternStr[i + 1] in '1'..'9') {
                val backref = patternStr.substring(i, i + 2)
                diagnostics.add(
                    CompilationDiagnostic(
                        code = "P2004",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Backreferences are not supported in RE2 regex: '$backref'",
                        messageKey = "error.regex.backreference_unsupported",
                        messageArgs = listOf(backref),
                        location = SourceLocation(
                            jsonPath = path,
                            target = Target.VALUE,
                            stageId = stageId,
                            span = TextSpan(i, i + 2)
                        )
                    )
                )
                return
            }
            i++
        }

        // 4. Repetition expansion guard (nested counts > 1000)
        // Scan for {N} or {N,M} repetitions
        val repRegex = Regex("""\{(\d+)(?:,\s*(\d*))?\}""")
        val repMatches = repRegex.findAll(patternStr).toList()
        if (repMatches.size >= 2) {
            var product = 1L
            for (m in repMatches) {
                val num = m.groupValues[1].toLongOrNull() ?: 1L
                product *= num
            }
            if (product > Pass2RegexValidator.MAX_REPETITION_EXPANSION) {
                diagnostics.add(
                    CompilationDiagnostic(
                        code = "P2005",
                        severity = DiagnosticSeverity.ERROR,
                        message = "Repetition expansion limit exceeded ($product > ${Pass2RegexValidator.MAX_REPETITION_EXPANSION}). Risk of state explosion",
                        messageKey = "error.regex.repetition_exceeded",
                        messageArgs = listOf(product.toString(), Pass2RegexValidator.MAX_REPETITION_EXPANSION.toString()),
                        location = SourceLocation(
                            jsonPath = path,
                            target = Target.VALUE,
                            stageId = stageId,
                            span = TextSpan(0, patternStr.length)
                        )
                    )
                )
                return
            }
        }

        // 5. Check if it is a pure literal (literal lowering candidate)
        val hasMeta = patternStr.any { it in regexMetaChars }
        if (!hasMeta) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P2101",
                    severity = DiagnosticSeverity.INFO,
                    message = "Pattern '$patternStr' contains no regex metacharacters and will be lowered to direct string search",
                    messageKey = "info.regex.literal_lowering",
                    messageArgs = listOf(patternStr),
                    location = SourceLocation(
                        jsonPath = path,
                        target = Target.VALUE,
                        stageId = stageId,
                        span = TextSpan(0, patternStr.length)
                    )
                )
            )
            literalLoweringCandidates.add(path)
        }

        // 6. Precompile with RE2/J
        val flags = if (caseSensitive) 0 else Pattern.CASE_INSENSITIVE
        try {
            val compiled = Pattern.compile(patternStr, flags)
            compiledPatterns[path] = compiled
        } catch (e: PatternSyntaxException) {
            val errIndex = e.index.coerceIn(0, patternStr.length)
            val errEnd = minOf(errIndex + 1, patternStr.length).coerceAtLeast(errIndex)
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P2002",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Invalid RE2 syntax: ${e.description}",
                    messageKey = "error.regex.syntax_invalid",
                    messageArgs = listOf(e.description),
                    location = SourceLocation(
                        jsonPath = path,
                        target = Target.VALUE,
                        stageId = stageId,
                        span = TextSpan(errIndex, errEnd)
                    )
                )
            )
        } catch (t: Throwable) {
            diagnostics.add(
                CompilationDiagnostic(
                    code = "P2002",
                    severity = DiagnosticSeverity.ERROR,
                    message = "Invalid RE2 syntax: ${t.message ?: "compile error"}",
                    messageKey = "error.regex.syntax_invalid",
                    messageArgs = listOf(t.message ?: "compile error"),
                    location = SourceLocation(
                        jsonPath = path,
                        target = Target.VALUE,
                        stageId = stageId,
                        span = TextSpan(0, patternStr.length)
                    )
                )
            )
        }
    }
}
