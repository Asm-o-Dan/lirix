package com.lirix.app.connect.registry

import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.DeviceType
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.proto.SessionSnapshot
import com.lirix.app.connect.proto.TrackRef
import com.lirix.app.connect.security.TrustedDevice
import com.lirix.app.connect.security.TrustedDeviceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Информация об обнаруженном/сопряжённом устройстве в реестре (SPEC-CONNECT-01 §4.4, TASK-CON-REG-05).
 */
data class ConnectedDeviceInfo(
    val deviceId: String,
    val endpointId: Bytes,
    val deviceName: String,
    val deviceType: DeviceType,
    val isOnline: Boolean,
    val isPlaying: Boolean,
    val currentTrack: TrackRef? = null,
    val lastSeenEpochMs: Long = 0L,
    val batteryPct: Int? = null
)

/**
 * Реестр устройств и мониторинга их динамического присутствия (SPEC-CONNECT-01 §4.4, TASK-CON-REG-05).
 *
 * Объединяет сопряжённые устройства из [TrustedDeviceStore] с оперативным статусом активности (Heartbeat),
 * обновляет состояние при приходе [LirixConnectMsg.DeviceHeartbeat], [LirixConnectMsg.RemoteStateUpdate],
 * [SessionSnapshot] и сбрасывает статус в offline при таймауте (по умолчанию 25 с).
 */
