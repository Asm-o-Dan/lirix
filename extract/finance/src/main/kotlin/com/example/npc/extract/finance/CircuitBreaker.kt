package com.example.npc.extract.finance

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Предохранитель времени выполнения и сбоев (Circuit Breaker).
 * Защищает конвейер от ReDoS и зависаний парсеров (лимит времени budget 50ms, 3 trips -> OPEN, cooldown 10 минут).
 */
class CircuitBreaker(
    val failureThreshold: Int = 3,
    cooldownDurationMs: Long = 10 * 60 * 1000L,
    val timeBudgetMs: Long = 50L,
    cooldownMs: Long = cooldownDurationMs
) {
    val cooldownDurationMs: Long = if (cooldownMs != 10 * 60 * 1000L && cooldownDurationMs == 10 * 60 * 1000L) {
        cooldownMs
    } else {
        cooldownDurationMs
    }

    val cooldownMs: Long get() = this.cooldownDurationMs

    enum class State { CLOSED, OPEN, HALF_OPEN }

    private val state = AtomicReference(State.CLOSED)
    private val consecutiveFailures = AtomicInteger(0)
    private val lastOpenedTimestamp = AtomicLong(0L)

    fun canExecute(currentTimeMs: Long = System.currentTimeMillis()): Boolean {
        return when (state.get()) {
            State.CLOSED -> true
            State.OPEN -> {
                val openedAt = lastOpenedTimestamp.get()
                if (currentTimeMs - openedAt > this.cooldownDurationMs) {
                    state.compareAndSet(State.OPEN, State.HALF_OPEN)
                    true
                } else {
                    false
                }
            }
            State.HALF_OPEN -> true
        }
    }

    fun recordSuccess() {
        consecutiveFailures.set(0)
        state.set(State.CLOSED)
    }

    fun recordFailure(currentTimeMs: Long = System.currentTimeMillis()) {
        val failures = consecutiveFailures.incrementAndGet()
        if (failures >= failureThreshold) {
            state.set(State.OPEN)
            lastOpenedTimestamp.set(currentTimeMs)
        }
    }

    fun isCircuitOpen(extractorId: String = ""): Boolean = !canExecute()

    fun reset(extractorId: String = "") {
        recordSuccess()
    }

    fun <T> execute(extractorId: String = "", block: () -> T): Result<T> {
        val now = System.currentTimeMillis()
        if (!canExecute(now)) {
            return Result.failure(IllegalStateException("CircuitBreaker is OPEN for extractor: $extractorId"))
        }

        val startTime = System.currentTimeMillis()
        return try {
            val result = block()
            val elapsed = System.currentTimeMillis() - startTime
            if (elapsed > timeBudgetMs) {
                recordFailure(System.currentTimeMillis())
                Result.failure(IllegalStateException("Execution timeout exceeded budget of ${timeBudgetMs}ms (took ${elapsed}ms)"))
            } else {
                recordSuccess()
                Result.success(result)
            }
        } catch (t: Throwable) {
            recordFailure(System.currentTimeMillis())
            Result.failure(t)
        }
    }
}
