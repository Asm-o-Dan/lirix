package com.example.npc.induction

import com.example.npc.induction.model.BuiltTemplate
import com.google.re2j.Pattern
import com.google.re2j.PatternSyntaxException

sealed interface LintResult {
    object Pass : LintResult
    data class Failed(val errors: List<LintError>) : LintResult {
        val hasErrors: Boolean get() = errors.isNotEmpty()
    }

    val isValid: Boolean get() = this is Pass
}

data class LintError(
    val code: String,
    val message: String
)

/**
 * Статический линтер RE2/J шаблонов в соответствии с лимитами компилятора:
 * - Защита от ReDoS (отсутствие незащищенных жадных квантификаторов)
 * - Глубина вложенности круглых скобок <= 2
 * - Максимальная длина паттерна <= 256 символов (или заданный лимит)
 * - Максимальное количество именованных групп <= 12
 * - Разрешенные имена групп (белый список слотов)
 * - Минимальная специфичность литералов (>= 2 литералов, длина >= 8)
 * - Валидный RE2/J синтаксис
 */
object TemplateLint {

    const val DEFAULT_MAX_LENGTH = 256
    const val EXTENDED_MAX_LENGTH = 1024
    const val DEFAULT_MAX_PAREN_DEPTH = 2
    const val DEFAULT_MAX_GROUPS = 12

    val ALLOWED_GROUP_NAMES = setOf(
        "amount", "curr", "card", "mask", "merchant", "bal", "balcurr", "fee", "other",
        "card_mask", "CARD_MASK", "balance", "BALANCE", "tx_amount", "TX_AMOUNT", "AMOUNT", "CURR", "MERCHANT"
    )

    fun lint(
        template: BuiltTemplate,
        maxLength: Int = DEFAULT_MAX_LENGTH,
        maxParenDepth: Int = DEFAULT_MAX_PAREN_DEPTH,
        checkSpecificity: Boolean = true
    ): LintResult {
        if (template.decomposedSpec != null) {
            return lint(
                spec = template.decomposedSpec,
                maxLength = maxLength,
                maxParenDepth = maxParenDepth,
                checkSpecificity = checkSpecificity
            )
        }
        return lint(
            pattern = template.pattern,
            namedGroups = template.namedGroups,
            requiredLiterals = template.requiredLiterals,
            maxLength = maxLength,
            maxParenDepth = maxParenDepth,
            checkSpecificity = checkSpecificity
        )
    }

    /**
     * Валидация конвейера слотовых регулярок (Slot-Decomposed Regex Pipeline, OPT-PIPE-001).
     * Каждое правило (якорь и каждый слотовый экстрактор) проверяется независимо,
     * укладываясь в лимит maxLength (по умолчанию 256 символов).
     */
    fun lint(
        spec: com.example.npc.induction.model.DecomposedTemplateSpec,
        maxLength: Int = DEFAULT_MAX_LENGTH,
        maxParenDepth: Int = DEFAULT_MAX_PAREN_DEPTH,
        checkSpecificity: Boolean = false
    ): LintResult {
        return lintDecomposed(
            anchorPattern = spec.anchorPattern,
            slotRules = spec.slotRules.mapKeys { it.key.name.lowercase() },
            requiredLiterals = spec.requiredLiterals,
            maxLength = maxLength,
            maxParenDepth = maxParenDepth,
            checkSpecificity = checkSpecificity
        )
    }

    /**
     * Независимая валидация якорного паттерна и набора слотовых правил.
     */
    fun lintDecomposed(
        anchorPattern: String,
        slotRules: Map<String, String>,
        requiredLiterals: List<String> = emptyList(),
        maxLength: Int = DEFAULT_MAX_LENGTH,
        maxParenDepth: Int = DEFAULT_MAX_PAREN_DEPTH,
        checkSpecificity: Boolean = false
    ): LintResult {
        val errors = ArrayList<LintError>()

        // 1. Валидация якорного паттерна
        val anchorResult = lintRule(
            ruleName = "anchor",
            pattern = anchorPattern,
            maxLength = maxLength,
            maxParenDepth = maxParenDepth
        )
        if (anchorResult is LintResult.Failed) {
            errors.addAll(anchorResult.errors)
        }

        // 2. Валидация каждого слотового правила независимо
        for ((slotName, rulePattern) in slotRules) {
            val slotResult = lintRule(
                ruleName = slotName,
                pattern = rulePattern,
                maxLength = maxLength,
                maxParenDepth = maxParenDepth
            )
            if (slotResult is LintResult.Failed) {
                errors.addAll(slotResult.errors)
            }
        }

        // 3. Проверка специфичности при необходимости
        if (checkSpecificity) {
            val totalLiteralLen = requiredLiterals.sumOf { it.length }
            if (requiredLiterals.size < 2 || totalLiteralLen < 8) {
                errors.add(
                    LintError(
                        code = "E_LOW_SPECIFICITY",
                        message = "Specificity too low: found ${requiredLiterals.size} literals with total length $totalLiteralLen (required >= 2 literals and total length >= 8)"
                    )
                )
            }
        }

        return if (errors.isEmpty()) LintResult.Pass else LintResult.Failed(errors)
    }

