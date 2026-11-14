package com.lirix.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.metadata.TrackMetadataCoordinator
import com.lirix.app.feature.metadata.TrackMetadataSettings
import com.lirix.app.metadata.AudioFeatures
import com.lirix.app.metadata.MetadataOutcome
import com.lirix.app.metadata.ResolvedMetadata
import com.lirix.app.metadata.TrackQuery
import com.lirix.app.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Loading rules of .sdd/specs/media-core/track_metadata_and_avatar_v1.md, section "Загрузка":
 * nothing leaves the device without consent, a confirmed answer is final, failures store nothing.
 * The catalogue is the only fake; storage is the real Room database.
 */
@RunWith(AndroidJUnit4::class)
class TrackMetadataCoordinatorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private var enabled = true
    private var now = 1_000_000L
    private var key = ""
    private val asked = mutableListOf<Pair<TrackQuery, String?>>()
    private var answer: (TrackQuery) -> MetadataOutcome = { MetadataOutcome.NotFound }

    private fun coordinator(maxPerHour: Int = 30) = TrackMetadataCoordinator(
        dao = db.trackMetadataDao(), enabled = { enabled }, lastFmKey = { key },
        resolve = { query, lastFm -> asked += query to lastFm; answer(query) }, clock = { now }, maxPerHour = maxPerHour
    )

    private fun track(key: String = "k", title: String = "Nightmare", artist: String = "Deuce") =
        TrackEntity(key, title, artist, sourcePackage = "player")

    private val found = MetadataOutcome.Found(ResolvedMetadata(
        genres = listOf("Rock", "Metal"), releaseYear = 2018, durationMs = 261_000, isrc = "USDPK1700195",
        features = AudioFeatures(bpm = 165.887, musicalKey = 7, musicalMode = 1, energy = 0.948, valence = 0.145),
        tags = listOf("nu metal"), sources = mapOf("genre" to "itunes+deezer", "features" to "reccobeats")
    ))

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun tearDown() { db.close() }

    @Test fun nothingIsAskedWithoutConsentOrNetwork() = runBlocking<Unit> {
        enabled = false
        assertNull(coordinator().lookup(track(), 261_000, networkAllowed = true))
        enabled = true
        assertNull(coordinator().lookup(track(), 261_000, networkAllowed = false))
        assertTrue(asked.isEmpty())
        assertNull(db.trackMetadataDao().get("k"))
    }

    @Test fun tracksThatCannotIdentifyASongAreNotAsked() = runBlocking<Unit> {
        val c = coordinator()
        listOf(track(title = "09", artist = "Unknown Artist"), track(title = "Voice message", artist = "Unknown Artist"),
            track(title = "  ", artist = "Deuce"), track(title = "Nightmare", artist = "")).forEach { assertNull(c.lookup(it, 0, true)) }
        assertTrue(asked.isEmpty())
    }

    @Test fun confirmedAnswerIsStoredForThatTrackAndNeverAskedAgain() = runBlocking<Unit> {
        answer = { found }
        key = "0123456789abcdef0123456789abcdef"
        val c = coordinator()
        val stored = c.lookup(track(title = " Nightmare ", artist = "Deuce "), 261_000, true)!!
        assertEquals(listOf(TrackQuery("Nightmare", "Deuce", 261_000) to key), asked)

        val row = db.trackMetadataDao().get("k")!!
        assertEquals(stored, row)
        assertEquals(TrackMetadataStatus.FOUND.name, row.status)
        assertEquals(listOf("Rock", "Metal"), row.genreList())
        assertEquals(2018, row.releaseYear)
        assertEquals(261_000L, row.durationMs)
        assertEquals("USDPK1700195", row.isrc)
        assertEquals(165.887, row.bpm!!, 1e-9)
        assertEquals(7, row.musicalKey)
        assertEquals(1, row.musicalMode)
        assertEquals(0.948, row.energy!!, 1e-9)
        assertEquals(0.145, row.valence!!, 1e-9)
        assertNull("a value no source gave stays unknown", row.danceability)
        assertEquals(listOf("nu metal"), row.tagList())
        assertEquals("genre=itunes+deezer;features=reccobeats", row.sources)
        assertEquals(now, row.fetchedAt)

        now += 400L * 24 * 60 * 60 * 1000
        assertNull(c.lookup(track(), 261_000, true))
        assertEquals(1, asked.size)
        assertNull("another track's row is untouched", db.trackMetadataDao().get("other"))
    }

    @Test fun blankLastFmKeyIsNotPassed() = runBlocking<Unit> {
        key = "  "
        coordinator().lookup(track(), 0, true)
        assertEquals(listOf<Pair<TrackQuery, String?>>(TrackQuery("Nightmare", "Deuce", 0) to null), asked)
    }

    @Test fun notFoundAndAmbiguousAreRememberedForAWeekThenAskedAgain() = runBlocking<Unit> {
        val c = coordinator()
        answer = { if (it.title == "Nightmare") MetadataOutcome.NotFound else MetadataOutcome.Ambiguous }
        assertEquals(TrackMetadataStatus.NOT_FOUND.name, c.lookup(track(), 0, true)!!.status)
        assertEquals(TrackMetadataStatus.AMBIGUOUS.name, c.lookup(track("s", "Song", "Band"), 0, true)!!.status)
        assertNull(db.trackMetadataDao().get("s")!!.genres)
        assertTrue("unconfirmed rows never feed the profile", db.trackMetadataDao().getAllFound().isEmpty())

        now += 7L * 24 * 60 * 60 * 1000 - 1
        assertNull(c.lookup(track(), 0, true))
        assertEquals(2, asked.size)

        now += 1
        answer = { found }
        assertEquals(TrackMetadataStatus.FOUND.name, c.lookup(track(), 0, true)!!.status)
        assertEquals(listOf("Rock", "Metal"), db.trackMetadataDao().get("k")!!.genreList())
    }

    @Test fun sourceFailureStoresNothingAndIsRetried() = runBlocking<Unit> {
        val c = coordinator()
        answer = { MetadataOutcome.Unavailable }
        assertNull(c.lookup(track(), 0, true))
        assertNull(db.trackMetadataDao().get("k"))
        answer = { found }
        assertEquals(TrackMetadataStatus.FOUND.name, c.lookup(track(), 0, true)!!.status)
        assertEquals(2, asked.size)
    }

    @Test fun failureDoesNotEraseAnEarlierAnswer() = runBlocking<Unit> {
        val c = coordinator()
        assertEquals(TrackMetadataStatus.NOT_FOUND.name, c.lookup(track(), 0, true)!!.status)
        val before = db.trackMetadataDao().get("k")
        now += 8L * 24 * 60 * 60 * 1000
        answer = { MetadataOutcome.Unavailable }
        assertNull(c.lookup(track(), 0, true))
        assertEquals(before, db.trackMetadataDao().get("k"))
    }

    @Test fun hourlyBudgetLimitsRequests() = runBlocking<Unit> {
        val c = coordinator(maxPerHour = 2)
        assertTrue(c.lookup(track("a", "Song A", "Band"), 0, true) != null)
        assertTrue(c.lookup(track("b", "Song B", "Band"), 0, true) != null)
        assertNull(c.lookup(track("c", "Song C", "Band"), 0, true))
        assertEquals(2, asked.size)
        assertNull(db.trackMetadataDao().get("c"))

        now += 60 * 60_000L
        assertTrue(c.lookup(track("c", "Song C", "Band"), 0, true) != null)
        assertEquals(3, asked.size)
    }

    @Test fun answerThatArrivesAfterConsentWasWithdrawnIsDiscarded() = runBlocking<Unit> {
        answer = { enabled = false; found }
        assertNull(coordinator().lookup(track(), 0, true))
        assertNull(db.trackMetadataDao().get("k"))
    }

    @Test fun settingsAreOffByDefaultAndRejectAMalformedKey() {
        val preferences = context.getSharedPreferences("metadata-test-${UUID.randomUUID()}", Context.MODE_PRIVATE)
        val settings = TrackMetadataSettings(preferences)
        assertFalse(settings.enabled.value)
        assertEquals("", settings.lastFmKey)
        assertThrows(IllegalArgumentException::class.java) { settings.lastFmKey = "key&artist=x" }
        settings.lastFmKey = " 0123456789ABCDEF0123456789abcdef "
        settings.setEnabled(true)
        val reopened = TrackMetadataSettings(preferences)
        assertTrue(reopened.enabled.value)
        assertEquals("0123456789ABCDEF0123456789abcdef", reopened.lastFmKey)
        reopened.lastFmKey = ""
        assertEquals("", TrackMetadataSettings(preferences).lastFmKey)
    }

    @Test fun switchInSettingsIsSeenByEveryHolderAtOnce() {
        val screen = TrackMetadataSettings.of(context)
        val loader = TrackMetadataSettings.of(context)
        val before = screen.enabled.value
        try {
            screen.setEnabled(!before)
            assertEquals(!before, loader.enabled.value)
        } finally { screen.setEnabled(before) }
    }

    @Test fun migration15To16KeepsUserRowsAndAddsMetadataTable() {
        val name = "schema16-${UUID.randomUUID()}.db"
        try {
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                    .callback(object : SupportSQLiteOpenHelper.Callback(15) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            requireNotNull(javaClass.getResourceAsStream("/translations/schema-v11.sql")).bufferedReader().useLines { lines ->
                                lines.filter { it.isNotBlank() }.forEach { db.execSQL(it) }
                            }
                            AppDatabase.MIGRATION_11_12.migrate(db)
                            AppDatabase.MIGRATION_12_14.migrate(db)
                            AppDatabase.MIGRATION_14_15.migrate(db)
                            db.execSQL("INSERT INTO music_tracks (trackKey,title,artist,album,sourcePackage,playCount,totalDurationMs,firstPlayedAt,lastPlayedAt,userNotes,isFavorite,plainLyrics) " +
                                "VALUES ('kept','Kept song','Band','','player',9,1200000,1,2,'User note',1,'Synthetic words')")
                            db.execSQL("INSERT INTO music_listening_sessions VALUES (1,'kept','player',1,1001,8000,1,'[]')")
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("fixture only")
                    }).build())
            helper.writableDatabase
            helper.close()
            repeat(2) { pass ->
                val room = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries()
                    .addMigrations(AppDatabase.MIGRATION_15_16).build()
                try {
                    runBlocking {
                        val kept = room.musicDao().getAllTracks().single()
                        assertEquals("User note", kept.userNotes)
                        assertEquals("Synthetic words", kept.plainLyrics)
                        assertEquals(9, kept.playCount)
                        assertTrue(kept.isFavorite)
                        assertEquals(8000L, room.musicDao().getAllSessions().single().durationMs)
                        if (pass == 0) {
                            assertNull(room.trackMetadataDao().get("kept"))
                            room.trackMetadataDao().save(TrackMetadataEntity("kept", TrackMetadataStatus.FOUND.name, genres = "Rock", fetchedAt = 3))
                        } else assertEquals(listOf("Rock"), room.trackMetadataDao().get("kept")!!.genreList())
                    }
                    assertEquals(16, room.openHelper.writableDatabase.version)
                } finally { room.close() }
            }
        } finally { context.deleteDatabase(name) }
    }
}
