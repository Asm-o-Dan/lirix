package com.example.npc.app.receiver

import com.example.npc.app.worker.AbsenceAlertConfig
import com.example.npc.app.worker.AbsenceAlertScheduler
import com.example.npc.ingest.notification.watchdog.IngestWatchdog
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [BootCompletedHandler] — the testable core of [BootCompletedReceiver].
 *
 * Verifies:
 * 1. BOOT_COMPLETED triggers both scheduler.schedulePeriodicCheck() calls.
 * 2. MY_PACKAGE_REPLACED triggers both scheduler.schedulePeriodicCheck() calls.
 * 3. An unrelated action is ignored (no scheduling).
 * 4. Null action is ignored (no scheduling).
 */
class BootCompletedReceiverTest {

    private lateinit var ingestWatchdog: IngestWatchdog
    private lateinit var absenceAlertScheduler: AbsenceAlertScheduler
    private lateinit var handler: BootCompletedHandler

    @BeforeEach
    fun setUp() {
        ingestWatchdog = mockk(relaxed = true)
        absenceAlertScheduler = mockk(relaxed = true)
        handler = BootCompletedHandler(ingestWatchdog, absenceAlertScheduler)
    }

    @Test
    fun `BOOT_COMPLETED schedules both workers and returns true`() {
        val handled = handler.handle("android.intent.action.BOOT_COMPLETED")

        handled shouldBe true
        verify(exactly = 1) { ingestWatchdog.schedulePeriodicCheck() }
        verify(exactly = 1) { absenceAlertScheduler.schedulePeriodicCheck() }
    }

    @Test
    fun `MY_PACKAGE_REPLACED schedules both workers and returns true`() {
        val handled = handler.handle("android.intent.action.MY_PACKAGE_REPLACED")

        handled shouldBe true
        verify(exactly = 1) { ingestWatchdog.schedulePeriodicCheck() }
        verify(exactly = 1) { absenceAlertScheduler.schedulePeriodicCheck() }
    }

    @Test
    fun `unrelated action is ignored — no schedulers called and returns false`() {
        val handled = handler.handle("com.malicious.ACTION")

        handled shouldBe false
        verify(exactly = 0) { ingestWatchdog.schedulePeriodicCheck() }
        verify(exactly = 0) { absenceAlertScheduler.schedulePeriodicCheck() }
    }

    @Test
    fun `null action is ignored — no schedulers called and returns false`() {
        val handled = handler.handle(null)

        handled shouldBe false
        verify(exactly = 0) { ingestWatchdog.schedulePeriodicCheck() }
        verify(exactly = 0) { absenceAlertScheduler.schedulePeriodicCheck() }
    }

    @Test
    fun `empty string action is ignored`() {
        val handled = handler.handle("")

        handled shouldBe false
        verify(exactly = 0) { ingestWatchdog.schedulePeriodicCheck() }
        verify(exactly = 0) { absenceAlertScheduler.schedulePeriodicCheck() }
    }

    @Test
    fun `BOOT_COMPLETED action is case-sensitive — wrong case is ignored`() {
        val handled = handler.handle("android.intent.action.boot_completed")

        handled shouldBe false
        verify(exactly = 0) { ingestWatchdog.schedulePeriodicCheck() }
        verify(exactly = 0) { absenceAlertScheduler.schedulePeriodicCheck() }
    }
}