    /**
     * Валидация отдельного правила (якорного или слотового) в рамках конвейера.
     */
    fun lintRule(
        ruleName: String,
        pattern: String,
        maxLength: Int = DEFAULT_MAX_LENGTH,
        maxParenDepth: Int = DEFAULT_MAX_PAREN_DEPTH
    ): LintResult {
        val errors = ArrayList<LintError>()

        // 1. Проверка длины паттерна
        if (pattern.length > maxLength) {
            errors.add(
                LintError(
                    code = "E_PATTERN_TOO_LONG",
                    message = "Rule '$ruleName' pattern length (${pattern.length}) exceeds maximum allowed $maxLength characters"
                )
            )
        }

        // 2. Проверка именованных групп
        val extractedGroups = extractNamedGroups(pattern)
        if (extractedGroups.size > DEFAULT_MAX_GROUPS) {
            errors.add(
                LintError(
                    code = "E_TOO_MANY_GROUPS",
                    message = "Rule '$ruleName' named groups count (${extractedGroups.size}) exceeds maximum allowed $DEFAULT_MAX_GROUPS"
                )
            )
        }

        for (grp in extractedGroups) {
            if (grp !in ALLOWED_GROUP_NAMES) {
                errors.add(
                    LintError(
                        code = "E_ILLEGAL_GROUP_NAME",
                        message = "Rule '$ruleName' has illegal named capture group '$grp'. Allowed: $ALLOWED_GROUP_NAMES"
                    )
                )
            }
        }

        // 3. Проверка глубины вложенности круглых скобок
        val actualParenDepth = calculateMaxParenDepth(pattern)
        if (actualParenDepth > maxParenDepth) {
            errors.add(
                LintError(
                    code = "E_PAREN_NESTING_TOO_DEEP",
                    message = "Rule '$ruleName' parenthesis nesting depth ($actualParenDepth) exceeds maximum allowed $maxParenDepth"
                )
            )
        }

        // 4. Проверка на ReDoS
        val redosRisk = checkRedosRisk(pattern)
        if (redosRisk != null) {
            errors.add(
                LintError(
                    code = redosRisk.code,
                    message = "Rule '$ruleName': ${redosRisk.message}"
                )
            )
        }

        // 5. Проверка компилируемости в RE2/J
        try {
            Pattern.compile(pattern)
        } catch (e: PatternSyntaxException) {
            errors.add(
                LintError(
                    code = "E_RE2_SYNTAX_ERROR",
                    message = "Rule '$ruleName' RE2/J syntax error: ${e.message}"
                )
            )
        } catch (e: Exception) {
            errors.add(
                LintError(
                    code = "E_RE2_SYNTAX_ERROR",
                    message = "Rule '$ruleName' regex syntax error: ${e.message}"
                )
            )
        }

        return if (errors.isEmpty()) LintResult.Pass else LintResult.Failed(errors)
    }

