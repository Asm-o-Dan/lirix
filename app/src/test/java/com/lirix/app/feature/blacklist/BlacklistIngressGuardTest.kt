package com.lirix.app.feature.blacklist

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.feature.MusicFeatureEngine
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.blacklist.BlacklistMatchMode
import com.lirix.app.storage.blacklist.BlacklistRuleEntity
import com.lirix.app.storage.blacklist.BlacklistRuleType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BlacklistIngressGuardTest {

    @Test
    fun test_blacklisted_track_is_not_persisted_to_db_and_flow_updated() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val engine = MusicFeatureEngine(
            musicDao = db.musicDao(),
            lyricsDao = db.lyricsDao(),
            blacklistDao = db.blacklistDao()
        )

        // Add a blacklist rule
        val rule = BlacklistRuleEntity(
            id = "rule_ads",
            ruleType = BlacklistRuleType.TITLE,
            pattern = "Ad Jingle",
            matchMode = BlacklistMatchMode.CONTAINS_IGNORE_CASE,
            isEnabled = true
        )
        db.blacklistDao().insertRule(rule)

        // Start sync
        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        engine.startBlacklistRuleSync(testScope)

        // Attempt to record blacklisted track
        val result = engine.recordPlaybackSignal(
            title = "Special Ad Jingle 2026",
            artist = "Sponsor",
            album = "",
            sourcePackage = "com.spotify.music"
        )

        // Result must be null (not persisted)
        assertNull(result)

        // Verify database is completely empty
        assertEquals(0, db.musicDao().getAllTracks().size)
        assertEquals(0, db.musicDao().getAllSessions().size)

        // Verify livePlaybackFlow has isBlacklisted = true
        val snapshot = engine.livePlaybackFlow.value
        assertNotNull(snapshot)
        assertTrue(snapshot!!.isBlacklisted)
        assertEquals("Special Ad Jingle 2026", snapshot.title)

        // Record a non-blacklisted track
        val normalTrack = engine.recordPlaybackSignal(
            title = "In The End",
            artist = "Linkin Park",
            album = "Hybrid Theory",
            sourcePackage = "com.spotify.music"
        )

        assertNotNull(normalTrack)
        assertEquals(1, db.musicDao().getAllTracks().size)
        assertEquals(1, db.musicDao().getAllSessions().size)

        db.close()
    }

    @Test
    fun test_disabled_rule_allows_persistence() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val engine = MusicFeatureEngine(
            musicDao = db.musicDao(),
            lyricsDao = db.lyricsDao(),
            blacklistDao = db.blacklistDao()
        )

        // Add a disabled blacklist rule
        val rule = BlacklistRuleEntity(
            id = "rule_disabled",
            ruleType = BlacklistRuleType.ARTIST,
            pattern = "Blocked Artist",
            matchMode = BlacklistMatchMode.EXACT,
            isEnabled = false
        )
        db.blacklistDao().insertRule(rule)

        val testScope = TestScope(UnconfinedTestDispatcher(testScheduler))
        engine.startBlacklistRuleSync(testScope)

        val track = engine.recordPlaybackSignal(
            title = "Song",
            artist = "Blocked Artist",
            album = "",
            sourcePackage = "com.pkg"
        )

        assertNotNull("Disabled rule should not block track", track)
        assertEquals(1, db.musicDao().getAllTracks().size)

        db.close()
    }
}
