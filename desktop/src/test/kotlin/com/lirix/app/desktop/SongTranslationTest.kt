package com.lirix.app.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.lirix.app.storage.*
import com.lirix.app.translations.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.CountDownLatch

class SongTranslationTest {
    private val url = "https://www.amalgama-lab.com/songs/a/artist/song.html"
    private val html = """<meta charset="windows-1251"><h2 class="original">Example <span>(оригинал Artist)</span></h2>
        <h2 class="translate">Пример <span>(перевод <a href="/author/?id=1">Автор</a>)</span></h2>
        <div class="translate">Ignore navigation</div><div id="click_area">
        <div class="string_container"><div class="original">[Verse]</div><div class="translate">[Куплет]</div></div>
        <div class="string_container"><div class="original">First &amp; second<br>Next</div><div class="translate">Первая &amp; вторая<br>Дальше</div></div>
        <div class="string_container"><div class="original">Missing</div></div>
        <div class="string_container"><div class="original">Repeat</div><div class="translate">Повтор</div></div>
        <div class="string_container"><div class="original">Repeat</div><div class="translate">Повтор снова</div></div></div>""".trimIndent()
    private fun bytes() = html.toByteArray(charset("windows-1251"))
    private fun document() = AmalgamaTranslationProvider.parse(bytes(), url)

    @Test fun windows1251MetadataEntitiesSectionsAndMissingPairsArePreserved() {
        val document = document()
        assertEquals("Example", document.title)
        assertEquals("Artist", document.artist)
        assertEquals("Пример", document.translatedTitle)
        assertEquals("Автор", document.translator)
        assertEquals(5, document.blocks.size)
        assertTrue(document.blocks.first().section)
        assertEquals("First & second\nNext", document.blocks[1].original)
        assertEquals("Первая & вторая\nДальше", document.blocks[1].translated)
        assertEquals("", document.blocks[2].translated)
        assertEquals("Повтор снова", document.blocks[4].translated)
        assertEquals(document, TranslationDocuments.decode(TranslationDocuments.encode(document)))
    }

    @Test fun charsetFromHttpHeaderWorksWithoutMetaAndUtf8IsRespected() {
        val noMeta = html.substringAfter('>')
        assertEquals("Автор", AmalgamaTranslationProvider.parse(noMeta.toByteArray(charset("windows-1251")), url, "windows-1251").translator)
        assertEquals("Пример", AmalgamaTranslationProvider.parse(noMeta.toByteArray(Charsets.UTF_8), url, "UTF-8").translatedTitle)
    }

    @Test fun wrongHostSchemesCredentialsQueriesAndNonSongPagesAreRejected() {
        listOf("http://www.amalgama-lab.com/songs/a/a/a.html", "https://www.amalgama-lab.com.evil/songs/a/a/a.html",
            "https://user@www.amalgama-lab.com/songs/a/a/a.html", "https://www.amalgama-lab.com/songs/a/a/a.html?q=x",
            "https://www.amalgama-lab.com/songs/a/../x.html", "https://www.amalgama-lab.com/author/index.html").forEach {
            assertThrows(IllegalArgumentException::class.java) { AmalgamaTranslationProvider.canonicalUrl(it) }
        }
        assertEquals(url, AmalgamaTranslationProvider.canonicalUrl(url.replace("www.", "") + "#translation"))
    }

    @Test fun errorPagesAndOversizedDocumentsAreNotTranslations() {
        assertThrows(IllegalStateException::class.java) { AmalgamaTranslationProvider.parse("<html>Search results</html>".toByteArray(), url) }
        assertThrows(IllegalArgumentException::class.java) { AmalgamaTranslationProvider.parse(ByteArray(AmalgamaTranslationProvider.MAX_BYTES + 1), url) }
        assertThrows(IllegalArgumentException::class.java) { AmalgamaTranslationProvider.parse(html.replace("string_container", "not_rows").toByteArray(), url, "UTF-8") }
    }

    private open class FakeConnection(private val status: Int, private val bytes: ByteArray = byteArrayOf(), private val location: String? = null) : HttpURLConnection(URL("https://www.amalgama-lab.com")) {
        var disconnected = false
        override fun connect() {}
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getResponseCode() = status
        override fun getContentType() = "text/html; charset=windows-1251"
        override fun getContentLengthLong() = bytes.size.toLong()
        override fun getHeaderField(name: String?) = if (name == "Location") location else null
        override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(bytes)
    }

