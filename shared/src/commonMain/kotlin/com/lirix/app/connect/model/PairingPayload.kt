package com.lirix.app.connect.model

import kotlinx.serialization.Serializable

@Serializable
enum class DeviceType {
    WINDOWS_PC,
    ANDROID_PHONE,
    ANDROID_TABLET
}

@Serializable
data class PairingPayload(
    val version: Int = 1,
    val endpointId: Bytes,          // 32 байта Ed25519
    val pairingSecret: Bytes,       // 16 байт одноразовый секрет
    val deviceName: String,
    val addrs: List<String> = emptyList(),        // "192.168.1.50:4433"
    val relayUrl: String? = null,
    val expiresAtEpochSec: Long     // currentTimeSec + 120
)

@Serializable
data class PairRequest(
    val nonce: Bytes,
    val proof: Bytes,
    val deviceName: String,
    val deviceType: DeviceType = DeviceType.ANDROID_PHONE
)

@Serializable
data class PairResult(
    val ok: Boolean,
    val verifyCode: String? = null
)
