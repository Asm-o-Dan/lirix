package com.example.npc.app.worker

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [AbsenceAlertLogic] — the pure-logic extraction of [AbsenceAlertWorker].
 *
 * Tests the threshold comparison:
 *   - delta > thresholdMs  => checkAbsence returns true  => notification must be posted
 *   - delta <= thresholdMs => checkAbsence returns false => notification must be cancelled
 *   - lastEventEpochMs == null => checkAbsence returns true (no events ever)
 *
 * No Android APIs, no WorkManager, no mocks.
 */
class AbsenceAlertWorkerTest {

    private lateinit var logic: AbsenceAlertLogic

    private val thresholdMs = AbsenceAlertConfig().thresholdHours * 3_600_000L // 6 hours in ms

    @BeforeEach
    fun setUp() {
        logic = AbsenceAlertLogic()
    }

    // ── AbsenceAlertConfig validation ─────────────────────────────────────────

    @Nested
    inner class ConfigValidation {

        @Test
        fun `default config is valid`() {
            val config = AbsenceAlertConfig()
            config.thresholdHours shouldBe 6L
            config.checkIntervalMinutes shouldBe 30L
            config.flexIntervalMinutes shouldBe 10L
            config.suppressAlertWindowHours shouldBe 4L
        }

        @Test
        fun `custom valid config constructs successfully`() {
            val config = AbsenceAlertConfig(
                thresholdHours = 12L,
                checkIntervalMinutes = 60L,
                flexIntervalMinutes = 15L,
                suppressAlertWindowHours = 6L
            )
            config.thresholdHours shouldBe 12L
        }

        @Test
        fun `thresholdHours below 1 throws`() {
            val ex = runCatching { AbsenceAlertConfig(thresholdHours = 0L) }.exceptionOrNull()
            (ex is IllegalArgumentException) shouldBe true
        }

        @Test
        fun `checkIntervalMinutes below 15 throws`() {
            val ex = runCatching { AbsenceAlertConfig(checkIntervalMinutes = 10L) }.exceptionOrNull()
            (ex is IllegalArgumentException) shouldBe true
        }

        @Test
        fun `flexIntervalMinutes below 5 throws`() {
            val ex = runCatching {
                AbsenceAlertConfig(checkIntervalMinutes = 30L, flexIntervalMinutes = 4L)
            }.exceptionOrNull()
            (ex is IllegalArgumentException) shouldBe true
        }

        @Test
        fun `flexIntervalMinutes exceeding checkIntervalMinutes throws`() {
            val ex = runCatching {
                AbsenceAlertConfig(checkIntervalMinutes = 20L, flexIntervalMinutes = 25L)
            }.exceptionOrNull()
            (ex is IllegalArgumentException) shouldBe true
        }

        @Test
        fun `suppressAlertWindowHours below 1 throws`() {
            val ex = runCatching { AbsenceAlertConfig(suppressAlertWindowHours = 0L) }.exceptionOrNull()
            (ex is IllegalArgumentException) shouldBe true
        }
    }

    // ── checkAbsence ──────────────────────────────────────────────────────────

    @Nested
    inner class CheckAbsence {

        @Test
        fun `null lastEventEpochMs returns true (no events ever recorded)`() {
            val now = System.currentTimeMillis()
            logic.checkAbsence(
                lastEventEpochMs = null,
                currentEpochMs = now,
                thresholdMs = thresholdMs
            ) shouldBe true
        }

        @Test
        fun `delta exactly equals threshold returns false (not over threshold)`() {
            val now = System.currentTimeMillis()
            val lastEvent = now - thresholdMs
            logic.checkAbsence(
                lastEventEpochMs = lastEvent,
                currentEpochMs = now,
                thresholdMs = thresholdMs
            ) shouldBe false
        }

        @Test
        fun `delta one millisecond over threshold returns true`() {
            val now = System.currentTimeMillis()
            val lastEvent = now - thresholdMs - 1L
            logic.checkAbsence(
                lastEventEpochMs = lastEvent,
                currentEpochMs = now,
                thresholdMs = thresholdMs
            ) shouldBe true
        }

        @Test
        fun `delta 7 hours over 6-hour threshold returns true — notification must be posted`() {
            val now = 1_000_000_000_000L
            val sevenHoursAgo = now - 7 * 3_600_000L
            logic.checkAbsence(
                lastEventEpochMs = sevenHoursAgo,
                currentEpochMs = now,
                thresholdMs = thresholdMs
            ) shouldBe true
        }

        @Test
        fun `delta 2 hours under 6-hour threshold returns false — notification must be cancelled`() {
            val now = 1_000_000_000_000L
            val twoHoursAgo = now - 2 * 3_600_000L
            logic.checkAbsence(
                lastEventEpochMs = twoHoursAgo,
                currentEpochMs = now,
                thresholdMs = thresholdMs
            ) shouldBe false
        }

        @Test
        fun `delta 5h59m59s999ms under 6-hour threshold returns false`() {
            val now = 1_000_000_000_000L
            val almostSixHoursAgo = now - (6 * 3_600_000L - 1L)
            logic.checkAbsence(
                lastEventEpochMs = almostSixHoursAgo,
                currentEpochMs = now,
                thresholdMs = thresholdMs
            ) shouldBe false
        }

        @Test
        fun `lastEvent in the future relative to currentTime returns false`() {
            // e.g. user manually moved device clock backward
            val now = 1_000_000_000_000L
            val futureEvent = now + 3_600_000L
            logic.checkAbsence(
                lastEventEpochMs = futureEvent,
                currentEpochMs = now,
                thresholdMs = thresholdMs
            ) shouldBe false
        }
    }

    // ── silenceDurationHours ─────────────────────────────────────────────────

    @Nested
    inner class SilenceDurationHours {

        @Test
        fun `7 hours delta returns 7`() {
            val now = 1_000_000_000_000L
            val sevenHoursAgo = now - 7 * 3_600_000L
            logic.silenceDurationHours(sevenHoursAgo, now) shouldBe 7L
        }

        @Test
        fun `90 minutes delta returns 1 (floor division)`() {
            val now = 1_000_000_000_000L
            val ninetyMinutesAgo = now - 90 * 60_000L
            logic.silenceDurationHours(ninetyMinutesAgo, now) shouldBe 1L
        }

        @Test
        fun `exactly 6 hours delta returns 6`() {
            val now = 1_000_000_000_000L
            val sixHoursAgo = now - 6 * 3_600_000L
            logic.silenceDurationHours(sixHoursAgo, now) shouldBe 6L
        }
    }
}
