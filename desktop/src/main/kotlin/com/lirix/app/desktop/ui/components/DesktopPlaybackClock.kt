package com.lirix.app.desktop.ui.components

import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.ingestion.getSystemElapsedRealtimeMs
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

fun desktopPlaybackPositions(
    playback: LivePlaybackSnapshot?,
    tickMs: Long = 50L,
    nowMs: () -> Long = ::getSystemElapsedRealtimeMs
): Flow<Long> = flow {
    require(tickMs > 0) { "tickMs must be positive" }
    do {
        emit(playback?.currentPositionMs(nowMs()) ?: 0L)
        if (playback == null || !playback.isPlaying || playback.playbackSpeed <= 0f) break
        delay(tickMs)
    } while (currentCoroutineContext().isActive)
}