    @Test fun redirectsAreValidatedBeforeOpeningAndConnectionsAreClosed() = runBlocking {
        val redirect = FakeConnection(302, location = "https://evil.example/song.html")
        var opens = 0
        val provider = AmalgamaTranslationProvider { opens++; redirect }
        try { provider.load(url); fail("unsafe redirect") } catch (_: IllegalArgumentException) {}
        assertEquals(1, opens)
        assertTrue(redirect.disconnected)
        val success = FakeConnection(200, bytes())
        assertEquals("Пример", AmalgamaTranslationProvider { success }.load(url).translatedTitle)
        assertTrue(success.disconnected)
        assertEquals(8000, success.connectTimeout)
        assertFalse(success.instanceFollowRedirects)
    }

    @Test fun httpFailureDoesNotProduceDocumentAndCancellationDisconnects() = runBlocking {
        val failed = FakeConnection(404)
        try { AmalgamaTranslationProvider { failed }.load(url); fail("404") } catch (_: IllegalArgumentException) {}
        assertTrue(failed.disconnected)
        val entered = CompletableDeferred<Unit>()
        val stalled = object : FakeConnection(200) {
            override fun getInputStream(): java.io.InputStream {
                entered.complete(Unit)
                CountDownLatch(1).await()
                return ByteArrayInputStream(byteArrayOf())
            }
        }
        val job = launch { AmalgamaTranslationProvider { stalled }.load(url) }
        entered.await(); job.cancelAndJoin()
        assertTrue(stalled.disconnected)
    }

    @Test fun version11MigrationRetainsHistoryLyricsNotesAndTranslationsReopenOffline() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("lirix-translations-").toFile()
        val file = directory.resolve("library.db")
        try {
            BundledSQLiteDriver().open(file.absolutePath).use { connection ->
                val sql = requireNotNull(SongTranslationTest::class.java.getResourceAsStream("/translations/schema-v11.sql")).bufferedReader().use { it.readLines() }
                sql.filter { it.isNotBlank() }.forEach(connection::execSQL)
                connection.execSQL("PRAGMA user_version = 11")
                connection.execSQL("INSERT INTO music_tracks VALUES ('key','Title','Artist','','test',7,12345,1,2,'notes',1,'[00:01.00] Original','Original',NULL)")
                connection.execSQL("INSERT INTO lyrics_cache VALUES ('key','Original','[00:01.00] Original','Am','notes','USER',2)")
                connection.execSQL("INSERT INTO music_listening_sessions VALUES (1,'key','test',1,1001,1000,1)")
            }
            fun open() = Room.databaseBuilder<AppDatabase>(name = file.absolutePath).setDriver(BundledSQLiteDriver()).addMigrations(MIGRATION_11_12).build()
            var db = open()
            try {
                assertEquals(7, db.musicDao().getTrackByKey("key")!!.playCount)
                assertEquals("notes", db.musicDao().getTrackByKey("key")!!.userNotes)
                assertEquals("USER", db.lyricsDao().getLyrics("key")!!.provider)
                db.songTranslationDao().save(SongTranslationEntity("key", "ru", url, TranslationDocuments.encode(document()), 10))
                db.songTranslationDao().save(SongTranslationEntity("key", "ru", url, TranslationDocuments.encode(document()), 11))
                val variant = document().copy(sourceUrl = url.replace("song.html", "other.html"), translator = "Другой автор")
                db.songTranslationDao().save(SongTranslationEntity("key", "ru", variant.sourceUrl, TranslationDocuments.encode(variant), 12))
            } finally { db.close() }
            db = open()
            try {
                val saved = db.songTranslationDao().get("key")
                assertEquals(2, saved.size)
                assertEquals("Другой автор", TranslationDocuments.decode(saved.first().documentJson).translator)
                assertEquals("Original", db.lyricsDao().getLyrics("key")!!.plainLyrics)
                assertEquals("Am", db.lyricsDao().getLyrics("key")!!.chordsAmDm)
                assertEquals(1, db.musicDao().getAllSessions().size)
                assertTrue(db.songTranslationDao().get("other-track").isEmpty())
            } finally { db.close() }
        } finally { directory.deleteRecursively() }
    }
}
