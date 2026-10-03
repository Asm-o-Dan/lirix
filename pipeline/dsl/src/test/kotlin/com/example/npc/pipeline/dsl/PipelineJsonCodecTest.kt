package com.example.npc.pipeline.dsl

import com.example.npc.pipeline.dsl.codec.PipelineJsonCodec
import com.example.npc.pipeline.dsl.preset.LegacyPipelinePreset
import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PipelineJsonCodecTest {

    @Test
    fun `roundtrip serialization and deserialization`() {
        val original = LegacyPipelinePreset.create()
        val json = PipelineJsonCodec.encodeToString(original)
        assertFalse(json.contains("\n"))

        val decoded = PipelineJsonCodec.decodeFromString(json)
        assertEquals(original, decoded)
    }

    @Test
    fun `pretty print produces multiline indented json`() {
        val original = LegacyPipelinePreset.create()
        val pretty = PipelineJsonCodec.encodeToPrettyString(original)
        assertTrue(pretty.contains("\n"))
        assertTrue(pretty.contains("  \"id\": \"legacy-1.1-preset\""))

        val decoded = PipelineJsonCodec.decodeFromString(pretty)
        assertEquals(original, decoded)
    }

    @Test
    fun `canonical sha256 hash is deterministic and 64 hex chars`() {
        val p1 = LegacyPipelinePreset.create()
        val p2 = LegacyPipelinePreset.create()

        val hash1 = PipelineJsonCodec.calculateCanonicalHash(p1)
        val hash2 = PipelineJsonCodec.calculateCanonicalHash(p2)

        assertEquals(hash1, hash2)
        assertEquals(64, hash1.length)
        assertTrue(hash1.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun `strict mode rejects unknown fields`() {
        val validJson = PipelineJsonCodec.encodeToString(LegacyPipelinePreset.create())
        val jsonWithUnknownField = validJson.replace("\"id\":", "\"unknownProperty\":123,\"id\":")

        assertThrows<SerializationException> {
            PipelineJsonCodec.decodeFromString(jsonWithUnknownField, tolerant = false)
        }
    }

    @Test
    fun `tolerant mode ignores unknown fields`() {
        val validJson = PipelineJsonCodec.encodeToString(LegacyPipelinePreset.create())
        val jsonWithUnknownField = validJson.replace("\"id\":", "\"unknownProperty\":123,\"id\":")

        val decoded = PipelineJsonCodec.decodeFromString(jsonWithUnknownField, tolerant = true)
        assertEquals(LegacyPipelinePreset.PRESET_ID, decoded.id)
    }

    @Test
    fun `invalid json syntax throws SerializationException`() {
        assertThrows<SerializationException> {
            PipelineJsonCodec.decodeFromString("{invalid json content}")
        }
    }
}
