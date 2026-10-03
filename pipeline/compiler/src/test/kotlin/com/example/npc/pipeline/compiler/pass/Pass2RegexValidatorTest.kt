package com.example.npc.pipeline.compiler.pass

import com.example.npc.pipeline.compiler.diagnostic.DiagnosticSeverity
import com.example.npc.pipeline.dsl.ActionDefinition
import com.example.npc.pipeline.dsl.ConditionDefinition
import com.example.npc.pipeline.dsl.PipelineDefinition
import com.example.npc.pipeline.dsl.StageDefinition
import com.example.npc.pipeline.dsl.TriggerDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Pass2RegexValidatorTest {

    private val validator = Pass2RegexValidator.create()

    private fun defWithRegex(pattern: String): PipelineDefinition {
        return PipelineDefinition(
            schemaVersion = 1,
            id = "regex-test",
            name = "Regex Test",
            triggers = listOf(TriggerDefinition.Notification()),
            stages = listOf(
                StageDefinition(
                    id = "stage-regex",
                    name = "Regex Stage",
                    condition = ConditionDefinition.TextRegexMatch(pattern = pattern),
                    actions = listOf(ActionDefinition.DropEvent("r"))
                )
            )
        )
    }

    @Test
    fun validRegex_compilesSuccessfully() {
        val def = defWithRegex("""(?i)перевод\s+\d+""")
        val res = validator.validate(def)
        assertEquals(0, res.diagnostics.count { it.severity == DiagnosticSeverity.ERROR })
        assertEquals(1, res.compiledPatterns.size)
    }

    @Test
    fun literalPattern_detectedForLowering() {
        val def = defWithRegex("plain literal text")
        val res = validator.validate(def)
        assertEquals(1, res.literalLoweringCandidates.size)
        assertTrue(res.diagnostics.any { it.code == "P2101" })
    }

    @Test
    fun patternLengthExceeded_emitsP2001() {
        val longPattern = "a".repeat(257)
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        val regexMatch = unsafe.allocateInstance(ConditionDefinition.TextRegexMatch::class.java) as ConditionDefinition.TextRegexMatch
        val patField = ConditionDefinition.TextRegexMatch::class.java.getDeclaredField("pattern")
        patField.isAccessible = true
        patField.set(regexMatch, longPattern)
        val tfField = ConditionDefinition.TextRegexMatch::class.java.getDeclaredField("targetField")
        tfField.isAccessible = true
        tfField.set(regexMatch, com.example.npc.pipeline.dsl.TextFieldTarget.TITLE_OR_TEXT)

        val def = PipelineDefinition(
            schemaVersion = 1,
            id = "regex-test",
            name = "Regex Test",
            triggers = listOf(TriggerDefinition.Notification()),
            stages = listOf(
                StageDefinition(
                    id = "stage-regex",
                    name = "Regex Stage",
                    condition = regexMatch,
                    actions = listOf(ActionDefinition.DropEvent("r"))
                )
            )
        )
        val res = validator.validate(def)
        assertTrue(res.diagnostics.any { it.code == "P2001" })
    }

    @Test
    fun invalidSyntax_emitsP2002() {
        val def = defWithRegex("[a-z")
        val res = validator.validate(def)
        assertTrue(res.diagnostics.any { it.code == "P2002" })
    }

    @Test
    fun lookahead_rejectedWithP2003() {
        val def = defWithRegex("(?=test)abc")
        val res = validator.validate(def)
        assertTrue(res.diagnostics.any { it.code == "P2003" })
    }

    @Test
    fun lookbehind_rejectedWithP2003() {
        val def = defWithRegex("(?<=test)abc")
        val res = validator.validate(def)
        assertTrue(res.diagnostics.any { it.code == "P2003" })
    }

    @Test
    fun possessiveQuantifier_rejectedWithP2003() {
        val def = defWithRegex("a*+b")
        val res = validator.validate(def)
        assertTrue(res.diagnostics.any { it.code == "P2003" })
    }

    @Test
    fun backreference_rejectedWithP2004() {
        val def = defWithRegex("""(a|b)\1""")
        val res = validator.validate(def)
        assertTrue(res.diagnostics.any { it.code == "P2004" })
    }

    @Test
    fun repetitionExpansionExceeded_emitsP2005() {
        val def = defWithRegex("""a{50}b{30}""") // 50 * 30 = 1500 > 1000
        val res = validator.validate(def)
        assertTrue(res.diagnostics.any { it.code == "P2005" })
    }
}
