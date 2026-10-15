package com.lirix.app.platform

import com.lirix.app.ingestion.LivePlaybackSnapshot
import kotlinx.coroutines.flow.StateFlow

interface MediaIngressProvider {
    val currentPlayback: StateFlow<LivePlaybackSnapshot?>
    fun playPause()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
}
