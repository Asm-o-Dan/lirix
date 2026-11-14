package com.lirix.app

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.lirix.app.feature.MusicFeatureEngine
import com.lirix.app.feature.lyrics.*
import com.lirix.app.storage.*
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.ui.blacklist.BlacklistViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** SPEC-MEDIA-FAV-01 on Android: manual favourite priority, messenger rules, migration 14→15, lookup gate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteUserChoiceTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val base = 1_700_000_000_000L

    private fun memoryDb() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

    private suspend fun MusicFeatureEngine.play(title: String, at: Long, artist: String = "Band", pkg: String = "player") =
        recordPlaybackSignal(title, artist, sourcePackage = pkg, playbackState = "PLAYING", timestamp = base + at, monotonicMs = at)
    private suspend fun MusicFeatureEngine.stop(title: String, at: Long, artist: String = "Band", pkg: String = "player") =
        recordPlaybackSignal(title, artist, sourcePackage = pkg, playbackState = "STOPPED", timestamp = base + at, monotonicMs = at)

    @Test fun userRemovalSurvivesPlayCountAndListeningTimeRules() = runBlocking {
        val db = memoryDb()
        try {
            val dao = db.musicDao()
            val engine = MusicFeatureEngine(dao, db.lyricsDao())
            var t = 0L
            // Five separate plays (two minutes apart, beyond the 60 s cooldown) promote both tracks automatically.
            repeat(5) {
                engine.play("Kept", t); engine.stop("Kept", t + 1_000); t += 120_000
                engine.play("Removed", t); engine.stop("Removed", t + 1_000); t += 120_000
            }
            val removedKey = MusicFeatureEngine.computeTrackKey("Removed", "Band")
            val keptKey = MusicFeatureEngine.computeTrackKey("Kept", "Band")
            assertEquals(5, dao.getTrackByKey(removedKey)!!.playCount)
            assertTrue(dao.getTrackByKey(removedKey)!!.isFavorite)
            assertNull(dao.getTrackByKey(removedKey)!!.favoriteUserSetAt)

            dao.setFavorite(removedKey, false)

            // More plays and 16 minutes of confirmed listening must not bring the heart back.
            engine.play("Removed", t)
            repeat(32) { step -> engine.updateActiveSessionProgress("player", 0, base + t + (step + 1) * 30_000L, t + (step + 1) * 30_000L) }
            t += 33 * 30_000L
            engine.stop("Removed", t); t += 120_000
            engine.play("Removed", t); engine.stop("Removed", t + 1_000)
            val removed = dao.getTrackByKey(removedKey)!!
            assertTrue("listening time was still credited", removed.totalDurationMs >= 960_000L)
            assertEquals(7, removed.playCount)
            assertFalse("manual removal must win over automatic rules", removed.isFavorite)
            assertNotNull(removed.favoriteUserSetAt)
            assertFalse(dao.getFavoriteTracks().any { it.trackKey == removedKey })

            // A track without a user choice keeps the automatic behaviour.
            assertTrue(dao.getTrackByKey(keptKey)!!.isFavorite)
            // A manual add via the player toggle is remembered too.
            assertTrue(dao.toggleFavorite(dao.getTrackByKey(removedKey)!!))
            assertNotNull(dao.getTrackByKey(removedKey)!!.favoriteUserSetAt)
        } finally { db.close() }
    }

    @Test fun pureRecordPlaybackKeepsUserChoice() {
        val existing = TrackEntity(MusicFeatureEngine.computeTrackKey("Song", "Band"), "Song", "Band", sourcePackage = "p",
            playCount = 9, totalDurationMs = 2_000_000, isFavorite = false, favoriteUserSetAt = 77)
        val next = MusicFeatureEngine.recordPlayback(existing, "Song", "Band", sourcePackage = "p", sessionDurationMs = 60_000)
        assertFalse(next.isFavorite)
        assertEquals(77L, next.favoriteUserSetAt)
        assertTrue(MusicFeatureEngine.recordPlayback(existing.copy(favoriteUserSetAt = null), "Song", "Band", sourcePackage = "p").isFavorite)
    }

    @Test fun builtInMessengerRulesBlockVoiceNotesButKeepMusicFromTheSameApp() = runBlocking {
        val db = memoryDb()
        try {
            Schema15.messengerRules.forEach { db.blacklistDao().insertRule(it) }
            val engine = MusicFeatureEngine(db.musicDao(), db.lyricsDao(), blacklistDao = db.blacklistDao())
            engine.play("Вероника", 0, artist = "Голосовое сообщение", pkg = "org.telegram.messenger")
            engine.play("Мишаня", 120_000, artist = "Видеосообщение", pkg = "com.radolyn.ayugram")
            engine.play("Nightmare", 240_000, artist = "Avenged Sevenfold", pkg = "com.radolyn.ayugram")
            assertEquals(listOf("Nightmare"), db.musicDao().getAllTracks().map { it.title })
            assertTrue(db.musicDao().getAllSessions().all { it.trackKey == MusicFeatureEngine.computeTrackKey("Nightmare", "Avenged Sevenfold") })
        } finally { db.close() }
    }

    @Test fun migration14To15PreservesRowsAndSeedsRules() {
        val name = "schema15-${UUID.randomUUID()}.db"
        try {
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                    .callback(object : SupportSQLiteOpenHelper.Callback(14) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            requireNotNull(javaClass.getResourceAsStream("/translations/schema-v11.sql")).bufferedReader().useLines { lines ->
                                lines.filter { it.isNotBlank() }.forEach { db.execSQL(it) }
                            }
                            AppDatabase.MIGRATION_11_12.migrate(db)
                            AppDatabase.MIGRATION_12_14.migrate(db)
                            db.execSQL("INSERT INTO music_tracks VALUES ('removed','User title','User artist','','test',9,1200000,1,2,'User note',0,NULL,'Original',NULL)")
                            db.execSQL("INSERT INTO music_tracks VALUES ('fav','Fav','User artist','','test',1,1000,1,2,'',1,NULL,NULL,NULL)")
                            db.execSQL("INSERT INTO music_listening_sessions VALUES (1,'removed','test',1,1001,8000,1,'[]')")
                            db.execSQL("INSERT INTO blacklist_rules (id, ruleType, pattern, matchMode, isEnabled, createdAt) VALUES ('mine','TITLE','Ad','EXACT',1,3)")
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("fixture only")
                    }).build())
            helper.writableDatabase
            helper.close()
            val room = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries()
                .addMigrations(AppDatabase.MIGRATION_14_15).build()
            try {
                runBlocking {
                    val tracks = room.musicDao().getAllTracks().associateBy { it.trackKey }
                    assertEquals(false, tracks.getValue("removed").isFavorite)
                    assertEquals(true, tracks.getValue("fav").isFavorite)
                    assertEquals("User note", tracks.getValue("removed").userNotes)
                    assertEquals(9, tracks.getValue("removed").playCount)
                    assertTrue(tracks.values.all { it.favoriteUserSetAt == null })
                    assertEquals(8000L, room.musicDao().getAllSessions().single().durationMs)
                    val rules = room.blacklistDao().getAllRules().map { it.id }.toSet()
                    assertEquals(setOf("mine") + Schema15.messengerRules.map { it.id }, rules)
                    assertTrue(room.blacklistDao().getActiveRules().size == 5)
                }
                assertEquals(15, room.openHelper.writableDatabase.version)
            } finally { room.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun freshProductionDatabaseSeedsBuiltInRules() {
        context.deleteDatabase(AppDatabase.DATABASE_NAME)
        val db = getAppDatabaseBuilder(context).allowMainThreadQueries().build()
        try {
            runBlocking { assertEquals(Schema15.messengerRules.map { it.id }.toSet(), db.blacklistDao().getAllRules().map { it.id }.toSet()) }
        } finally { db.close(); context.deleteDatabase(AppDatabase.DATABASE_NAME) }
    }

    @Test fun builtInRuleAsksBeforeDeletingHistoryAndUndoKeepsTheRule() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val db = memoryDb()
        val store = ViewModelStore()
        try {
            val dao = db.musicDao()
            val voice = TrackEntity("voice", "Вероника", "Голосовое сообщение", sourcePackage = "org.telegram.messenger")
            dao.insertOrUpdateTrack(voice)
            dao.insertOrUpdateTrack(TrackEntity("song", "Nightmare", "Avenged Sevenfold", sourcePackage = "org.telegram.messenger"))
            val rule = Schema15.messengerRules.first().copy(isEnabled = false)
            db.blacklistDao().insertRule(rule)
            val vm = ViewModelProvider(store, BlacklistViewModel.provideFactory(db))[BlacklistViewModel::class.java]
            suspend fun current(): BlacklistRuleEntity = db.blacklistDao().getRuleById(rule.id)!!

            vm.toggleRule(rule)
            val pending = withTimeout(5_000) { vm.uiState.first { it.pendingBuiltInPurge != null } }.pendingBuiltInPurge!!
            assertEquals(1, pending.matchingTracks)
            assertTrue("filtering starts immediately", current().isEnabled)
            assertEquals("nothing deleted without consent", 2, dao.getAllTracks().size)
            vm.dismissBuiltInPurge()
            assertNull(vm.uiState.value.pendingBuiltInPurge)
            assertEquals(2, dao.getAllTracks().size)

            vm.toggleRule(current())
            withTimeout(5_000) { while (current().isEnabled) delay(10) }
            vm.toggleRule(current())
            withTimeout(5_000) { vm.uiState.first { it.pendingBuiltInPurge != null } }
            vm.confirmBuiltInPurge()
            withTimeout(5_000) { vm.uiState.first { it.showUndoForPurge } }
            assertEquals(listOf("song"), dao.getAllTracks().map { it.trackKey })

            vm.undoLastPurge()
            withTimeout(5_000) { while (dao.getTrackByKey("voice") == null) delay(10) }
            delay(100)
            assertTrue("undo keeps the built-in rule enabled", current().isEnabled)
        } finally { store.clear(); db.close(); Dispatchers.resetMain() }
    }

    @Test fun coordinatorDoesNotSearchUnknownArtistNumericTitle() = runTest {
        val tracks = mapOf(
            "digits" to TrackEntity("digits", "09", "Unknown Artist", sourcePackage = "player"),
            "song" to TrackEntity("song", "69", "Anacondaz", sourcePackage = "player"))
        var calls = 0
        val coordinator = AutomaticLookupCoordinator(object : AutomaticLookupStore {
            override suspend fun track(key: String) = tracks[key]
            override suspend fun cache(key: String): LyricsCacheEntity? = null
            override suspend fun rejected(key: String) = emptySet<String>()
            override suspend fun merge(key: String, candidate: AutomaticLookupCandidate): LyricsCacheEntity? = null
        }, { _, _, _ -> calls++; AutomaticLookupCandidate() }, backgroundScope, 1_000)
        assertEquals(LookupPhase.Incomplete, coordinator.lookup("digits", LookupTarget.LYRICS, true))
        assertEquals(0, calls)
        assertEquals(LookupPhase.Incomplete, coordinator.lookup("song", LookupTarget.LYRICS, true))
        assertEquals(1, calls)
    }
}
