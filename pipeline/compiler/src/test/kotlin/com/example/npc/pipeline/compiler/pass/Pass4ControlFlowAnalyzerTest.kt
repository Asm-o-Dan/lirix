package com.example.npc.pipeline.compiler.pass

import com.example.npc.pipeline.dsl.ActionDefinition
import com.example.npc.pipeline.dsl.ConditionDefinition
import com.example.npc.pipeline.dsl.PipelineDefinition
import com.example.npc.pipeline.dsl.StageDefinition
import com.example.npc.pipeline.dsl.TriggerDefinition
import com.example.npc.pipeline.dsl.preset.LegacyPipelinePreset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Pass4ControlFlowAnalyzerTest {

    private val analyzer = Pass4ControlFlowAnalyzer.create()

    private fun baseDef(stages: List<StageDefinition>): PipelineDefinition {
        return PipelineDefinition(
            schemaVersion = 1,
            id = "cfg-test",
            name = "CFG Test",
            triggers = listOf(TriggerDefinition.Notification()),
            stages = stages
        )
    }

    @Test
    fun canonicalPreset_hasNoDeadStages() {
        val preset = LegacyPipelinePreset.canonicalDefinition
        val res = analyzer.analyze(preset)
        assertEquals(0, res.deadStageIds.size)
        assertFalse(res.diagnostics.any { it.code == "P4001" })
    }

    @Test
    fun unconditionalDropEvent_marksSubsequentStagesAsDead() {
        val def = baseDef(
            listOf(
                StageDefinition(
                    id = "stage-drop",
                    name = "Drop",
                    condition = null, // Unconditional
                    actions = listOf(ActionDefinition.DropEvent("drop reason"))
                ),
                StageDefinition(
                    id = "stage-unreachable",
                    name = "Unreachable",
                    actions = listOf(ActionDefinition.DropEvent("never reached"))
                )
            )
        )
        val res = analyzer.analyze(def)
        assertTrue(res.deadStageIds.contains("stage-unreachable"))
        assertTrue(res.diagnostics.any { it.code == "P4001" && it.location.stageId == "stage-unreachable" })
    }

    @Test
    fun unconditionalTerminateOnMatch_marksSubsequentStagesAsDead() {
        val def = baseDef(
            listOf(
                StageDefinition(
                    id = "stage-term",
                    name = "Term",
                    condition = null,
                    terminateOnMatch = true,
                    actions = listOf(ActionDefinition.SaveToStorage())
                ),
                StageDefinition(
                    id = "stage-dead",
                    name = "Dead",
                    actions = listOf(ActionDefinition.SaveToStorage())
                )
            )
        )
        val res = analyzer.analyze(def)
        assertTrue(res.deadStageIds.contains("stage-dead"))
        assertTrue(res.diagnostics.any { it.code == "P4001" })
    }

    @Test
    fun conditionalTerminateOnMatch_keepsSubsequentStagesReachable() {
        val def = baseDef(
            listOf(
                StageDefinition(
                    id = "stage-cond-term",
                    name = "Cond Term",
                    condition = ConditionDefinition.PackageMatch(listOf("com.apb.mobile")),
                    terminateOnMatch = true,
                    actions = listOf(ActionDefinition.SaveToStorage())
                ),
                StageDefinition(
                    id = "stage-reachable",
                    name = "Reachable",
                    actions = listOf(ActionDefinition.SaveToStorage())
                )
            )
        )
        val res = analyzer.analyze(def)
        assertFalse(res.deadStageIds.contains("stage-reachable"))
        assertFalse(res.diagnostics.any { it.code == "P4001" })
    }

    @Test
    fun trivialCondition_emitsP4002() {
        val def = baseDef(
            listOf(
                StageDefinition(
                    id = "stage-trivial",
                    name = "Trivial",
                    condition = ConditionDefinition.LogicalAnd(
                        listOf(
                            ConditionDefinition.AlwaysFalse,
                            ConditionDefinition.PackageMatch(listOf("com.apb.mobile"))
                        )
                    ),
                    actions = listOf(ActionDefinition.DropEvent("r"))
                )
            )
        )
        val res = analyzer.analyze(def)
        assertTrue(res.diagnostics.any { it.code == "P4002" })
    }
}
