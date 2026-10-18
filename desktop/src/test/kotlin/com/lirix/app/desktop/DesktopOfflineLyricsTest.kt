package com.lirix.app.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.lirix.app.desktop.library.DesktopLibraryRepository
import com.lirix.app.desktop.lyrics.DesktopLyricsService
import com.lirix.app.desktop.lyrics.DesktopLyricsState
import com.lirix.app.feature.music.MusicTrackParser
import com.lirix.app.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/** Real service + temporary Room. Every provider and disk location is isolated. */
class DesktopOfflineLyricsTest {
    private class Fixture {
        val directory = Files.createTempDirectory("lirix-offline-").toFile()
        private val file = directory.resolve("library.db")
        var db = open()
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var repo = DesktopLibraryRepository(db, scope)
        private fun open() = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver()).build()
        fun key(title: String) = MusicTrackParser.computeTrackKey(title, "Artist")
        suspend fun seed(title: String, provider: String, plain: String? = "saved", lrc: String? = null, chords: String? = null) {
            db.musicDao().insertOrUpdateTrack(TrackEntity(key(title), title, "Artist", sourcePackage = "test"))
            repo.saveLyrics(key(title), plain, lrc, chords, provider)
        }
        fun service(
            lookup: suspend (String, String, String, String, Set<String>) -> DesktopLyricsState = { _, _, title, _, _ ->
                DesktopLyricsState(plainLyrics = "online:$title", source = "LrcLib", hasLyrics = true)
            },
            read: (suspend (String) -> LyricsCacheEntity?)? = null,
            writer: (suspend (String, String?, String?, String?, String) -> Unit)? = null
        ) = DesktopLyricsService(scope, repo, cacheDirectory = directory.resolve("cache"),
            savedLookup = read, persistLyrics = writer, automaticLookup = lookup)
        suspend fun reopen() {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            db.close()
            db = open()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            repo = DesktopLibraryRepository(db, scope)
        }
        suspend fun close() {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            db.close()
            directory.deleteRecursively()
        }
    }
    private suspend fun Fixture.await(service: DesktopLyricsService, title: String, plain: String? = null): DesktopLyricsState =
        withTimeout(10_000) { service.lyricsState.first { it.trackKey == key(title) && !it.isLoading && (plain == null || it.plainLyrics == plain) } }

    @Test fun roomOnlyProviderLyricsHydrateAcrossReopenWithoutNetworkOrWrites() = runBlocking {
        val f = Fixture()
        try {
            f.seed("Timed", "LRCLIB", "saved timed", "[00:01.25] First\n[00:03.00] Second", "Am C")
            f.seed("Rule", "rule:local", "saved rule")
            f.seed("Chords", "AMDM", null, null, "Dm G")
            f.reopen()
            val calls = AtomicInteger()
            val service = f.service(lookup = { _, _, _, _, _ -> calls.incrementAndGet(); error("No network for saved content") },
                writer = { _, _, _, _, _ -> error("Saved hit must not write") })
            service.onTrackChanged("Timed", "Artist")
            val timed = f.await(service, "Timed")
            assertEquals("saved timed", timed.plainLyrics)
            assertEquals("[00:01.25] First\n[00:03.00] Second", timed.syncedLyricsLrc)
            assertEquals(listOf(1_250L, 3_000L), timed.lrcLines.map { it.timestampMs })
            assertEquals("Am C", timed.chords)
            assertEquals("Timed", timed.displayTitle)
            assertEquals("Artist", timed.displayArtist)
            service.onTrackChanged("Rule", "Artist")
            assertEquals("rule:local", f.await(service, "Rule").source)
            service.onTrackChanged("Chords", "Artist")
            val chords = f.await(service, "Chords")
            assertEquals("Dm G", chords.chords)
            assertTrue(chords.lrcLines.isEmpty())
            assertTrue(chords.hasLyrics)
            assertEquals(0, calls.get())
        } finally { f.close() }
    }

    @Test fun userWinsForcedRefreshButAutomaticCacheDoesNot() = runBlocking {
        val f = Fixture()
        try {
            f.seed("Mine", "USER", "my edit")
            f.seed("Automatic", "LRCLIB", "old cached")
            val calls = AtomicInteger()
            val service = f.service(lookup = { _, _, title, _, _ -> calls.incrementAndGet(); DesktopLyricsState(plainLyrics = "new:$title", source = "LrcLib", hasLyrics = true) })
            service.onTrackChanged("Mine", "Artist", forceRefresh = true)
            assertEquals("my edit", f.await(service, "Mine").plainLyrics)
            assertEquals(0, calls.get())
            service.onTrackChanged("Automatic", "Artist", forceRefresh = true)
            assertEquals("new:Automatic", f.await(service, "Automatic").plainLyrics)
            assertEquals(1, calls.get())
        } finally { f.close() }
    }

    @Test fun blankAndNoneCachesContinueLookupWithoutInventingTiming() = runBlocking {
        val f = Fixture()
        try {
            f.seed("Blank", "LRCLIB", "   ")
            f.seed("None", "NONE", "not a positive hit")
            val calls = AtomicInteger()
            val service = f.service(lookup = { _, _, title, _, _ -> calls.incrementAndGet(); DesktopLyricsState(plainLyrics = "new:$title", source = "Web Scraper", hasLyrics = true) })
            for (title in listOf("Blank", "None")) {
                service.onTrackChanged(title, "Artist")
                val state = f.await(service, title)
                assertEquals("new:$title", state.plainLyrics)
                assertTrue(state.lrcLines.isEmpty())
            }
            assertEquals(2, calls.get())
        } finally { f.close() }
    }

    @Test fun rejectedProviderAliasesDoNotHydrateRoom() = runBlocking {
        val f = Fixture()
        try {
            val aliases = listOf("LRCLIB" to "builtin:lrclib", "LrcLib (Search)" to "builtin:lrclib",
                "AMDM" to "builtin:amdm", "AmDm / MyChords" to "builtin:amdm",
                "Web Scraper" to "builtin:web", "rule:abc" to "rule:abc", "other" to "other")
            val seen = mutableListOf<Set<String>>()
            val service = f.service(lookup = { _, _, _, _, rejected ->
                synchronized(seen) { seen.add(rejected) }
                DesktopLyricsState(plainLyrics = "replacement", source = "fresh-source", hasLyrics = true)
            })
            for ((index, alias) in aliases.withIndex()) {
                val title = "Rejected$index"
                f.seed(title, alias.first, "rejected")
                f.db.lyricsDao().insertRejection(LyricsRejectionEntity(f.key(title), alias.second))
                service.onTrackChanged(title, "Artist")
                assertEquals("replacement", f.await(service, title).plainLyrics)
                assertTrue(synchronized(seen) { seen.last().contains(alias.second) })
            }
        } finally { f.close() }
    }

    @Test fun rejectedMemoryAndDiskCacheCannotReturnAfterTrackRevisit() = runBlocking {
        val f = Fixture()
        try {
            val calls = AtomicInteger()
            val loader: suspend (String, String, String, String, Set<String>) -> DesktopLyricsState = { _, _, title, _, rejected ->
                calls.incrementAndGet()
                DesktopLyricsState(plainLyrics = if ("builtin:lrclib" in rejected) "replacement" else "cached:$title",
                    source = if ("builtin:lrclib" in rejected) "Web Scraper" else "LrcLib", hasLyrics = true)
            }
            val service = f.service(lookup = loader)
            service.onTrackChanged("Song", "Artist")
            f.await(service, "Song", "cached:Song")
            val disk = f.directory.resolve("cache").resolve("${f.key("Song")}.cache")
            val rejectedDisk = disk.readBytes()
            f.db.lyricsDao().insertRejection(LyricsRejectionEntity(f.key("Song"), "builtin:lrclib"))
            service.onTrackChanged("Other", "Artist")
            f.await(service, "Other")
            service.onTrackChanged("Song", "Artist")
            assertEquals("replacement", f.await(service, "Song").plainLyrics)
            // A new service has no memory; its on-disk stale result must also be rejected.
            disk.writeBytes(rejectedDisk)
            f.db.lyricsDao().saveLyrics(LyricsCacheEntity(f.key("Song"), "rejected", null, null, null, "LRCLIB"))
            val fresh = f.service(lookup = loader)
            fresh.onTrackChanged("Song", "Artist")
            assertEquals("replacement", f.await(fresh, "Song").plainLyrics)
            assertTrue(calls.get() >= 4)
        } finally { f.close() }
    }

    @Test fun trackSwitchCancelsHeldSavedReadWithoutPublishingPreviousTrack() = runBlocking {
        val f = Fixture()
        try {
            f.seed("A", "LRCLIB", "saved A")
            f.seed("B", "USER", "saved B")
            val entered = CompletableDeferred<Unit>(); val cancelled = CompletableDeferred<Unit>()
            val service = f.service(read = { key ->
                if (key == f.key("A")) {
                    entered.complete(Unit)
                    try { awaitCancellation() } finally { cancelled.complete(Unit) }
                }
                f.repo.savedLyrics(key)
            })
            service.onTrackChanged("A", "Artist")
            withTimeout(10_000) { entered.await() }
            service.onTrackChanged("B", "Artist")
            withTimeout(10_000) { cancelled.await() }
            assertEquals("saved B", f.await(service, "B").plainLyrics)
        } finally { f.close() }
    }

    @Test fun saveUserCancelsAndJoinsLookupBeforePersistenceThenSurvivesReopen() = runBlocking {
        val f = Fixture()
        try {
            f.seed("A", "NONE", null)
            val entered = CompletableDeferred<Unit>(); val cancelled = CompletableDeferred<Unit>()
            val service = f.service(lookup = { _, _, _, _, _ ->
                entered.complete(Unit)
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }, writer = { key, plain, lrc, chords, provider ->
                assertTrue("Lookup must finish cancellation before USER write", cancelled.isCompleted)
                f.repo.saveLyrics(key, plain, lrc, chords, provider)
            })
            service.onTrackChanged("A", "Artist")
            withTimeout(10_000) { entered.await() }
            withTimeout(10_000) { service.saveUserLyrics(f.key("A"), "A", "Artist", "my edit", "[00:01.00] my edit", null) }
            assertEquals("my edit", f.await(service, "A").plainLyrics)
            f.reopen()
            assertEquals("USER", f.db.lyricsDao().getLyrics(f.key("A"))!!.provider)
            val fresh = f.service(lookup = { _, _, _, _, _ -> error("Must reload USER offline") })
            fresh.onTrackChanged("A", "Artist")
            assertEquals("my edit", f.await(fresh, "A").plainLyrics)
        } finally { f.close() }
    }

    @Test fun automaticSaveReturningAfterTrackSwitchCannotPublishPreviousTrack() = runBlocking {
        val f = Fixture()
        val releaseA = CompletableDeferred<Unit>()
        try {
            f.seed("B", "USER", "B edit")
            val savingA = CompletableDeferred<Job>()
            val service = f.service(writer = { key, plain, lrc, chords, provider ->
                f.repo.saveLyrics(key, plain, lrc, chords, provider)
                if (key == f.key("A")) {
                    savingA.complete(currentCoroutineContext()[Job]!!)
                    // Models a storage adapter completing its commit despite cancellation.
                    withContext(NonCancellable) { releaseA.await() }
                }
            })
            service.onTrackChanged("A", "Artist")
            val oldLookup = withTimeout(10_000) { savingA.await() }
            service.onTrackChanged("B", "Artist")
            f.await(service, "B", "B edit")
            releaseA.complete(Unit)
            withTimeout(10_000) { oldLookup.join() }
            assertEquals(f.key("B"), service.lyricsState.value.trackKey)
            assertEquals("B edit", service.lyricsState.value.plainLyrics)
        } finally { releaseA.complete(Unit); f.close() }
    }

    @Test fun heldUserSaveAfterTrackSwitchPersistsWithoutReplacingOrCancellingNewLookup() = runBlocking {
        val f = Fixture()
        val releaseSave = CompletableDeferred<Unit>()
        var pendingSave: Job? = null
        try {
            val saving = CompletableDeferred<Unit>()
            val lookupB = CompletableDeferred<Unit>(); val releaseB = CompletableDeferred<Unit>()
            val service = f.service(lookup = { _, _, title, _, _ ->
                if (title == "B") { lookupB.complete(Unit); releaseB.await() }
                DesktopLyricsState(plainLyrics = "online:$title", source = "LrcLib", hasLyrics = true)
            }, writer = { key, plain, lrc, chords, provider ->
                if (provider == "USER") { saving.complete(Unit); releaseSave.await() }
                f.repo.saveLyrics(key, plain, lrc, chords, provider)
            })
            service.onTrackChanged("A", "Artist")
            f.await(service, "A")
            val save = async { service.saveUserLyrics(f.key("A"), "A", "Artist", "edit A", null, null) }
            pendingSave = save
            withTimeout(10_000) { saving.await() }
            service.onTrackChanged("B", "Artist")
            withTimeout(10_000) { lookupB.await() }
            releaseSave.complete(Unit); withTimeout(10_000) { save.await() }
            assertEquals(f.key("B"), service.lyricsState.value.trackKey)
            releaseB.complete(Unit)
            assertEquals("online:B", f.await(service, "B").plainLyrics)
            assertEquals("edit A", f.repo.savedLyrics(f.key("A"))!!.plainLyrics)
        } finally { releaseSave.complete(Unit); pendingSave?.cancelAndJoin(); f.close() }
    }

    @Test fun savingDifferentLibraryTrackDoesNotCancelCurrentLookup() = runBlocking {
        val f = Fixture()
        try {
            val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
            val service = f.service(lookup = { _, _, _, _, _ ->
                entered.complete(Unit); release.await()
                DesktopLyricsState(plainLyrics = "B result", source = "LrcLib", hasLyrics = true)
            })
            service.onTrackChanged("B", "Artist")
            withTimeout(10_000) { entered.await() }
            service.saveUserLyrics(f.key("C"), "C", "Artist", "C edit", null, null)
            release.complete(Unit)
            assertEquals("B result", f.await(service, "B").plainLyrics)
            assertEquals("C edit", f.repo.savedLyrics(f.key("C"))!!.plainLyrics)
        } finally { f.close() }
    }

    @Test fun repositoryProtectsUserAgainstAutomaticWritesInBothTablesAfterReopen() = runBlocking {
        val f = Fixture()
        try {
            f.seed("A", "LRCLIB", "automatic")
            f.repo.saveLyrics(f.key("A"), "user", "[00:01.00] user", "Am", "USER")
            f.repo.saveLyrics(f.key("A"), "late automatic", null, null, "LRCLIB")
            f.reopen()
            val cache = f.repo.savedLyrics(f.key("A"))!!
            assertEquals("USER", cache.provider)
            assertEquals("user", cache.plainLyrics)
            val track = f.db.musicDao().getTrackByKey(f.key("A"))!!
            assertEquals(cache.plainLyrics, track.plainLyrics)
            assertEquals(cache.syncedLyricsLrc, track.syncedLyrics)
            // Explicit clearing is still a USER edit and cannot resurrect automatic content.
            f.repo.saveLyrics(f.key("A"), null, null, null, "USER")
            f.repo.saveLyrics(f.key("A"), "resurrection", null, null, "AMDM")
            assertNull(f.repo.savedLyrics(f.key("A"))!!.plainLyrics)
            assertNull(f.db.musicDao().getTrackByKey(f.key("A"))!!.plainLyrics)
        } finally { f.close() }
    }
}
