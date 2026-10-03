package com.example.npc.core.model.normalize

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Lang
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class EventNormalizerNormalizeTest {

    private val sampleSource = SourceId("notification")
    private val sampleHash = DeduplicationKey("b".repeat(64))
    private val sampleTime = Instant.parse("2026-09-26T06:00:00Z")

    @Test
    fun `normalize maps rawEvent fields, cleans text, and detects Russian language`() {
        val rawEvent = RawEvent(
            id = 10L,
            seq = 1L,
            source = sampleSource,
            packageName = "ru.sberbankmobile",
            receivedAt = sampleTime,
            payloadJson = "{}",
            hash = sampleHash
        )
        val rawTitle = "СберБанк"
        val rawText = "  Поступление  зарплаты: 50000р\u200B   \r\n\r\n\r\nНа карту *1234  "

        val event = EventNormalizer.normalize(
            rawEvent = rawEvent,
            title = rawTitle,
            text = rawText
        )

        assertEquals(0L, event.id)
        assertEquals(10L, event.rawId)
        assertEquals(sampleTime, event.ts)
        assertEquals(rawTitle, event.title)
        assertEquals(rawText, event.text)
        assertEquals("Поступление зарплаты: 50000р\n\nНа карту *1234", event.normalizedText)
        assertEquals(Lang.RU, event.lang)
        assertNull(event.threadKey)
        assertNull(event.isUpdateOf)
    }

    @Test
    fun `normalize maps threadKey and detects English language`() {
        val rawEvent = RawEvent(
            id = 25L,
            seq = 2L,
            source = sampleSource,
            packageName = "com.slack",
            receivedAt = sampleTime,
            payloadJson = "{}",
            hash = sampleHash
        )
        val threadKey = ThreadKey("channel_alerts_123")
        val rawTitle = "CI/CD Pipeline"
        val rawText = "Build #42 passed successfully.\nAll 150 tests green."

        val event = EventNormalizer.normalize(
            rawEvent = rawEvent,
            title = rawTitle,
            text = rawText,
            threadKey = threadKey,
            isUpdateOf = null
        )

        assertEquals(0L, event.id)
        assertEquals(25L, event.rawId)
        assertEquals(sampleTime, event.ts)
        assertEquals(rawTitle, event.title)
        assertEquals(rawText, event.text)
        assertEquals(rawText, event.normalizedText)
        assertEquals(Lang.EN, event.lang)
        assertEquals(threadKey, event.threadKey)
        assertNull(event.isUpdateOf)
    }

    @Test
    fun `normalize preserves isUpdateOf link when updating an existing event`() {
        val rawEvent = RawEvent(
            id = 30L,
            seq = 3L,
            source = sampleSource,
            packageName = "org.telegram.messenger",
            receivedAt = sampleTime,
            payloadJson = "{}",
            hash = sampleHash
        )

        val event = EventNormalizer.normalize(
            rawEvent = rawEvent,
            title = "Edited Message",
            text = "Updated message content",
            threadKey = ThreadKey("chat_42"),
            isUpdateOf = 15L
        )

        assertEquals(0L, event.id)
        assertEquals(30L, event.rawId)
        assertEquals(15L, event.isUpdateOf)
        assertEquals(ThreadKey("chat_42"), event.threadKey)
    }

    @Test
    fun `normalize handles empty text returning empty normalizedText and Lang UNK`() {
        val rawEvent = RawEvent(
            id = 40L,
            seq = 4L,
            source = sampleSource,
            packageName = "com.google.android.calendar",
            receivedAt = sampleTime,
            payloadJson = "{}",
            hash = sampleHash
        )

        val event = EventNormalizer.normalize(
            rawEvent = rawEvent,
            title = "Standup",
            text = "   \t\n  "
        )

        assertEquals(0L, event.id)
        assertEquals(40L, event.rawId)
        assertEquals("Standup", event.title)
        assertEquals("   \t\n  ", event.text)
        assertEquals("", event.normalizedText)
        assertEquals(Lang.UNK, event.lang)
    }
}
