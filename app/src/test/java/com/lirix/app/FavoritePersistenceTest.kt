package com.lirix.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.lirix.app.storage.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FavoritePersistenceTest {
    @Test fun missingTrackAndRepeatedStaleTogglesPersistWithoutLosingFields() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "favorite-regression-${java.util.UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
        val stale = TrackEntity("favorite-fixture", "Gold", "Fixture performer", sourcePackage = "test",
            playCount = 9, totalDurationMs = 12_345L, userNotes = "keep me", plainLyrics = "fixture")
        try {
            assertTrue(db.musicDao().toggleFavorite(stale))
            assertTrue(db.musicDao().observeRecentTracks().first().single().isFavorite)
            assertFalse(db.musicDao().toggleFavorite(stale))
            assertTrue(db.musicDao().toggleFavorite(stale.copy(playCount = 1, userNotes = "stale")))
            val actual = requireNotNull(db.musicDao().getTrackByKey(stale.trackKey))
            assertEquals(stale.copy(isFavorite = true), actual)
            db.close()
            db = Room.databaseBuilder(context, AppDatabase::class.java, name).allowMainThreadQueries().build()
            assertTrue(requireNotNull(db.musicDao().getTrackByKey(stale.trackKey)).isFavorite)
            assertEquals("keep me", db.musicDao().observeFavoriteTracks().first().single().userNotes)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
