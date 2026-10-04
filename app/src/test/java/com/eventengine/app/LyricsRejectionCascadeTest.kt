package com.eventengine.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eventengine.app.feature.AggregatedLyricsProvider
import com.eventengine.app.feature.LyricsProvider
import com.eventengine.app.feature.LyricsResult
import com.eventengine.app.feature.LyricsSourceIds
import com.eventengine.app.feature.MusicFeatureEngine
import com.eventengine.app.storage.AppDatabase
import com.eventengine.app.storage.LyricsCacheEntity
import com.eventengine.app.storage.LyricsDao
import com.eventengine.app.storage.LyricsRejectionEntity
import com.eventengine.app.storage.MusicDao
import com.eventengine.app.storage.TrackEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * TDD Unit-tests for TASK-LYR-03-A and TASK-LYR-03-B:
 * 1. Model & DAO tests for LyricsRejectionEntity in Room:
 *    - Insert, retrieve by trackKey.
 *    - Delete single rejection (Undo).
 *    - Clear all rejections for a track.
 * 2. Cascade rejection & fallback tests:
 *    - Rejection advances to the next provider in the chain (LRCLIB -> AmDm -> VsePesni).
 *    - When all available providers are rejected -> returns hasLyrics=false, sourceId="none".
 *    - Undo rejection restores availability of the source.
 *    - Cache & Prefetch protection: background prefetch and cache queries do not return/save rejected providers.
 */
@RunWith(AndroidJUnit4::class)
class LyricsRejectionCascadeTest {

    private lateinit var db: AppDatabase
    private lateinit var lyricsDao: LyricsDao
    private lateinit var musicDao: MusicDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        lyricsDao = db.lyricsDao()
        musicDao = db.musicDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    // ------------------------------------------------------------------------
    // TASK-LYR-03-A: DAO operations for lyrics_rejections
    // ------------------------------------------------------------------------

    @Test
    fun test_rejection_insertion_and_retrieval() = runBlocking {
        val trackKey = "track_sha256_rejection_test"

        lyricsDao.insertRejection(
            LyricsRejectionEntity(
                trackKey = trackKey,
                sourceId = LyricsSourceIds.LRCLIB,
                rejectedAt = 1000L
            )
        )
        lyricsDao.insertRejection(
            LyricsRejectionEntity(
                trackKey = trackKey,
                sourceId = LyricsSourceIds.AMDM,
                rejectedAt = 2000L
            )
        )

        val rejected = lyricsDao.getRejectedSourceIds(trackKey)
        assertEquals("Should contain 2 rejected sources", 2, rejected.size)
        assertTrue(rejected.contains(LyricsSourceIds.LRCLIB))
        assertTrue(rejected.contains(LyricsSourceIds.AMDM))
    }

    @Test
    fun test_rejection_deletion_and_clear() = runBlocking {
        val trackKey = "track_sha256_undo_test"

        lyricsDao.insertRejection(LyricsRejectionEntity(trackKey, LyricsSourceIds.LRCLIB))
        lyricsDao.insertRejection(LyricsRejectionEntity(trackKey, LyricsSourceIds.AMDM))

        // Single deletion (Undo)
        lyricsDao.deleteRejection(trackKey, LyricsSourceIds.LRCLIB)
        val remaining = lyricsDao.getRejectedSourceIds(trackKey)
        assertEquals(1, remaining.size)
        assertEquals(LyricsSourceIds.AMDM, remaining.first())

        // Clear all rejections for track
        lyricsDao.clearRejectionsForTrack(trackKey)
        val afterClear = lyricsDao.getRejectedSourceIds(trackKey)
        assertTrue("All rejections must be cleared", afterClear.isEmpty())
    }

    @Test
    fun test_rejections_do_not_affect_existing_lyrics_cache() = runBlocking {
        val trackKey = "track_cache_isolation_test"

        lyricsDao.saveLyrics(
            LyricsCacheEntity(
                trackKey = trackKey,
                plainLyrics = "Some lyrics",
                syncedLyricsLrc = null,
                chordsAmDm = null,
                userNotes = "User note",
                provider = LyricsSourceIds.LRCLIB
            )
        )

        lyricsDao.insertRejection(LyricsRejectionEntity(trackKey, LyricsSourceIds.AMDM))

        val cached = lyricsDao.getLyrics(trackKey)
        assertNotNull(cached)
        assertEquals("Some lyrics", cached!!.plainLyrics)
        assertEquals(LyricsSourceIds.LRCLIB, cached.provider)
    }

