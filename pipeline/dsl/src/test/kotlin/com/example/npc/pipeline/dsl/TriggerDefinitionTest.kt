package com.example.npc.pipeline.dsl

import com.example.npc.pipeline.dsl.codec.PipelineJsonCodec
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TriggerDefinitionTest {

    @Test
    fun `notification trigger has correct defaults and type`() {
        val trigger = TriggerDefinition.Notification()
        assertEquals("NOTIFICATION", trigger.type)
        assertTrue(trigger.ignoreSelf)
        assertTrue(trigger.subTypes.isEmpty())
    }

    @Test
    fun `notification trigger with custom subtypes and ignoreSelf false`() {
        val trigger = TriggerDefinition.Notification(
            ignoreSelf = false,
            subTypes = listOf("CALL", "SMS")
        )
        assertFalse(trigger.ignoreSelf)
        assertEquals(listOf("CALL", "SMS"), trigger.subTypes)
        assertEquals("NOTIFICATION", trigger.type)
    }

    @Test
    fun `sms trigger has correct defaults and type`() {
        val trigger = TriggerDefinition.Sms()
        assertEquals("SMS", trigger.type)
        assertTrue(trigger.allowDirectReceiver)
        assertTrue(trigger.allowMessagingApps)
    }

    @Test
    fun `media trigger has correct defaults and type`() {
        val trigger = TriggerDefinition.Media()
        assertEquals("MEDIA", trigger.type)
        assertFalse(trigger.captureArtwork)
    }

    @Test
    fun `polymorphic serialization roundtrip for all trigger types`() {
        val triggers: List<TriggerDefinition> = listOf(
            TriggerDefinition.Notification(ignoreSelf = true, subTypes = listOf("urgent")),
            TriggerDefinition.Sms(allowDirectReceiver = false, allowMessagingApps = true),
            TriggerDefinition.Media(captureArtwork = true)
        )

        for (trigger in triggers) {
            val json = PipelineJsonCodec.json.encodeToString(trigger)
            assertTrue(json.contains("\"type\":\"${trigger.type}\""))
            val decoded = PipelineJsonCodec.json.decodeFromString<TriggerDefinition>(json)
            assertEquals(trigger, decoded)
        }
    }
}
