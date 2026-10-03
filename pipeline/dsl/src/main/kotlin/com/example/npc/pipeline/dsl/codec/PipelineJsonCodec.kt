package com.example.npc.pipeline.dsl.codec

import com.example.npc.pipeline.dsl.PipelineDefinition
import kotlinx.serialization.json.Json
import java.security.MessageDigest

object PipelineJsonCodec {
    val json: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        prettyPrint = false
        encodeDefaults = true
        coerceInputValues = false
        classDiscriminator = "type"
    }

    val prettyJson: Json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        coerceInputValues = false
        classDiscriminator = "type"
    }

    val tolerantJson: Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        prettyPrint = false
        encodeDefaults = true
        coerceInputValues = false
        classDiscriminator = "type"
    }

    fun encodeToString(definition: PipelineDefinition): String =
        json.encodeToString(PipelineDefinition.serializer(), definition)

    fun encodeToPrettyString(definition: PipelineDefinition): String =
        prettyJson.encodeToString(PipelineDefinition.serializer(), definition)

    fun decodeFromString(jsonString: String, tolerant: Boolean = false): PipelineDefinition {
        val parser = if (tolerant) tolerantJson else json
        return parser.decodeFromString(PipelineDefinition.serializer(), jsonString)
    }

    fun calculateCanonicalHash(definition: PipelineDefinition): String {
        val compactJson = encodeToString(definition)
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(compactJson.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
