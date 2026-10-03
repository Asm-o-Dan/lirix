package com.example.npc.pipeline.compiler.pass

import com.example.npc.pipeline.dsl.ActionDefinition
import com.example.npc.pipeline.dsl.ConditionDefinition
import com.example.npc.pipeline.dsl.PipelineDefinition
import com.example.npc.pipeline.dsl.StageDefinition
import com.example.npc.pipeline.dsl.TransformDefinition
import com.example.npc.pipeline.dsl.TriggerDefinition
import com.example.npc.pipeline.nodes.api.frame.FrameLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class Pass3DataflowAnalyzerTest {

    private val analyzer = Pass3DataflowAnalyzer.create()

    private fun baseDef(stages: List<StageDefinition>): PipelineDefinition {
        return PipelineDefinition(
            schemaVersion = 1,
            id = "dataflow-test",
            name = "Dataflow Test",
            triggers = listOf(TriggerDefinition.Notification()),
            stages = stages
        )
    }

    @Test
    fun useBeforeDef_emitsP3001() {
        val def = baseDef(
            listOf(
                StageDefinition(
                    id = "stage-read",
                    name = "Read Unknown Var",
                    actions = listOf(
                        ActionDefinition.CreateTransaction(
                            amountVar = "unknownVar",
                            currencyVar = "resolvedCurrency"
                        )
                    )
                )
            )
        )
        val res = analyzer.analyze(def)
        assertTrue(res.diagnostics.any { it.code == "P3001" })
    }

    @Test
    fun conditionalDefRead_emitsP3002() {
        val def = baseDef(
            listOf(
                StageDefinition(
                    id = "stage-cond",
                    name = "Conditional Stage",
                    condition = ConditionDefinition.PackageMatch(listOf("com.apb.mobile")),
                    transforms = listOf(
                        TransformDefinition.AmountParse(
                            sourceVar = "input.text",
                            targetVar = "parsedAmount"
                        )
                    )
                ),
                StageDefinition(
                    id = "stage-uncond",
                    name = "Unconditional Consumer",
                    actions = listOf(
                        ActionDefinition.CreateTransaction(
                            amountVar = "parsedAmount",
                            currencyVar = "input.text"
                        )
                    )
                )
            )
        )
        val res = analyzer.analyze(def)
        assertTrue(res.diagnostics.any { it.code == "P3002" })
    }

    @Test
    fun writeToReadOnlyInput_emitsP3003() {
        val def = baseDef(
            listOf(
                StageDefinition(
                    id = "stage-write-input",
                    name = "Write to input",
                    transforms = listOf(
                        TransformDefinition.RegionalTextSanitize(targetVar = "input.text")
                    )
                )
            )
        )
        val res = analyzer.analyze(def)
        assertTrue(res.diagnostics.any { it.code == "P3003" })
    }

    @Test
    fun typeMismatch_emitsP3004() {
        val def = baseDef(
            listOf(
                StageDefinition(
                    id = "stage-1",
                    name = "Produce String",
                    transforms = listOf(
                        TransformDefinition.RegionalTextSanitize(targetVar = "myVar")
                    )
                ),
                StageDefinition(
                    id = "stage-2",
                    name = "Consume as Long",
                    actions = listOf(
                        ActionDefinition.CreateTransaction(
                            amountVar = "myVar", // myVar is STRING, but amountVar expects LONG
                            currencyVar = "input.text"
                        )
                    )
                )
            )
        )
        val res = analyzer.analyze(def)
        assertTrue(res.diagnostics.any { it.code == "P3004" })
    }

    @Test
    fun deadStoreAndUnusedVariable_warningsEmitted() {
        val def = baseDef(
            listOf(
                StageDefinition(
                    id = "stage-producer",
                    name = "Producer",
                    transforms = listOf(
                        TransformDefinition.RegionalTextSanitize(targetVar = "unusedVar")
                    ),
                    actions = listOf(ActionDefinition.DropEvent("r"))
                )
            )
        )
        val res = analyzer.analyze(def)
        assertTrue(res.diagnostics.any { it.code == "P3101" })
    }

    @Test
    fun requiredInputMask_correctlyAccumulated() {
        val def = baseDef(
            listOf(
                StageDefinition(
                    id = "stage-1",
                    name = "Checks package and text",
                    condition = ConditionDefinition.PackageMatch(listOf("com.apb.mobile")),
                    transforms = listOf(
                        TransformDefinition.RegionalTextSanitize(targetVar = "cleanText")
                    ),
                    actions = listOf(ActionDefinition.DropEvent("r"))
                )
            )
        )
        val res = analyzer.analyze(def)
        val expected = FrameLayout.MASK_INPUT_PACKAGE or FrameLayout.MASK_INPUT_TEXT
        assertEquals(expected, res.requiredInputMask and expected)
    }
}
