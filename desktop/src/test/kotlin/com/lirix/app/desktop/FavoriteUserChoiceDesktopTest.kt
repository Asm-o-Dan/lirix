package com.lirix.app.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.lirix.app.desktop.lyrics.DesktopLyricsService
import com.lirix.app.desktop.lyrics.DesktopLyricsState
import com.lirix.app.feature.blacklist.BlacklistMatcher
import com.lirix.app.storage.*
import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

/** SPEC-MEDIA-FAV-01 on the Desktop database, archive and lyrics service. */
class FavoriteUserChoiceDesktopTest {

    private fun buildVersion14(file: java.io.File) {
        BundledSQLiteDriver().open(file.absolutePath).use { connection ->
            requireNotNull(javaClass.getResourceAsStream("/translations/schema-v11.sql")).bufferedReader().use { it.readLines() }
                .filter { it.isNotBlank() }.forEach(connection::execSQL)
            MIGRATION_11_12.migrate(connection)
            MIGRATION_12_13.migrate(connection)
            MIGRATION_13_14.migrate(connection)
            // Favourite reached automatically, a track the user had un-hearted, and a manual low-play favourite.
            connection.execSQL("INSERT INTO music_tracks VALUES ('auto','Auto song','Band','','player',7,1000,1,2,'',1,NULL,NULL,NULL)")
            connection.execSQL("INSERT INTO music_tracks VALUES ('removed','Removed song','Band','','player',9,1000,1,3,'keep',0,NULL,'Words',NULL)")
            connection.execSQL("INSERT INTO music_tracks VALUES ('manual','Manual song','Band','','player',1,1000,1,4,'',1,NULL,NULL,NULL)")
            connection.execSQL("INSERT INTO music_listening_sessions VALUES (1,'removed','player',1,1001,1000,1,'[]')")
            connection.execSQL("INSERT INTO blacklist_rules (id, ruleType, pattern, matchMode, isEnabled, createdAt) VALUES ('user-rule','PACKAGE_NAME','com.example.ads','CONTAINS_IGNORE_CASE',0,5)")
            connection.execSQL("PRAGMA user_version = 14")
        }
    }

