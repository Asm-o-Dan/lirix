package com.lirix.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.MusicFeatureEngine
import com.lirix.app.feature.lyrics.AutomaticLookupCoordinator
import com.lirix.app.feature.lyrics.LookupPhase
import com.lirix.app.feature.lyrics.LookupTarget
import com.lirix.app.feature.lyrics.NextTrackWarmCache
import com.lirix.app.feature.lyrics.PlayerQueue
import com.lirix.app.feature.lyrics.QueueEntry
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.AutomaticLookupCandidate
import com.lirix.app.storage.AutomaticLookupField
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.blacklist.BlacklistRuleType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Preloading lyrics for the upcoming track from the player's queue (owner request 2026-11-14).
 * Players differ: some publish a queue with a current-item id, some a queue without it, some
 * nothing. Song texts here are synthetic.
 */
@RunWith(AndroidJUnit4::class)
class NextTrackPrefetchTest {
    private lateinit var db: AppDatabase
    private lateinit var engine: MusicFeatureEngine

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        engine = MusicFeatureEngine(db.musicDao(), db.lyricsDao(), blacklistDao = db.blacklistDao())
        NextTrackWarmCache.shared.clear()
    }

    @After
    fun cleanup() {
        NextTrackWarmCache.shared.clear()
        db.close()
    }

    private val queue = listOf(
        QueueEntry(11, "First Light", "Echo Lane"),
        QueueEntry(12, "Rain", "Echo Lane"),
        QueueEntry(13, "Paper Skies", "Echo Lane • Station Stairs")
    )

    private fun words(text: String, source: String = "lrclib") = AutomaticLookupCandidate(plain = AutomaticLookupField(text, source))

    // --- Reading the queue ---

    @Test
    fun next_item_follows_the_active_queue_id() {
        assertEquals(queue[2], PlayerQueue.next(queue, 12, "Rain", "Echo Lane"))
        assertEquals(queue[1], PlayerQueue.next(queue, 11, "First Light", "Echo Lane"))
    }

    @Test
    fun the_last_item_and_players_without_a_queue_give_nothing() {
        assertNull(PlayerQueue.next(queue, 13, "Paper Skies", "Echo Lane"))
        assertNull(PlayerQueue.next(null, 12, "Rain", "Echo Lane"))
        assertNull(PlayerQueue.next(emptyList(), 12, "Rain", "Echo Lane"))
    }

    @Test
    fun without_an_active_id_the_current_item_is_found_by_text_only_when_unambiguous() {
        assertEquals(queue[2], PlayerQueue.next(queue, PlayerQueue.UNKNOWN_QUEUE_ID, "rain", "Echo Lane"))
        assertEquals(queue[2], PlayerQueue.next(queue, null, "Rain", ""))
        assertNull("Current track is not in the queue", PlayerQueue.next(queue, null, "Other Song", "Echo Lane"))
        assertNull("An id the queue does not contain and no text match", PlayerQueue.next(queue, 99, "Other Song", "Echo Lane"))

        val twice = queue + QueueEntry(14, "Rain", "Echo Lane") + QueueEntry(15, "Coda", "Echo Lane")
        assertNull("The same song twice: position unknown, no guess", PlayerQueue.next(twice, null, "Rain", "Echo Lane"))
        assertEquals("The id still resolves it", QueueEntry(15, "Coda", "Echo Lane"), PlayerQueue.next(twice, 14, "Rain", "Echo Lane"))
    }

    @Test
    fun a_repeated_or_untitled_next_item_is_not_preloaded() {
        val repeat = listOf(QueueEntry(1, "Rain", "Echo Lane"), QueueEntry(2, "Rain", "Echo Lane"))
        assertNull(PlayerQueue.next(repeat, 1, "Rain", "Echo Lane"))
        val untitled = listOf(QueueEntry(1, "Rain", "Echo Lane"), QueueEntry(2, "", "Echo Lane"))
        assertNull(PlayerQueue.next(untitled, 1, "Rain", "Echo Lane"))
    }

    // --- Deciding whether to load ---

    @Test
    fun upcoming_track_is_looked_up_once_and_kept_out_of_the_library() = runBlocking {
        var lookups = 0
        val acquire: suspend (TrackEntity) -> AutomaticLookupCandidate = { probe ->
            lookups++
            assertEquals("Paper Skies", probe.title)
            assertEquals("Echo Lane", probe.artist)
            words("Grey water folding\nunder paper skies")
        }

        assertTrue(engine.prefetchUpcoming("Paper Skies", "Echo Lane", "player", networkAllowed = true, acquire = acquire))
        assertFalse("Second announcement of the same track", engine.prefetchUpcoming("Paper Skies", "Echo Lane", "player", networkAllowed = true, acquire = acquire))
        assertEquals(1, lookups)
        assertTrue("A song that has not played gets no library row", db.musicDao().getAllTracks().isEmpty())
    }

    @Test
    fun nothing_is_requested_without_network_for_unidentifiable_or_blacklisted_tracks() = runBlocking {
        db.blacklistDao().insertRule(
            BlacklistRuleEntity(id = "voice", ruleType = BlacklistRuleType.ARTIST, pattern = "Voice Messages",
                matchMode = BlacklistMatchMode.EXACT, isEnabled = true)
        )
        var lookups = 0
        val acquire: suspend (TrackEntity) -> AutomaticLookupCandidate = { lookups++; words("x") }

        assertFalse(engine.prefetchUpcoming("Rain", "Echo Lane", "player", networkAllowed = false, acquire = acquire))
        assertFalse(engine.prefetchUpcoming("09", "Unknown Artist", "player", networkAllowed = true, acquire = acquire))
        assertFalse(engine.prefetchUpcoming("Rain", "", "player", networkAllowed = true, acquire = acquire))
        assertFalse(engine.prefetchUpcoming("Audio 14:02", "Voice Messages", "messenger", networkAllowed = true, acquire = acquire))
        assertEquals(0, lookups)
    }

    @Test
    fun a_track_that_already_has_words_is_not_looked_up_again() = runBlocking {
        val played = engine.recordPlaybackSignal("Rain", "Echo Lane", album = "Station Stairs", sourcePackage = "player")!!
        db.musicDao().updateLyrics(played.trackKey, "words the user already has", null)
        var lookups = 0

        assertFalse(engine.prefetchUpcoming("rain", "echo lane", "other.player", networkAllowed = true) { lookups++; words("x") })
        assertEquals(0, lookups)
    }

    // --- Using the stored result ---

    @Test
    fun stored_lookup_is_used_when_the_track_starts_and_only_once() = runBlocking {
        engine.prefetchUpcoming("Paper Skies", "Echo Lane • Station Stairs", "player", networkAllowed = true) {
            words("Grey water folding\nunder paper skies")
        }
        val started = engine.recordPlaybackSignal("Paper Skies", "Echo Lane", album = "Station Stairs", sourcePackage = "player")!!

        // The regular automatic lookup: with the stored result it needs no network at all.
        val phase = AutomaticLookupCoordinator.get(db).lookup(started.trackKey, LookupTarget.LYRICS, usable = true)

        assertEquals(LookupPhase.Available, phase)
        assertEquals("Grey water folding\nunder paper skies", db.lyricsDao().getLyrics(started.trackKey)?.plainLyrics)
        assertNull("Consumed", NextTrackWarmCache.shared.take("Paper Skies", "Echo Lane", emptySet()))
    }

    @Test
    fun stored_lookup_from_a_rejected_source_or_for_another_artist_is_not_used() {
        val cache = NextTrackWarmCache()
        cache.put("Rain", "Echo Lane", words("synthetic words", source = "rule:site"))
        assertNull("Another artist's song with the same title", cache.take("Rain", "Northern Choir", emptySet()))
        assertNull("The user rejected this source for the track", cache.take("Rain", "Echo Lane", setOf("rule:site")))
        assertNull("A rejected entry is dropped, not offered again", cache.take("Rain", "Echo Lane", emptySet()))
    }

    @Test
    fun cache_is_small_expires_and_ignores_empty_results() {
        var now = 0L
        val cache = NextTrackWarmCache(maxEntries = 2, ttlMs = 1_000L, clock = { now })
        cache.put("Empty", "Echo Lane", AutomaticLookupCandidate())
        assertFalse(cache.contains("Empty", "Echo Lane"))

        cache.put("A", "Echo Lane", words("a"))
        cache.put("B", "Echo Lane", words("b"))
        cache.put("C", "Echo Lane", words("c"))
        assertFalse("Oldest entry evicted", cache.contains("A", "Echo Lane"))
        assertTrue(cache.contains("B", "Echo Lane"))

        now = 1_000L
        assertFalse("Expired", cache.contains("B", "Echo Lane"))
        assertNull(cache.take("C", "Echo Lane", emptySet()))
    }
}
