package com.example.npc.pipeline.runtime.resilience

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NodeCircuitBreakerTest {

    @Test
    fun closed_allowsCalls() {
        val breaker = NodeCircuitBreaker("test-stage", failureThreshold = 3, cooldownPeriodMs = 1000L)
        assertFalse(breaker.shouldBypass(nowMonotonicMs = 100L))
        assertEquals(CircuitState.CLOSED, breaker.getState(100L))
    }

    @Test
    fun tripsToOpen_afterThresholdFailures() {
        var tripReported = false
        val breaker = NodeCircuitBreaker("test-stage", failureThreshold = 3, cooldownPeriodMs = 1000L)

        breaker.recordFailure(100L) { _, _ -> tripReported = true }
        assertEquals(CircuitState.CLOSED, breaker.getState(100L))
        assertFalse(tripReported)

        breaker.recordFailure(110L) { _, _ -> tripReported = true }
        assertEquals(CircuitState.CLOSED, breaker.getState(110L))
        assertFalse(tripReported)

        val state3 = breaker.recordFailure(120L) { stage, count ->
            tripReported = true
            assertEquals("test-stage", stage)
            assertEquals(3, count)
        }

        assertEquals(CircuitState.OPEN, state3)
        assertTrue(tripReported)
        assertTrue(breaker.shouldBypass(nowMonotonicMs = 200L))
    }

    @Test
    fun cooldownExpiration_transitionsToHalfOpen() {
        val breaker = NodeCircuitBreaker("test-stage", failureThreshold = 2, cooldownPeriodMs = 500L)

        breaker.recordFailure(100L)
        breaker.recordFailure(100L) // Tripped to OPEN until 600L

        // Before cooldown
        assertTrue(breaker.shouldBypass(nowMonotonicMs = 599L))

        // At cooldown expiry -> single test probe allowed
        assertFalse(breaker.shouldBypass(nowMonotonicMs = 600L)) // 1 probe admitted

        // Subsequent call while probe in flight should bypass
        assertTrue(breaker.shouldBypass(nowMonotonicMs = 601L))

        // Success closes breaker
        breaker.recordSuccess()
        assertEquals(CircuitState.CLOSED, breaker.getState(602L))
        assertFalse(breaker.shouldBypass(602L))
    }

    @Test
    fun probeFailure_reopensCircuit() {
        val breaker = NodeCircuitBreaker("test-stage", failureThreshold = 2, cooldownPeriodMs = 500L)

        breaker.recordFailure(100L)
        breaker.recordFailure(100L) // Open until 600L

        // Probe allowed at 600L
        assertFalse(breaker.shouldBypass(600L))

        // Probe fails -> reopens
        val state = breaker.recordFailure(605L)
        assertEquals(CircuitState.OPEN, state)
        assertTrue(breaker.shouldBypass(610L))
    }
}
