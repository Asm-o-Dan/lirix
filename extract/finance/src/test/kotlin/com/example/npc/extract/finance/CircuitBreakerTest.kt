package com.example.npc.extract.finance

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CircuitBreakerTest {

    private lateinit var circuitBreaker: CircuitBreaker

    @BeforeEach
    fun setUp() {
        circuitBreaker = CircuitBreaker(
            failureThreshold = 3,
            cooldownDurationMs = 10 * 60 * 1000L // 10 minutes
        )
    }

    @Test
    fun `initial state is CLOSED and canExecute returns true`() {
        assertTrue(circuitBreaker.canExecute())
    }

    @Test
    fun `records successes and keeps state CLOSED`() {
        circuitBreaker.recordSuccess()
        circuitBreaker.recordSuccess()

        assertTrue(circuitBreaker.canExecute())
    }

    @Test
    fun `transitions to OPEN state after 3 consecutive failures`() {
        val now = 1_000_000L

        circuitBreaker.recordFailure(now)
        assertTrue(circuitBreaker.canExecute(now), "1st failure: circuit should still be CLOSED")

        circuitBreaker.recordFailure(now)
        assertTrue(circuitBreaker.canExecute(now), "2nd failure: circuit should still be CLOSED")

        circuitBreaker.recordFailure(now)
        assertFalse(circuitBreaker.canExecute(now), "3rd failure: circuit must trip to OPEN")
    }

    @Test
    fun `remains OPEN during cooldown period`() {
        val tripTime = 1_000_000L
        repeat(3) { circuitBreaker.recordFailure(tripTime) }

        // 5 minutes later (cooldown is 10 minutes)
        val duringCooldown = tripTime + (5 * 60 * 1000L)
        assertFalse(circuitBreaker.canExecute(duringCooldown), "Circuit must reject execution during cooldown")
    }

    @Test
    fun `transitions to HALF_OPEN and allows trial execution after cooldown expires`() {
        val tripTime = 1_000_000L
        repeat(3) { circuitBreaker.recordFailure(tripTime) }

        // 10 minutes + 1 ms later
        val afterCooldown = tripTime + (10 * 60 * 1000L) + 1L
        assertTrue(circuitBreaker.canExecute(afterCooldown), "Circuit must allow trial execution after cooldown")

        // Successful trial execution recovers to CLOSED
        circuitBreaker.recordSuccess()
        assertTrue(circuitBreaker.canExecute(afterCooldown + 1000L))
    }

    @Test
    fun `success resets consecutive failure count`() {
        circuitBreaker.recordFailure()
        circuitBreaker.recordFailure()
        // Successful operation resets count
        circuitBreaker.recordSuccess()

        // 2 more failures should not trip because count was reset
        circuitBreaker.recordFailure()
        circuitBreaker.recordFailure()
        assertTrue(circuitBreaker.canExecute())
    }
}
