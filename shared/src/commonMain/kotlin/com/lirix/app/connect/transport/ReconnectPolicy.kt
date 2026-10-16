package com.lirix.app.connect.transport

import kotlin.math.min
import kotlin.math.pow

/**
 * Политика и стратегия автоматического переподключения (SPEC-CONNECT-01 §4.1, TASK-CON-CORE-02).
 *
 * Инварианты:
 * - Начальная задержка: 500 мс.
 * - Множитель экспоненты: 1.5.
 * - Максимальная задержка одной попытки: 5000 мс.
 * - Максимум попыток: 5.
 * - Суммарный интервал ожидания: <= 10 000 мс (10 секунд).
 * - При смене сети (NetworkChanged): немедленный сброс таймера задержки (0 мс)
 *   и запуск форсированной попытки подключения.
 */
class ReconnectPolicy(
    val initialDelayMs: Long = DEFAULT_INITIAL_DELAY_MS,
    val multiplier: Double = DEFAULT_MULTIPLIER,
    val maxDelayMs: Long = DEFAULT_MAX_DELAY_MS,
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val totalTimeoutMs: Long = DEFAULT_TOTAL_TIMEOUT_MS
) {
    companion object {
        const val DEFAULT_INITIAL_DELAY_MS = 500L
        const val DEFAULT_MULTIPLIER = 1.5
        const val DEFAULT_MAX_DELAY_MS = 5000L
        const val DEFAULT_MAX_ATTEMPTS = 5
        const val DEFAULT_TOTAL_TIMEOUT_MS = 10_000L
    }

    var attempt: Int = 0
        private set

    var currentDelayMs: Long = initialDelayMs
        private set

    var accumulatedDelayMs: Long = 0L
        private set

    var currentState: ConnectState = ConnectState.DISCONNECTED
        private set

    val hasAttemptsRemaining: Boolean
        get() = attempt < maxAttempts && accumulatedDelayMs < totalTimeoutMs

    fun canRetry(): Boolean = hasAttemptsRemaining

    /**
     * Вычисляет теоретическую задержку для попытки с заданным индексом (0-based)
     * с учетом множителя и maxDelayMs, без учета накопленного бюджета totalTimeoutMs.
     */
    fun calculateDelayForAttempt(attemptIndex: Int): Long {
        if (attemptIndex <= 0) return initialDelayMs
        val factor = multiplier.pow(attemptIndex.toDouble())
        val calculated = (initialDelayMs * factor).toLong()
        return min(calculated, maxDelayMs)
    }

    /**
     * Возвращает задержку (в миллисекундах) перед следующей попыткой переподключения
     * либо null, если лимит попыток или суммарный 10-секундный бюджет исчерпан.
     *
     * Гарантирует, что накопленная задержка никогда не превысит totalTimeoutMs (10с).
     */
    fun nextDelayMs(): Long? {
        if (!hasAttemptsRemaining) {
            currentState = ConnectState.FAILED
            return null
        }

        val calculated = calculateDelayForAttempt(attempt)
        val remainingBudget = totalTimeoutMs - accumulatedDelayMs

        if (remainingBudget <= 0) {
            currentState = ConnectState.FAILED
            return null
        }

        val delay = min(calculated, remainingBudget)

        attempt++
        currentDelayMs = delay
        accumulatedDelayMs += delay
        currentState = ConnectState.RECONNECTING

        return delay
    }

    /**
     * Обработка события потери соединения.
     * Переводит состояние в RECONNECTING.
     */
    fun onConnectionLost() {
        currentState = ConnectState.RECONNECTING
    }

    /**
     * Успешное подключение. Сбрасывает счетчики попыток и накопленного времени.
     */
    fun onConnected() {
        reset()
        currentState = ConnectState.CONNECTED
    }

    /**
     * Сбрасывает все счетчики в начальное состояние.
     */
    fun reset() {
        attempt = 0
        currentDelayMs = initialDelayMs
        accumulatedDelayMs = 0L
    }

    /**
     * Обработка события смены сети (Wi-Fi <-> 4G / NetworkChanged).
     *
     * Немедленно сбрасывает экспоненциальный таймер, обнуляет накопленные задержки
     * и возвращает 0 мс для форсированной немедленной попытки соединения по новой топологии.
     */
    fun onNetworkChanged(): Long {
        reset()
        currentState = ConnectState.RECONNECTING
        return 0L
    }

    /**
     * Перевод в состояние DISCONNECTED (например, при явном отключении пользователем).
     */
    fun onDisconnected() {
        reset()
        currentState = ConnectState.DISCONNECTED
    }

    /**
     * Перевод в состояние CONNECTING (ручной старт первичного подключения).
     */
    fun onConnecting() {
        reset()
        currentState = ConnectState.CONNECTING
    }
}
