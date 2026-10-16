package com.lirix.app.connect.security

import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.DeviceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

@Serializable
data class TrustedDevice(
    val endpointId: Bytes,
    val deviceName: String,
    val deviceType: String,
    val pairedAtEpochSec: Long,
    val lastSeenEpochSec: Long
) {
    constructor(
        endpointId: Bytes,
        deviceName: String,
        deviceType: DeviceType,
        pairedAtEpochSec: Long,
        lastSeenEpochSec: Long
    ) : this(
        endpointId = endpointId,
        deviceName = deviceName,
        deviceType = deviceType.name,
        pairedAtEpochSec = pairedAtEpochSec,
        lastSeenEpochSec = lastSeenEpochSec
    )
}

/**
 * Хранилище доверенных устройств с привязкой публичных ключей (endpoint_id pinning).
 */
interface TrustedDeviceStore {
    fun addTrusted(device: TrustedDevice)
    fun isTrusted(endpointId: Bytes): Boolean
    fun getTrusted(endpointId: Bytes): TrustedDevice?
    fun removeTrusted(endpointId: Bytes): Boolean
    fun getAll(): List<TrustedDevice>
    fun clear()

    companion object {
        fun inMemory(): TrustedDeviceStore = InMemoryTrustedDeviceStore()
    }
}

class InMemoryTrustedDeviceStore : TrustedDeviceStore {
    private val devices = MutableStateFlow<Map<Bytes, TrustedDevice>>(emptyMap())

    override fun addTrusted(device: TrustedDevice) {
        devices.update { current ->
            current + (device.endpointId to device)
        }
    }

    override fun isTrusted(endpointId: Bytes): Boolean {
        return devices.value.containsKey(endpointId)
    }

    override fun getTrusted(endpointId: Bytes): TrustedDevice? {
        return devices.value[endpointId]
    }

    override fun removeTrusted(endpointId: Bytes): Boolean {
        var removed = false
        devices.update { current ->
            if (current.containsKey(endpointId)) {
                removed = true
                current - endpointId
            } else {
                current
            }
        }
        return removed
    }

    override fun getAll(): List<TrustedDevice> {
        return devices.value.values.toList()
    }

    override fun clear() {
        devices.value = emptyMap()
    }
}
