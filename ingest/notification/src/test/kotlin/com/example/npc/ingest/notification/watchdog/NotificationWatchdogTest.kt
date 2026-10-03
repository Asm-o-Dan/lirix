package com.example.npc.ingest.notification.watchdog

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class NotificationWatchdogTest {

    @Test
    fun `checkHeartbeat returns true when last pulse is within threshold`() {
        val now = 1_700_001_800_000L
        val lastPulse = 1_700_001_200_000L // 10 minutes ago
        val threshold = 1_800_000L // 30 minutes

        val result = NotificationWatchdogWorker.checkHeartbeat(lastPulse, now, threshold)
        result shouldBe true
    }

    @Test
    fun `checkHeartbeat returns false when last pulse exceeds threshold`() {
        val now = 1_700_002_000_000L
        val lastPulse = 1_700_000_000_000L // 33.3 minutes ago
        val threshold = 1_800_000L // 30 minutes

        val result = NotificationWatchdogWorker.checkHeartbeat(lastPulse, now, threshold)
        result shouldBe false
    }

    @Test
    fun `checkHeartbeat returns true when time is set back`() {
        val now = 1_700_000_000_000L
        val lastPulse = 1_700_000_500_000L // lastPulse in future due to clock change
        val threshold = 1_800_000L

        val result = NotificationWatchdogWorker.checkHeartbeat(lastPulse, now, threshold)
        result shouldBe true
    }

    @Test
    fun `checkHeartbeat returns false when last pulse is 0 or negative`() {
        val result = NotificationWatchdogWorker.checkHeartbeat(0L, 1_700_000_000_000L, 1_800_000L)
        result shouldBe false
    }
}
