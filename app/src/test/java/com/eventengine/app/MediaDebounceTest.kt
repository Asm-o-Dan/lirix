package com.eventengine.app

import com.eventengine.app.ingestion.MediaDebounceFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaDebounceTest {

    @Test
    fun testDebounceCollapsesBufferingIntoPlaying() = runTest {
        val emittedEvents = mutableListOf<String>()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val filter = MediaDebounceFilter(
            scope = testScope,
            debounceDelayMs = 600L
        ) { pkg, title, artist, album, stateName ->
            emittedEvents.add(stateName)
        }

        // 1. BUFFERING arrives at t = 0ms
        filter.onStateChange(
            packageName = "com.spotify.music",
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            stateName = "BUFFERING"
        )

        assertTrue(filter.hasPendingDebounce("com.spotify.music", "Starboy", "The Weeknd"))
        assertEquals(0, emittedEvents.size)

        // Advance 300ms (still within 600ms debounce window)
        testScheduler.advanceTimeBy(300)
        assertEquals(0, emittedEvents.size)

        // 2. PLAYING arrives at t = 300ms
        filter.onStateChange(
            packageName = "com.spotify.music",
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            stateName = "PLAYING"
        )

        testScheduler.advanceUntilIdle()

        // BUFFERING must have been cancelled; only PLAYING emitted
        assertFalse(filter.hasPendingDebounce("com.spotify.music", "Starboy", "The Weeknd"))
        assertEquals(1, emittedEvents.size)
        assertEquals("PLAYING", emittedEvents[0])
    }

    @Test
    fun testIsolatedBufferingEmittedAfterDelay() = runTest {
        val emittedEvents = mutableListOf<String>()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val filter = MediaDebounceFilter(
            scope = testScope,
            debounceDelayMs = 600L
        ) { pkg, title, artist, album, stateName ->
            emittedEvents.add(stateName)
        }

        // BUFFERING arrives at t = 0ms
        filter.onStateChange(
            packageName = "ru.yandex.music",
            title = "Track A",
            artist = "Artist B",
            album = "Album C",
            stateName = "BUFFERING"
        )

        // At t = 400ms: still waiting
        testScheduler.advanceTimeBy(400)
        assertEquals(0, emittedEvents.size)

        // At t = 650ms: 600ms timeout passed -> BUFFERING emitted
        testScheduler.advanceTimeBy(250)
        assertEquals(1, emittedEvents.size)
        assertEquals("BUFFERING", emittedEvents[0])
    }

    @Test
    fun testPausedEmittedImmediately() = runTest {
        val emittedEvents = mutableListOf<String>()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val filter = MediaDebounceFilter(
            scope = testScope,
            debounceDelayMs = 600L
        ) { pkg, title, artist, album, stateName ->
            emittedEvents.add(stateName)
        }

        filter.onStateChange(
            packageName = "com.spotify.music",
            title = "Track A",
            artist = "Artist B",
            album = null,
            stateName = "PAUSED"
        )

        testScheduler.advanceUntilIdle()

        assertEquals(1, emittedEvents.size)
        assertEquals("PAUSED", emittedEvents[0])
    }
}