class DeviceRegistry(
    private val trustedDeviceStore: TrustedDeviceStore? = null,
    val heartbeatTimeoutMs: Long = DEFAULT_HEARTBEAT_TIMEOUT_MS
) {
    companion object {
        const val DEFAULT_HEARTBEAT_TIMEOUT_MS = 25_000L
    }

    private val deviceMap = mutableMapOf<Bytes, ConnectedDeviceInfo>()
    private val _devices = MutableStateFlow<List<ConnectedDeviceInfo>>(emptyList())
    val devices: StateFlow<List<ConnectedDeviceInfo>> = _devices.asStateFlow()

    init {
        syncWithTrustedStore()
    }

    /**
     * Синхронизирует список доверенных устройств из [TrustedDeviceStore].
     * Не перезаписывает активный статус (online/playing) для уже известных устройств.
     */
    fun syncWithTrustedStore(nowEpochMs: Long = 0L) {
        val trusted = trustedDeviceStore?.getAll() ?: return
        for (td in trusted) {
            val existing = deviceMap[td.endpointId]
            if (existing == null) {
                val parsedType = parseDeviceType(td.deviceType)
                val info = ConnectedDeviceInfo(
                    deviceId = td.endpointId.toHex(),
                    endpointId = td.endpointId,
                    deviceName = td.deviceName,
                    deviceType = parsedType,
                    isOnline = false,
                    isPlaying = false,
                    currentTrack = null,
                    lastSeenEpochMs = if (nowEpochMs > 0L) nowEpochMs else td.lastSeenEpochSec * 1000L
                )
                deviceMap[td.endpointId] = info
            }
        }
        publish()
    }

    /**
     * Регистрация или прямое обновление информации об устройстве.
     */
    fun registerDevice(
        endpointId: Bytes,
        deviceName: String,
        deviceType: DeviceType = DeviceType.ANDROID_PHONE,
        isOnline: Boolean = true,
        isPlaying: Boolean = false,
        currentTrack: TrackRef? = null,
        nowEpochMs: Long = 0L,
        batteryPct: Int? = null
    ): ConnectedDeviceInfo {
        val existing = deviceMap[endpointId]
        val updated = ConnectedDeviceInfo(
            deviceId = endpointId.toHex(),
            endpointId = endpointId,
            deviceName = deviceName.ifBlank { existing?.deviceName ?: "Lirix Device" },
            deviceType = deviceType,
            isOnline = isOnline,
            isPlaying = isPlaying,
            currentTrack = currentTrack ?: existing?.currentTrack,
            lastSeenEpochMs = if (nowEpochMs > 0L) nowEpochMs else existing?.lastSeenEpochMs ?: 0L,
            batteryPct = batteryPct ?: existing?.batteryPct
        )
        deviceMap[endpointId] = updated
        publish()
        return updated
    }

    /**
     * Фиксирует факт активности пира (например, входящий пакет или рукопожатие).
     */
    fun onDeviceSeen(
        endpointId: Bytes,
        deviceName: String? = null,
        deviceType: DeviceType? = null,
        nowEpochMs: Long
    ) {
        val existing = deviceMap[endpointId] ?: resolveFromTrustedStore(endpointId)
        val updated = if (existing != null) {
            existing.copy(
                deviceName = deviceName ?: existing.deviceName,
                deviceType = deviceType ?: existing.deviceType,
                isOnline = true,
                lastSeenEpochMs = nowEpochMs
            )
        } else {
            ConnectedDeviceInfo(
                deviceId = endpointId.toHex(),
                endpointId = endpointId,
                deviceName = deviceName ?: "Unknown Device",
                deviceType = deviceType ?: DeviceType.ANDROID_PHONE,
                isOnline = true,
                isPlaying = false,
                lastSeenEpochMs = nowEpochMs
            )
        }
        deviceMap[endpointId] = updated
        publish()
    }

    /**
     * Обработка входящего периодического DeviceHeartbeat от удаленного устройства.
     */
    fun onHeartbeatReceived(
        endpointId: Bytes,
        heartbeat: LirixConnectMsg.DeviceHeartbeat,
        nowEpochMs: Long
    ) {
        val existing = deviceMap[endpointId] ?: resolveFromTrustedStore(endpointId)
        val updated = if (existing != null) {
            existing.copy(
                isOnline = heartbeat.isAvailable,
                isPlaying = heartbeat.nowPlaying,
                batteryPct = heartbeat.batteryPct ?: existing.batteryPct,
                lastSeenEpochMs = nowEpochMs
            )
        } else {
            ConnectedDeviceInfo(
                deviceId = endpointId.toHex(),
                endpointId = endpointId,
                deviceName = "Peer ${endpointId.toHex().take(6)}",
                deviceType = DeviceType.ANDROID_PHONE,
                isOnline = heartbeat.isAvailable,
                isPlaying = heartbeat.nowPlaying,
                batteryPct = heartbeat.batteryPct,
                lastSeenEpochMs = nowEpochMs
            )
        }
        deviceMap[endpointId] = updated
        publish()
    }

    /**
     * Обработка обновления состояния удаленного плеера RemoteStateUpdate.
     */
    fun onRemoteStateUpdate(
        endpointId: Bytes,
        update: LirixConnectMsg.RemoteStateUpdate,
        nowEpochMs: Long
    ) {
        val existing = deviceMap[endpointId] ?: resolveFromTrustedStore(endpointId)
        val track = TrackRef(
            provider = "remote",
            trackId = update.trackId,
            title = update.trackTitle,
            artist = update.trackArtist,
            album = null
        )
        val updated = if (existing != null) {
            existing.copy(
                isOnline = true,
                isPlaying = update.isPlaying,
                currentTrack = track,
                lastSeenEpochMs = nowEpochMs
            )
        } else {
            ConnectedDeviceInfo(
                deviceId = endpointId.toHex(),
                endpointId = endpointId,
                deviceName = "Peer ${endpointId.toHex().take(6)}",
                deviceType = DeviceType.ANDROID_PHONE,
                isOnline = true,
                isPlaying = update.isPlaying,
                currentTrack = track,
                lastSeenEpochMs = nowEpochMs
            )
        }
        deviceMap[endpointId] = updated
        publish()
    }

    /**
     * Обработка полного снимка сессии SessionSnapshot (например, при подключении).
     */
    fun onSessionSnapshot(
        endpointId: Bytes,
        snapshot: SessionSnapshot,
        nowEpochMs: Long
    ) {
        val existing = deviceMap[endpointId] ?: resolveFromTrustedStore(endpointId)
        val updated = if (existing != null) {
            existing.copy(
                isOnline = true,
                isPlaying = snapshot.isPlaying,
                currentTrack = snapshot.track,
                lastSeenEpochMs = nowEpochMs
            )
        } else {
            ConnectedDeviceInfo(
                deviceId = endpointId.toHex(),
                endpointId = endpointId,
                deviceName = "Peer ${endpointId.toHex().take(6)}",
                deviceType = DeviceType.ANDROID_PHONE,
                isOnline = true,
                isPlaying = snapshot.isPlaying,
                currentTrack = snapshot.track,
                lastSeenEpochMs = nowEpochMs
            )
        }
        deviceMap[endpointId] = updated
        publish()
    }

    /**
     * Явное отключение устройства (дисконнект / Bye).
     */
    fun onDeviceDisconnected(endpointId: Bytes) {
        val existing = deviceMap[endpointId] ?: return
        deviceMap[endpointId] = existing.copy(isOnline = false, isPlaying = false)
        publish()
    }

    /**
     * Проверка таймаутов присутствия: устройства, от которых не было сигналов дольше [heartbeatTimeoutMs],
     * переводятся в статус offline (isOnline = false).
     */
    fun checkTimeouts(nowEpochMs: Long) {
        var changed = false
        for ((endpointId, info) in deviceMap) {
            if (info.isOnline && (nowEpochMs - info.lastSeenEpochMs > heartbeatTimeoutMs)) {
                deviceMap[endpointId] = info.copy(isOnline = false, isPlaying = false)
                changed = true
            }
        }
        if (changed) {
            publish()
        }
    }

    fun getDevice(endpointId: Bytes): ConnectedDeviceInfo? = deviceMap[endpointId]

    fun getDevice(deviceId: String): ConnectedDeviceInfo? {
        return deviceMap.values.firstOrNull { it.deviceId.equals(deviceId, ignoreCase = true) }
    }

    fun removeDevice(endpointId: Bytes): Boolean {
        val removed = deviceMap.remove(endpointId) != null
        if (removed) {
            publish()
        }
        return removed
    }

    fun clear() {
        deviceMap.clear()
        publish()
    }

    private fun resolveFromTrustedStore(endpointId: Bytes): ConnectedDeviceInfo? {
        val td = trustedDeviceStore?.getTrusted(endpointId) ?: return null
        return ConnectedDeviceInfo(
            deviceId = td.endpointId.toHex(),
            endpointId = td.endpointId,
            deviceName = td.deviceName,
            deviceType = parseDeviceType(td.deviceType),
            isOnline = false,
            isPlaying = false,
            lastSeenEpochMs = td.lastSeenEpochSec * 1000L
        )
    }

    private fun parseDeviceType(typeString: String?): DeviceType {
        return runCatching { DeviceType.valueOf(typeString ?: "") }.getOrDefault(DeviceType.ANDROID_PHONE)
    }

    private fun publish() {
        _devices.value = deviceMap.values.toList()
    }
}
