package com.lirix.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.*
import com.lirix.app.feature.lyrics.*
import com.lirix.app.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticLookupRoomTest {
    @Test fun rejectActualChordOriginDuringCompletionKeepsPermittedWordsAndNotes() = runBlocking {
        for (chordSource in listOf("builtin:amdm", "builtin:mychords")) {
            val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
            try {
                val track = TrackEntity("key", "Song", "Artist", sourcePackage = "test", userNotes = "private exact\nnotes")
                db.musicDao().insertOrUpdateTrack(track)
                val ready = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
                val job = async(Dispatchers.IO) {
                    ready.complete(Unit); finish.await()
                    db.lyricsDao().fillMissingAutomaticLookup("key", AutomaticLookupCandidate(
                        plain = AutomaticLookupField("actual words", "builtin:lrclib"),
                        synced = AutomaticLookupField("[00:01.00]actual words", "builtin:lrclib"),
                        chords = AutomaticLookupField("Am C G", chordSource)))
                }
                ready.await(); db.lyricsDao().insertRejection(LyricsRejectionEntity("key", chordSource)); finish.complete(Unit)
                val result = job.await()!!
                assertEquals("actual words", result.plainLyrics); assertNull(result.chordsAmDm)
                assertEquals(track.userNotes, result.userNotes); assertFalse(result.sourceAttributionJson.orEmpty().contains(chordSource))
                assertTrue(result.sourceAttributionJson.orEmpty().contains("builtin:lrclib"))
                val savedTrack = db.musicDao().getTrackByKey("key")!!
                assertEquals("actual words", savedTrack.plainLyrics); assertEquals(track.userNotes, savedTrack.userNotes)
            } finally { db.close() }
        }
    }
    @Test fun freshManualAndAutomaticFieldsAreProtectedInBothTablesAndProvenance() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val track = TrackEntity("key", "Song", "Artist", sourcePackage = "test", userNotes = "track notes", plainLyrics = "fresh track words")
            db.musicDao().insertOrUpdateTrack(track)
            val oldJson = "{\"lyrics\":{\"sourceId\":\"old-source\"},\"extra\":\"keep\"}"
            db.lyricsDao().saveLyrics(LyricsCacheEntity("key", "fresh accepted words", null, null, "exact cache notes", "old-source", sourceAttributionJson = oldJson))
            val candidate = AutomaticLookupCandidate(plain = AutomaticLookupField("stale words", "builtin:lrclib"),
                chords = AutomaticLookupField("Dm G", "builtin:amdm"))
            val result = db.lyricsDao().fillMissingAutomaticLookup("key", candidate)!!
            assertEquals("fresh accepted words", result.plainLyrics); assertEquals("Dm G", result.chordsAmDm)
            assertEquals("exact cache notes", result.userNotes); assertEquals("old-source", result.provider)
            assertTrue(result.sourceAttributionJson!!.contains("old-source")); assertTrue(result.sourceAttributionJson!!.contains("keep"))
            assertEquals("fresh track words", db.musicDao().getTrackByKey("key")!!.plainLyrics)
            val manual = result.copy(provider = "USER", plainLyrics = "manual during HTTP", syncedLyricsLrc = "[00:00.00]manual")
            db.lyricsDao().saveLyrics(manual)
            db.musicDao().updateLyrics("key", manual.plainLyrics, manual.syncedLyricsLrc)
            assertEquals(manual, db.lyricsDao().fillMissingAutomaticLookup("key", candidate))
            assertEquals("manual during HTTP", db.musicDao().getTrackByKey("key")!!.plainLyrics)
        } finally { db.close() }
    }
    @Test fun noteOnlyOfflineThenRealAutomaticProviderFetchPreservesNotesAndSideEffectGuard() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        val jobs = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val track = TrackEntity("key", "Unseen song", "Unseen artist", sourcePackage = "test", userNotes = "DO NOT COPY C Am")
            db.musicDao().insertOrUpdateTrack(track)
            db.lyricsDao().saveLyrics(LyricsCacheEntity("key", null, null, null, track.userNotes, "note:user"))
            var calls = 0; var legacyWrites = 0
            val provider = AggregatedLyricsProvider(lrcLibProvider = LrcLibLyricsProvider {
                calls++; "{\"plainLyrics\":\"Actual song words beyond private notes\",\"syncedLyrics\":null}"
            }, fallbackScraper = null, onLyricsDiscovered = { _, _, _ -> legacyWrites++ })
            val store = object : AutomaticLookupStore {
                override suspend fun track(key: String) = db.musicDao().getTrackByKey(key)
                override suspend fun cache(key: String) = db.lyricsDao().getLyrics(key)
                override suspend fun rejected(key: String) = db.lyricsDao().getRejectedSourceIds(key).toSet()
                override suspend fun merge(key: String, candidate: AutomaticLookupCandidate) = db.lyricsDao().fillMissingAutomaticLookup(key, candidate)
            }
            val coordinator = AutomaticLookupCoordinator(store, provider::acquireAutomatic, jobs)
            assertEquals(LookupPhase.WaitingForNetwork, coordinator.lookup("key", LookupTarget.LYRICS, false)); assertEquals(0, calls)
            assertEquals(LookupPhase.Available, coordinator.lookup("key", LookupTarget.LYRICS, true))
            assertEquals(1, calls); assertEquals(0, legacyWrites)
            val cache = db.lyricsDao().getLyrics("key")!!
            assertEquals("Actual song words beyond private notes", cache.plainLyrics)
            assertEquals(track.userNotes, cache.userNotes); assertEquals(track.userNotes, db.musicDao().getTrackByKey("key")!!.userNotes)
            assertFalse(cache.plainLyrics!!.contains("DO NOT COPY"))
        } finally { jobs.cancel(); db.close() }
    }

    @Test fun actualProviderAndCoordinatorRejectChordOriginInsertedDuringNetworkWait() = runBlocking {
        val body = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("lookup/sakura-1888.json").bufferedReader().use { it.readText() }
        val expected = org.json.JSONObject(body).getString("plainLyrics")
        for (origin in listOf(LyricsSourceIds.AMDM, LyricsSourceIds.MYCHORDS)) {
            val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
            val jobs = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                db.musicDao().insertOrUpdateTrack(TrackEntity("race", "Unseen Sakura", "Traditional", sourcePackage = "test", userNotes = "exact private notes"))
                val ready = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
                val fallback = object : LyricsProvider {
                    override suspend fun getLyrics(track: TrackEntity): LyricsResult {
                        ready.complete(Unit); finish.await()
                        return LyricsResult(false, "", "chord source", sourceId = origin, chords = "Am F C G")
                    }
                }
                val provider = AggregatedLyricsProvider(lrcLibProvider = LrcLibLyricsProvider { body }, fallbackScraper = fallback)
                val coordinator = AutomaticLookupCoordinator(object : AutomaticLookupStore {
                    override suspend fun track(key: String) = db.musicDao().getTrackByKey(key)
                    override suspend fun cache(key: String) = db.lyricsDao().getLyrics(key)
                    override suspend fun rejected(key: String) = db.lyricsDao().getRejectedSourceIds(key).toSet()
                    override suspend fun merge(key: String, candidate: AutomaticLookupCandidate) = db.lyricsDao().fillMissingAutomaticLookup(key, candidate)
                }, provider::acquireAutomatic, jobs)
                val request = async { coordinator.lookup("race", LookupTarget.LYRICS, true) }
                withTimeout(5000) { ready.await() }
                db.lyricsDao().insertRejection(LyricsRejectionEntity("race", origin)); finish.complete(Unit)
                assertEquals(LookupPhase.Available, request.await())
                val saved = db.lyricsDao().getLyrics("race")!!
                assertEquals(expected, saved.plainLyrics); assertNull(saved.chordsAmDm)
                assertEquals("exact private notes", saved.userNotes)
                assertFalse(saved.sourceAttributionJson.orEmpty().contains(origin))
                assertTrue(saved.sourceAttributionJson.orEmpty().contains(LyricsSourceIds.LRCLIB))
                assertEquals(expected, db.musicDao().getTrackByKey("race")!!.plainLyrics)
                assertEquals("exact private notes", db.musicDao().getTrackByKey("race")!!.userNotes)
            } finally { jobs.cancel(); db.close() }
        }
    }

    @Test fun manualSaveWhileRealProviderIsSuspendedRemainsExactAfterCompletion() = runBlocking {
        val body = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
            .open("lookup/molihua-1763.json").bufferedReader().use { it.readText() }
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        val jobs = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            db.musicDao().insertOrUpdateTrack(TrackEntity("manual-race", "Mo Li Hua", "Traditional", sourcePackage = "test"))
            val ready = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
            val provider = AggregatedLyricsProvider(lrcLibProvider = LrcLibLyricsProvider {
                ready.complete(Unit); finish.await(); body
            }, fallbackScraper = null)
            val coordinator = AutomaticLookupCoordinator(object : AutomaticLookupStore {
                override suspend fun track(key: String) = db.musicDao().getTrackByKey(key)
                override suspend fun cache(key: String) = db.lyricsDao().getLyrics(key)
                override suspend fun rejected(key: String) = db.lyricsDao().getRejectedSourceIds(key).toSet()
                override suspend fun merge(key: String, candidate: AutomaticLookupCandidate) = db.lyricsDao().fillMissingAutomaticLookup(key, candidate)
            }, provider::acquireAutomatic, jobs)
            val request = async { coordinator.lookup("manual-race", LookupTarget.LYRICS, true) }
            withTimeout(5000) { ready.await() }
            val manual = LyricsCacheEntity("manual-race", "Exact manual text", "[00:00.00]Exact manual text", "Dm G", "private manual notes", "USER:studio",
                sourceAttributionJson = "{\"lyrics\":{\"sourceId\":\"USER:studio\"}}")
            db.lyricsDao().saveLyrics(manual)
            db.musicDao().updateLyrics("manual-race", manual.plainLyrics, manual.syncedLyricsLrc)
            finish.complete(Unit); request.await()
            assertEquals(manual, db.lyricsDao().getLyrics("manual-race"))
            val fresh = db.musicDao().getTrackByKey("manual-race")!!
            assertEquals(manual.plainLyrics, fresh.plainLyrics); assertEquals(manual.syncedLyricsLrc, fresh.syncedLyrics)
        } finally { jobs.cancel(); db.close() }
    }

    @Test fun completeAcceptedRowIsByteForFieldUnchangedByStaleAutomaticCandidate() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val track = TrackEntity("complete", "Song", "Artist", sourcePackage = "test", plainLyrics = "Independent track words", syncedLyrics = "[00:03.00]Independent", userNotes = "track exact")
            db.musicDao().insertOrUpdateTrack(track)
            val old = LyricsCacheEntity("complete", "Accepted cache words", "[00:01.00]Accepted", "Am G", "cache exact", "builtin:lrclib", updatedAt = 123L,
                sourceAttributionJson = "{\"lyrics\":{\"sourceId\":\"retained\"},\"unknown\":\"unchanged\"}")
            db.lyricsDao().saveLyrics(old)
            val candidate = AutomaticLookupCandidate(AutomaticLookupField("stale", "builtin:lrclib"),
                AutomaticLookupField("[00:00.00]stale", "builtin:lrclib"), AutomaticLookupField("Dm", "builtin:amdm"))
            assertEquals(old, db.lyricsDao().fillMissingAutomaticLookup("complete", candidate))
            assertEquals(old, db.lyricsDao().getLyrics("complete"))
            assertEquals(track, db.musicDao().getTrackByKey("complete"))
        } finally { db.close() }
    }

    @Test fun legacyNoteDualWriteRecoversThroughActualAggregatorAndRoomWithoutChangingNotes() = runBlocking {
        val assets = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        val legacy = org.json.JSONObject(assets.open("lookup/legacy-note-row.json").bufferedReader().use { it.readText() })
        val notes = legacy.getString("userNotes")
        val response = assets.open("lookup/sakura-1888.json").bufferedReader().use { it.readText() }
        val expected = org.json.JSONObject(response).getString("plainLyrics")
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        val jobs = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val track = TrackEntity("legacy", "Sakura", "Traditional", sourcePackage = "test", userNotes = notes, plainLyrics = legacy.getString("plainLyrics"))
            val cache = LyricsCacheEntity("legacy", track.plainLyrics, null, null, notes, legacy.getString("provider"))
            db.musicDao().insertOrUpdateTrack(track); db.lyricsDao().saveLyrics(cache)
            var calls = 0
            val provider = AggregatedLyricsProvider(lrcLibProvider = LrcLibLyricsProvider { calls++; response }, fallbackScraper = null)
            val coordinator = AutomaticLookupCoordinator(object : AutomaticLookupStore {
                override suspend fun track(key: String) = db.musicDao().getTrackByKey(key)
                override suspend fun cache(key: String) = db.lyricsDao().getLyrics(key)
                override suspend fun rejected(key: String) = db.lyricsDao().getRejectedSourceIds(key).toSet()
                override suspend fun merge(key: String, candidate: AutomaticLookupCandidate) = db.lyricsDao().fillMissingAutomaticLookup(key, candidate)
            }, provider::acquireAutomatic, jobs)
            assertEquals(LookupPhase.WaitingForNetwork, coordinator.lookup("legacy", LookupTarget.LYRICS, false)); assertEquals(0, calls)
            assertEquals(LookupPhase.Available, coordinator.lookup("legacy", LookupTarget.LYRICS, true)); assertEquals(1, calls)
            val actual = db.lyricsDao().getLyrics("legacy")!!; val fresh = db.musicDao().getTrackByKey("legacy")!!
            assertEquals(expected, actual.plainLyrics); assertEquals(expected, fresh.plainLyrics)
            assertEquals(notes, actual.userNotes); assertEquals(notes, fresh.userNotes)
            assertFalse(actual.plainLyrics!!.contains(notes)); assertFalse(actual.provider == "note:user")
        } finally { jobs.cancel(); db.close() }
    }

    @Test fun chordOnlyMergeKeepsLegacyEvidenceAndIndependentTrackLyricsAuthoritative() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val notes = "Exact private notes\nAm G"
            for (independent in listOf(false, true)) {
                val key = "legacy-$independent"
                val track = TrackEntity(key, "Song", "Artist", sourcePackage = "test", userNotes = notes,
                    plainLyrics = notes, syncedLyrics = if (independent) "[00:01.00]Independent actual words" else null)
                val cache = LyricsCacheEntity(key, notes, null, null, notes, "note:user")
                db.musicDao().insertOrUpdateTrack(track); db.lyricsDao().saveLyrics(cache)
                val chordOnly = db.lyricsDao().fillMissingAutomaticLookup(key,
                    AutomaticLookupCandidate(chords = AutomaticLookupField("Dm F", LyricsSourceIds.AMDM)))!!
                assertEquals("note:user", chordOnly.provider); assertEquals(notes, chordOnly.plainLyrics)
                assertEquals(track, db.musicDao().getTrackByKey(key)) // Chord-only never rewrites lyrics.
                assertEquals(independent, automaticLookupSatisfied(LookupTarget.LYRICS, track, chordOnly))
                val acquired = db.lyricsDao().fillMissingAutomaticLookup(key,
                    AutomaticLookupCandidate(plain = AutomaticLookupField("Actual acquired words", LyricsSourceIds.LRCLIB)))!!
                val fresh = db.musicDao().getTrackByKey(key)!!
                assertEquals("Actual acquired words", acquired.plainLyrics); assertEquals("Actual acquired words", fresh.plainLyrics)
                assertEquals(track.syncedLyrics, fresh.syncedLyrics); assertEquals(track.syncedLyrics, acquired.syncedLyricsLrc)
                assertEquals(notes, fresh.userNotes); assertEquals(notes, acquired.userNotes)
                assertEquals("Dm F", acquired.chordsAmDm)
            }
        } finally { db.close() }
    }
}
