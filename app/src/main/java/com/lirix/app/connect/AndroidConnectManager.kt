package com.lirix.app.connect

import android.content.Context
import android.media.AudioManager
import android.media.projection.MediaProjection
import com.lirix.app.connect.cast.AndroidCastSourceManager
import com.lirix.app.connect.cast.AudioCastForegroundService
import com.lirix.app.connect.handoff.HandoffStateMachine
import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.DeviceType
import com.lirix.app.connect.model.PairingPayload
import com.lirix.app.connect.proto.ConnectMessageCodec
import com.lirix.app.connect.proto.Envelope
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.registry.DeviceRegistry
import com.lirix.app.connect.security.TrustedDevice
import com.lirix.app.connect.security.TrustedDeviceStore
import com.lirix.app.connect.transport.ConnectState
import com.lirix.app.connect.transport.ConnectTransport
import com.lirix.app.connect.transport.LanConnectTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android SharedPreferences-backed persistence for trusted devices (SPEC-CONNECT-01 §4.4).
 */
class AndroidPreferencesTrustedDeviceStore(context: Context) : TrustedDeviceStore {
    private val prefs = context.getSharedPreferences("lirix_trusted_devices", Context.MODE_PRIVATE)
    private val memoryStore = TrustedDeviceStore.inMemory()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        val raw = prefs.getString("trusted_devices_json", null) ?: return
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val endpointHex = obj.getString("endpoint_id")
                val name = obj.getString("device_name")
                val type = obj.optString("device_type", DeviceType.WINDOWS_PC.name)
                val pairedAt = obj.optLong("paired_at", 0L)
                val lastSeen = obj.optLong("last_seen", 0L)

                val device = TrustedDevice(
                    endpointId = Bytes.fromHex(endpointHex),
                    deviceName = name,
                    deviceType = type,
                    pairedAtEpochSec = pairedAt,
                    lastSeenEpochSec = lastSeen
                )
                memoryStore.addTrusted(device)
            }
        } catch (_: Exception) {}
    }

    private fun saveToPrefs() {
        val all = memoryStore.getAll()
        val array = JSONArray()
        for (d in all) {
            val obj = JSONObject().apply {
                put("endpoint_id", d.endpointId.toHex())
                put("device_name", d.deviceName)
                put("device_type", d.deviceType)
                put("paired_at", d.pairedAtEpochSec)
                put("last_seen", d.lastSeenEpochSec)
            }
            array.put(obj)
        }
        prefs.edit().putString("trusted_devices_json", array.toString()).apply()
    }

    override fun addTrusted(device: TrustedDevice) {
        memoryStore.addTrusted(device)
        saveToPrefs()
    }

    override fun isTrusted(endpointId: Bytes): Boolean = memoryStore.isTrusted(endpointId)

    override fun getTrusted(endpointId: Bytes): TrustedDevice? = memoryStore.getTrusted(endpointId)

    override fun removeTrusted(endpointId: Bytes): Boolean {
        val removed = memoryStore.removeTrusted(endpointId)
        if (removed) saveToPrefs()
        return removed
    }

    override fun getAll(): List<TrustedDevice> = memoryStore.getAll()

    override fun clear() {
        memoryStore.clear()
        prefs.edit().remove("trusted_devices_json").apply()
    }
}

/**
 * Singleton coordinator for Lirix Connect on Android (SPEC-CONNECT-01 §5.1).
 */
class AndroidConnectManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val trustedStore: TrustedDeviceStore = AndroidPreferencesTrustedDeviceStore(appContext)
    val deviceRegistry: DeviceRegistry = DeviceRegistry(trustedStore)
    val handoffStateMachine: HandoffStateMachine = HandoffStateMachine(scope = scope)

    private val addrsPrefs = appContext.getSharedPreferences("lirix_device_addrs", Context.MODE_PRIVATE)
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _localVolumePercent = MutableStateFlow(getCurrentVolumePct())
    val localVolumePercent: StateFlow<Float> = _localVolumePercent.asStateFlow()

    var activeTransport: ConnectTransport? = null
        private set

    init {
        // Ensure known trusted devices are synced into registry
        deviceRegistry.syncWithTrustedStore(System.currentTimeMillis())
    }

    fun getCurrentVolumePct(): Float {
        val am = audioManager ?: return 1.0f
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        return (current / max).coerceIn(0f, 1f)
    }

    fun setLocalVolumePct(pct: Float) {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (pct.coerceIn(0f, 1f) * max).toInt()
        am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        _localVolumePercent.value = pct
    }

    fun pairWithPayload(payload: PairingPayload): TrustedDevice {
        val nowSec = System.currentTimeMillis() / 1000L
        val trustedDevice = TrustedDevice(
            endpointId = payload.endpointId,
            deviceName = payload.deviceName.ifBlank { "Lirix PC" },
            deviceType = DeviceType.WINDOWS_PC.name,
            pairedAtEpochSec = nowSec,
            lastSeenEpochSec = nowSec
        )
        trustedStore.addTrusted(trustedDevice)
        deviceRegistry.registerDevice(
            endpointId = payload.endpointId,
            deviceName = trustedDevice.deviceName,
            deviceType = DeviceType.WINDOWS_PC,
            isOnline = true,
            isPlaying = false,
            nowEpochMs = System.currentTimeMillis()
        )

        // Сохраняем адреса устройства для автоматического переподключения
        saveDeviceAddrs(payload.endpointId.toHex(), payload.addrs)

        // Инициализируем сетевое подключение в фоне
        scope.launch {
            connectTransport(payload.addrs, trustedDevice.deviceName)
        }

        return trustedDevice
    }

    suspend fun connectTransport(addrs: List<String>, deviceName: String): ConnectTransport? {
        val current = activeTransport
        if (current != null && current.state.value == ConnectState.CONNECTED) {
            return current
        }

        val transport = LanConnectTransport(scope)
        val result = transport.connect(
            PairingPayload(
                version = 1,
                endpointId = Bytes.EMPTY,
                pairingSecret = Bytes.EMPTY,
                deviceName = deviceName,
                addrs = addrs,
                relayUrl = null,
                expiresAtEpochSec = System.currentTimeMillis() / 1000L + 3600
            )
        )

        return if (result.isSuccess) {
            activeTransport = transport

            // Отправляем PairRequest для подтверждения на хосте
            try {
                val pairMsg = LirixConnectMsg.PairRequest(
                    nonce = Bytes(ByteArray(16)),
                    proof = Bytes(ByteArray(32)),
                    deviceName = "Телефон (Android)",
                    deviceType = DeviceType.ANDROID_PHONE
                )
                val envelope = Envelope(
                    msgId = System.currentTimeMillis(),
                    sentAtMonoMs = System.currentTimeMillis(),
                    body = pairMsg
                )
                transport.sendControlMessage(ConnectMessageCodec.encode(envelope))
            } catch (_: Exception) {}

            transport
        } else {
            null
        }
    }

    /**
     * Запуск прямой трансляции системного аудио (Audio Cast) на удалённое устройство.
     */
    suspend fun startAudioCastToDevice(
        deviceName: String,
        endpointHex: String? = null,
        projection: MediaProjection? = null
    ): Boolean {
        var transport = activeTransport
        if (transport == null || transport.state.value != ConnectState.CONNECTED) {
            val addrs = if (endpointHex != null) getDeviceAddrs(endpointHex) else emptyList()
            transport = connectTransport(addrs, deviceName)
        }

        if (transport == null) {
            return false
        }

        return AndroidCastSourceManager.getInstance().startCast(
            transport = transport,
            deviceName = deviceName,
            projection = projection
        )
    }

    /**
     * Остановка трансляции Audio Cast.
     */
    fun stopAudioCast() {
        AndroidCastSourceManager.getInstance().stopCast()
        AudioCastForegroundService.stop(appContext)
    }

    fun saveDeviceAddrs(endpointHex: String, addrs: List<String>) {
        if (addrs.isEmpty()) return
        val array = JSONArray()
        for (a in addrs) array.put(a)
        addrsPrefs.edit().putString("addrs_$endpointHex", array.toString()).apply()
    }

    fun getDeviceAddrs(endpointHex: String): List<String> {
        val raw = addrsPrefs.getString("addrs_$endpointHex", null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun renameDevice(endpointId: Bytes, newName: String) {
        val current = trustedStore.getTrusted(endpointId) ?: return
        val updated = current.copy(deviceName = newName.trim())
        trustedStore.addTrusted(updated)
        deviceRegistry.onDeviceSeen(
            endpointId = endpointId,
            deviceName = updated.deviceName,
            nowEpochMs = System.currentTimeMillis()
        )
    }

    fun forgetDevice(endpointId: Bytes) {
        trustedStore.removeTrusted(endpointId)
        deviceRegistry.removeDevice(endpointId)
        addrsPrefs.edit().remove("addrs_${endpointId.toHex()}").apply()
    }

    companion object {
        @Volatile
        private var instance: AndroidConnectManager? = null

        fun getInstance(context: Context): AndroidConnectManager {
            return instance ?: synchronized(this) {
                instance ?: AndroidConnectManager(context).also { instance = it }
            }
        }
    }
}
