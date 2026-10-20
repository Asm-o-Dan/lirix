package com.lirix.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.ingestion.SyncOffsetStore
import com.lirix.app.ui.components.*
import com.lirix.app.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KaraokeFoundationsTest {
    @Test fun signedClockInverseAndBounds() {
        assertEquals(-300L,karaokeLyricPosition(0,-300))
        assertEquals(-1,karaokeActiveLineIndex(listOf(0L,1000L),karaokeLyricPosition(0,-300)))
        listOf(-3000L,-50L,0L,50L,3000L).forEach { offset ->
            assertEquals(5000L,karaokeMediaSeekPosition(karaokeLyricPosition(5000,offset),offset,10000))
        }
        assertEquals(0L,karaokeMediaSeekPosition(0,3000,10000))
        assertEquals(10000L,karaokeMediaSeekPosition(10000,-3000,10000))
        assertEquals(Long.MAX_VALUE,karaokeLyricPosition(Long.MAX_VALUE,3000))
        assertEquals(3000L,boundedSyncOffset(Long.MAX_VALUE))
        assertEquals(-3000L,boundedSyncOffset(Long.MIN_VALUE))
        assertNotEquals(karaokeOccurrenceKey(0,1000),karaokeOccurrenceKey(1,1000))
    }
    @Test fun pausedSeekAndOffsetResetUseRawClockOnce() {
        val paused=com.lirix.app.ingestion.LivePlaybackSnapshot("p","t","a",isPlaying=false,
            basePositionMs=3000,lastPositionUpdateTimeMs=100)
        assertEquals(3000L,paused.currentPositionMs(90000))
        val rawSeek=karaokeMediaSeekPosition(5000,300,10000)
        assertEquals(4700L,rawSeek)
        assertEquals(5000L,karaokeLyricPosition(rawSeek,300))
        assertEquals(4700L,karaokeLyricPosition(rawSeek,0))
    }
    @Test fun sourceCaptureUsesCurrentPausedPlayerAndRejectsLaterTrackOrSource() {
        val key=com.lirix.app.feature.MusicFeatureEngine.computeTrackKey("A","Artist","")
        val track=TrackEntity(key,"A","Artist",sourcePackage="old.player")
        val paused=com.lirix.app.ingestion.LivePlaybackSnapshot("new.player","A","Artist",isPlaying=false,
            basePositionMs=1000,lastPositionUpdateTimeMs=100)
        val source=tapSyncSourcePackage(track,paused)
        assertEquals("new.player",source)
        assertTrue(tapSyncPlaybackMatches(key,source,paused))
        assertFalse(tapSyncPlaybackMatches(key,source,paused.copy(title="B")))
        assertFalse(tapSyncPlaybackMatches(key,source,paused.copy(packageName="third.player")))
    }
    @Test fun persistedOffsetIsExactlyDisplayedAndReopensWithoutMigration() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("media_sync_offsets",Context.MODE_PRIVATE)
        prefs.edit().clear().putLong("global_offset",200).commit()
        val store=SyncOffsetStore(context)
        assertEquals(200L,store.getOffset("b"))
        assertEquals(3000L,store.setOffsetAndGet("a",9000))
        assertEquals(3000L,SyncOffsetStore(context).getOffset("a"))
        assertEquals(200L,store.getOffset("b"))
        prefs.edit().clear().commit()
    }
    @Test fun capturedTargetSavePreservesLatestFieldsAndOtherSong()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val a=TrackEntity("a","A","Artist",sourcePackage="player",plainLyrics="line")
            val b=a.copy(trackKey="b",title="B")
            db.musicDao().insertOrUpdateTrack(a);db.musicDao().insertOrUpdateTrack(b)
            val target=TapSyncEditingTarget(a,"line",null)
            db.lyricsDao().saveLyrics(LyricsCacheEntity("a","line",null,"fresh chords","fresh notes","TEST"))
            val saved=saveTapSyncLyrics(db,target,"[00:01.00] edited line","edited line")
            assertEquals("fresh notes",saved.userNotes);assertEquals("fresh chords",saved.chordsAmDm)
            assertEquals("[00:01.00] edited line",db.musicDao().getTrackByKey("a")!!.syncedLyrics)
            assertEquals("edited line",db.musicDao().getTrackByKey("a")!!.plainLyrics)
            assertEquals("edited line",saved.plainLyrics)
            assertNull(db.musicDao().getTrackByKey("b")!!.syncedLyrics)
            assertFalse(shouldDisplayTapSyncSave(target,"b"))
            assertTrue(shouldDisplayTapSyncSave(target,"a"))
        } finally { db.close() }
    }
    @Test fun failedSecondWriteRollsBackTrackAndCanRetry()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(context,AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val a=TrackEntity("a","A","Artist",sourcePackage="player",plainLyrics="line")
            db.musicDao().insertOrUpdateTrack(a)
            val target=TapSyncEditingTarget(a,"line",null)
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_karaoke BEFORE INSERT ON lyrics_cache BEGIN SELECT RAISE(ABORT, 'forced failure'); END")
            var failed=false
            try {saveTapSyncLyrics(db,target,"[00:01.00] line")} catch(e:Exception) {failed=true}
            assertTrue(failed);assertNull(db.musicDao().getTrackByKey("a")!!.syncedLyrics)
            assertNull(db.lyricsDao().getLyrics("a"))
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_karaoke")
            assertEquals("[00:01.00] line",saveTapSyncLyrics(db,target,"[00:01.00] line").syncedLyricsLrc)
        } finally {db.close()}
    }
}