    // ------------------------------------------------------------------------
    // TASK-LYR-03-B: Cascade provider engine with rejection exclusion
    // ------------------------------------------------------------------------

    @Test
    fun test_rejection_advances_to_next_source() = runBlocking {
        val trackKey = "track_cascade_test"
        val track = TrackEntity(
            trackKey = trackKey,
            title = "Numb",
            artist = "Linkin Park",
            sourcePackage = "com.spotify.music"
        )
        musicDao.insertOrUpdateTrack(track)

        // Mock providers in sequence:
        // 1. LRCLIB -> returns wrong lyrics
        // 2. AmDm -> returns alternative chords/lyrics
        val mockLrcLib = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity): LyricsResult {
                return LyricsResult(
                    hasLyrics = true,
                    lyricsText = "Wrong lyrics from LRCLIB",
                    source = "LRCLIB",
                    sourceId = LyricsSourceIds.LRCLIB,
                    plainLyrics = "Wrong lyrics from LRCLIB"
                )
            }
        }
        val mockAmDm = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity): LyricsResult {
                return LyricsResult(
                    hasLyrics = true,
                    lyricsText = "Em C G D\nI'm tired of being what you want me to be",
                    source = "AmDm",
                    sourceId = LyricsSourceIds.AMDM,
                    plainLyrics = "I'm tired of being what you want me to be"
                )
            }
        }

        val aggregator = AggregatedLyricsProvider(
            lrcLibProvider = mockLrcLib,
            fallbackScraper = mockAmDm
        )
        val engine = MusicFeatureEngine(musicDao, lyricsDao, aggregator)

        // Initially, LRCLIB is returned
        val initialLyrics = engine.getLyrics(trackKey)
        assertEquals(LyricsSourceIds.LRCLIB, initialLyrics.sourceId)

        // User rejects LRCLIB:
        val nextLyrics = engine.rejectCurrentLyrics(trackKey, LyricsSourceIds.LRCLIB)

        // Verification:
        // 1. Next lyrics must be from AmDm
        assertEquals("Must advance to AmDm provider", LyricsSourceIds.AMDM, nextLyrics.sourceId)
        assertTrue(nextLyrics.hasLyrics)
        assertEquals("I'm tired of being what you want me to be", nextLyrics.plainLyrics)

        // 2. DB cache must be updated with the new provider
        val cached = lyricsDao.getLyrics(trackKey)
        assertNotNull(cached)
        assertEquals(LyricsSourceIds.AMDM, cached!!.provider)
    }

    @Test
    fun test_all_sources_rejected_yields_none() = runBlocking {
        val trackKey = "track_exhausted_test"
        val track = TrackEntity(
            trackKey = trackKey,
            title = "Obscure Song",
            artist = "Unknown Artist",
            sourcePackage = "com.spotify.music"
        )
        musicDao.insertOrUpdateTrack(track)

        val mockLrcLib = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity): LyricsResult {
                return LyricsResult(true, "Lyrics A", "LRCLIB", sourceId = LyricsSourceIds.LRCLIB, plainLyrics = "Lyrics A")
            }
        }
        val mockAmDm = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity): LyricsResult {
                return LyricsResult(true, "Lyrics B", "AmDm", sourceId = LyricsSourceIds.AMDM, plainLyrics = "Lyrics B")
            }
        }

        val aggregator = AggregatedLyricsProvider(
            lrcLibProvider = mockLrcLib,
            fallbackScraper = mockAmDm
        )
        val engine = MusicFeatureEngine(musicDao, lyricsDao, aggregator)

        // Reject first source
        engine.rejectCurrentLyrics(trackKey, LyricsSourceIds.LRCLIB)
        // Reject second source
        val finalResult = engine.rejectCurrentLyrics(trackKey, LyricsSourceIds.AMDM)

        // All sources exhausted -> result must be none
        assertFalse("Must have no lyrics when all sources are rejected", finalResult.hasLyrics)
        assertEquals(LyricsSourceIds.NONE, finalResult.sourceId)

        // DB tracks lyrics must be cleared
        val trackInDb = musicDao.getTrackByKey(trackKey)
        assertNotNull(trackInDb)
        assertNull("Plain lyrics must be null when all sources rejected", trackInDb!!.plainLyrics)
        assertNull("Synced lyrics must be null when all sources rejected", trackInDb.syncedLyrics)
    }

    @Test
    fun test_undo_restores_previous_source() = runBlocking {
        val trackKey = "track_undo_source_test"
        val track = TrackEntity(
            trackKey = trackKey,
            title = "Crawling",
            artist = "Linkin Park",
            sourcePackage = "com.spotify.music"
        )
        musicDao.insertOrUpdateTrack(track)

        val mockLrcLib = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity): LyricsResult {
                return LyricsResult(true, "Crawling in my skin", "LRCLIB", sourceId = LyricsSourceIds.LRCLIB, plainLyrics = "Crawling in my skin")
            }
        }

        val aggregator = AggregatedLyricsProvider(
            lrcLibProvider = mockLrcLib,
            fallbackScraper = object : LyricsProvider {
                override suspend fun getLyrics(track: TrackEntity) = LyricsResult(false, "", "", sourceId = LyricsSourceIds.NONE)
            }
        )
        val engine = MusicFeatureEngine(musicDao, lyricsDao, aggregator)

        // Reject
        engine.rejectCurrentLyrics(trackKey, LyricsSourceIds.LRCLIB)
        val rejectedList = lyricsDao.getRejectedSourceIds(trackKey)
        assertTrue(rejectedList.contains(LyricsSourceIds.LRCLIB))

        // Undo rejection
        val restored = engine.undoLyricsRejection(trackKey, LyricsSourceIds.LRCLIB)
        assertTrue("Undoing rejection must restore lyrics", restored.hasLyrics)
        assertEquals(LyricsSourceIds.LRCLIB, restored.sourceId)
        assertEquals("Crawling in my skin", restored.plainLyrics)

        // Rejection must be removed from DB
        val afterUndoRejected = lyricsDao.getRejectedSourceIds(trackKey)
        assertFalse("Source must no longer be marked as rejected", afterUndoRejected.contains(LyricsSourceIds.LRCLIB))
    }

    @Test
    fun test_prefetch_respects_rejections() = runBlocking {
        val trackKey = "track_prefetch_test"
        val track = TrackEntity(
            trackKey = trackKey,
            title = "Papercut",
            artist = "Linkin Park",
            sourcePackage = "com.spotify.music"
        )
        musicDao.insertOrUpdateTrack(track)

        // Explicitly mark LRCLIB as rejected for this track in DB
        lyricsDao.insertRejection(LyricsRejectionEntity(trackKey, LyricsSourceIds.LRCLIB))

        val mockLrcLib = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity): LyricsResult {
                return LyricsResult(true, "Bad text", "LRCLIB", sourceId = LyricsSourceIds.LRCLIB, plainLyrics = "Bad text")
            }
        }
        val mockAmDm = object : LyricsProvider {
            override suspend fun getLyrics(track: TrackEntity): LyricsResult {
                return LyricsResult(true, "Good AmDm text", "AmDm", sourceId = LyricsSourceIds.AMDM, plainLyrics = "Good AmDm text")
            }
        }

        val aggregator = AggregatedLyricsProvider(
            lrcLibProvider = mockLrcLib,
            fallbackScraper = mockAmDm
        )
        val engine = MusicFeatureEngine(musicDao, lyricsDao, aggregator)

        // Run prefetch
        val appContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        engine.prefetchLyricsAsync(appContext, this, track)
        kotlinx.coroutines.delay(100)

        // Verify that prefetch did NOT save the rejected LRCLIB text,
        // but either saved AmDm or skipped LRCLIB
        val cached = lyricsDao.getLyrics(trackKey)
        if (cached != null) {
            assertEquals("Prefetch must never store rejected source", LyricsSourceIds.AMDM, cached.provider)
            assertEquals("Good AmDm text", cached.plainLyrics)
        }
    }
}
