package com.example.npc.pipeline.dsl

import com.example.npc.pipeline.dsl.codec.PipelineJsonCodec
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TransformDefinitionTest {

    @Test
    fun `fingerprint compute defaults and serialization`() {
        val transform = TransformDefinition.FingerprintCompute()
        assertEquals("FingerprintCompute", transform.type)
        assertEquals("SHA-256-TEMPLATED", transform.algorithm)
        assertEquals("contentFingerprint", transform.targetVar)

        val json = PipelineJsonCodec.json.encodeToString<TransformDefinition>(transform)
        val decoded = PipelineJsonCodec.json.decodeFromString<TransformDefinition>(json)
        assertEquals(transform, decoded)
    }

    @Test
    fun `regional text sanitize defaults and serialization`() {
        val transform = TransformDefinition.RegionalTextSanitize(
            maxChars = 2048,
            normalizeNbsp = true,
            stripDiacritics = true,
            targetVar = "cleanText"
        )
        assertEquals("RegionalTextSanitize", transform.type)
        assertEquals(2048, transform.maxChars)
        assertTrue(transform.normalizeNbsp)
        assertTrue(transform.stripDiacritics)
        assertEquals("cleanText", transform.targetVar)

        val json = PipelineJsonCodec.json.encodeToString<TransformDefinition>(transform)
        val decoded = PipelineJsonCodec.json.decodeFromString<TransformDefinition>(json)
        assertEquals(transform, decoded)
    }

    @Test
    fun `amount parse defaults and serialization`() {
        val transform = TransformDefinition.AmountParse(
            currencyHint = "RUP",
            sourceVar = "input.text",
            targetVar = "parsedMinorUnits"
        )
        assertEquals("AmountParse", transform.type)
        assertEquals("RUP", transform.currencyHint)
        assertEquals("input.text", transform.sourceVar)
        assertEquals("parsedMinorUnits", transform.targetVar)

        val json = PipelineJsonCodec.json.encodeToString<TransformDefinition>(transform)
        val decoded = PipelineJsonCodec.json.decodeFromString<TransformDefinition>(json)
        assertEquals(transform, decoded)
    }

    @Test
    fun `currency resolve defaults and serialization`() {
        val transform = TransformDefinition.CurrencyResolve(
            defaultCurrency = "MDL",
            sourceVar = "cleanText",
            targetVar = "currency"
        )
        assertEquals("CurrencyResolve", transform.type)
        assertEquals("MDL", transform.defaultCurrency)

        val json = PipelineJsonCodec.json.encodeToString<TransformDefinition>(transform)
        val decoded = PipelineJsonCodec.json.decodeFromString<TransformDefinition>(json)
        assertEquals(transform, decoded)
    }

    @Test
    fun `finance extract timeout validation and serialization`() {
        // Valid bounds: 5ms to 500ms
        val minTransform = TransformDefinition.FinanceExtract(timeoutMs = 5L)
        assertEquals(5L, minTransform.timeoutMs)

        val maxTransform = TransformDefinition.FinanceExtract(timeoutMs = 500L)
        assertEquals(500L, maxTransform.timeoutMs)

        // Invalid: below 5ms
        val exLow = assertThrows<IllegalArgumentException> {
            TransformDefinition.FinanceExtract(timeoutMs = 4L)
        }
        assertTrue(exLow.message!!.contains("must be between 5 and 500 ms"))

        // Invalid: above 500ms
        val exHigh = assertThrows<IllegalArgumentException> {
            TransformDefinition.FinanceExtract(timeoutMs = 501L)
        }
        assertTrue(exHigh.message!!.contains("must be between 5 and 500 ms"))

        // Serialization
        val json = PipelineJsonCodec.json.encodeToString<TransformDefinition>(minTransform)
        val decoded = PipelineJsonCodec.json.decodeFromString<TransformDefinition>(json)
        assertEquals(minTransform, decoded)
    }
}
