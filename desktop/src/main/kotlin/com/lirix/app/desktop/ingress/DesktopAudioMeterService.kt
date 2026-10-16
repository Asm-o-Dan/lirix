package com.lirix.app.desktop.ingress

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Authentic audio meter metrics for stereo Left and Right channels.
 * Uses decibel full scale (dBFS) where 0 dBFS is digital maximum and -60 dBFS is silence floor.
 */
data class AudioLevels(
    val leftDbfs: Float = -60.0f,
    val rightDbfs: Float = -60.0f,
    val leftPeak: Float = 0.0f,
    val rightPeak: Float = 0.0f,
    val masterPeak: Float = 0.0f,
    val masterDbfs: Float = -60.0f,
    val isClipping: Boolean = false,
    val isActive: Boolean = false
) {
    /**
     * Normalized logarithmic VU meter scale [0.0f..1.0f] calculated from -60 dBFS to 0 dBFS.
     */
    val leftNormalized: Float
        get() = if (isActive && leftDbfs > -60.0f) ((leftDbfs + 60.0f) / 60.0f).coerceIn(0f, 1f) else 0f

    val rightNormalized: Float
        get() = if (isActive && rightDbfs > -60.0f) ((rightDbfs + 60.0f) / 60.0f).coerceIn(0f, 1f) else 0f

    companion object {
        val SILENCE = AudioLevels(
            leftDbfs = -60.0f,
            rightDbfs = -60.0f,
            leftPeak = 0.0f,
            rightPeak = 0.0f,
            masterPeak = 0.0f,
            masterDbfs = -60.0f,
            isClipping = false,
            isActive = false
        )
    }
}

/**
 * Windows WASAPI Loopback Peak Audio Meter Service.
 * Consumes honest peak levels from the native C# bridge (IAudioMeterInformation) over the JSON stream.
 * Exposes a thread-safe StateFlow<AudioLevels> for Compose UI components.
 */
class DesktopAudioMeterService(
    private val bridgeProcessManager: BridgeProcessManager,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    private val _audioLevels = MutableStateFlow(AudioLevels.SILENCE)
    val audioLevels: StateFlow<AudioLevels> = _audioLevels.asStateFlow()

    private var collectJob: Job? = null

    init {
        start()
    }

    fun start() {
        if (collectJob != null) return
        collectJob = scope.launch {
            bridgeProcessManager.messages.collect { message ->
                handleInboundMessage(message)
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        _audioLevels.value = AudioLevels.SILENCE
    }

    private fun handleInboundMessage(message: BridgeInboundMessage) {
        when (message.type.lowercase()) {
            "meter" -> {
                val leftDbfs = message.leftDbfs ?: -60.0f
                val rightDbfs = message.rightDbfs ?: -60.0f
                val leftPeak = message.leftPeak ?: 0.0f
                val rightPeak = message.rightPeak ?: 0.0f
                val masterPeak = message.masterPeak ?: maxOf(leftPeak, rightPeak)
                val masterDbfs = message.masterDbfs ?: maxOf(leftDbfs, rightDbfs)
                val isClipping = message.isClipping ?: (leftDbfs > -1.0f || rightDbfs > -1.0f)
                val isActive = message.isActive ?: (leftPeak > 0.0001f || rightPeak > 0.0001f)

                _audioLevels.value = if (isActive) {
                    AudioLevels(
                        leftDbfs = leftDbfs,
                        rightDbfs = rightDbfs,
                        leftPeak = leftPeak,
                        rightPeak = rightPeak,
                        masterPeak = masterPeak,
                        masterDbfs = masterDbfs,
                        isClipping = isClipping,
                        isActive = true
                    )
                } else {
                    AudioLevels.SILENCE
                }
            }
            "closed" -> {
                _audioLevels.value = AudioLevels.SILENCE
            }
        }
    }
}
