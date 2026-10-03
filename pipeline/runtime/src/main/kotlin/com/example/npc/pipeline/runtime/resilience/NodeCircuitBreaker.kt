package com.example.npc.pipeline.runtime.resilience

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class CircuitState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

class NodeCircuitBreaker(
    val stageId: String,
    val failureThreshold: Int = 3,
    val cooldownPeriodMs: Long = 5 * 60 * 1000L
) {
    init {
        require(failureThreshold > 0) { "failureThreshold must be positive: $failureThreshold" }
        require(cooldownPeriodMs > 0) { "cooldownPeriodMs must be positive: $cooldownPeriodMs" }
    }

    companion object {
        private const val STATE_CLOSED = 0L shl 62
        private const val STATE_OPEN = 1L shl 62
        private const val STATE_HALF_OPEN = 2L shl 62
        private const val TIME_MASK = (1L shl 62) - 1L
    }

    private val stateWord = AtomicLong(STATE_CLOSED)
    private val consecutiveFailures = AtomicInteger(0)

    fun shouldBypass(nowMonotonicMs: Long): Boolean {
        val w = stateWord.get()
        val stateBits = (w ushr 62) and 3L

        return when (stateBits) {
            0L -> false // CLOSED
            1L -> { // OPEN
                val openUntil = w and TIME_MASK
                if (nowMonotonicMs < openUntil) {
                    true
                } else {
                    // Cooldown elapsed -> attempt transition to HALF_OPEN
                    if (stateWord.compareAndSet(w, STATE_HALF_OPEN)) {
                        false // Granted test call
                    } else {
                        true
                    }
                }
            }
            2L -> true // HALF_OPEN: test call is in flight
            else -> false
        }
    }

    fun recordSuccess() {
        consecutiveFailures.set(0)
        stateWord.set(STATE_CLOSED)
    }

    fun recordFailure(
        nowMonotonicMs: Long,
        onTrip: ((stageId: String, consecutiveFailures: Int) -> Unit)? = null
    ): CircuitState {
        val fails = consecutiveFailures.incrementAndGet()
        val currentStateBits = (stateWord.get() ushr 62) and 3L

        if (fails >= failureThreshold || currentStateBits == 2L) { // trip or failed probe in HALF_OPEN
            val openUntil = (nowMonotonicMs + cooldownPeriodMs) and TIME_MASK
            stateWord.set(STATE_OPEN or openUntil)
            onTrip?.invoke(stageId, fails)
            return CircuitState.OPEN
        }

        return when (currentStateBits) {
            0L -> CircuitState.CLOSED
            1L -> CircuitState.OPEN
            else -> CircuitState.HALF_OPEN
        }
    }

    fun getState(nowMonotonicMs: Long): CircuitState {
        val w = stateWord.get()
        val stateBits = (w ushr 62) and 3L
        return when (stateBits) {
            0L -> CircuitState.CLOSED
            1L -> {
                val openUntil = w and TIME_MASK
                if (nowMonotonicMs >= openUntil) CircuitState.HALF_OPEN else CircuitState.OPEN
            }
            else -> CircuitState.HALF_OPEN
        }
    }

    fun reset() {
        consecutiveFailures.set(0)
        stateWord.set(STATE_CLOSED)
    }
}
