package com.lirix.app.connect

import com.lirix.app.connect.transport.ConnectState
import com.lirix.app.connect.transport.ReconnectPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReconnectPolicyTest {

    @Test
    fun testDefaultPolicyParameters() {
        val policy = ReconnectPolicy()

        assertEquals(500L, policy.initialDelayMs)
        assertEquals(1.5, policy.multiplier)
        assertEquals(5000L, policy.maxDelayMs)
        assertEquals(5, policy.maxAttempts)
        assertEquals(10_000L, policy.totalTimeoutMs)
        assertEquals(0, policy.attempt)
        assertEquals(0L, policy.accumulatedDelayMs)
        assertEquals(ConnectState.DISCONNECTED, policy.currentState)
        assertTrue(policy.canRetry())
    }

    @Test
    fun testExponentialBackoffProgressionAnd10sLimit() {
        val policy = ReconnectPolicy()

        // Attempt 1: 500 ms
        val delay1 = policy.nextDelayMs()
        assertEquals(500L, delay1)
        assertEquals(1, policy.attempt)
        assertEquals(500L, policy.accumulatedDelayMs)
        assertEquals(ConnectState.RECONNECTING, policy.currentState)
        assertTrue(policy.canRetry())

        // Attempt 2: 500 * 1.5 = 750 ms
        val delay2 = policy.nextDelayMs()
        assertEquals(750L, delay2)
        assertEquals(2, policy.attempt)
        assertEquals(1250L, policy.accumulatedDelayMs)
        assertTrue(policy.canRetry())

        // Attempt 3: 750 * 1.5 = 1125 ms
        val delay3 = policy.nextDelayMs()
        assertEquals(1125L, delay3)
        assertEquals(3, policy.attempt)
        assertEquals(2375L, policy.accumulatedDelayMs)
        assertTrue(policy.canRetry())

        // Attempt 4: 1125 * 1.5 = 1687 ms
        val delay4 = policy.nextDelayMs()
        assertEquals(1687L, delay4)
        assertEquals(4, policy.attempt)
        assertEquals(4062L, policy.accumulatedDelayMs)
        assertTrue(policy.canRetry())

        // Attempt 5: 1687.5 * 1.5 = 2531 ms
        val delay5 = policy.nextDelayMs()
        assertEquals(2531L, delay5)
        assertEquals(5, policy.attempt)
        assertEquals(6593L, policy.accumulatedDelayMs)

        // All 5 attempts completed within 10s budget (6593 ms <= 10000 ms)
        assertTrue(policy.accumulatedDelayMs <= 10_000L)
        assertFalse(policy.canRetry())

        // Attempt 6: Max attempts reached -> returns null and state becomes FAILED
        val delay6 = policy.nextDelayMs()
        assertNull(delay6)
        assertEquals(ConnectState.FAILED, policy.currentState)
    }

    @Test
    fun testTotalTimeoutMsBudgetStrictClamping() {
        // Policy with large max attempts but strict 3000 ms budget
        val policy = ReconnectPolicy(
            initialDelayMs = 1000L,
            multiplier = 2.0,
            maxDelayMs = 10_000L,
            maxAttempts = 10,
            totalTimeoutMs = 3000L
        )

        // Attempt 1: 1000 ms (accumulated 1000)
        assertEquals(1000L, policy.nextDelayMs())
        // Attempt 2: 2000 ms (accumulated 3000)
        assertEquals(2000L, policy.nextDelayMs())
        assertEquals(3000L, policy.accumulatedDelayMs)

        // Attempt 3: budget exhausted -> returns null
        assertNull(policy.nextDelayMs())
        assertEquals(ConnectState.FAILED, policy.currentState)
        assertEquals(3000L, policy.accumulatedDelayMs)
    }

    @Test
    fun testOnNetworkChangedResetsDelayAndOffersImmediateRetry() {
        val policy = ReconnectPolicy()

        // Make several failed attempts
        policy.nextDelayMs() // 500
        policy.nextDelayMs() // 750
        policy.nextDelayMs() // 1125
        assertEquals(3, policy.attempt)
        assertTrue(policy.accumulatedDelayMs > 0)

        // Network changes (e.g. Wi-Fi <-> 4G)
        val immediateDelay = policy.onNetworkChanged()
        assertEquals(0L, immediateDelay)
        assertEquals(0, policy.attempt)
        assertEquals(0L, policy.accumulatedDelayMs)
        assertEquals(ConnectState.RECONNECTING, policy.currentState)
        assertTrue(policy.canRetry())

        // Next standard retry cycle begins cleanly with initialDelayMs
        assertEquals(500L, policy.nextDelayMs())
    }

    @Test
    fun testStateTransitionsOnConnectedAndDisconnected() {
        val policy = ReconnectPolicy()

        policy.onConnecting()
        assertEquals(ConnectState.CONNECTING, policy.currentState)

        policy.onConnected()
        assertEquals(ConnectState.CONNECTED, policy.currentState)
        assertEquals(0, policy.attempt)
        assertEquals(0L, policy.accumulatedDelayMs)

        policy.onConnectionLost()
        assertEquals(ConnectState.RECONNECTING, policy.currentState)

        policy.onDisconnected()
        assertEquals(ConnectState.DISCONNECTED, policy.currentState)
    }
}
