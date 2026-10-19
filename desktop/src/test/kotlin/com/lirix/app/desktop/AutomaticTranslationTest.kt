package com.lirix.app.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.lirix.app.storage.AppDatabase
import com.lirix.app.translations.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class AutomaticTranslationTest {
    private val base = "https://www.amalgama-lab.com"
    private val url = "$base/songs/f/falling_in_reverse/joseph.html"
    private fun document(artist: String = "Falling In Reverse") = SongTranslation(url, "Joseph", artist, "Пример", "Автор",
        listOf(TranslationBlock("Synthetic line", "Искусственная строка")))
    private class MemoryDao : SongTranslationDao {
        val rows = MutableStateFlow<List<SongTranslationEntity>>(emptyList())
        override fun observe(trackKey: String, language: String) = rows
        override suspend fun get(trackKey: String, language: String) = rows.value.filter { it.trackKey == trackKey && it.language == language }
        override suspend fun save(entity: SongTranslationEntity) { rows.value = rows.value.filterNot { it.trackKey == entity.trackKey && it.sourceUrl == entity.sourceUrl } + entity }
    }
    private fun row(key: String = "key", doc: SongTranslation = document()) = SongTranslationEntity(key, "ru", doc.sourceUrl, TranslationDocuments.encode(doc), 1)
    private fun songHtml(artist: String = "Falling In Reverse", title: String = "Joseph") = """<meta charset="utf-8">
        <h2 class="original">$title <span>(оригинал $artist)</span></h2><h2 class="translate">Пример <span>(перевод Автор)</span></h2>
        <div id="click_area"><div class="string_container"><div class="original">Synthetic line</div><div class="translate">Искусственная строка</div></div></div>"""
    private class Connection(uri: URI, private val text: String?, private val location: String? = null) : HttpURLConnection(uri.toURL()) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = if (location != null) 302 else if (text == null) 404 else 200
        override fun getContentType() = "text/html; charset=utf-8"
        override fun getContentLengthLong() = (text?.toByteArray()?.size ?: 0).toLong()
        override fun getInputStream() = ByteArrayInputStream(text.orEmpty().toByteArray())
        override fun getHeaderField(name: String?) = if (name == "Location") location else null
    }
    private class Fixture(val pages: MutableMap<String, String>) {
        val requests = ConcurrentHashMap<String, AtomicInteger>()
        val provider = AmalgamaTranslationProvider { uri -> requests.getOrPut(uri.path) { AtomicInteger() }.incrementAndGet(); Connection(uri, pages[uri.path]) }
        fun count(path: String) = requests[path]?.get() ?: 0
    }
    private fun fixture(catalog: String = """<a href="joseph.html">Joseph</a>""", page: String = songHtml()) = Fixture(mutableMapOf(
        "/songs/f/" to """<a href="/songs/f/wrong/">Falling In Reverse</a><div id="songs_nav"><a href="/songs/f/falling_in_reverse/">Falling In Reverse</a><a href="https://evil.example/">Falling In Reverse</a></div>""",
        "/songs/f/falling_in_reverse/" to """<a href="decoy.html">Joseph</a><div id="songs_nav">$catalog</div>""",
        "/songs/f/falling_in_reverse/joseph.html" to page))

    @Test fun catalogDiscoveryUsesRealLinksAndValidatesSongMetadataBeforeAutomaticMatch() = runBlocking {
        val f = fixture()
        val result = f.provider.find("Falling In Reverse - Joseph (Official Music Video)", "Falling In Reverse feat. Guest")
        assertTrue(result is TranslationSearchResult.Found)
        assertEquals(url, (result as TranslationSearchResult.Found).document.sourceUrl)
        assertEquals(3, f.requests.size)
        assertEquals(1, f.count("/songs/f/falling_in_reverse/joseph.html"))
        assertEquals(0, f.count("/songs/f/wrong/"))
        assertEquals(0, f.count("/songs/f/falling_in_reverse/decoy.html"))
    }

    @Test fun normalizationHandlesVideoChannelsAccentsArticlesButKeepsMusicalVersions() {
        assertEquals(TranslationMetadata.artistKey("Ozzy Osbourne"), TranslationMetadata.artistKey("OzzyOsbourneVEVO"))
        assertEquals(TranslationMetadata.artistKey("Beyoncé"), TranslationMetadata.artistKey("Beyonce - Topic"))
        assertEquals(TranslationMetadata.artistKey("Beatles, The"), TranslationMetadata.artistKey("The Beatles"))
        assertEquals("b", TranslationMetadata.bucket("The Beatles"))
        assertEquals(TranslationMetadata.artistKey("30 Seconds To Mars"), TranslationMetadata.artistKey("Thirty Seconds To Mars"))
        assertEquals(TranslationMetadata.artistKey("21 Pilots"), TranslationMetadata.artistKey("Twenty One Pilots"))
        assertEquals(TranslationMetadata.titleKey("Hellraiser"), TranslationMetadata.titleKey("Hellraiser (30th Anniversary Edition - Official Animated Video)"))
        assertEquals(TranslationMetadata.titleKey("Joseph"), TranslationMetadata.titleKey("Joseph (feat. Guest)"))
        assertNotEquals(TranslationMetadata.titleKey("Joseph"), TranslationMetadata.titleKey("Joseph (Live)"))
        assertNotEquals(TranslationMetadata.titleKey("Joseph"), TranslationMetadata.titleKey("Joseph (Acoustic Remix)"))
    }

    @Test fun differentVersionAndDuplicateExactTitlesRequireChoiceAndDoNotFetchSong() = runBlocking {
        val version = fixture()
        assertTrue(version.provider.find("Joseph (Live)", "Falling In Reverse") is TranslationSearchResult.Candidates)
        assertEquals(0, version.count("/songs/f/falling_in_reverse/joseph.html"))
        val duplicate = fixture("""<a href="joseph.html">Joseph</a><a href="another.html">Joseph</a>""")
        val result = duplicate.provider.find("Joseph", "Falling In Reverse") as TranslationSearchResult.Candidates
        assertEquals(2, result.values.size)
        assertEquals(0, duplicate.count("/songs/f/falling_in_reverse/joseph.html"))
    }

    @Test fun wrongPageIdentityDoesNotAutomaticallyAttachEvenWithMatchingCatalog() = runBlocking {
        val f = fixture(page = songHtml("Different Artist"))
        val result = f.provider.find("Joseph", "Falling In Reverse") as TranslationSearchResult.Candidates
        assertEquals("Different Artist", result.values.single().artist)
    }

    @Test fun oneCharacterSourceHeadingTypoNeedsUniqueExactCatalogAndLongArtistName() = runBlocking {
        assertTrue(TranslationMetadata.artistHeadingMatches("OzzyOsbourneVEVO", "Ozzy Ozbourne"))
        assertFalse(TranslationMetadata.artistHeadingMatches("Queen", "Queem"))
        assertFalse(TranslationMetadata.artistHeadingMatches("Falling In Reverse", "Different Artist"))
        val f = fixture(page = songHtml("Falling In Reverze"))
        assertTrue(f.provider.find("Joseph", "Falling In Reverse") is TranslationSearchResult.Found)
        // The tolerance must not leak into finding an artist in the catalog.
        val wrongCatalog = fixture()
        assertEquals(TranslationSearchResult.Missing, wrongCatalog.provider.find("Joseph", "Falling In Reverze"))
    }

    @Test fun catalogIsReusedAndExplicitRetryRefreshesItWhileMissingIsHonest() = runBlocking {
        val f = fixture()
        f.provider.find("Joseph", "Falling In Reverse")
        assertEquals(TranslationSearchResult.Missing, f.provider.find("Missing title", "Falling In Reverse"))
        assertEquals(1, f.count("/songs/f/"))
        assertEquals(1, f.count("/songs/f/falling_in_reverse/"))
        f.provider.find("Joseph", "Falling In Reverse", true)
        assertEquals(2, f.count("/songs/f/"))
        assertEquals(2, f.count("/songs/f/falling_in_reverse/"))
        assertEquals(TranslationSearchResult.Missing, f.provider.find("Joseph", "Unknown Artist"))
        assertEquals(0, f.count("/songs/u/"))
    }

    @Test fun changedCatalogAndUnsafeRedirectAreSourceErrorsNotFalseMatches() = runBlocking {
        val f = fixture()
        f.pages["/songs/f/"] = "<html>Access unavailable</html>"
        try { f.provider.find("Joseph", "Falling In Reverse"); fail("format error") } catch (_: IllegalStateException) {}
        var opens = 0
        val provider = AmalgamaTranslationProvider { uri -> opens++; Connection(uri, null, "https://evil.example/songs/f/") }
        try { provider.find("Joseph", "Falling In Reverse"); fail("unsafe redirect") } catch (_: IllegalArgumentException) {}
        assertEquals(1, opens)
    }

    @Test fun popularIndexResolvesAnArtistWhoseSourceBucketDiffersFromSpelling() = runBlocking {
        val f = Fixture(mutableMapOf(
            "/songs/t/" to "<div id='songs_nav'><a href='/songs/t/other/'>Other</a></div>",
            "/songs/" to "<div class='bands_list'><a href='/songs/3/30_seconds_to_mars/'>Thirty Seconds To Mars</a></div>",
            "/songs/3/30_seconds_to_mars/" to "<div id='songs_nav'><a href='example.html'>Example</a></div>",
            "/songs/3/30_seconds_to_mars/example.html" to songHtml("30 Seconds To Mars", "Example")))
        assertTrue(f.provider.find("Example", "Thirty Seconds To Mars") is TranslationSearchResult.Found)
        assertEquals(1, f.count("/songs/3/30_seconds_to_mars/example.html"))
    }

    @Test fun savedSelectionIsOfflineFirstAndUniqueMatchIsAutomaticallySavedOnce() = runBlocking {
        val dao = MemoryDao()
        val calls = AtomicInteger()
        val automatic = AutomaticSongTranslations(dao, { _, _, _ -> calls.incrementAndGet(); TranslationSearchResult.Found(document()) })
        automatic.resolve("key", "Joseph", "Falling In Reverse")
        assertEquals(document(), TranslationDocuments.decode(dao.rows.value.single().documentJson))
        automatic.resolve("key", "Joseph", "Falling In Reverse", true)
        assertEquals(1, calls.get())
        assertEquals(AutomaticTranslationState.Ready, automatic.states.value["key"])
    }

    @Test fun ambiguousResultsAreNotSavedAndRepeatedNegativeResultsHaveTtlAndRetry() = runBlocking {
        val dao = MemoryDao()
        val calls = AtomicInteger()
        val clock = AtomicLong(10)
        val automatic = AutomaticSongTranslations(dao, { _, _, _ -> calls.incrementAndGet(); TranslationSearchResult.Missing }, clock::get)
        automatic.resolve("key", "Missing", "Artist")
        automatic.resolve("key", "Missing", "Artist")
        assertEquals(1, calls.get())
        automatic.resolve("key", "Missing", "Artist", true)
        assertEquals(2, calls.get())
        clock.addAndGet(16 * 60_000)
        automatic.resolve("key", "Missing", "Artist")
        assertEquals(3, calls.get())
        val choice = AutomaticSongTranslations(dao, { _, _, _ -> TranslationSearchResult.Candidates(listOf(TranslationCandidate(url, "Joseph", "Artist"))) })
        choice.resolve("key", "Joseph", "Artist")
        assertTrue(choice.states.value["key"] is AutomaticTranslationState.Candidates)
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test fun concurrentViewsCoalesceAndCancelledOldTrackCannotSaveLateResult() = runBlocking {
        val dao = MemoryDao()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val automatic = AutomaticSongTranslations(dao, { _, _, _ -> calls.incrementAndGet(); entered.complete(Unit); release.await(); TranslationSearchResult.Found(document()) })
        val first = launch { automatic.resolve("key", "Joseph", "Artist") }
        entered.await()
        val second = launch { automatic.resolve("key", "Joseph", "Artist") }
        release.complete(Unit); first.join(); second.join()
        assertEquals(1, calls.get())
        val delayed = CompletableDeferred<Unit>()
        val late = CompletableDeferred<Unit>()
        val cancelled = AutomaticSongTranslations(dao, { _, _, _ -> withContext(NonCancellable) { delayed.complete(Unit); late.await() }; TranslationSearchResult.Found(document()) })
        val job = launch { cancelled.resolve("old-track", "Joseph", "Artist") }
        delayed.await(); job.cancel(); late.complete(Unit); job.join()
        assertTrue(dao.get("old-track").isEmpty())
        assertNull(cancelled.states.value["old-track"])
    }

    @Test fun manualImportDuringSearchWinsAtomicDatabaseCheck() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("lirix-auto-translation-").toFile()
        val db = Room.databaseBuilder<AppDatabase>(name = directory.resolve("test.db").absolutePath).setDriver(BundledSQLiteDriver()).build()
        try {
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val automatic = AutomaticSongTranslations(db.songTranslationDao(), { _, _, _ -> entered.complete(Unit); release.await(); TranslationSearchResult.Found(document()) })
            val job = launch { automatic.resolve("key", "Joseph", "Artist") }
            entered.await()
            val manual = row(doc = document().copy(sourceUrl = url.replace("joseph.html", "manual.html"), translator = "Выбран вручную"))
            db.songTranslationDao().save(manual)
            release.complete(Unit); job.join()
            assertEquals(listOf(manual), db.songTranslationDao().get("key"))
        } finally { db.close(); directory.deleteRecursively() }
    }
}
