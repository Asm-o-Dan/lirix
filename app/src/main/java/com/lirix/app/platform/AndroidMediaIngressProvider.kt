package com.lirix.app.platform

import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.ingestion.MediaSessionCollector
import kotlinx.coroutines.flow.StateFlow

class AndroidMediaIngressProvider : MediaIngressProvider {
    override val currentPlayback: StateFlow<LivePlaybackSnapshot?>
        get() = MediaSessionCollector.livePlaybackFlow

    override fun playPause() {
        MediaSessionCollector.togglePlayPause()
    }

    override fun seekTo(positionMs: Long) {
        MediaSessionCollector.seekTo(positionMs)
    }

    override fun skipNext() {
        MediaSessionCollector.skipToNext()
    }

    override fun skipPrevious() {
        MediaSessionCollector.skipToPrevious()
    }
}
