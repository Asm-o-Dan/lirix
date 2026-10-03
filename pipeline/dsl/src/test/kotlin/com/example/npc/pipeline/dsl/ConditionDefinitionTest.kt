package com.example.npc.pipeline.dsl

import com.example.npc.core.model.classify.Category
import com.example.npc.pipeline.dsl.codec.PipelineJsonCodec
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConditionDefinitionTest {

    @Test
    fun `package match condition defaults and values`() {
        val cond = ConditionDefinition.PackageMatch(
            packages = listOf("com.example.bank"),
            matchMode = MatchMode.PREFIX,
            negate = true
        )
        assertEquals("PackageMatch", cond.type)
        assertEquals(listOf("com.example.bank"), cond.packages)
        assertEquals(MatchMode.PREFIX, cond.matchMode)
        assertTrue(cond.negate)
    }

    @Test
    fun `sender match condition defaults and values`() {
        val cond = ConditionDefinition.SenderMatch(
            senders = listOf("APB", "PRISBANK"),
            caseSensitive = true,
            negate = false
        )
        assertEquals("SenderMatch", cond.type)
        assertEquals(2, cond.senders.size)
        assertTrue(cond.caseSensitive)
        assertFalse(cond.negate)
    }

    @Test
    fun `text regex match valid pattern within limits`() {
        val validPattern = "a".repeat(256)
        val cond = ConditionDefinition.TextRegexMatch(
            pattern = validPattern,
            targetField = TextFieldTarget.TEXT,
            caseSensitive = true,
            maxMatchLength = 512
        )
        assertEquals("TextRegexMatch", cond.type)
        assertEquals(validPattern, cond.pattern)
        assertEquals(TextFieldTarget.TEXT, cond.targetField)
        assertTrue(cond.caseSensitive)
        assertEquals(512, cond.maxMatchLength)
    }

    @Test
    fun `text regex match throws on blank pattern`() {
        val ex = assertThrows<IllegalArgumentException> {
            ConditionDefinition.TextRegexMatch(pattern = "   ")
        }
        assertTrue(ex.message!!.contains("cannot be blank"))
    }

    @Test
    fun `text regex match throws on pattern exceeding max length`() {
        val ex = assertThrows<IllegalArgumentException> {
            ConditionDefinition.TextRegexMatch(pattern = "a".repeat(257))
        }
        assertTrue(ex.message!!.contains("exceeds max allowed 256"))
    }

    @Test
    fun `category match condition`() {
        val cond = ConditionDefinition.CategoryMatch(
            category = Category.FINANCE,
            minConfidence = 0.85
        )
        assertEquals("CategoryMatch", cond.type)
        assertEquals(Category.FINANCE, cond.category)
        assertEquals(0.85, cond.minConfidence, 0.001)
    }

    @Test
    fun `prototype support count condition`() {
        val cond = ConditionDefinition.PrototypeSupportCount(minSupportCount = 3)
        assertEquals("PrototypeSupportCount", cond.type)
        assertEquals(3, cond.minSupportCount)
    }

    @Test
    fun `logical and validates arity`() {
        // Valid with 2 conditions (min)
        val cond2 = ConditionDefinition.LogicalAnd(
            conditions = listOf(ConditionDefinition.AlwaysTrue, ConditionDefinition.AlwaysFalse)
        )
        assertEquals("LogicalAnd", cond2.type)
        assertEquals(2, cond2.conditions.size)

        // Valid with 16 conditions (max)
        val cond16 = ConditionDefinition.LogicalAnd(
            conditions = List(16) { ConditionDefinition.AlwaysTrue }
        )
        assertEquals(16, cond16.conditions.size)

        // Throws with 1 condition
        val exUnder = assertThrows<IllegalArgumentException> {
            ConditionDefinition.LogicalAnd(conditions = listOf(ConditionDefinition.AlwaysTrue))
        }
        assertTrue(exUnder.message!!.contains("requires between 2 and 16"))

        // Throws with 17 conditions
        val exOver = assertThrows<IllegalArgumentException> {
            ConditionDefinition.LogicalAnd(conditions = List(17) { ConditionDefinition.AlwaysTrue })
        }
        assertTrue(exOver.message!!.contains("requires between 2 and 16"))
    }

    @Test
    fun `logical or validates arity`() {
        val cond2 = ConditionDefinition.LogicalOr(
            conditions = listOf(ConditionDefinition.AlwaysTrue, ConditionDefinition.AlwaysFalse)
        )
        assertEquals("LogicalOr", cond2.type)

        assertThrows<IllegalArgumentException> {
            ConditionDefinition.LogicalOr(conditions = listOf(ConditionDefinition.AlwaysTrue))
        }
        assertThrows<IllegalArgumentException> {
            ConditionDefinition.LogicalOr(conditions = List(17) { ConditionDefinition.AlwaysTrue })
        }
    }

    @Test
    fun `logical not condition`() {
        val cond = ConditionDefinition.LogicalNot(condition = ConditionDefinition.AlwaysTrue)
        assertEquals("LogicalNot", cond.type)
        assertEquals(ConditionDefinition.AlwaysTrue, cond.condition)
    }

    @Test
    fun `always true and always false constants`() {
        assertEquals("AlwaysTrue", ConditionDefinition.AlwaysTrue.type)
        assertEquals("AlwaysFalse", ConditionDefinition.AlwaysFalse.type)
    }

    @Test
    fun `serialization roundtrip for all 10 condition predicates`() {
        val allConditions: List<ConditionDefinition> = listOf(
            ConditionDefinition.PackageMatch(listOf("com.test"), MatchMode.GLOB, false),
            ConditionDefinition.SenderMatch(listOf("Sender1"), false, true),
            ConditionDefinition.TextRegexMatch("[0-9]+", TextFieldTarget.TITLE, true, 256),
            ConditionDefinition.CategoryMatch(Category.COMMUNICATION, 0.9),
            ConditionDefinition.PrototypeSupportCount(2),
            ConditionDefinition.LogicalAnd(listOf(ConditionDefinition.AlwaysTrue, ConditionDefinition.AlwaysFalse)),
            ConditionDefinition.LogicalOr(listOf(ConditionDefinition.AlwaysTrue, ConditionDefinition.AlwaysFalse)),
            ConditionDefinition.LogicalNot(ConditionDefinition.AlwaysFalse),
            ConditionDefinition.AlwaysTrue,
            ConditionDefinition.AlwaysFalse
        )

        for (cond in allConditions) {
            val json = PipelineJsonCodec.json.encodeToString(cond)
            assertTrue(json.contains("\"type\":\"${cond.type}\""), "JSON missing type for ${cond.type}")
            val decoded = PipelineJsonCodec.json.decodeFromString<ConditionDefinition>(json)
            assertEquals(cond, decoded)
        }
    }
}
