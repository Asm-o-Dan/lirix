package com.lirix.app.desktop.ingress

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val bridgeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    coerceInputValues = true
}

@Serializable
data class BridgeInboundMessage(
    val type: String = "",
    val app: String? = null,
    val packageName: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val isPlaying: Boolean? = null,
    val status: String? = null,
    val thumbnailPath: String? = null,
    val leftDbfs: Float? = null,
    val rightDbfs: Float? = null,
    val leftPeak: Float? = null,
    val rightPeak: Float? = null,
    val masterPeak: Float? = null,
    val masterDbfs: Float? = null,
    val isClipping: Boolean? = null,
    val isActive: Boolean? = null
)

@Serializable
data class BridgeCommandMessage(
    @SerialName("cmd")
    val cmd: String,
    @SerialName("command")
    val command: String = cmd,
    val positionMs: Long? = null
)
