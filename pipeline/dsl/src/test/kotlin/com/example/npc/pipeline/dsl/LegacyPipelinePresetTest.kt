package com.example.npc.pipeline.dsl

import com.example.npc.core.model.classify.Category
import com.example.npc.pipeline.dsl.codec.PipelineJsonCodec
import com.example.npc.pipeline.dsl.preset.LegacyPipelinePreset
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LegacyPipelinePresetTest {

    @Test
    fun `preset matches specification and has 7 stages`() {
        val preset = LegacyPipelinePreset.create()
        assertEquals(LegacyPipelinePreset.PRESET_ID, preset.id)
        assertEquals(1, preset.schemaVersion)
        assertEquals(1L, preset.revision)
        assertTrue(preset.enabled)
        assertEquals(1000, preset.priority)
        assertEquals(3, preset.triggers.size)
        assertEquals(7, preset.stages.size)

        val expectedStageIds = listOf(
            "stage-fingerprint",
            "stage-prototype-feedback",
            "stage-bank-finance",
            "stage-music",
            "stage-communication",
            "stage-services",
            "stage-fallback-other"
        )
        assertEquals(expectedStageIds, preset.stages.map { it.id })

        // Stage 1: stage-fingerprint has FingerprintCompute
        val s1 = preset.stages[0]
        assertNull(s1.condition)
        assertEquals(1, s1.transforms.size)
        assertTrue(s1.transforms[0] is TransformDefinition.FingerprintCompute)

        // Stage 2: stage-prototype-feedback has PrototypeSupportCount
        val s2 = preset.stages[1]
        assertTrue(s2.condition is ConditionDefinition.PrototypeSupportCount)
        assertEquals(2, (s2.condition as ConditionDefinition.PrototypeSupportCount).minSupportCount)

        // Stage 3: stage-bank-finance has FINANCE action and terminateOnMatch
        val s3 = preset.stages[2]
        assertTrue(s3.terminateOnMatch)
        assertTrue(s3.actions.any { it is ActionDefinition.SetCategory && it.category == Category.FINANCE })
        assertTrue(s3.transforms.any { it is TransformDefinition.FinanceExtract })

        // Stage 4: stage-music has MUSIC
        val s4 = preset.stages[3]
        assertTrue(s4.terminateOnMatch)
        assertTrue(s4.actions.any { it is ActionDefinition.SetCategory && it.category == Category.MUSIC })

        // Stage 5: stage-communication has COMMUNICATION
        val s5 = preset.stages[4]
        assertTrue(s5.terminateOnMatch)
        assertTrue(s5.actions.any { it is ActionDefinition.SetCategory && it.category == Category.COMMUNICATION })

        // Stage 6: stage-services has SERVICES
        val s6 = preset.stages[5]
        assertTrue(s6.terminateOnMatch)
        assertTrue(s6.actions.any { it is ActionDefinition.SetCategory && it.category == Category.SERVICES })

        // Stage 7: stage-fallback-other has OTHER
        val s7 = preset.stages[6]
        assertTrue(s7.terminateOnMatch)
        assertNull(s7.condition)
        assertTrue(s7.actions.any { it is ActionDefinition.SetCategory && it.category == Category.OTHER })
    }

    @Test
    fun `preset round-trip serialization preserves equality`() {
        val original = LegacyPipelinePreset.create()
        val json = PipelineJsonCodec.encodeToString(original)
        val restored = PipelineJsonCodec.decodeFromString(json)
        assertEquals(original, restored)
    }
}
