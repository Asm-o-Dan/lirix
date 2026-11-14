package com.lirix.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.lyrics.AutomaticLookupCoordinator
import com.lirix.app.feature.lyrics.AutomaticLookupStore
import com.lirix.app.feature.lyrics.ContentRejector
import com.lirix.app.feature.lyrics.LookupTarget
import com.lirix.app.feature.lyrics.RejectionOutcome
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.AutomaticLookupCandidate
import com.lirix.app.storage.AutomaticLookupField
import com.lirix.app.storage.ContentRejection
import com.lirix.app.storage.LyricsAttribution
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.storage.TrackEntity
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
 * "Wrong lyrics" / "Wrong chords" (roadmap addendum Н1).
 *
 * Real Room database and the real automatic lookup coordinator; only the network sources are
 * replaced by a list of synthetic ones, asked in order like the production cascade.
 */
@RunWith(AndroidJUnit4::class)
class ContentRejectionTest {
    private lateinit var db: AppDatabase
    private lateinit var rejector: ContentRejector
    private val key = "song-key"

    /** Synthetic sources in cascade order: id -> (words, chords). */
    private var sources = listOf(
        "builtin:first" to ("first source words\nsecond line" to "Am C first"),
        "rule:second" to ("second source words\nsecond line" to "Dm G second")
    )
    private var networkFails = false

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        val coordinator = AutomaticLookupCoordinator(
            object : AutomaticLookupStore {
                override suspend fun track(key: String) = db.musicDao().getTrackByKey(key)
                override suspend fun cache(key: String) = db.lyricsDao().getLyrics(key)
                override suspend fun rejected(key: String) = db.lyricsDao().getRejectedSourceIds(key).toSet()
                override suspend fun merge(key: String, candidate: AutomaticLookupCandidate) = db.lyricsDao().fillMissingAutomaticLookup(key, candidate)
            },
            acquire = { _, target, rejected ->
                if (networkFails) throw java.io.IOException("offline")
                val chordRejected = ContentRejection.forChords(rejected)
                val chords = sources.firstOrNull { it.first !in chordRejected }?.let { AutomaticLookupField(it.second.second, it.first) }
                if (target == LookupTarget.CHORDS) AutomaticLookupCandidate(chords = chords)
                else AutomaticLookupCandidate(
                    plain = sources.firstOrNull { it.first !in rejected }?.let { AutomaticLookupField(it.second.first, it.first) },
                    chords = chords
                )
            }
        )
        rejector = ContentRejector(db.lyricsDao(), clock = { 1_000L }) { trackKey, target -> coordinator.lookup(trackKey, target, usable = true) }
        runBlocking {
            db.musicDao().insertOrUpdateTrack(TrackEntity(trackKey = key, title = "Rain", artist = "Echo Lane", sourcePackage = "player", userNotes = "my note"))
            // First automatic lookup, as when the track starts playing.
            coordinator.lookup(key, LookupTarget.LYRICS, usable = true)
        }
    }

    @After
    fun cleanup() = db.close()

    private suspend fun cache() = db.lyricsDao().getLyrics(key)!!
    private suspend fun rejections() = db.lyricsDao().getRejectedSourceIds(key).toSet()

    private suspend fun saveManual(provider: String, text: String = "words typed by the user") {
        db.lyricsDao().saveLyrics(cache().copy(plainLyrics = text, provider = provider, sourceAttributionJson = null))
        db.musicDao().updateLyrics(key, text, null)
    }

    @Test
    fun starting_point_is_the_first_source() = runBlocking {
        assertEquals("first source words\nsecond line", cache().plainLyrics)
        assertEquals("Am C first", cache().chordsAmDm)
    }

    @Test
    fun wrong_lyrics_moves_to_the_next_source_and_keeps_chords_and_notes() = runBlocking {
        val outcome = rejector.reject(key, chords = false)

        assertEquals("rule:second", (outcome as RejectionOutcome.Replaced).newSourceId)
        assertEquals("second source words\nsecond line", cache().plainLyrics)
        assertEquals("second source words\nsecond line", db.musicDao().getTrackByKey(key)!!.plainLyrics)
        assertEquals("Chords were not rejected", "Am C first", cache().chordsAmDm)
        assertEquals("my note", db.musicDao().getTrackByKey(key)!!.userNotes)
        assertEquals(setOf("builtin:first"), rejections())
    }

    @Test
    fun pressing_again_goes_further_and_then_reports_that_nothing_is_left() = runBlocking {
        rejector.reject(key, chords = false)
        val second = rejector.reject(key, chords = false)

        assertTrue("Expected no more sources, got $second", second is RejectionOutcome.NoMoreSources)
        assertNull(cache().plainLyrics)
        assertNull(db.musicDao().getTrackByKey(key)!!.plainLyrics)
        assertEquals(setOf("builtin:first", "rule:second"), rejections())
        assertEquals("Nothing left to reject", RejectionOutcome.NotAvailable, rejector.reject(key, chords = false))
    }

    @Test
    fun the_users_own_text_is_never_rejected_or_removed() = runBlocking {
        for (provider in listOf("USER", "USER:EDIT", "USER:TAP_SYNC", "USER:teach:amdm.ru")) {
            saveManual(provider)
            assertEquals(provider, RejectionOutcome.NotAvailable, rejector.reject(key, chords = false))
            assertEquals(provider, "words typed by the user", cache().plainLyrics)
            assertEquals(provider, "words typed by the user", db.musicDao().getTrackByKey(key)!!.plainLyrics)
        }
        assertTrue(rejections().isEmpty())
    }

    @Test
    fun a_personal_note_shown_as_text_is_not_a_source() = runBlocking {
        db.lyricsDao().saveLyrics(cache().copy(provider = "note:user", sourceAttributionJson = null))
        assertEquals(RejectionOutcome.NotAvailable, rejector.reject(key, chords = false))
        assertTrue(rejections().isEmpty())
    }

    @Test
    fun undo_brings_the_previous_text_back_and_allows_the_source_again() = runBlocking {
        val outcome = rejector.reject(key, chords = false) as RejectionOutcome.Replaced

        assertTrue(rejector.undo(outcome.rejected))

        assertEquals("first source words\nsecond line", cache().plainLyrics)
        assertEquals("first source words\nsecond line", db.musicDao().getTrackByKey(key)!!.plainLyrics)
        assertEquals("builtin:first", LyricsAttribution.decode(cache().sourceAttributionJson).lyrics?.sourceId)
        assertTrue(rejections().isEmpty())
    }

    @Test
    fun undo_after_nothing_was_found_also_restores_the_text() = runBlocking {
        sources = sources.take(1)
        val outcome = rejector.reject(key, chords = false) as RejectionOutcome.NoMoreSources
        assertNull(cache().plainLyrics)

        assertTrue(rejector.undo(outcome.rejected))
        assertEquals("first source words\nsecond line", cache().plainLyrics)
    }

    @Test
    fun undo_does_not_overwrite_text_the_user_entered_meanwhile() = runBlocking {
        val outcome = rejector.reject(key, chords = false) as RejectionOutcome.Replaced
        saveManual("USER:EDIT", "my own corrected words")

        assertFalse(rejector.undo(outcome.rejected))

        assertEquals("my own corrected words", cache().plainLyrics)
        assertEquals("my own corrected words", db.musicDao().getTrackByKey(key)!!.plainLyrics)
    }

    @Test
    fun wrong_chords_is_independent_of_the_words() = runBlocking {
        val outcome = rejector.reject(key, chords = true)

        assertEquals("rule:second", (outcome as RejectionOutcome.Replaced).newSourceId)
        assertEquals("Dm G second", cache().chordsAmDm)
        assertEquals("Words stay from the first source", "first source words\nsecond line", cache().plainLyrics)
        assertEquals("Stored separately from word rejections", setOf("chords:builtin:first"), rejections())
        assertEquals("builtin:first", ContentRejection.lyricsSource(cache()))

        // The words of that source are still acceptable: rejecting the second source's words
        // leaves nothing, but never brings the first source's chords back.
        val undone = rejector.undo(outcome.rejected)
        assertTrue(undone)
        assertEquals("Am C first", cache().chordsAmDm)
        assertTrue(rejections().isEmpty())
    }

    @Test
    fun chords_bound_by_the_user_are_not_rejectable_even_next_to_automatic_words() = runBlocking {
        val attribution = LyricsAttribution.decode(cache().sourceAttributionJson)
        db.lyricsDao().saveLyrics(cache().copy(chordsAmDm = "Em D mine",
            sourceAttributionJson = attribution.copy(chords = com.lirix.app.storage.LyricsSourceAttribution("USER:teach:amdm.ru")).encode()))

        assertEquals(RejectionOutcome.NotAvailable, rejector.reject(key, chords = true))
        assertEquals("Em D mine", cache().chordsAmDm)
    }

    @Test
    fun a_rejected_source_does_not_come_back_on_a_later_lookup() = runBlocking {
        sources = sources.take(1)
        rejector.reject(key, chords = false)
        // Later: network restored, preload, another screen — all go through the same merge.
        db.lyricsDao().fillMissingAutomaticLookup(key, AutomaticLookupCandidate(plain = AutomaticLookupField("first source words\nsecond line", "builtin:first")))
        assertNull(cache().plainLyrics)

        rejector.reject(key, chords = true)
        db.lyricsDao().fillMissingAutomaticLookup(key, AutomaticLookupCandidate(chords = AutomaticLookupField("Am C first", "builtin:first")))
        assertNull(cache().chordsAmDm)
    }

    @Test
    fun a_failed_search_keeps_the_rejection_and_can_be_undone() = runBlocking {
        networkFails = true
        val outcome = rejector.reject(key, chords = false)

        assertTrue(outcome is RejectionOutcome.NoMoreSources)
        assertEquals(setOf("builtin:first"), rejections())
        assertTrue(rejector.undo((outcome as RejectionOutcome.NoMoreSources).rejected))
        assertEquals("first source words\nsecond line", cache().plainLyrics)
    }

    @Test
    fun chord_rejections_do_not_block_words_and_word_rejections_block_both() {
        assertEquals(setOf("chords:a", "a", "b"), ContentRejection.forChords(setOf("chords:a", "b")))
        assertNull(ContentRejection.lyricsSource(null))
        assertNull(ContentRejection.chordsSource(LyricsCacheEntity(key, "words", null, null, null, "builtin:first")))
        assertNull("Unknown origin cannot be rejected", ContentRejection.lyricsSource(LyricsCacheEntity(key, "words", null, null, null, "cache:room")))
    }
}
