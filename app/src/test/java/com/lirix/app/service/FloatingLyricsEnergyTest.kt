package com.lirix.app.service

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.ingestion.MediaSessionCollector
import com.lirix.app.ingestion.SyncOffsetStore
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.storage.LyricsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class FloatingLyricsEnergyTest {
    private lateinit var service: FloatingLyricsService
    private lateinit var lyrics: LyricsDao
    private lateinit var interactive: MutableStateFlow<Boolean>
    private lateinit var playback: MutableStateFlow<LivePlaybackSnapshot?>
    private lateinit var dispatcher: TestDispatcher
    private var previousDatabase: Any? = null
    private val databaseField = AppDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }

    @Suppress("UNCHECKED_CAST")
    @Before fun setup() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("media_sync_offsets", Context.MODE_PRIVATE).edit().clear().commit()
        lyrics = mock(LyricsDao::class.java)
        val database = mock(AppDatabase::class.java)
        `when`(database.lyricsDao()).thenReturn(lyrics)
        previousDatabase = databaseField.get(null)
        databaseField.set(null, database)
        // Attach the Service context without creating its WindowManager overlay or foreground notification.
        service = Robolectric.buildService(FloatingLyricsService::class.java).get()
        FloatingLyricsService::class.java.getDeclaredField("syncOffsetStore").apply { isAccessible = true }
            .set(service, SyncOffsetStore(context))
        interactive = FloatingLyricsService::class.java.getDeclaredField("screenInteractive")
            .apply { isAccessible = true }.get(service) as MutableStateFlow<Boolean>
        playback = MediaSessionCollector::class.java.getDeclaredField("_livePlaybackFlow")
            .apply { isAccessible = true }.get(null) as MutableStateFlow<LivePlaybackSnapshot?>
        playback.value = null
        FloatingLyricsService::class.java.getDeclaredMethod("startPlaybackSync")
            .apply { isAccessible = true }.invoke(service)
    }

    @After fun cleanup() {
        service.onDestroy()
        dispatcher.scheduler.runCurrent()
        playback.value = null
        databaseField.set(null, previousDatabase)
        Dispatchers.resetMain()
    }

    private fun snapshot(playing: Boolean = true, title: String = "Song") = LivePlaybackSnapshot(
        "player", title, "Artist", isPlaying = playing, basePositionMs = 1_000L,
        lastPositionUpdateTimeMs = SystemClock.elapsedRealtime(), durationMs = 100_000L
    )

    private suspend fun cacheLyrics(nextTime: String = "00:10.00") {
        `when`(lyrics.getLyrics(anyString())).thenReturn(
            LyricsCacheEntity("cached", "First\nSecond", "[00:00.00]First\n[$nextTime]Second", null, null, "TEST")
        )
    }

    @Test fun screenOffSuspendsProcessingAndScreenOnResynchronizesImmediately() = runTest {
        try {
            cacheLyrics()
            playback.value = snapshot()
            runCurrent()
            assertEquals("", service.overlayState.value.title)
            verify(lyrics, never()).getLyrics(anyString())

            interactive.value = true
            runCurrent()
            assertEquals("Song", service.overlayState.value.title)
            assertEquals("First", service.overlayState.value.activeLineText)

            interactive.value = false
            runCurrent()
            val before = service.overlayState.value
            playback.value = snapshot(title = "Next song")
            advanceTimeBy(5_000)
            runCurrent()
            assertSame(before, service.overlayState.value)

            interactive.value = true
            runCurrent()
            assertEquals("Next song", service.overlayState.value.title)
            verify(lyrics, times(2)).getLyrics(anyString())
        } finally {
            service.onDestroy()
            runCurrent()
        }
    }

    @Test fun invisiblePositionDoesNotPublishStateAndPauseHasNoRecurringClock() = runTest {
        try {
            cacheLyrics()
            interactive.value = true
            playback.value = snapshot()
            runCurrent()
            val before = service.overlayState.value
            // Same visible line with a different media anchor must not publish invisible position changes.
            playback.value = snapshot().copy(basePositionMs = 2_000L)
            advanceTimeBy(500)
            runCurrent()
            assertSame(before, service.overlayState.value)

            playback.value = snapshot(playing = false)
            runCurrent()
            assertFalse(service.overlayState.value.isPlaying)
            // This completes only if the paused collector has no periodic coroutine work.
            advanceUntilIdle()
            verify(lyrics, times(1)).getLyrics(anyString())
        } finally {
            service.onDestroy()
            runCurrent()
        }
    }

    @Test fun calibrationChangeUpdatesPausedLyricsWithoutPolling() = runTest {
        try {
            cacheLyrics(nextTime = "00:02.00")
            interactive.value = true
            playback.value = snapshot(playing = false)
            runCurrent()
            assertEquals("First", service.overlayState.value.activeLineText)
            val context = ApplicationProvider.getApplicationContext<Context>()
            SyncOffsetStore(context).setGlobalOffset(2_000L)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            runCurrent()
            assertEquals("Second", service.overlayState.value.activeLineText)
            advanceUntilIdle()
        } finally {
            service.onDestroy()
            runCurrent()
        }
    }
}
