package com.example.npc.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class DomainEntitiesTest {

    private val sampleHash = DeduplicationKey("a".repeat(64))
    private val sampleSource = SourceId("notification")
    private val sampleTime = Instant.parse("2026-09-26T04:00:00Z")

    // --- RawEvent Tests ---

    @Test
    fun `RawEvent creates valid instance with default and explicit parameters`() {
        val rawEvent = assertDoesNotThrow {
            RawEvent(
                id = 0L,
                seq = 100L,
                source = sampleSource,
                packageName = "org.telegram.messenger",
                receivedAt = sampleTime,
                payloadJson = "{\"title\":\"Alice\",\"text\":\"Hello\"}",
                hash = sampleHash
            )
        }

        assertEquals(0L, rawEvent.id)
        assertEquals(100L, rawEvent.seq)
        assertEquals(sampleSource, rawEvent.source)
        assertEquals("org.telegram.messenger", rawEvent.packageName)
        assertEquals(sampleTime, rawEvent.receivedAt)
        assertEquals("{\"title\":\"Alice\",\"text\":\"Hello\"}", rawEvent.payloadJson)
        assertEquals(sampleHash, rawEvent.hash)

        val persistedEvent = RawEvent(
            id = 42L,
            seq = 0L,
            source = sampleSource,
            packageName = "com.whatsapp",
            receivedAt = sampleTime,
            payloadJson = "{}",
            hash = sampleHash
        )
        assertEquals(42L, persistedEvent.id)
        assertEquals(0L, persistedEvent.seq)
    }

    @Test
    fun `RawEvent throws IllegalArgumentException when id is negative`() {
        assertThrows<IllegalArgumentException> {
            RawEvent(
                id = -1L,
                seq = 1L,
                source = sampleSource,
                packageName = "org.telegram.messenger",
                receivedAt = sampleTime,
                payloadJson = "{}",
                hash = sampleHash
            )
        }
    }

    @Test
    fun `RawEvent throws IllegalArgumentException when seq is negative`() {
        assertThrows<IllegalArgumentException> {
            RawEvent(
                id = 1L,
                seq = -1L,
                source = sampleSource,
                packageName = "org.telegram.messenger",
                receivedAt = sampleTime,
                payloadJson = "{}",
                hash = sampleHash
            )
        }
    }

    @Test
    fun `RawEvent throws IllegalArgumentException when packageName is blank or empty`() {
        assertThrows<IllegalArgumentException> {
            RawEvent(
                id = 1L,
                seq = 1L,
                source = sampleSource,
                packageName = "",
                receivedAt = sampleTime,
                payloadJson = "{}",
                hash = sampleHash
            )
        }

        assertThrows<IllegalArgumentException> {
            RawEvent(
                id = 1L,
                seq = 1L,
                source = sampleSource,
                packageName = "   ",
                receivedAt = sampleTime,
                payloadJson = "{}",
                hash = sampleHash
            )
        }
    }

    @Test
    fun `RawEvent throws IllegalArgumentException when payloadJson is blank or empty`() {
        assertThrows<IllegalArgumentException> {
            RawEvent(
                id = 1L,
                seq = 1L,
                source = sampleSource,
                packageName = "org.telegram.messenger",
                receivedAt = sampleTime,
                payloadJson = "",
                hash = sampleHash
            )
        }

        assertThrows<IllegalArgumentException> {
            RawEvent(
                id = 1L,
                seq = 1L,
                source = sampleSource,
                packageName = "org.telegram.messenger",
                receivedAt = sampleTime,
                payloadJson = "   \n\t",
                hash = sampleHash
            )
        }
    }

    // --- Event Tests ---

    @Test
    fun `Event creates valid instance with nullable threadKey and isUpdateOf`() {
        val eventWithoutOptionals = assertDoesNotThrow {
            Event(
                id = 0L,
                rawId = 10L,
                ts = sampleTime,
                title = "Payment",
                text = "Transfer completed",
                normalizedText = "Transfer completed",
                lang = Lang.EN
            )
        }

        assertEquals(0L, eventWithoutOptionals.id)
        assertEquals(10L, eventWithoutOptionals.rawId)
        assertEquals(sampleTime, eventWithoutOptionals.ts)
        assertEquals("Payment", eventWithoutOptionals.title)
        assertEquals("Transfer completed", eventWithoutOptionals.text)
        assertEquals("Transfer completed", eventWithoutOptionals.normalizedText)
        assertEquals(Lang.EN, eventWithoutOptionals.lang)
        assertNull(eventWithoutOptionals.threadKey)
        assertNull(eventWithoutOptionals.isUpdateOf)

        val threadKey = ThreadKey("chat_777")
        val eventWithOptionals = assertDoesNotThrow {
            Event(
                id = 5L,
                rawId = 20L,
                ts = sampleTime,
                title = "Chat Message",
                text = "Привет",
                normalizedText = "Привет",
                lang = Lang.RU,
                threadKey = threadKey,
                isUpdateOf = 4L
            )
        }

        assertEquals(5L, eventWithOptionals.id)
        assertEquals(20L, eventWithOptionals.rawId)
        assertEquals(threadKey, eventWithOptionals.threadKey)
        assertEquals(4L, eventWithOptionals.isUpdateOf)
    }

    @Test
    fun `Event throws IllegalArgumentException when id is negative`() {
        assertThrows<IllegalArgumentException> {
            Event(
                id = -1L,
                rawId = 1L,
                ts = sampleTime,
                title = "Title",
                text = "Text",
                normalizedText = "Text",
                lang = Lang.EN
            )
        }
    }

    @Test
    fun `Event throws IllegalArgumentException when rawId is negative`() {
        assertThrows<IllegalArgumentException> {
            Event(
                id = 0L,
                rawId = -1L,
                ts = sampleTime,
                title = "Title",
                text = "Text",
                normalizedText = "Text",
                lang = Lang.EN
            )
        }
    }

    @Test
    fun `Event throws IllegalArgumentException when isUpdateOf is less than or equal to 0`() {
        assertThrows<IllegalArgumentException> {
            Event(
                id = 0L,
                rawId = 1L,
                ts = sampleTime,
                title = "Title",
                text = "Text",
                normalizedText = "Text",
                lang = Lang.EN,
                isUpdateOf = 0L
            )
        }

        assertThrows<IllegalArgumentException> {
            Event(
                id = 0L,
                rawId = 1L,
                ts = sampleTime,
                title = "Title",
                text = "Text",
                normalizedText = "Text",
                lang = Lang.EN,
                isUpdateOf = -10L
            )
        }
    }

    @Test
    fun `Event defaults classification and pipelineRevisionId correctly`() {
        val event = Event(
            id = 1L,
            rawId = 2L,
            ts = sampleTime,
            title = "Title",
            text = "Text",
            normalizedText = "Text",
            lang = Lang.EN
        )
        assertEquals(com.example.npc.core.model.classify.Category.UNCLASSIFIED, event.category)
        assertEquals(0.0f, event.confidence)
        assertEquals(com.example.npc.core.model.classify.Engine.NONE, event.engineUsed)
        assertEquals(false, event.isUserCorrected)
        assertNull(event.contentFingerprint)
        assertNull(event.pipelineRevisionId)
    }

    @Test
    fun `Event throws IllegalArgumentException when confidence is out of 0 to 1 range`() {
        assertThrows<IllegalArgumentException> {
            Event(
                id = 1L,
                rawId = 2L,
                ts = sampleTime,
                title = "Title",
                text = "Text",
                normalizedText = "Text",
                lang = Lang.EN,
                confidence = -0.1f
            )
        }

        assertThrows<IllegalArgumentException> {
            Event(
                id = 1L,
                rawId = 2L,
                ts = sampleTime,
                title = "Title",
                text = "Text",
                normalizedText = "Text",
                lang = Lang.EN,
                confidence = 1.01f
            )
        }
    }

    // --- SourceHealth Tests ---

    @Test
    fun `SourceHealth creates valid instance with valid metrics`() {
        val health = assertDoesNotThrow {
            SourceHealth(
                source = sampleSource,
                lastEventAt = sampleTime,
                events24h = 1500,
                lastError = null,
                queueDepth = 3
            )
        }

        assertEquals(sampleSource, health.source)
        assertEquals(sampleTime, health.lastEventAt)
        assertEquals(1500, health.events24h)
        assertNull(health.lastError)
        assertEquals(3, health.queueDepth)

        val healthWithError = assertDoesNotThrow {
            SourceHealth(
                source = sampleSource,
                lastEventAt = null,
                events24h = 0,
                lastError = "Connection timeout",
                queueDepth = 0
            )
        }
        assertEquals(0, healthWithError.events24h)
        assertEquals(0, healthWithError.queueDepth)
        assertEquals("Connection timeout", healthWithError.lastError)
        assertNull(healthWithError.lastEventAt)
    }

    @Test
    fun `SourceHealth throws IllegalArgumentException when events24h is negative`() {
        assertThrows<IllegalArgumentException> {
            SourceHealth(
                source = sampleSource,
                lastEventAt = sampleTime,
                events24h = -1,
                lastError = null,
                queueDepth = 0
            )
        }
    }

    @Test
    fun `SourceHealth throws IllegalArgumentException when queueDepth is negative`() {
        assertThrows<IllegalArgumentException> {
            SourceHealth(
                source = sampleSource,
                lastEventAt = sampleTime,
                events24h = 0,
                lastError = null,
                queueDepth = -1
            )
        }
    }

    @Test
    fun `SourceHealth throws IllegalArgumentException when lastError is blank or exceeds 1000 characters`() {
        assertThrows<IllegalArgumentException> {
            SourceHealth(
                source = sampleSource,
                lastEventAt = sampleTime,
                events24h = 0,
                lastError = "   ",
                queueDepth = 0
            )
        }

        val over1000Chars = "e".repeat(1001)
        assertThrows<IllegalArgumentException> {
            SourceHealth(
                source = sampleSource,
                lastEventAt = sampleTime,
                events24h = 0,
                lastError = over1000Chars,
                queueDepth = 0
            )
        }

        val exactly1000Chars = "e".repeat(1000)
        assertDoesNotThrow {
            SourceHealth(
                source = sampleSource,
                lastEventAt = sampleTime,
                events24h = 0,
                lastError = exactly1000Chars,
                queueDepth = 0
            )
        }
    }
}