    fun lint(
        pattern: String,
        namedGroups: List<String> = emptyList(),
        requiredLiterals: List<String> = emptyList(),
        maxLength: Int = DEFAULT_MAX_LENGTH,
        maxParenDepth: Int = DEFAULT_MAX_PAREN_DEPTH,
        checkSpecificity: Boolean = false
    ): LintResult {
        val errors = ArrayList<LintError>()

        // 1. Проверка длины паттерна
        if (pattern.length > maxLength) {
            errors.add(
                LintError(
                    code = "E_PATTERN_TOO_LONG",
                    message = "Pattern length (${pattern.length}) exceeds maximum allowed $maxLength characters"
                )
            )
        }

        // 2. Проверка именованных групп
        val extractedGroups = extractNamedGroups(pattern)
        val allGroups = (namedGroups + extractedGroups).distinct()

        if (allGroups.size > DEFAULT_MAX_GROUPS) {
            errors.add(
                LintError(
                    code = "E_TOO_MANY_GROUPS",
                    message = "Named groups count (${allGroups.size}) exceeds maximum allowed $DEFAULT_MAX_GROUPS"
                )
            )
        }

        for (grp in allGroups) {
            if (grp !in ALLOWED_GROUP_NAMES) {
                errors.add(
                    LintError(
                        code = "E_ILLEGAL_GROUP_NAME",
                        message = "Illegal named capture group '$grp'. Allowed: $ALLOWED_GROUP_NAMES"
                    )
                )
            }
        }

        // 3. Проверка глубины вложенности круглых скобок (Parenthesis nesting depth <= maxParenDepth)
        val actualParenDepth = calculateMaxParenDepth(pattern)
        if (actualParenDepth > maxParenDepth) {
            errors.add(
                LintError(
                    code = "E_PAREN_NESTING_TOO_DEEP",
                    message = "Parenthesis nesting depth ($actualParenDepth) exceeds maximum allowed $maxParenDepth"
                )
            )
        }

        // 4. Проверка на ReDoS (неограниченные опасные квантификаторы .* или .+)
        val redosRisk = checkRedosRisk(pattern)
        if (redosRisk != null) {
            errors.add(redosRisk)
        }

        // 5. Проверка специфичности
        if (checkSpecificity) {
            val totalLiteralLen = requiredLiterals.sumOf { it.length }
            if (requiredLiterals.size < 2 || totalLiteralLen < 8) {
                errors.add(
                    LintError(
                        code = "E_LOW_SPECIFICITY",
                        message = "Specificity too low: found ${requiredLiterals.size} literals with total length $totalLiteralLen (required >= 2 literals and total length >= 8)"
                    )
                )
            }
        }

        // 6. Проверка компилируемости в RE2/J
        try {
            Pattern.compile(pattern)
        } catch (e: PatternSyntaxException) {
            errors.add(
                LintError(
                    code = "E_RE2_SYNTAX_ERROR",
                    message = "RE2/J syntax error: ${e.message}"
                )
            )
        } catch (e: Exception) {
            errors.add(
                LintError(
                    code = "E_RE2_SYNTAX_ERROR",
                    message = "Regex syntax error: ${e.message}"
                )
            )
        }

        return if (errors.isEmpty()) LintResult.Pass else LintResult.Failed(errors)
    }

    /**
     * Вычисляет максимальную глубину вложенности круглых скобок, игнорируя скобки внутри \Q...\E,
     * экранированные скобки \( и \) и квадратные скобки [...].
     */
    fun calculateMaxParenDepth(pattern: String): Int {
        var maxDepth = 0
        var currentDepth = 0
        var i = 0
        var inQuote = false
        var inCharClass = false

        while (i < pattern.length) {
            val c = pattern[i]

            if (inQuote) {
                if (c == '\\' && i + 1 < pattern.length && pattern[i + 1] == 'E') {
                    inQuote = false
                    i += 2
                    continue
                }
                i++
                continue
            }

            if (c == '\\') {
                if (i + 1 < pattern.length && pattern[i + 1] == 'Q') {
                    inQuote = true
                    i += 2
                    continue
                }
                // Пропускаем экранированный символ
                i += 2
                continue
            }

            if (inCharClass) {
                if (c == ']') {
                    inCharClass = false
                }
                i++
                continue
            }

            if (c == '[') {
                inCharClass = true
                i++
                continue
            }

            if (c == '(') {
                currentDepth++
                maxDepth = maxOf(maxDepth, currentDepth)
            } else if (c == ')') {
                if (currentDepth > 0) {
                    currentDepth--
                }
            }

            i++
        }

        return maxDepth
    }

    private fun extractNamedGroups(pattern: String): List<String> {
        val groups = ArrayList<String>()
        val m = java.util.regex.Pattern.compile("\\(\\?P?<([a-zA-Z0-9_]+)>").matcher(pattern)
        while (m.find()) {
            groups.add(m.group(1))
        }
        return groups
    }

    private fun checkRedosRisk(pattern: String): LintError? {
        // Проверяем опасные открытые квантификаторы .* или .+ вне экранирования \Q...\E
        var i = 0
        var inQuote = false
        while (i < pattern.length) {
            val c = pattern[i]
            if (inQuote) {
                if (c == '\\' && i + 1 < pattern.length && pattern[i + 1] == 'E') {
                    inQuote = false
                    i += 2
                    continue
                }
                i++
                continue
            }
            if (c == '\\') {
                if (i + 1 < pattern.length && pattern[i + 1] == 'Q') {
                    inQuote = true
                    i += 2
                    continue
                }
                i += 2
                continue
            }

            // Опасный квантификатор .* или .+
            if ((c == '.' || c == ' ') && i + 1 < pattern.length && (pattern[i + 1] == '*' || pattern[i + 1] == '+')) {
                // Если это не \s+ (так как \s экранирован)
                return LintError(
                    code = "E_REDOS_RISK",
                    message = "Unbounded open wildcard quantifier detected: '${pattern.substring(i, i + 2)}'"
                )
            }
            i++
        }
        return null
    }
}
