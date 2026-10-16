package com.lirix.app.connect.registry

import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.proto.LirixConnectMsg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Монитор присутствия и периодической отправки Heartbeat (SPEC-CONNECT-01 §4.4, TASK-CON-REG-05).
 *
 * Инварианты:
 * - Интервал отправки Heartbeat: каждые 10 секунд (HEARTBEAT_INTERVAL_MS).
 * - Таймаут перевода в offline: 25 секунд (HEARTBEAT_TIMEOUT_MS).
 * - Предоставляет ручной [tick] для детерминированного тестирования и [start] для фонового цикла.
 */
class DeviceHeartbeatMonitor(
    private val registry: DeviceRegistry,
    val heartbeatIntervalMs: Long = DEFAULT_HEARTBEAT_INTERVAL_MS,
    val heartbeatTimeoutMs: Long = DEFAULT_HEARTBEAT_TIMEOUT_MS,
    private val timeProvider: () -> Long = { kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeMilliseconds },
    private val heartbeatSender: (suspend (endpointId: Bytes, heartbeat: LirixConnectMsg.DeviceHeartbeat) -> Unit)? = null
) {
    companion object {
        const val DEFAULT_HEARTBEAT_INTERVAL_MS = 10_000L
        const val DEFAULT_HEARTBEAT_TIMEOUT_MS = 25_000L
    }

    private var monitorJob: Job? = null

    val isRunning: Boolean get() = monitorJob?.isActive == true

    /**
     * Запускает фоновый цикл проверки таймаутов и отправки Heartbeat.
     */
    fun start(
        scope: CoroutineScope,
        activePeersProvider: () -> List<Bytes>,
        nowPlayingProvider: () -> Boolean = { false },
        batteryPctProvider: () -> Int? = { null }
    ) {
        stop()
        monitorJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            while (isActive) {
                delay(heartbeatIntervalMs)
                tick(
                    activePeers = activePeersProvider(),
                    nowPlaying = nowPlayingProvider(),
                    batteryPct = batteryPctProvider(),
                    nowEpochMs = timeProvider()
                )
            }
        }
    }

    /**
     * Выполняет одну итерацию: проверку таймаутов в реестре и отправку Heartbeat активным пирам.
     */
    suspend fun tick(
        activePeers: List<Bytes> = emptyList(),
        nowPlaying: Boolean = false,
        batteryPct: Int? = null,
        nowEpochMs: Long = timeProvider()
    ) {
        // 1. Проверяем таймауты отсутствия активности (25 с)
        registry.checkTimeouts(nowEpochMs)

        // 2. Рассылаем Heartbeat соединенным пирам
        if (heartbeatSender != null && activePeers.isNotEmpty()) {
            val heartbeat = LirixConnectMsg.DeviceHeartbeat(
                isAvailable = true,
                batteryPct = batteryPct,
                nowPlaying = nowPlaying
            )
            for (peer in activePeers) {
                heartbeatSender.invoke(peer, heartbeat)
            }
        }
    }

    /**
     * Принудительная проверка таймаутов в реестре.
     */
    fun checkTimeouts(nowEpochMs: Long = timeProvider()) {
        registry.checkTimeouts(nowEpochMs)
    }

    /**
     * Остановка фонового цикла.
     */
    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
    }
}
