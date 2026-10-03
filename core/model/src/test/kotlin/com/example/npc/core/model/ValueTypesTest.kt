package com.example.npc.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class ValueTypesTest {

    // --- SourceId Tests ---

    @Test
    fun `SourceId allows valid formats`() {
        val validIds = listOf(
            "notification",
            "sms",
            "media",
            "my-source_1",
            "A",
            "a",
            "0",
            "source-123_test",
            "a".repeat(64)
        )
        for (id in validIds) {
            val sourceId = assertDoesNotThrow { SourceId(id) }
            assertEquals(id, sourceId.value)
        }
    }

    @Test
    fun `SourceId companion object constants are correctly defined`() {
        assertEquals("notification", SourceId.NOTIFICATION.value)
        assertEquals("sms", SourceId.SMS.value)
        assertEquals("media", SourceId.MEDIA.value)
    }

    @Test
    fun `SourceId throws IllegalArgumentException on invalid formats`() {
        val invalidIds = listOf(
            "",
            "   ",
            "source with spaces",
            "source@name",
            "source#1",
            "source$1",
            "source/1",
            "source.1",
            "a".repeat(65)
        )
        for (id in invalidIds) {
            val ex = assertThrows<IllegalArgumentException>("Expected IllegalArgumentException for '$id'") {
                SourceId(id)
            }
            assertTrue(ex.message?.isNotEmpty() == true)
        }
    }

    // --- ThreadKey Tests ---

    @Test
    fun `ThreadKey allows valid values`() {
        val validKeys = listOf(
            "chat_12345",
            "dialog:user_42",
            "group/tech-channel",
            "A",
            "x".repeat(256)
        )
        for (key in validKeys) {
            val threadKey = assertDoesNotThrow { ThreadKey(key) }
            assertEquals(key, threadKey.value)
        }
    }

    @Test
    fun `ThreadKey throws IllegalArgumentException on blank or invalid length`() {
        val invalidKeys = listOf(
            "",
            "   ",
            "\t\n",
            "x".repeat(257)
        )
        for (key in invalidKeys) {
            assertThrows<IllegalArgumentException>("Expected IllegalArgumentException for key with length ${key.length}") {
                ThreadKey(key)
            }
        }
    }

    // --- DeduplicationKey Tests ---

    @Test
    fun `DeduplicationKey allows valid 64-char lowercase hex SHA-256`() {
        val validHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val key = assertDoesNotThrow { DeduplicationKey(validHex) }
        assertEquals(validHex, key.value)

        val allZeros = "0".repeat(64)
        assertEquals(allZeros, DeduplicationKey(allZeros).value)

        val allFs = "f".repeat(64)
        assertEquals(allFs, DeduplicationKey(allFs).value)
    }

    @Test
    fun `DeduplicationKey throws IllegalArgumentException on invalid length, uppercase, or non-hex`() {
        val invalidHexValues = listOf(
            "",
            "a".repeat(63), // length 63
            "a".repeat(65), // length 65
            "0123456789ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef", // contains uppercase A-F
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeg", // contains 'g'
            "z".repeat(64), // non-hex
            " ".repeat(64)  // spaces
        )
        for (value in invalidHexValues) {
            assertThrows<IllegalArgumentException>("Expected IllegalArgumentException for value '$value'") {
                DeduplicationKey(value)
            }
        }
    }

    // --- EmbeddingRef Tests ---

    @Test
    fun `EmbeddingRef allows valid vectorId`() {
        val validIds = listOf(
            "vec-1",
            "embedding-uuid-12345",
            "v".repeat(128)
        )
        for (id in validIds) {
            val ref = assertDoesNotThrow { EmbeddingRef(id) }
            assertEquals(id, ref.vectorId)
        }
    }

    @Test
    fun `EmbeddingRef throws IllegalArgumentException on empty, blank, or length over 128`() {
        val invalidIds = listOf(
            "",
            "   ",
            "\t",
            "v".repeat(129)
        )
        for (id in invalidIds) {
            assertThrows<IllegalArgumentException>("Expected IllegalArgumentException for id with length ${id.length}") {
                EmbeddingRef(id)
            }
        }
    }

    // --- Lang Enum Tests ---

    @Test
    fun `Lang enum has RU, EN, and UNK values`() {
        val expectedValues = setOf("RU", "EN", "UNK")
        val actualValues = Lang.values().map { it.name }.toSet()
        assertEquals(expectedValues, actualValues)

        assertNotNull(Lang.valueOf("RU"))
        assertNotNull(Lang.valueOf("EN"))
        assertNotNull(Lang.valueOf("UNK"))
    }
}
