package com.example.npc.pipeline.dsl

import com.example.npc.pipeline.dsl.codec.PipelineJsonCodec
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PipelineDefinitionTest {

    private fun createValidStage(id: String = "stage-1"): StageDefinition {
        return StageDefinition(
            id = id,
            name = "Valid Stage",
            actions = listOf(ActionDefinition.SaveToStorage())
        )
    }

    private fun createValidTriggers(): List<TriggerDefinition> {
        return listOf(TriggerDefinition.Notification())
    }

    @Test
    fun `valid pipeline definition creation`() {
        val pipeline = PipelineDefinition(
            id = "pipeline-01",
            name = "Main Pipeline",
            description = "Test description",
            schemaVersion = 1,
            revision = 1L,
            enabled = true,
            priority = 500,
            packageWhitelist = listOf("com.bank"),
            triggers = createValidTriggers(),
            stages = listOf(createValidStage()),
            metadata = mapOf("env" to "prod")
        )
        assertEquals("pipeline-01", pipeline.id)
        assertEquals("Main Pipeline", pipeline.name)
        assertEquals(1, pipeline.schemaVersion)
        assertEquals(1L, pipeline.revision)
        assertEquals(500, pipeline.priority)
        assertEquals(1, pipeline.stages.size)
        assertEquals(1, pipeline.triggers.size)
    }

    @Test
    fun `pipeline id validation regex`() {
        // Valid bounds
        PipelineDefinition(id = "min", name = "Valid", triggers = createValidTriggers(), stages = listOf(createValidStage()))
        PipelineDefinition(id = "a".repeat(64), name = "Valid", triggers = createValidTriggers(), stages = listOf(createValidStage()))

        // Invalid: too short
        assertThrows<IllegalArgumentException> {
            PipelineDefinition(id = "ab", name = "Too short", triggers = createValidTriggers(), stages = listOf(createValidStage()))
        }
        // Invalid: too long
        assertThrows<IllegalArgumentException> {
            PipelineDefinition(id = "a".repeat(65), name = "Too long", triggers = createValidTriggers(), stages = listOf(createValidStage()))
        }
        // Invalid: uppercase
        assertThrows<IllegalArgumentException> {
            PipelineDefinition(id = "Pipeline-1", name = "Upper", triggers = createValidTriggers(), stages = listOf(createValidStage()))
        }
    }

    @Test
    fun `pipeline name validation`() {
        assertThrows<IllegalArgumentException> {
            PipelineDefinition(id = "valid-id", name = "   ", triggers = createValidTriggers(), stages = listOf(createValidStage()))
        }
        assertThrows<IllegalArgumentException> {
            PipelineDefinition(id = "valid-id", name = "a".repeat(129), triggers = createValidTriggers(), stages = listOf(createValidStage()))
        }
    }

    @Test
    fun `schemaVersion must be CURRENT_SCHEMA_VERSION (1)`() {
        assertThrows<IllegalArgumentException> {
            PipelineDefinition(
                id = "valid-id",
                name = "Valid",
                schemaVersion = 2,
                triggers = createValidTriggers(),
                stages = listOf(createValidStage())
            )
        }
    }

    @Test
    fun `revision must be positive`() {
        assertThrows<IllegalArgumentException> {
            PipelineDefinition(
                id = "valid-id",
                name = "Valid",
                revision = 0L,
                triggers = createValidTriggers(),
                stages = listOf(createValidStage())
            )
        }
    }

    @Test
    fun `priority must be within 0 to 1000`() {
        // Valid bounds
        PipelineDefinition(id = "valid-id", name = "Valid", priority = 0, triggers = createValidTriggers(), stages = listOf(createValidStage()))
        PipelineDefinition(id = "valid-id", name = "Valid", priority = 1000, triggers = createValidTriggers(), stages = listOf(createValidStage()))

        assertThrows<IllegalArgumentException> {
            PipelineDefinition(id = "valid-id", name = "Valid", priority = -1, triggers = createValidTriggers(), stages = listOf(createValidStage()))
        }
        assertThrows<IllegalArgumentException> {
            PipelineDefinition(id = "valid-id", name = "Valid", priority = 1001, triggers = createValidTriggers(), stages = listOf(createValidStage()))
        }
    }

    @Test
    fun `triggers must not be empty`() {
        assertThrows<IllegalArgumentException> {
            PipelineDefinition(
                id = "valid-id",
                name = "Valid",
                triggers = emptyList(),
                stages = listOf(createValidStage())
            )
        }
    }

    @Test
    fun `stages must have 1 to 50 items`() {
        // Empty stages throws
        assertThrows<IllegalArgumentException> {
            PipelineDefinition(
                id = "valid-id",
                name = "Valid",
                triggers = createValidTriggers(),
                stages = emptyList()
            )
        }

        // 50 stages is valid
        val stages50 = (1..50).map { createValidStage("stage-$it") }
        val p50 = PipelineDefinition(
            id = "valid-id",
            name = "Valid 50",
            triggers = createValidTriggers(),
            stages = stages50
        )
        assertEquals(50, p50.stages.size)

        // 51 stages throws
        val stages51 = (1..51).map { createValidStage("stage-$it") }
        assertThrows<IllegalArgumentException> {
            PipelineDefinition(
                id = "valid-id",
                name = "Over limit",
                triggers = createValidTriggers(),
                stages = stages51
            )
        }
    }
}
