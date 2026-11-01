package com.lirix.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.MusicFeatureEngine
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.ingestion.MediaSessionCollector
import com.lirix.app.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.mockito.Mockito.*
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the production observation queue, Room transactions and recorder together. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class MediaSessionAccountingTest {
    private lateinit var db: AppDatabase
    private lateinit var collector: MediaSessionCollector
    private lateinit var queue: Channel<Any>
    private val stateCommits = AtomicInteger()
    private var previousDatabase: Any? = null
    private val instance = AppDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }
    private val base = 1_700_000_000_000L
    private val key = MusicFeatureEngine.computeTrackKey("Song", "Artist", "")

    @Suppress("UNCHECKED_CAST")
    @Before fun setup() = runBlocking<Unit> {
        MediaSessionCollector.resetInstanceForTesting()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.musicDao().insertOrUpdateTrack(TrackEntity(key, "Song", "Artist", sourcePackage = "player", playCount = 0, plainLyrics = "Cached"))
        db.lyricsDao().saveLyrics(LyricsCacheEntity(key, "Cached", null, null, null, "USER"))
        previousDatabase = instance.get(null)
        val actual = db.musicDao()
        val countedDao = object : MusicDao by actual {
            override suspend fun commitPlaybackState(track: TrackEntity, session: ListeningSessionEntity, completed: ListeningSessionEntity?): Long {
                stateCommits.incrementAndGet()
                return actual.commitPlaybackState(track, session, completed)
            }
        }
        val facade = mock(AppDatabase::class.java)
        `when`(facade.musicDao()).thenReturn(countedDao)
        `when`(facade.lyricsDao()).thenReturn(db.lyricsDao())
        `when`(facade.blacklistDao()).thenReturn(db.blacklistDao())
        instance.set(null, facade)
        collector = MediaSessionCollector.getInstance(context)
        queue = MediaSessionCollector::class.java.getDeclaredField("listeningObservations")
            .apply { isAccessible = true }.get(collector) as Channel<Any>
    }

    @After fun cleanup() = runBlocking<Unit> {
        val scope = MediaSessionCollector::class.java.getDeclaredField("scope").apply { isAccessible = true }
            .get(collector) as CoroutineScope
        val job = scope.coroutineContext[Job]
        MediaSessionCollector.resetInstanceForTesting()
        job?.join()
        db.close()
        instance.set(null, previousDatabase)
    }

    private fun enqueue(at: Long, state: String, position: Long = 480_000, progress: Boolean = false, removed: Boolean = false) {
        val snapshot = if (removed) null else LivePlaybackSnapshot("player", "Song", "Artist",
            isPlaying = state == "PLAYING", basePositionMs = position, lastPositionUpdateTimeMs = at)
        val type = MediaSessionCollector::class.java.declaredClasses.first { it.simpleName == "ListeningObservation" }
        val constructor = type.declaredConstructors.first { it.parameterCount == 5 }.apply { isAccessible = true }
        assertTrue(queue.trySend(constructor.newInstance(snapshot, state, base + at, at, progress)).isSuccess)
    }

    @Test fun pausedMetadataAndHeartbeatPositionsDoNotAccrueOrDoubleCredit() = runBlocking<Unit> {
        enqueue(0, "PLAYING")
        enqueue(10_000, "PLAYING", position = 3_600_000, progress = true)
        enqueue(10_000, "PAUSED")
        enqueue(50_000, "PAUSED")
        enqueue(90_000, "PLAYING")
        enqueue(100_000, "PLAYING", position = 1_000, progress = true)
        enqueue(100_000, "STOPPED")
        val rows = withTimeout(10_000) { db.musicDao().observeAllSessions().first { it.singleOrNull()?.isCompleted == true } }
        assertEquals(20_000L, rows.single().durationMs)
        assertEquals(20_000L, db.musicDao().getTrackByKey(key)!!.totalDurationMs)
        assertEquals(1, db.musicDao().getTrackByKey(key)!!.playCount)
    }

    @Test fun sourceRemovalFinalizesOnceAndCannotCreditTheOfflineGap() = runBlocking<Unit> {
        enqueue(0, "PLAYING")
        enqueue(10_000, "PLAYING", progress = true)
        enqueue(20_000, "STOPPED", removed = true)
        enqueue(100_000, "PLAYING")
        enqueue(110_000, "STOPPED")
        val rows = withTimeout(10_000) { db.musicDao().observeAllSessions().first { it.size == 2 && it.all { row -> row.isCompleted } } }
        assertEquals(listOf(20_000L, 10_000L), rows.map { it.durationMs })
        assertEquals(30_000L, db.musicDao().getTrackByKey(key)!!.totalDurationMs)
    }

    @Test fun positionCallbacksAreCoalescedInsteadOfWritingEveryPositionToRoom() = runBlocking<Unit> {
        enqueue(0, "PLAYING")
        repeat(100) { enqueue((it + 1) * 10L, "PLAYING", position = 480_000 + it.toLong()) }
        enqueue(10_000, "PLAYING", progress = true)
        enqueue(10_000, "STOPPED")
        val rows = withTimeout(10_000) { db.musicDao().observeAllSessions().first { it.singleOrNull()?.isCompleted == true } }
        assertEquals(10_000L, rows.single().durationMs)
        assertEquals(2, stateCommits.get()) // PLAYING and STOPPED; the hundred position updates do not write.
    }

    @Suppress("UNCHECKED_CAST")
    @Test fun repeatedPlayingCallbacksDoNotResetTheHeartbeatDeadline() {
        val playback = MediaSessionCollector::class.java.getDeclaredField("_livePlaybackFlow")
            .apply { isAccessible = true }.get(null) as kotlinx.coroutines.flow.MutableStateFlow<LivePlaybackSnapshot?>
        playback.value = LivePlaybackSnapshot("player", "Song", "Artist", isPlaying = true,
            basePositionMs = 0, lastPositionUpdateTimeMs = 0)
        val sync = MediaSessionCollector::class.java.getDeclaredMethod("syncHeartbeat", java.lang.Boolean.TYPE, String::class.java)
            .apply { isAccessible = true }
        val field = MediaSessionCollector::class.java.getDeclaredField("heartbeatJob").apply { isAccessible = true }
        try {
            sync.invoke(collector, true, "player")
            val initial = field.get(collector) as Job
            repeat(100) { sync.invoke(collector, true, "player") }
            assertSame(initial, field.get(collector))
            sync.invoke(collector, false, "player")
            assertTrue(initial.isCancelled)
            assertNull(field.get(collector))
        } finally { playback.value = null }
    }
}
