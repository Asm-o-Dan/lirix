package com.lirix.app.desktop

import com.lirix.app.desktop.ui.components.DesktopSyncOffsetPreferences
import com.lirix.app.desktop.ui.components.desktopPlaybackPositions
import com.lirix.app.ingestion.LivePlaybackSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class DesktopPlaybackClockTest {
    private fun snapshot(position: Long = 900, playing: Boolean = true, duration: Long = 10000) = LivePlaybackSnapshot(
        packageName = "test", title = "Song", artist = "Artist", isPlaying = playing,
        basePositionMs = position, lastPositionUpdateTimeMs = 1000, durationMs = duration)

    @Test fun `one snapshot advances across lyric boundary without another ingress event`() = runBlocking {
        var now = 1000L
        val positions = withTimeout(2000) {
            desktopPlaybackPositions(snapshot(), tickMs = 1, nowMs = { now })
                .onEach { now += 100 }.take(3).toList()
        }
        assertEquals(listOf(900L, 1000L, 1100L), positions)
    }

    @Test fun `pause freezes and null terminates while seek and resume use new baseline`() = runBlocking {
        assertEquals(listOf(400L), desktopPlaybackPositions(snapshot(400, false), nowMs = { 9000 }).toList())
        assertEquals(listOf(0L), desktopPlaybackPositions(null, nowMs = { 9000 }).toList())
        assertEquals(400L, desktopPlaybackPositions(snapshot(400), nowMs = { 1000 }).first())
        assertEquals(7000L, desktopPlaybackPositions(snapshot(7000), nowMs = { 1000 }).first())
        assertEquals(7200L, desktopPlaybackPositions(snapshot(7000), nowMs = { 1200 }).first())
        assertEquals(10000L, desktopPlaybackPositions(snapshot(9900), nowMs = { 1500 }).first())
        assertEquals(1400L, desktopPlaybackPositions(snapshot(900, duration = 0), nowMs = { 1500 }).first())
    }

    @Test fun `cancelling a clock collector stops sampling its snapshot`() = runBlocking {
        var reads = 0
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            desktopPlaybackPositions(snapshot(), tickMs = 5, nowMs = { reads++; 1000L }).collect()
        }
        assertEquals(1, reads)
        collector.cancelAndJoin()
        val readsAtCancellation = reads
        delay(25)
        assertEquals(readsAtCancellation, reads)
    }

    @Test fun `offset updates notify existing subscribers while playback is paused`() = runBlocking {
        val store = DesktopSyncOffsetPreferences(MemoryPreferences())
        val original = store.getOffsetMs()
        try {
            val observed = mutableListOf<Long>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                store.offsetMs.take(3).toList(observed)
            }
            store.setOffsetMs(if (original == 125L) 126L else 125L)
            yield()
            store.setOffsetMs(-375L)
            withTimeout(2000) { collector.join() }
            assertEquals(original, observed.first())
            assertEquals(-375L, observed.last())
            assertEquals(3, observed.size)
            assertEquals(-375L, store.getOffsetMs())
        } finally { store.setOffsetMs(original) }
    }
}
