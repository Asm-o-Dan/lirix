package com.lirix.app.connect.qa

import com.lirix.app.connect.cast.CastDatagram
import kotlin.random.Random

/**
 * Модель симуляции одного сетевого пакета с задержкой доставки.
 */
data class SimulatedPacket<T>(
    val payload: T,
    val delayMs: Long,
    val isDropped: Boolean = false
)

/**
 * Результат симуляции NTP Ping-Pong обмена через сеть с аномалиями.
 */
data class SimulatedNtpRoundtrip(
    val t0: Long,
    val t1: Long,
    val t2: Long,
    val t3: Long,
    val uplinkDelayMs: Long,
    val downlinkDelayMs: Long
)

/**
 * Симулятор сетевых аномалий для интеграционных тестов Lirix Connect (SPEC-CONNECT-01 §7, TASK-CON-QA-13).
 *
 * Позволяет эмулировать:
 * - базовую сетевую задержку (RTT / latency);
 * - джиттер (случайные колебания задержки пакетов);
 * - асимметрию каналов (разная задержка uplink / downlink);
 * - случайные или детерминированные потери пакетов (packet loss rate);
 * - полный обрыв связи (сетевой раздел / network partition);
 * - смену сетевого интерфейса (Wi-Fi <-> LTE).
 */
class NetworkAnomalySimulator(
    var baseLatencyMs: Long = 15L,
    var jitterMs: Long = 5L,
    var packetLossRate: Double = 0.0,
    var isAsymmetric: Boolean = false,
    var downlinkMultiplier: Double = 1.0,
    private val random: Random = Random(42) // Детерминированный seed для воспроизводимости CI
) {
    var isPartitioned: Boolean = false
    var totalPacketsSent: Int = 0
        private set
    var totalPacketsDropped: Int = 0
        private set

    /**
     * Проверяет, должен ли пакет быть потерян на основе packetLossRate или обрыва сети.
     */
    fun shouldDrop(): Boolean {
        if (isPartitioned) return true
        if (packetLossRate <= 0.0) return false
        return random.nextDouble() < packetLossRate
    }

    /**
     * Вычисляет задержку доставки с учётом джиттера и асимметрии.
     */
    fun calculateDelayMs(isDownlink: Boolean = false): Long {
        val jitterOffset = if (jitterMs > 0) {
            random.nextLong(-jitterMs, jitterMs + 1)
        } else {
            0L
        }

        val effectiveBase = if (isDownlink && isAsymmetric) {
            (baseLatencyMs * downlinkMultiplier).toLong()
        } else {
            baseLatencyMs
        }

        return (effectiveBase + jitterOffset).coerceAtLeast(1L)
    }

    /**
     * Симуляция передачи отдельного объекта (сообщения или датаграммы).
     */
    fun <T> transmit(payload: T, isDownlink: Boolean = false): SimulatedPacket<T>? {
        totalPacketsSent++
        if (shouldDrop()) {
            totalPacketsDropped++
            return null
        }
        val delay = calculateDelayMs(isDownlink)
        return SimulatedPacket(payload, delayMs = delay, isDropped = false)
    }

    /**
     * Симулирует передачу потока датаграмм с потерями (например, для проверки PLC Audio Cast).
     */
    fun transmitDatagramStream(datagrams: List<CastDatagram>): List<CastDatagram> {
        val received = mutableListOf<CastDatagram>()
        for (dg in datagrams) {
            val pkt = transmit(dg)
            if (pkt != null) {
                received.add(pkt.payload)
            }
        }
        return received
    }

    /**
     * Симуляция сетевого обмена NTP Ping/Pong между клиентом и сервером с заданным смещением часов.
     */
    fun simulateNtpExchange(
        clientMonoMs: Long,
        trueServerClockOffsetMs: Long,
        serverProcessingMs: Long = 2L
    ): SimulatedNtpRoundtrip? {
        val upDelay = calculateDelayMs(isDownlink = false)
        if (shouldDrop()) return null

        val t0 = clientMonoMs
        val t1 = t0 + trueServerClockOffsetMs + upDelay
        val t2 = t1 + serverProcessingMs

        val downDelay = calculateDelayMs(isDownlink = true)
        if (shouldDrop()) return null

        val t3 = t0 + upDelay + serverProcessingMs + downDelay

        return SimulatedNtpRoundtrip(
            t0 = t0,
            t1 = t1,
            t2 = t2,
            t3 = t3,
            uplinkDelayMs = upDelay,
            downlinkDelayMs = downDelay
        )
    }

    /**
     * Сброс статистики симулятора.
     */
    fun resetStats() {
        totalPacketsSent = 0
        totalPacketsDropped = 0
        isPartitioned = false
    }
}
