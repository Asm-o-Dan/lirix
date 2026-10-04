package com.eventengine.app

import com.eventengine.app.domain.Category
import com.eventengine.app.domain.Event
import com.eventengine.app.domain.EventSource
import com.eventengine.app.domain.EventType
import com.eventengine.app.storage.toDomain
import com.eventengine.app.storage.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EventModelTest {

    @Test
    fun testDomainToEntityAndBackMapping() {
        val original = Event(
            id = "test-uuid-12345",
            timestamp = 1700000000000L,
            source = EventSource.NOTIFICATION,
            sourcePackage = "org.telegram.messenger",
            appName = "Telegram",
            title = "Pavel Durov",
            text = "Hello Android Event Engine",
            subText = "Chat",
            eventType = EventType.MESSAGING_CHAT,
            category = Category.MESSAGE,
            confidence = 0.98f,
            isOngoing = false,
            contentFingerprint = "abcd1234efgh5678",
            rawPayload = "sample",
            normalizedText = "Pavel Durov — Hello Android Event Engine",
            structuredData = "{}"
        )

        val entity = original.toEntity()
        assertEquals(original.id, entity.id)
        assertEquals(original.source, entity.source)
        assertEquals(original.sourcePackage, entity.sourcePackage)
        assertEquals(original.title, entity.title)
        assertEquals(original.category, entity.category)
        assertEquals(original.confidence, entity.confidence, 0.001f)

        val restored = entity.toDomain()
        assertEquals(original, restored)
    }

    @Test
    fun testDefaultEventGeneration() {
        val event = Event(
            source = EventSource.MEDIA_SESSION,
            sourcePackage = "com.spotify.music",
            appName = "Spotify",
            title = "Starboy",
            text = "The Weeknd"
        )

        assertNotNull(event.id)
        assertEquals(EventSource.MEDIA_SESSION, event.source)
        assertEquals("Spotify", event.appName)
    }
}
