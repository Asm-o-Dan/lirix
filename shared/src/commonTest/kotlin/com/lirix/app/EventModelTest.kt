package com.lirix.app

import com.lirix.app.domain.Category
import com.lirix.app.domain.Event
import com.lirix.app.domain.EventSource
import com.lirix.app.domain.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class EventModelTest {

    @Test
    fun testDefaultEventGeneration() {
        val event = Event(
            source = EventSource.MEDIA_SESSION,
            sourcePackage = "com.spotify.music",
            appName = "Spotify",
            title = "Starboy",
            text = "The Weeknd",
            category = Category.MUSIC,
            eventType = EventType.MEDIA_PLAYBACK
        )

        assertNotNull(event.id)
        assertEquals(EventSource.MEDIA_SESSION, event.source)
        assertEquals("Spotify", event.appName)
        assertEquals(Category.MUSIC, event.category)
        assertEquals(EventType.MEDIA_PLAYBACK, event.eventType)
    }
}
