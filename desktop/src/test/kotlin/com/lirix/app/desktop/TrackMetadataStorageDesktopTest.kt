package com.lirix.app.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.lirix.app.storage.*
import com.lirix.app.ui.components.TrackMetadataPresenter
import com.lirix.app.ui.i18n.InterfaceLanguage
import com.lirix.app.ui.i18n.LirixStrings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Room v16 on Desktop and what the track info dialog shows from a stored row. */
class TrackMetadataStorageDesktopTest {
    private val previousLanguage = LirixStrings.currentLanguage
    @After fun restoreLanguage() { LirixStrings.currentLanguage = previousLanguage }

    private val nightmare = TrackMetadataEntity(
        trackKey = "k", status = TrackMetadataStatus.FOUND.name, genres = "Rock|Metal", releaseYear = 2018, durationMs = 261_813,
        isrc = "USDPK1700195", bpm = 165.887, musicalKey = 7, musicalMode = 1, energy = 0.948, valence = 0.14, danceability = 1.0,
        tags = "nu metal", sources = "genre=itunes+deezer;release=itunes;features=reccobeats", fetchedAt = 5
    )

    @Test fun migration15To16KeepsLibraryAndAddsEmptyMetadataTable() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("lirix-metadata-migration-").toFile()
        val file = directory.resolve("library.db")
        try {
            BundledSQLiteDriver().open(file.absolutePath).use { connection ->
                requireNotNull(javaClass.getResourceAsStream("/translations/schema-v11.sql")).bufferedReader().use { it.readLines() }
                    .filter { it.isNotBlank() }.forEach(connection::execSQL)
                MIGRATION_11_12.migrate(connection)
                MIGRATION_12_13.migrate(connection)
                MIGRATION_13_14.migrate(connection)
                MIGRATION_14_15.migrate(connection)
                connection.execSQL("INSERT INTO music_tracks (trackKey,title,artist,album,sourcePackage,playCount,totalDurationMs,firstPlayedAt,lastPlayedAt,userNotes,isFavorite,plainLyrics) " +
                    "VALUES ('kept','Kept song','Band','','player',9,1000,1,3,'my note',1,'Synthetic words')")
                connection.execSQL("INSERT INTO music_listening_sessions VALUES (1,'kept','player',1,1001,1000,1,'[]')")
                connection.execSQL("PRAGMA user_version = 15")
            }
            fun open() = Room.databaseBuilder<AppDatabase>(name = file.absolutePath).setDriver(BundledSQLiteDriver()).addMigrations(MIGRATION_15_16).build()
            var db = open()
            try {
                val track = db.musicDao().getAllTracks().single()
                assertEquals("my note", track.userNotes)
                assertEquals("Synthetic words", track.plainLyrics)
                assertEquals(9, track.playCount)
                assertTrue(track.isFavorite)
                assertEquals(1, db.musicDao().getAllSessions().size)
                assertNull(db.trackMetadataDao().get("kept"))
                assertTrue(db.trackMetadataDao().getAllFound().isEmpty())
                db.trackMetadataDao().save(nightmare.copy(trackKey = "kept"))
                db.trackMetadataDao().save(TrackMetadataEntity("other", TrackMetadataStatus.NOT_FOUND.name, fetchedAt = 6))
            } finally { db.close() }
            db = open()
            try {
                assertEquals(nightmare.copy(trackKey = "kept"), db.trackMetadataDao().get("kept"))
                assertEquals(nightmare.copy(trackKey = "kept"), db.trackMetadataDao().observe("kept").first())
                assertEquals("only confirmed rows feed the profile", listOf("kept"), db.trackMetadataDao().observeAllFound().first().map { it.trackKey })
                // A newer answer replaces the row instead of adding a second one.
                db.trackMetadataDao().save(nightmare.copy(trackKey = "kept", releaseYear = 2019))
                assertEquals(2019, db.trackMetadataDao().get("kept")!!.releaseYear)
                assertEquals(1, db.trackMetadataDao().deleteByKeys(listOf("kept", "absent")))
                assertEquals(1, db.trackMetadataDao().clear())
                assertEquals("clearing the cache leaves the library alone", 1, db.musicDao().getAllTracks().size)
            } finally { db.close() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun storedRowIsShownWithUnitsAndSourcesInRussian() {
        LirixStrings.currentLanguage = InterfaceLanguage.RU
        val view = TrackMetadataPresenter.view(nightmare, enabled = true)
        assertEquals(listOf("Жанр: Rock, Metal", "Год выпуска: 2018", "Длительность: 4:22", "Темп: 166 уд/мин", "Тональность: G мажор",
            "Теги слушателей: nu metal"), view.facts)
        assertEquals(listOf("Энергия" to 95, "Позитивность" to 14, "Танцевальность" to 100), view.meters)
        assertEquals("Данные каталогов: iTunes, Deezer, ReccoBeats. Оценки звучания рассчитаны автоматически и могут ошибаться.", view.note)
    }

    @Test fun storedRowIsShownInEnglish() {
        LirixStrings.currentLanguage = InterfaceLanguage.EN
        val view = TrackMetadataPresenter.view(nightmare.copy(genres = null, tags = null, bpm = null, musicalMode = 0, sources = ""), enabled = true)
        assertEquals(listOf("Released: 2018", "Length: 4:22", "Key: G minor"), view.facts)
        assertEquals(listOf("Energy", "Happiness"), view.meters.map { it.first }.take(2))
        assertNull("no source, no attribution line", view.note)
    }

    @Test fun missingDataIsExplainedInsteadOfShownAsZero() {
        LirixStrings.currentLanguage = InterfaceLanguage.RU
        fun note(entity: TrackMetadataEntity?, enabled: Boolean) = TrackMetadataPresenter.view(entity, enabled).also {
            assertTrue(it.facts.isEmpty()); assertTrue(it.meters.isEmpty())
        }.note
        assertEquals("Загрузка данных о треке выключена. Включить её можно в настройках.", note(null, false))
        assertEquals("Данные о треке появятся после 20 секунд прослушивания", note(null, true))
        assertEquals("В каталогах этот трек не найден", note(TrackMetadataEntity("k", TrackMetadataStatus.NOT_FOUND.name, fetchedAt = 1), true))
        assertEquals("Найдено несколько разных записей с таким названием; данные не взяты",
            note(TrackMetadataEntity("k", TrackMetadataStatus.AMBIGUOUS.name, fetchedAt = 1), true))
        // A stored row stays visible after the user turns loading off.
        assertEquals(6, TrackMetadataPresenter.view(nightmare, enabled = false).facts.size)
    }

    @Test fun keyNamesFollowPitchClasses() {
        LirixStrings.currentLanguage = InterfaceLanguage.RU
        assertEquals("C минор", TrackMetadataPresenter.keyName(0, 0))
        assertEquals("B мажор", TrackMetadataPresenter.keyName(11, 1))
        assertEquals("E♭", TrackMetadataPresenter.keyName(3, null))
        assertNull(TrackMetadataPresenter.keyName(12, 1))
        assertNull(TrackMetadataPresenter.keyName(-1, 1))
        assertNull(TrackMetadataPresenter.keyName(null, 1))
    }

    @Test fun genresAndTagsAreStoredWithoutDuplicatesOrSeparators() {
        assertEquals("Rock|Metal|Hip-Hop/Rap", TrackMetadataEntity.join(listOf("Rock", " metal ".trim().replaceFirstChar { it.uppercase() }, "rock", "", "Hip-Hop|Rap")))
        assertNull(TrackMetadataEntity.join(listOf("", "  ")))
        assertEquals(listOf("Rock", "Metal"), nightmare.genreList())
        assertTrue(nightmare.copy(genres = null).genreList().isEmpty())
    }
}
