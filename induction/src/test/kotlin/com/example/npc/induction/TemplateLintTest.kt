package com.example.npc.induction

import com.example.npc.induction.model.AmountFormatSpec
import com.example.npc.induction.model.BuiltTemplate
import com.example.npc.induction.model.LiteralSegment
import com.example.npc.induction.model.SlotSegment
import com.example.npc.induction.model.SlotType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TemplateLintTest {

    @Test
    @DisplayName("Valid template passes lint")
    fun testValidTemplatePasses() {
        val template = BuiltTemplate(
            pattern = "(?i)\\QPlata\\E\\s+(?P<amount>[0-9]+)\\s*(?P<curr>MDL)\\s+\\QCard\\E",
            namedGroups = listOf("amount", "curr"),
            constants = mapOf("transactionType" to "DEBIT"),
            amountFormat = AmountFormatSpec(decimalSeparator = null, groupingSeparator = null),
            requiredLiterals = listOf("plata", "card")
        )

        val result = TemplateLint.lint(template, maxLength = 256, maxParenDepth = 2)
        assertThat(result).isInstanceOf(LintResult.Pass::class.java)
        assertThat(result.isValid).isTrue
    }

    @Test
    @DisplayName("Pattern length > 256 triggers E_PATTERN_TOO_LONG")
    fun testPatternTooLong() {
        val longPattern = "(?i)\\Q" + "A".repeat(260) + "\\E"
        val result = TemplateLint.lint(longPattern, maxLength = 256)

        assertThat(result).isInstanceOf(LintResult.Failed::class.java)
        val failed = result as LintResult.Failed
        assertThat(failed.errors).anyMatch { it.code == "E_PATTERN_TOO_LONG" }
    }

    @Test
    @DisplayName("Parenthesis depth > 2 triggers E_PAREN_NESTING_TOO_DEEP")
    fun testParenthesisDepthTooDeep() {
        // Depth 3: (((a)))
        val deepPattern = "(?i)(?:a(?:b(?:c)))"
        val depth = TemplateLint.calculateMaxParenDepth(deepPattern)
        assertThat(depth).isEqualTo(3)

        val result = TemplateLint.lint(deepPattern, maxParenDepth = 2)
        assertThat(result).isInstanceOf(LintResult.Failed::class.java)
        val failed = result as LintResult.Failed
        assertThat(failed.errors).anyMatch { it.code == "E_PAREN_NESTING_TOO_DEEP" }
    }

    @Test
    @DisplayName("Parenthesis depth <= 2 passes depth check")
    fun testParenthesisDepthValid() {
        // Depth 2: (?P<amount>[0-9]+(?:[.,][0-9]+)?)
        val pattern = "(?i)(?P<amount>[0-9]+(?:[.,][0-9]+)?)"
        val depth = TemplateLint.calculateMaxParenDepth(pattern)
        assertThat(depth).isEqualTo(2)

        val result = TemplateLint.lint(pattern, namedGroups = listOf("amount"), maxParenDepth = 2)
        assertThat(result.isValid).isTrue
    }

    @Test
    @DisplayName("ReDoS open wildcard .* triggers E_REDOS_RISK")
    fun testRedosRiskDetection() {
        val dangerousPattern = "(?i).*\\QPlata\\E.*"
        val result = TemplateLint.lint(dangerousPattern)

        assertThat(result).isInstanceOf(LintResult.Failed::class.java)
        val failed = result as LintResult.Failed
        assertThat(failed.errors).anyMatch { it.code == "E_REDOS_RISK" }
    }

    @Test
    @DisplayName("Illegal named capture group triggers E_ILLEGAL_GROUP_NAME")
    fun testIllegalGroupName() {
        val pattern = "(?i)(?P<unknown_field>[0-9]+)"
        val result = TemplateLint.lint(pattern)

        assertThat(result).isInstanceOf(LintResult.Failed::class.java)
        val failed = result as LintResult.Failed
        assertThat(failed.errors).anyMatch { it.code == "E_ILLEGAL_GROUP_NAME" }
    }

    @Test
    @DisplayName("Low specificity triggers E_LOW_SPECIFICITY")
    fun testLowSpecificity() {
        val template = BuiltTemplate(
            pattern = "(?i)(?P<amount>[0-9]+)",
            namedGroups = listOf("amount"),
            constants = emptyMap(),
            amountFormat = AmountFormatSpec(),
            requiredLiterals = listOf("ok") // only 1 literal of length 2
        )

        val result = TemplateLint.lint(template, checkSpecificity = true)
        assertThat(result).isInstanceOf(LintResult.Failed::class.java)
        val failed = result as LintResult.Failed
        assertThat(failed.errors).anyMatch { it.code == "E_LOW_SPECIFICITY" }
    }

    @Test
    @DisplayName("RE2 syntax error triggers E_RE2_SYNTAX_ERROR")
    fun testRe2SyntaxError() {
        val invalidPattern = "(?i)[0-9++" // unclosed bracket
        val result = TemplateLint.lint(invalidPattern)

        assertThat(result).isInstanceOf(LintResult.Failed::class.java)
        val failed = result as LintResult.Failed
        assertThat(failed.errors).anyMatch { it.code == "E_RE2_SYNTAX_ERROR" }
    }

    @Test
    @DisplayName("RightBoundedRule: merchant bounded by literal is valid")
    fun testRightBoundedRuleWithLiteral() {
        val segments = listOf(
            LiteralSegment("Plata in suma la "),
            SlotSegment(SlotType.MERCHANT, "McDonald's"),
            LiteralSegment(" cu cardul"),
            SlotSegment(SlotType.CARD_MASK, "*1234")
        )
        val rawPattern = "(?i)\\QPlata in suma la\\E\\s+(?P<merchant>[^\\n]{2,64}?)\\s+\\Qcu cardul\\E"

        val res = RightBoundedRule.enforce(segments, rawPattern)
        assertThat(res.isValid).isTrue
        assertThat(res.diagnostic).isNull()
    }

    @Test
    @DisplayName("RightBoundedRule: merchant at end of message appends line-end bound")
    fun testRightBoundedRuleAtEndAppendsLineEnd() {
        val segments = listOf(
            LiteralSegment("Plata la "),
            SlotSegment(SlotType.MERCHANT, "McDonald's")
        )
        val rawPattern = "(?i)\\QPlata la\\E\\s+(?P<merchant>[^\\n]{2,64}?)"

        val res = RightBoundedRule.enforce(segments, rawPattern)
        assertThat(res.isValid).isTrue
        assertThat(res.correctedPattern).endsWith("(?:\\n|$)")
        assertThat(res.diagnostic).isEqualTo("Appended line-end bound")
    }

    @Test
    @DisplayName("RightBoundedRule: merchant directly adjacent to another slot is rejected")
    fun testRightBoundedRuleDirectAdjacencyRejected() {
        val segments = listOf(
            SlotSegment(SlotType.MERCHANT, "McDonald's"),
            SlotSegment(SlotType.CARD_MASK, "*1234")
        )
        val rawPattern = "(?i)(?P<merchant>[^\\n]{2,64}?)\\*(?P<mask >[0-9]{4})"

        val res = RightBoundedRule.enforce(segments, rawPattern)
        assertThat(res.isValid).isFalse
        assertThat(res.diagnostic).contains("Merchant slot must be bounded by a literal")
    }

    @Test
    @DisplayName("AnchorSelector: find first anchor and trim tail")
    fun testAnchorSelector() {
        val segments = listOf(
            LiteralSegment("MAIB: "),
            LiteralSegment("Restituire 245,90 MDL "),
            SlotSegment(SlotType.TX_AMOUNT, "245,90 MDL"),
            LiteralSegment("TEMU "),
            SlotSegment(SlotType.MERCHANT, "TEMU"),
            LiteralSegment(" Card *1234 "),
            SlotSegment(SlotType.CARD_MASK, "*1234"),
            LiteralSegment(" Descarcati aplicatia noastra gratuita") // рекламный хвост
        )

        // Find anchor
        val anchor = AnchorSelector.findFirstAnchor(segments)
        assertThat(anchor).isNotNull
        assertThat(anchor?.text).contains("MAIB")

        // Tail trimming
        val trimmed = AnchorSelector.trimTail(segments)
        assertThat(trimmed.last()).isInstanceOf(SlotSegment::class.java)
        val lastSlot = trimmed.last() as SlotSegment
        assertThat(lastSlot.role).isEqualTo(SlotType.CARD_MASK)
    }

    @Test
    @DisplayName("OPT-PIPE-001: Slot-decomposed template passes linting when monolithic exceeds 256 limit")
    fun testDecomposedTemplatePassesLintWhenMonolithicExceedsLimit() {
        // Монолитная регулярка со многими альтернативами и слотами легко превышает 256 символов
        val monolithicPattern = "(?i)^(?:Пополнение счета|Перевод на карту|Зачисление заработной платы|Входящий перевод через СБП|Возврат средств от продавца)\\s+" +
            "(?P<amount>[0-9]{1,3}(?:[ .,'][0-9]{3}){0,4}(?:[.,][0-9]{1,2})?|[0-9]{1,9}(?:[.,][0-9]{1,2})?)\\s*(?P<curr>RUP|MDL|USD|EUR|RUB|lei|руб)\\s+" +
            "(?:от|в|списано)\\s+(?P<merchant>[^\\n]{2,64}?)\\s+" +
            "(?:карте|карту|счет)\\s+(?P<card>[0-9]{4})\\s+" +
            "(?:Остаток|Баланс):\\s*(?P<bal>[0-9]{1,3}(?:[ .,'][0-9]{3}){0,4}(?:[.,][0-9]{1,2})?)"

        assertThat(monolithicPattern.length).isGreaterThan(256)

        // 1. Монолитная регулярка выдает ошибку E_PATTERN_TOO_LONG
        val monolithicResult = TemplateLint.lint(monolithicPattern, maxLength = 256)
        assertThat(monolithicResult).isInstanceOf(LintResult.Failed::class.java)
        val failed = monolithicResult as LintResult.Failed
        assertThat(failed.errors).anyMatch { it.code == "E_PATTERN_TOO_LONG" }

        // 2. Конвейер слотовых регулярок: якорь < 60 символов, каждый слот 30-70 символов
        val anchorPattern = "(?i)(?:Пополнение счета|Перевод на карту|Зачисление)"
        assertThat(anchorPattern.length).isLessThanOrEqualTo(60)

        val slotRules = mapOf(
            "amount" to SafeFragments.SLOT_AMOUNT_PATTERN,
            "card" to SafeFragments.SLOT_CARD_MASK_PATTERN,
            "merchant" to SafeFragments.SLOT_MERCHANT_PATTERN,
            "bal" to SafeFragments.SLOT_BALANCE_PATTERN
        )

        // Каждое правило независимо укладывается в <= 256 символов
        for ((slot, rule) in slotRules) {
            assertThat(rule.length).isLessThan(256)
        }

        // 3. Проверка через TemplateLint.lintDecomposed
        val decomposedResult = TemplateLint.lintDecomposed(
            anchorPattern = anchorPattern,
            slotRules = slotRules,
            maxLength = 256
        )
        assertThat(decomposedResult).isInstanceOf(LintResult.Pass::class.java)
        assertThat(decomposedResult.isValid).isTrue

        // 4. Проверка через BuiltTemplate с decomposedSpec
        val decomposedSpec = com.example.npc.induction.model.DecomposedTemplateSpec.fromNamedRules(
            anchorPattern = anchorPattern,
            slotRulesByName = slotRules
        )
        val builtTemplate = BuiltTemplate(
            pattern = anchorPattern,
            namedGroups = listOf("amount", "card", "merchant", "bal"),
            constants = mapOf("transactionType" to "DEBIT"),
            amountFormat = AmountFormatSpec(),
            requiredLiterals = listOf("card"),
            decomposedSpec = decomposedSpec
        )

        val templateResult = TemplateLint.lint(builtTemplate, maxLength = 256, checkSpecificity = false)
        assertThat(templateResult.isValid).isTrue
    }

    @Test
    @DisplayName("OPT-PIPE-001: Decomposed slot rule exceeding max length triggers E_PATTERN_TOO_LONG")
    fun testDecomposedSlotRuleTooLong() {
        val anchorPattern = "(?i)(?:Пополнение|Списание)"
        val slotRules = mapOf(
            "amount" to "(?i)(?P<amount>" + "0".repeat(260) + ")",
            "card" to SafeFragments.SLOT_CARD_MASK_PATTERN
        )

        val result = TemplateLint.lintDecomposed(anchorPattern, slotRules, maxLength = 256)
        assertThat(result).isInstanceOf(LintResult.Failed::class.java)
        val failed = result as LintResult.Failed
        assertThat(failed.errors).anyMatch { it.code == "E_PATTERN_TOO_LONG" && it.message.contains("amount") }
    }
}
