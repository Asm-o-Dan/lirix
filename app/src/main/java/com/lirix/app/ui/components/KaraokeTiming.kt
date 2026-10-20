package com.lirix.app.ui.components

import com.lirix.app.ingestion.SyncOffsetStore

fun boundedSyncOffset(value:Long):Long = value.coerceIn(SyncOffsetStore.MIN_OFFSET_MS,SyncOffsetStore.MAX_OFFSET_MS)
private fun addTime(value:Long,delta:Long):Long = when {
    delta>0 && value>Long.MAX_VALUE-delta -> Long.MAX_VALUE
    delta<0 && value<Long.MIN_VALUE-delta -> Long.MIN_VALUE
    else -> value+delta
}
fun karaokeLyricPosition(rawMs:Long,offsetMs:Long):Long = addTime(rawMs,boundedSyncOffset(offsetMs))
fun karaokeMediaSeekPosition(lyricMs:Long,offsetMs:Long,durationMs:Long):Long =
    addTime(lyricMs,-boundedSyncOffset(offsetMs)).coerceIn(0,if(durationMs>0) durationMs else Long.MAX_VALUE)

fun karaokeActiveLineIndex(times:List<Long>,positionMs:Long):Int = times.indexOfLast {it<=positionMs}
fun karaokeOccurrenceKey(index:Int,timestampMs:Long):String = "$index:$timestampMs"
