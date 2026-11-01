package com.lirix.app.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.lirix.app.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SourceAttributionPersistenceTest {
    private fun cache(provider: String = "builtin:kugou", metadata: String? = null) =
        LyricsCacheEntity("key", "Synthetic words", "[00:01.00] First\n[00:03.00] Second", null, null, provider, 10, metadata)
    private val lyrics = LyricsSourceAttribution("builtin:kugou", "https://lyrics.kugou.com/search?keyword=synthetic")
    private val chords = LyricsSourceAttribution("builtin:akkords-pro", "https://akkords.pro/songs/artist/song/", "Synthetic arranger")

    @Test fun metadataSurvivesArchiveAndDefaultNullDoesNotChangeLegacyPayload() {
        val legacy = LibraryArchives.encode(LibrarySnapshot(lyrics = listOf(cache())), "test")
        assertFalse(legacy.contains("sourceAttributionJson"))
        assertNull(LibraryArchives.decode(legacy).data.lyrics.single().sourceAttributionJson)
        val attribution = LyricsAttribution(lyrics, chords).encode()
        val archive = LibraryArchives.encode(LibrarySnapshot(lyrics = listOf(cache(metadata = attribution))), "test")
        val restored = LibraryArchives.decode(archive).data.lyrics.single()
        assertEquals(LyricsAttribution(lyrics, chords), LyricsAttribution.decode(restored.sourceAttributionJson))
        assertEquals("Synthetic words", restored.plainLyrics)
    }

    @Test fun mergeKeepsChordAttributionWithItsArrangementAndManualVariantsWin() {
        val preferred = cache(metadata = LyricsAttribution(lyrics = lyrics).encode()).copy(updatedAt = 20)
        val secondary = cache(metadata = LyricsAttribution(chords = chords).encode()).copy(chordsAmDm = "Am\nSynthetic", updatedAt = 10)
        val merged = LibraryArchives.plan(LibrarySnapshot(lyrics = listOf(preferred)), LibrarySnapshot(lyrics = listOf(secondary))).data.lyrics.single()
        assertEquals("Am\nSynthetic", merged.chordsAmDm)
        assertEquals(LyricsAttribution(lyrics, chords), LyricsAttribution.decode(merged.sourceAttributionJson))
        val manual = preferred.copy(provider = "USER:EDIT", plainLyrics = "Manual", updatedAt = 1)
        val manualMerge = LibraryArchives.plan(LibrarySnapshot(lyrics = listOf(manual)), LibrarySnapshot(lyrics = listOf(preferred))).data.lyrics.single()
        assertEquals("USER:EDIT", manualMerge.provider)
        assertEquals("Manual", manualMerge.plainLyrics)
    }

    @Test fun migration13To14PreservesListeningIntervalsAndManualLyricsThenMetadataReopens() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("lirix-source-migration-").toFile()
        val file = directory.resolve("library.db")
        try {
            BundledSQLiteDriver().open(file.absolutePath).use { connection ->
                val sql = requireNotNull(javaClass.getResourceAsStream("/translations/schema-v11.sql")).bufferedReader().use { it.readLines() }
                sql.filter { it.isNotBlank() }.forEach(connection::execSQL)
                MIGRATION_11_12.migrate(connection)
                MIGRATION_12_13.migrate(connection)
                connection.execSQL("INSERT INTO music_tracks VALUES ('key','Song','Artist','','test',1,1000,1,2,'notes',1,NULL,'Manual',NULL)")
                connection.execSQL("INSERT INTO lyrics_cache VALUES ('key','Manual',NULL,'Am','notes','USER:EDIT',2)")
                connection.execSQL("INSERT INTO music_listening_sessions VALUES (1,'key','test',1,1001,1000,1,'[]')")
                connection.execSQL("PRAGMA user_version = 13")
            }
            fun open() = Room.databaseBuilder<AppDatabase>(name = file.absolutePath).setDriver(BundledSQLiteDriver()).addMigrations(MIGRATION_13_14).build()
            var db = open()
            try {
                assertEquals("USER:EDIT", db.lyricsDao().getLyrics("key")!!.provider)
                assertNull(db.lyricsDao().getLyrics("key")!!.sourceAttributionJson)
                assertEquals("[]", db.musicDao().getAllSessions().single().listeningIntervalsJson)
                db.lyricsDao().saveLyrics(cache(metadata = LyricsAttribution(lyrics, chords).encode()))
            } finally { db.close() }
            db = open()
            try {
                assertEquals(LyricsAttribution(lyrics, chords), LyricsAttribution.decode(db.lyricsDao().getLyrics("key")!!.sourceAttributionJson))
                assertEquals("notes", db.musicDao().getTrackByKey("key")!!.userNotes)
            } finally { db.close() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun automaticResultAfterConcurrentManualEditCannotOverwriteEitherTable() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("lirix-source-manual-").toFile()
        val db = Room.databaseBuilder<AppDatabase>(name = directory.resolve("db").absolutePath).setDriver(BundledSQLiteDriver()).build()
        try {
            db.musicDao().insertOrUpdateTrack(TrackEntity("key", "Song", "Artist", sourcePackage = "test", plainLyrics = "Manual"))
            val fetched = CompletableDeferred<LyricsCacheEntity>()
            val automatic = async(Dispatchers.IO) { db.lyricsDao().saveAutomaticIfNotUser(fetched.await()) }
            db.lyricsDao().saveLyrics(cache("USER:EDIT").copy(plainLyrics = "Manual", syncedLyricsLrc = null))
            fetched.complete(cache(metadata = LyricsAttribution(lyrics, chords).encode()))
            assertFalse(automatic.await())
            assertEquals("Manual", db.lyricsDao().getLyrics("key")!!.plainLyrics)
            assertEquals("Manual", db.musicDao().getTrackByKey("key")!!.plainLyrics)
            assertNull(db.lyricsDao().getLyrics("key")!!.sourceAttributionJson)
        } finally { db.close(); directory.deleteRecursively() }
    }
}
