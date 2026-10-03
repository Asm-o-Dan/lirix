package com.example.npc.pipeline.dsl

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.pipeline.dsl.codec.PipelineJsonCodec
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ActionDefinitionTest {

    @Test
    fun `set category validation and defaults`() {
        val action = ActionDefinition.SetCategory(
            category = Category.FINANCE,
            confidence = 0.95,
            engine = Engine.RULES
        )
        assertEquals("SetCategory", action.type)
        assertEquals(Category.FINANCE, action.category)
        assertEquals(0.95, action.confidence, 0.001)
        assertEquals(Engine.RULES, action.engine)

        // Valid boundary values
        ActionDefinition.SetCategory(category = Category.OTHER, confidence = 0.0)
        ActionDefinition.SetCategory(category = Category.OTHER, confidence = 1.0)

        // Invalid negative confidence
        val exLow = assertThrows<IllegalArgumentException> {
            ActionDefinition.SetCategory(category = Category.OTHER, confidence = -0.01)
        }
        assertTrue(exLow.message!!.contains("Confidence must be within 0.0..1.0"))

        // Invalid > 1.0 confidence
        val exHigh = assertThrows<IllegalArgumentException> {
            ActionDefinition.SetCategory(category = Category.OTHER, confidence = 1.001)
        }
        assertTrue(exHigh.message!!.contains("Confidence must be within 0.0..1.0"))
    }

    @Test
    fun `create transaction defaults and custom values`() {
        val action = ActionDefinition.CreateTransaction(
            direction = TransactionType.DEBIT,
            amountVar = "minorAmount",
            currencyVar = "resolvedCurr",
            status = TransactionStatus.COMPLETED
        )
        assertEquals("CreateTransaction", action.type)
        assertEquals(TransactionType.DEBIT, action.direction)
        assertEquals("minorAmount", action.amountVar)
        assertEquals("resolvedCurr", action.currencyVar)
        assertEquals(TransactionStatus.COMPLETED, action.status)
    }

    @Test
    fun `save to storage, drop event, stop processing actions`() {
        val save = ActionDefinition.SaveToStorage(completeProcessing = true)
        assertEquals("SaveToStorage", save.type)
        assertTrue(save.completeProcessing)

        val drop = ActionDefinition.DropEvent(reason = "Spam package")
        assertEquals("DropEvent", drop.type)
        assertEquals("Spam package", drop.reason)

        val stop = ActionDefinition.StopProcessing(reason = "Early exit condition satisfied")
        assertEquals("StopProcessing", stop.type)
        assertEquals("Early exit condition satisfied", stop.reason)
    }

    @Test
    fun `serialization roundtrip for all 5 actions`() {
        val actions: List<ActionDefinition> = listOf(
            ActionDefinition.SetCategory(Category.ADVERTISEMENT, 0.8, Engine.RULES),
            ActionDefinition.CreateTransaction(TransactionType.CREDIT, "amt", "cur", TransactionStatus.COMPLETED),
            ActionDefinition.SaveToStorage(true),
            ActionDefinition.DropEvent("Blacklisted sender"),
            ActionDefinition.StopProcessing("Done")
        )

        for (action in actions) {
            val json = PipelineJsonCodec.json.encodeToString<ActionDefinition>(action)
            assertTrue(json.contains("\"type\":\"${action.type}\""))
            val decoded = PipelineJsonCodec.json.decodeFromString<ActionDefinition>(json)
            assertEquals(action, decoded)
        }
    }
}
