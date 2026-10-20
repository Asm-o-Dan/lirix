package com.lirix.app.ui.components

import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.LyricsCacheEntity
import com.lirix.app.storage.TrackEntity
import com.lirix.app.storage.libraryTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.feature.MusicFeatureEngine

/** Immutable target captured at entry; playback changes cannot retarget an open editor. */
data class TapSyncEditingTarget(
    val track: TrackEntity,
    val plainLyrics: String,
    val initialLrc: String?
) {
    val trackKey get()=track.trackKey
    val title get()=track.title
    val artist get()=track.artist
}

fun shouldDisplayTapSyncSave(target:TapSyncEditingTarget,currentTrackKey:String?):Boolean = target.trackKey==currentTrackKey

fun tapSyncPlaybackMatches(trackKey:String,sourcePackage:String,snapshot:LivePlaybackSnapshot?):Boolean = snapshot?.let {
    (sourcePackage.isBlank() || sourcePackage==it.packageName) &&
        (trackKey.isBlank() || trackKey==MusicFeatureEngine.computeTrackKey(it.title,it.artist.ifBlank {"Unknown Artist"},it.album))
}==true

fun tapSyncSourcePackage(track:TrackEntity,snapshot:LivePlaybackSnapshot?):String =
    if(tapSyncPlaybackMatches(track.trackKey,"",snapshot)) snapshot!!.packageName else track.sourcePackage

/** Latest auxiliary fields are read within the same transaction as both lyric writes. */
suspend fun saveTapSyncLyrics(db:AppDatabase,target:TapSyncEditingTarget,generatedLrc:String,updatedPlain:String=target.plainLyrics):LyricsCacheEntity = withContext(Dispatchers.IO) {
    require(generatedLrc.isNotBlank()) { "Нет размеченных строк для сохранения" }
    db.libraryTransaction {
        val cache=db.lyricsDao().getLyrics(target.trackKey)
        val latest=db.musicDao().getTrackByKey(target.trackKey)
        val currentLrc=cache?.syncedLyricsLrc ?: latest?.syncedLyrics
        val currentPlain=cache?.plainLyrics ?: latest?.plainLyrics
        check(currentLrc.orEmpty()==target.initialLrc.orEmpty() && currentPlain.orEmpty()==target.plainLyrics) {
            "Текст песни изменился вне редактора. Закройте и откройте разметку заново; текущие метки пока остаются здесь."
        }
        val entity=LyricsCacheEntity(target.trackKey,updatedPlain,generatedLrc,cache?.chordsAmDm,
            cache?.userNotes ?: latest?.userNotes,"USER:TAP_SYNC",System.currentTimeMillis())
        if(latest==null) db.musicDao().insertOrUpdateTrack(target.track.copy(plainLyrics=updatedPlain,syncedLyrics=generatedLrc))
        else db.musicDao().updateLyrics(target.trackKey,updatedPlain,generatedLrc)
        db.lyricsDao().saveLyrics(entity)
        entity
    }
}
