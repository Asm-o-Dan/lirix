package com.lirix.app.connect.time

import com.lirix.app.connect.proto.LirixConnectMsg
import kotlin.math.abs

/**
 * Одиночный замер сетевой задержки NTP 4-timestamp (SPEC-CONNECT-01 §4.5).
 *
 * @param t0 Время отправки Ping клиентом (монотонные часы клиента)
 * @param t1 Время приёма Ping сервером (монотонные часы сервера)
 * @param t2 Время отправки Pong сервером (монотонные часы сервера)
 * @param t3 Время приёма Pong клиентом (монотонные часы клиента)
 */
data class NtpSample(
    val t0: Long,
    val t1: Long,
    val t2: Long,
    val t3: Long
) {
    /**
     * Round-Trip Time (RTT) за вычетом времени обработки на сервере (t2 - t1).
     */
    val rtt: Long get() = (t3 - t0) - (t2 - t1)

    /**
     * Оценка смещения часов сервера относительно клиента:
     * offset = ((t1 - t0) + (t2 - t3)) / 2
     *
     * Соответственно: serverTime ≈ clientTime + offset.
     */
    val offset: Long get() = ((t1 - t0) + (t2 - t3)) / 2
}

/**
 * Движок синхронизации часов Lirix Connect (SPEC-CONNECT-01 §4.5, TASK-CON-TIME-04).
 *
 * Собирает серии замеров Ping/Pong (5-8 образцов), фильтрует выбросы и аномалии,
 * и вычисляет смещение часов по замеру с минимальным RTT.
 */
class ClockSyncEngine(
    val windowSize: Int = DEFAULT_WINDOW_SIZE,
    val maxValidRttMs: Long = DEFAULT_MAX_VALID_RTT_MS
) {
    companion object {
        const val DEFAULT_WINDOW_SIZE = 8
        const val DEFAULT_MAX_VALID_RTT_MS = 2000L // отсечка явных сетевых таймаутов
        const val MIN_SAMPLES_FOR_SYNC = 3
    }

    private val samples = mutableListOf<NtpSample>()

    val sampleCount: Int get() = samples.size

    val allSamples: List<NtpSample> get() = samples.toList()

    /**
     * Замер с минимальным RTT среди валидных (наименьшее сетевое дрожание).
     */
    val bestSample: NtpSample?
        get() = samples
            .filter { it.rtt >= 0 && it.rtt <= maxValidRttMs }
            .minByOrNull { it.rtt }

    /**
     * Текущая оценка смещения часов (offset) по лучшему замеру с минимальным RTT.
     * Возвращает 0L, если замеров ещё нет.
     */
    val estimatedOffset: Long
        get() = bestSample?.offset ?: 0L

    /**
     * Минимальный зафиксированный RTT в миллисекундах.
     */
    val minRtt: Long?
        get() = bestSample?.rtt

    /**
     * Флаг готовности синхронизации (накоплено не менее MIN_SAMPLES_FOR_SYNC валидных замеров).
     */
    val isSynchronized: Boolean
        get() = samples.count { it.rtt >= 0 && it.rtt <= maxValidRttMs } >= MIN_SAMPLES_FOR_SYNC

    /**
     * Добавление готового замера NTP 4-timestamp.
     */
    fun addSample(t0: Long, t1: Long, t2: Long, t3: Long): NtpSample {
        val sample = NtpSample(t0, t1, t2, t3)
        if (samples.size >= windowSize) {
            samples.removeAt(0)
        }
        samples.add(sample)
        return sample
    }

    /**
     * Генерация исходящего Ping для клиента.
     */
    fun createPing(currentClientMonoMs: Long): LirixConnectMsg.Ping {
        return LirixConnectMsg.Ping(t0 = currentClientMonoMs)
    }

    /**
     * Обработка Ping на стороне сервера и формирование ответа Pong.
     */
    fun handlePingOnServer(
        ping: LirixConnectMsg.Ping,
        receiveServerMonoMs: Long,
        sendServerMonoMs: Long = receiveServerMonoMs
    ): LirixConnectMsg.Pong {
        return LirixConnectMsg.Pong(
            t0 = ping.t0,
            t1 = receiveServerMonoMs,
            t2 = sendServerMonoMs
        )
    }

    /**
     * Обработка полученного Pong на стороне клиента с фиксацией t3.
     */
    fun handlePongOnClient(
        pong: LirixConnectMsg.Pong,
        receiveClientMonoMs: Long
    ): NtpSample {
        return addSample(
            t0 = pong.t0,
            t1 = pong.t1,
            t2 = pong.t2,
            t3 = receiveClientMonoMs
        )
    }

    /**
     * Сброс всех накопленных образцов (например, при смене сети или начале новой сессии).
     */
    fun clear() {
        samples.clear()
    }
}
