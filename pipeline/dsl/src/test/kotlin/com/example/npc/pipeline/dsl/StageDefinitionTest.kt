package com.example.npc.pipeline.dsl

import com.example.npc.core.model.classify.Category
import com.example.npc.pipeline.dsl.codec.PipelineJsonCodec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StageDefinitionTest {

    @Test
    fun `valid stage definition creation`() {
        val stage = StageDefinition(
            id = "stage-01_test",
            name = "Test Stage",
            enabled = true,
            condition = ConditionDefinition.AlwaysTrue,
            transforms = listOf(TransformDefinition.FingerprintCompute()),
            actions = listOf(ActionDefinition.SaveToStorage()),
            terminateOnMatch = true
        )
        assertEquals("stage-01_test", stage.id)
        assertEquals("Test Stage", stage.name)
        assertTrue(stage.enabled)
        assertTrue(stage.terminateOnMatch)
        assertEquals(1, stage.transforms.size)
        assertEquals(1, stage.actions.size)
    }

    @Test
    fun `stage id validation regex`() {
        // Valid IDs: 3 to 64 chars, a-z0-9_-
        StageDefinition(
            id = "abc",
            name = "Valid",
            actions = listOf(ActionDefinition.SaveToStorage())
        )
        StageDefinition(
            id = "a".repeat(64),
            name = "Valid",
            actions = listOf(ActionDefinition.SaveToStorage())
        )

        // Invalid: 2 chars (too short)
        assertThrows<IllegalArgumentException> {
            StageDefinition(id = "ab", name = "Too short", actions = listOf(ActionDefinition.SaveToStorage()))
        }

        // Invalid: 65 chars (too long)
        assertThrows<IllegalArgumentException> {
            StageDefinition(id = "a".repeat(65), name = "Too long", actions = listOf(ActionDefinition.SaveToStorage()))
        }

        // Invalid: uppercase letters
        assertThrows<IllegalArgumentException> {
            StageDefinition(id = "Stage_01", name = "Upper", actions = listOf(ActionDefinition.SaveToStorage()))
        }

        // Invalid: spaces or special chars
        assertThrows<IllegalArgumentException> {
            StageDefinition(id = "stage 01", name = "Space", actions = listOf(ActionDefinition.SaveToStorage()))
        }
    }

    @Test
    fun `stage name validation`() {
        // Blank name
        assertThrows<IllegalArgumentException> {
            StageDefinition(id = "stage-1", name = "   ", actions = listOf(ActionDefinition.SaveToStorage()))
        }

        // Name > 128 chars
        assertThrows<IllegalArgumentException> {
            StageDefinition(id = "stage-1", name = "a".repeat(129), actions = listOf(ActionDefinition.SaveToStorage()))
        }
    }

    @Test
    fun `stage requires at least one transform or action`() {
        assertThrows<IllegalArgumentException> {
            StageDefinition(
                id = "empty-stage",
                name = "Empty Stage",
                transforms = emptyList(),
                actions = emptyList()
            )
        }

        // Stage with only transform is valid
        val transformOnly = StageDefinition(
            id = "transform-only",
            name = "Transform Only",
            transforms = listOf(TransformDefinition.FingerprintCompute())
        )
        assertEquals(1, transformOnly.transforms.size)

        // Stage with only action is valid
        val actionOnly = StageDefinition(
            id = "action-only",
            name = "Action Only",
            actions = listOf(ActionDefinition.DropEvent("drop"))
        )
        assertEquals(1, actionOnly.actions.size)
    }

    @Test
    fun `stage serialization roundtrip`() {
        val stage = StageDefinition(
            id = "sample-stage",
            name = "Sample Stage",
            condition = ConditionDefinition.PackageMatch(listOf("com.app")),
            actions = listOf(ActionDefinition.SetCategory(Category.SERVICES, 0.9)),
            terminateOnMatch = true
        )
        val json = PipelineJsonCodec.json.encodeToString(StageDefinition.serializer(), stage)
        val decoded = PipelineJsonCodec.json.decodeFromString(StageDefinition.serializer(), json)
        assertEquals(stage, decoded)
    }
}
