package com.example.npc.induction.validator

import com.example.npc.induction.model.BuiltTemplate
import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException

/**
 * Образец исторического события для оффлайн-валидации и симуляции шаблонов.
 */
data class HistoricalEventSample(
    val eventId: Long,
    val sourcePackage: String,
    val normalizedText: String,
    val isFinancial: Boolean,
    val isOtp: Boolean = false,
    val existingTransaction: Any? = null
)

/**
 * Отчет о симуляции шаблона на историческом корпусе событий (Replay Engine).
 */
data class ReplayValidationReport(
    val positiveMatchesCount: Int,
    val negativeViolationsCount: Int,
    val conflictCount: Int,
    val conflictingEventIds: List<Long>,
    val negativeViolationSample: String? = null,
    val diagnostics: List<String> = emptyList()
) {
    val canActivate: Boolean
        get() = negativeViolationsCount == 0 && diagnostics.isEmpty()
}

/**
 * Симулятор поведения нового шаблона на выборке исторических событий (Positive, Negative, Conflict).
 */
object ReplayValidator {

    fun validate(
        template: BuiltTemplate,
        targetPackage: String,
        historySamples: List<HistoricalEventSample>
    ): ReplayValidationReport {
        val pattern: Pattern = try {
            Pattern.compile(template.pattern)
        } catch (e: PatternSyntaxException) {
            return ReplayValidationReport(
                positiveMatchesCount = 0,
                negativeViolationsCount = 0,
                conflictCount = 0,
                conflictingEventIds = emptyList(),
                diagnostics = listOf("E_RE2_SYNTAX_ERROR: ${e.message}")
            )
        }

        var positiveCount = 0
        var negativeCount = 0
        var conflictCount = 0
        val conflictingIds = ArrayList<Long>()
        var negativeSample: String? = null
        val diagnostics = ArrayList<String>()

        for (sample in historySamples) {
            val isTargetPackage = sample.sourcePackage == targetPackage

            // Быстрый префильтр по литералам, если заданы
            var literalsMatch = true
            for (lit in template.requiredLiterals) {
                if (!sample.normalizedText.contains(lit, ignoreCase = true)) {
                    literalsMatch = false
                    break
                }
            }
            if (!literalsMatch) continue

            val matches = pattern.matcher(sample.normalizedText).find()
            if (matches) {
                if (!sample.isFinancial || sample.isOtp) {
                    negativeCount++
                    if (negativeSample == null) {
                        negativeSample = sample.normalizedText
                    }
                    diagnostics.add("NEGATIVE_MATCH_VIOLATION: Pattern matched non-financial/OTP event #${sample.eventId}")
                } else if (isTargetPackage) {
                    positiveCount++
                    if (sample.existingTransaction != null) {
                        conflictCount++
                        conflictingIds.add(sample.eventId)
                    }
                }
            }
        }

        return ReplayValidationReport(
            positiveMatchesCount = positiveCount,
            negativeViolationsCount = negativeCount,
            conflictCount = conflictCount,
            conflictingEventIds = conflictingIds,
            negativeViolationSample = negativeSample,
            diagnostics = diagnostics
        )
    }
}
