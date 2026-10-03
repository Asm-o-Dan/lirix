package com.example.npc.pipeline.compiler.pass

import com.example.npc.pipeline.compiler.diagnostic.DiagnosticSeverity
import com.example.npc.pipeline.dsl.ActionDefinition
import com.example.npc.pipeline.dsl.ConditionDefinition
import com.example.npc.pipeline.dsl.PipelineDefinition
import com.example.npc.pipeline.dsl.StageDefinition
import com.example.npc.pipeline.dsl.TriggerDefinition
import com.example.npc.pipeline.dsl.preset.LegacyPipelinePreset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Pass1StructuralValidatorTest {

    private val validator = Pass1StructuralValidator.create()

    private fun allocateUnsafe(clazz: Class<*>): Any {
        val unsafeField = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null) as sun.misc.Unsafe
        return unsafe.allocateInstance(clazz)
    }

    private fun createPipeline(
        id: String = "valid-pipeline-id",
        name: String = "Valid Pipeline",
        schemaVersion: Int = 1,
        stages: List<StageDefinition> = listOf(
            StageDefinition(
                id = "stage-1",
                name = "Stage 1",
                actions = listOf(ActionDefinition.DropEvent(reason = "test"))
            )
        ),
        triggers: List<TriggerDefinition> = listOf(TriggerDefinition.Notification())
    ): PipelineDefinition {
        val def = allocateUnsafe(PipelineDefinition::class.java) as PipelineDefinition
        fun set(name: String, v: Any?) {
            val f = PipelineDefinition::class.java.getDeclaredField(name)
            f.isAccessible = true
            f.set(def, v)
        }
        set("id", id)
        set("name", name)
        set("schemaVersion", schemaVersion)
        set("revision", 1L)
        set("enabled", true)
        set("priority", 100)
        set("packageWhitelist", emptyList<String>())
        set("triggers", triggers)
        set("stages", stages)
        set("metadata", emptyMap<String, String>())
        return def
    }

    private fun createStage(
        id: String = "stage-1",
        name: String = "Stage 1",
        transforms: List<com.example.npc.pipeline.dsl.TransformDefinition> = emptyList(),
        actions: List<ActionDefinition> = emptyList(),
        condition: ConditionDefinition? = null
    ): StageDefinition {
        val s = allocateUnsafe(StageDefinition::class.java) as StageDefinition
        fun set(fieldName: String, v: Any?) {
            val f = StageDefinition::class.java.getDeclaredField(fieldName)
            f.isAccessible = true
            f.set(s, v)
        }
        set("id", id)
        set("name", name)
        set("transforms", transforms)
        set("actions", actions)
        set("condition", condition)
        set("terminateOnMatch", false)
        return s
    }

    @Test
    fun canonicalPreset_passesWithZeroDiagnostics() {
        val preset = LegacyPipelinePreset.canonicalDefinition
        val diags = validator.validate(preset)
        assertTrue(diags.isEmpty(), "Expected 0 diagnostics for canonical preset, but got: $diags")
    }

    @Test
    fun schemaVersion_invalid_emitsP1001() {
        val def = createPipeline(schemaVersion = 2)
        val diags = validator.validate(def)
        assertEquals(1, diags.size)
        assertEquals("P1001", diags[0].code)
        assertEquals(DiagnosticSeverity.ERROR, diags[0].severity)
        assertEquals("$['schemaVersion']", diags[0].location.jsonPath)
    }

    @Test
    fun pipelineId_invalid_emitsP1002() {
        val def = createPipeline(id = "INVALID ID WITH SPACES")
        val diags = validator.validate(def)
        assertTrue(diags.any { it.code == "P1002" })
    }

    @Test
    fun stageId_invalid_emitsP1002() {
        val def = createPipeline(
            stages = listOf(
                createStage(id = "BAD ID!!", actions = listOf(ActionDefinition.DropEvent("r")))
            )
        )
        val diags = validator.validate(def)
        assertTrue(diags.any { it.code == "P1002" })
    }

    @Test
    fun duplicateStageId_emitsP1003() {
        val def = createPipeline(
            stages = listOf(
                createStage(id = "same-id", name = "S1", actions = listOf(ActionDefinition.DropEvent("r"))),
                createStage(id = "same-id", name = "S2", actions = listOf(ActionDefinition.DropEvent("r")))
            )
        )
        val diags = validator.validate(def)
        val dupDiag = diags.find { it.code == "P1003" }
        assertTrue(dupDiag != null)
        assertEquals("$['stages'][1]['id']", dupDiag?.location?.jsonPath)
        assertEquals("$['stages'][0]['id']", dupDiag?.relatedLocations?.first()?.jsonPath)
    }

    @Test
    fun triggers_empty_emitsP1004() {
        val def = createPipeline(triggers = emptyList())
        val diags = validator.validate(def)
        assertTrue(diags.any { it.code == "P1004" })
    }

    @Test
    fun stages_empty_emitsP1005() {
        val def = createPipeline(stages = emptyList())
        val diags = validator.validate(def)
        assertTrue(diags.any { it.code == "P1005" })
    }

    @Test
    fun stages_exceed50_emitsP1006() {
        val stages = (1..51).map {
            createStage(id = "stage-$it", actions = listOf(ActionDefinition.DropEvent("r")))
        }
        val def = createPipeline(stages = stages)
        val diags = validator.validate(def)
        assertTrue(diags.any { it.code == "P1006" })
    }

    @Test
    fun stage_emptyBody_emitsP1007() {
        val def = createPipeline(
            stages = listOf(
                createStage(id = "stage-empty", name = "Empty Stage", transforms = emptyList(), actions = emptyList())
            )
        )
        val diags = validator.validate(def)
        assertTrue(diags.any { it.code == "P1007" })
    }

    @Test
    fun name_blank_emitsP1008() {
        val def = createPipeline(name = "   ")
        val diags = validator.validate(def)
        assertTrue(diags.any { it.code == "P1008" })
    }

    @Test
    fun conditionNesting_exceedsLimit_emitsP4003() {
        var cond: ConditionDefinition = ConditionDefinition.AlwaysTrue
        repeat(6) {
            cond = ConditionDefinition.LogicalNot(cond)
        }
        val def = createPipeline(
            stages = listOf(
                createStage(
                    id = "stage-nested",
                    condition = cond,
                    actions = listOf(ActionDefinition.DropEvent("r"))
                )
            )
        )
        val diags = validator.validate(def)
        assertTrue(diags.any { it.code == "P4003" })
    }
}
