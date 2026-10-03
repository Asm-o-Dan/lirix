package com.example.npc.core.model.normalize

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.SourceId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class EventNormalizerDeduplicationKeyTest {

    private val sourceNotification = SourceId("notification")
    private val sourceSms = SourceId("sms")
    private val packageNameTelegram = "org.telegram.messenger"
    private val packageNameWhatsapp = "com.whatsapp"

    @Test
    fun `computeDeduplicationKey produces strictly identical key when only postTime or when differ`() {
        val payload1 = """{"title":"Alice","text":"Привет","postTime":1774567890}"""
        val payload2 = """{"title":"Alice","text":"Привет","postTime":1774599999}"""
        val payload3 = """{"title":"Alice","text":"Привет","when":1774500000}"""
        val payload4 = """{"title":"Alice","text":"Привет","postTime":100,"when":200}"""

        val key1 = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, payload1)
        val key2 = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, payload2)
        val key3 = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, payload3)
        val key4 = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, payload4)

        assertEquals(key1, key2)
        assertEquals(key1, key3)
        assertEquals(key1, key4)
    }

    @Test
    fun `computeDeduplicationKey produces strictly identical key regardless of JSON key order`() {
        val jsonOrderA = """{"title":"Meeting","text":"In 5 minutes","sender":"Bob"}"""
        val jsonOrderB = """{"sender":"Bob","title":"Meeting","text":"In 5 minutes"}"""
        val jsonOrderC = """{"text":"In 5 minutes","sender":"Bob","title":"Meeting"}"""

        val keyA = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, jsonOrderA)
        val keyB = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, jsonOrderB)
        val keyC = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, jsonOrderC)

        assertEquals(keyA, keyB)
        assertEquals(keyA, keyC)
    }

    @Test
    fun `computeDeduplicationKey ignores JSON whitespace formatting`() {
        val compactJson = """{"title":"Alert","text":"Server down"}"""
        val formattedJson = """
            {
                "title" : "Alert" ,
                "text"  : "Server down"
            }
        """.trimIndent()

        val keyCompact = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, compactJson)
        val keyFormatted = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, formattedJson)

        assertEquals(keyCompact, keyFormatted)
    }

    @Test
    fun `computeDeduplicationKey produces different key when title or text changes`() {
        val basePayload = """{"title":"Code","text":"123456"}"""
        val changedTitlePayload = """{"title":"New Code","text":"123456"}"""
        val changedTextPayload = """{"title":"Code","text":"654321"}"""

        val baseKey = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, basePayload)
        val titleChangedKey = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, changedTitlePayload)
        val textChangedKey = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, changedTextPayload)

        assertNotEquals(baseKey, titleChangedKey)
        assertNotEquals(baseKey, textChangedKey)
    }

    @Test
    fun `computeDeduplicationKey produces different key for different sources or packages`() {
        val payload = """{"title":"Title","text":"Message"}"""

        val keyTelegram = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, payload)
        val keyWhatsapp = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameWhatsapp, payload)
        val keySms = EventNormalizer.computeDeduplicationKey(sourceSms, packageNameTelegram, payload)

        assertNotEquals(keyTelegram, keyWhatsapp)
        assertNotEquals(keyTelegram, keySms)
    }

    @Test
    fun `computeDeduplicationKey handles JSON containing only postTime and when without error`() {
        val payloadOnlyTimestamps = """{"postTime": 1700000000, "when": 1700000000}"""
        val emptyObjectPayload = "{}"

        val keyOnlyTimestamps = assertDoesNotThrow {
            EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, payloadOnlyTimestamps)
        }
        val keyEmptyObject = assertDoesNotThrow {
            EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, emptyObjectPayload)
        }

        assertEquals(keyOnlyTimestamps, keyEmptyObject)
        assertTrue(keyOnlyTimestamps.value.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun `computeDeduplicationKey throws IllegalArgumentException on invalid JSON or non-object JSON`() {
        val invalidJsons = listOf(
            "{broken",
            "not a json",
            "[1, 2, 3]",
            "\"just a string\"",
            "12345",
            ""
        )

        for (invalidJson in invalidJsons) {
            assertThrows<IllegalArgumentException>("Expected IllegalArgumentException for: '$invalidJson'") {
                EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, invalidJson)
            }
        }
    }

    @Test
    fun `computeDeduplicationKey throws IllegalArgumentException on blank packageName or payloadJson`() {
        assertThrows<IllegalArgumentException> {
            EventNormalizer.computeDeduplicationKey(sourceNotification, "", """{"title":"Hello"}""")
        }

        assertThrows<IllegalArgumentException> {
            EventNormalizer.computeDeduplicationKey(sourceNotification, "   ", """{"title":"Hello"}""")
        }

        assertThrows<IllegalArgumentException> {
            EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, "   ")
        }
    }

    @Test
    fun `computeDeduplicationKey returns valid 64-char lowercase hex DeduplicationKey`() {
        val payload = """{"title":"Test","text":"Payload"}"""
        val key = EventNormalizer.computeDeduplicationKey(sourceNotification, packageNameTelegram, payload)

        assertEquals(64, key.value.length)
        assertTrue(key.value.matches(Regex("^[0-9a-f]{64}$")))
    }
}
