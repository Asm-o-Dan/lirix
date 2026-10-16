package com.lirix.app.connect.transport

import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.PairingPayload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Маршрут обнаружения и подключения к пиру (SPEC-CONNECT-01 §4.1, TASK-CON-CORE-02).
 */
enum class DiscoveryRoute {
    DIRECT_LAN,
    IROH_RELAY,
    UNKNOWN
}

/**
 * Сетевой транспорт Lirix Connect (SPEC-CONNECT-01 §4.1, §4.2, TASK-CON-CORE-02).
 *
 * Управляет P2P-соединением Iroh QUIC с ALPN "lirix/connect/1":
 * - двунаправленный bi-stream для управляющих сообщений (LirixConnectMsg),
 * - канал датаграмм QUIC для потокового Audio Cast (Opus 48 kHz).
 */
interface ConnectTransport {
    val alpn: String get() = "lirix/connect/1"
    val state: StateFlow<ConnectState>

    /**
     * Первичное подключение по данным разового PairingPayload (QR-код).
     */
    suspend fun connect(target: PairingPayload): Result<Unit>

    /**
     * Подключение к доверенному узлу по закрепленному EndpointId (Ed25519).
     * Приоритет: прямое соединение по knownAddrs в LAN -> fallback на relayUrl.
     */
    suspend fun connectPinned(endpointId: Bytes, knownAddrs: List<String>, relayUrl: String?): Result<Unit>

    /**
     * Завершение активного соединения.
     */
    suspend fun disconnect(reason: String = "Normal")

    // ── Двунаправленный стрим управления (Control Bi-stream) ──
    suspend fun sendControlMessage(data: ByteArray)
    fun incomingControlMessages(): Flow<ByteArray>

    // ── Канал датаграмм для Audio Cast ──
    suspend fun sendDatagram(datagram: ByteArray)
    fun incomingDatagrams(): Flow<ByteArray>
}