    @Test fun migration14To15KeepsRowsAddsNullChoiceAndSeedsMessengerRulesOnce() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("lirix-fav-migration-").toFile()
        val file = directory.resolve("library.db")
        try {
            buildVersion14(file)
            fun open() = Room.databaseBuilder<AppDatabase>(name = file.absolutePath).setDriver(BundledSQLiteDriver())
                .addMigrations(MIGRATION_14_15).build()
            var db = open()
            try {
                val tracks = db.musicDao().getAllTracks().associateBy { it.trackKey }
                assertEquals(setOf("auto", "removed", "manual"), tracks.keys)
                assertEquals(mapOf("auto" to true, "removed" to false, "manual" to true), tracks.mapValues { it.value.isFavorite })
                // User decision D2: no backfill of remembered choices.
                assertTrue(tracks.values.all { it.favoriteUserSetAt == null })
                assertEquals("keep", tracks.getValue("removed").userNotes)
                assertEquals("Words", tracks.getValue("removed").plainLyrics)
                assertEquals(9, tracks.getValue("removed").playCount)
                assertEquals(1, db.musicDao().getAllSessions().size)

                val rules = db.blacklistDao().getAllRules().associateBy { it.id }
                assertFalse("existing user rule stays untouched", rules.getValue("user-rule").isEnabled)
                val expected = mapOf(
                    "builtin:messenger-voice-ru" to "Голосовое сообщение",
                    "builtin:messenger-video-ru" to "Видеосообщение",
                    "builtin:messenger-voice-en" to "Voice message",
                    "builtin:messenger-video-en" to "Video message")
                expected.forEach { (id, pattern) ->
                    val rule = rules.getValue(id)
                    assertEquals(pattern, rule.pattern)
                    assertEquals(BlacklistRuleType.ARTIST, rule.ruleType)
                    assertEquals(BlacklistMatchMode.EXACT, rule.matchMode)
                    assertTrue(rule.isEnabled)
                }
                assertEquals(5, rules.size)
                db.blacklistDao().deleteRule("builtin:messenger-video-en")
            } finally { db.close() }
            db = open()
            try {
                assertNull("a deleted built-in rule must not come back", db.blacklistDao().getRuleById("builtin:messenger-video-en"))
                assertEquals(4, db.blacklistDao().countRules())
            } finally { db.close() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun automaticCreditAddsFavouriteOnlyWithoutUserChoice() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("lirix-fav-credit-").toFile()
        val db = Room.databaseBuilder<AppDatabase>(name = directory.resolve("db").absolutePath).setDriver(BundledSQLiteDriver()).build()
        try {
            val music = db.musicDao()
            music.insertOrUpdateTrack(TrackEntity("free", "Free", "Band", sourcePackage = "player", totalDurationMs = 899_000))
            music.insertOrUpdateTrack(TrackEntity("chosen", "Chosen", "Band", sourcePackage = "player", totalDurationMs = 0, isFavorite = true))

            music.addListeningCredit("free", 1_000, 10)
            assertTrue("15 minutes still promote a track without a user choice", music.getTrackByKey("free")!!.isFavorite)

            music.setFavorite("chosen", false)
            val removedAt = music.getTrackByKey("chosen")!!.favoriteUserSetAt
            assertNotNull(removedAt)
            music.addListeningCredit("chosen", 3_600_000, 20)
            val chosen = music.getTrackByKey("chosen")!!
            assertFalse("an un-hearted track must stay un-hearted", chosen.isFavorite)
            assertEquals(3_600_000L, chosen.totalDurationMs)
            assertEquals(removedAt, chosen.favoriteUserSetAt)

            music.setFavorite("free", false)
            music.invertFavorite("free")
            assertTrue(music.getTrackByKey("free")!!.isFavorite)
            assertNotNull(music.getTrackByKey("free")!!.favoriteUserSetAt)
        } finally { db.close(); directory.deleteRecursively() }
    }

    private fun track(key: String, favorite: Boolean, at: Long?) =
        TrackEntity(key, "Song", "Band", sourcePackage = "player", isFavorite = favorite, favoriteUserSetAt = at, firstPlayedAt = 1, lastPlayedAt = 2)

    @Test fun legacyArchivesDecodeAndUnsetChoiceIsNotWritten() {
        val legacy = LibraryArchives.encode(LibrarySnapshot(tracks = listOf(track("a", true, null))), "test")
        assertFalse(legacy.contains("favoriteUserSetAt"))
        // A v1 archive has the same snapshot format; the checksum covers data only.
        val v1 = legacy.replace("\"version\":2", "\"version\":1")
        assertNotEquals(legacy, v1)
        val decoded = LibraryArchives.decode(v1).data.tracks.single()
        assertTrue(decoded.isFavorite)
        assertNull(decoded.favoriteUserSetAt)

        val chosen = LibraryArchives.encode(LibrarySnapshot(tracks = listOf(track("a", false, 42))), "test")
        assertEquals(42L, LibraryArchives.decode(chosen).data.tracks.single().favoriteUserSetAt)
    }

    @Test fun archiveMergeLetsTheNewestUserChoiceWin() {
        fun merge(local: TrackEntity, incoming: TrackEntity) =
            LibraryArchives.plan(LibrarySnapshot(tracks = listOf(local)), LibrarySnapshot(tracks = listOf(incoming))).data.tracks.single()
        // Neither side chose: automatic union as before.
        merge(track("k", false, null), track("k", true, null)).let { assertTrue(it.isFavorite); assertNull(it.favoriteUserSetAt) }
        // Only the incoming side chose (removed on the other phone): that removal wins.
        merge(track("k", true, null), track("k", false, 50)).let { assertFalse(it.isFavorite); assertEquals(50L, it.favoriteUserSetAt) }
        // Only the local side chose.
        merge(track("k", false, 50), track("k", true, null)).let { assertFalse(it.isFavorite); assertEquals(50L, it.favoriteUserSetAt) }
        // Both chose: newer wins in either direction.
        merge(track("k", false, 50), track("k", true, 60)).let { assertTrue(it.isFavorite); assertEquals(60L, it.favoriteUserSetAt) }
        merge(track("k", true, 70), track("k", false, 60)).let { assertTrue(it.isFavorite); assertEquals(70L, it.favoriteUserSetAt) }
        // Equal timestamps keep the local value.
        merge(track("k", true, 60), track("k", false, 60)).let { assertTrue(it.isFavorite) }
    }

    @Test fun messengerRulesHideVoiceNotesButNotMusicFromTheSameApp() {
        val rules = Schema15.messengerRules
        assertTrue(BlacklistMatcher.isBlacklisted("Вероника", "Голосовое сообщение", "org.telegram.messenger", rules))
        assertTrue(BlacklistMatcher.isBlacklisted("Мишаня", "Видеосообщение", "com.radolyn.ayugram", rules))
        assertTrue(BlacklistMatcher.isBlacklisted("Alex", "voice message", "org.telegram.messenger", rules))
        assertFalse(BlacklistMatcher.isBlacklisted("Nightmare", "Avenged Sevenfold", "com.radolyn.ayugram", rules))
        assertFalse("exact match only", BlacklistMatcher.isBlacklisted("Song", "Голосовое сообщение (cover)", "ru.yandex.music", rules))
    }

    @Test fun desktopSkipsAutomaticLookupForUnknownArtistNumericTitle() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("lirix-fav-lookup-").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val calls = AtomicInteger()
        try {
            val service = DesktopLyricsService(scope, cacheDirectory = directory, savedLookup = { null },
                persistLyrics = { _, _, _, _, _ -> },
                automaticLookup = { _, _, title, artist, _ ->
                    calls.incrementAndGet()
                    DesktopLyricsState(displayTitle = title, displayArtist = artist, plainLyrics = "Synthetic words", hasLyrics = true, source = "builtin:lrclib")
                },
                additionalLookup = { _, _, _, _ -> null }, httpFetch = { _, _ -> null })
            service.onTrackChanged("09", "Unknown Artist")
            val skipped = withTimeout(5_000) { service.lyricsState.first { !it.isLoading && it.displayTitle == "09" } }
            assertFalse(skipped.hasLyrics)
            assertNotNull(skipped.errorMessage)
            assertEquals(0, calls.get())

            service.onTrackChanged("69", "Anacondaz")
            withTimeout(5_000) { service.lyricsState.first { !it.isLoading && it.displayArtist == "Anacondaz" } }
            assertEquals(1, calls.get())
        } finally { scope.cancel(); directory.deleteRecursively() }
    }
}
