package com.lirix.app.desktop.ingress

import com.lirix.app.ingestion.LivePlaybackSnapshot
import com.lirix.app.platform.MediaIngressProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DesktopGsmtcIngressProvider(
    val bridgeProcessManager: BridgeProcessManager = BridgeProcessManager(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : MediaIngressProvider {

    private val _currentPlayback = MutableStateFlow<LivePlaybackSnapshot?>(null)
    override val currentPlayback: StateFlow<LivePlaybackSnapshot?> = _currentPlayback.asStateFlow()

    init {
        scope.launch {
            bridgeProcessManager.messages.collect { message ->
                handleInboundMessage(message)
            }
        }
        bridgeProcessManager.start()
    }

    private fun handleInboundMessage(message: BridgeInboundMessage) {
        val nowMono = System.nanoTime() / 1_000_000L
        when (message.type.lowercase()) {
            "session" -> {
                val isPlaying = message.isPlaying
                    ?: (message.status?.equals("Playing", ignoreCase = true) == true)
                val app = message.packageName ?: message.app ?: "Unknown"

                _currentPlayback.value = LivePlaybackSnapshot(
                    packageName = app,
                    title = message.title ?: "",
                    artist = message.artist ?: "",
                    album = message.album ?: "",
                    isPlaying = isPlaying,
                    basePositionMs = message.positionMs,
                    lastPositionUpdateTimeMs = nowMono,
                    playbackSpeed = if (isPlaying) 1.0f else 0.0f,
                    durationMs = message.durationMs,
                    timestamp = System.currentTimeMillis(),
                    albumArtUri = message.thumbnailPath,
                    isBlacklisted = false
                )
            }
            "timeline" -> {
                val current = _currentPlayback.value
                if (current != null) {
                    _currentPlayback.value = current.copy(
                        basePositionMs = message.positionMs,
                        lastPositionUpdateTimeMs = nowMono
                    )
                }
            }
            "closed" -> {
                _currentPlayback.value = null
            }
        }
    }

    override fun playPause() {
        bridgeProcessManager.sendCommand("playPause")
    }

    override fun seekTo(positionMs: Long) {
        bridgeProcessManager.sendCommand("seek", positionMs)
    }

    override fun skipNext() {
        bridgeProcessManager.sendCommand("skipNext")
    }

    override fun skipPrevious() {
        bridgeProcessManager.sendCommand("skipPrevious")
    }

    fun stop() {
        bridgeProcessManager.stop()
    }
}
