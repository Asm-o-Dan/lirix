package com.eventengine.app

import com.eventengine.app.feature.lyrics.ChordAutoscrollCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TDD Unit-tests for TASK-CHR-01:
 * - Smart adaptive chord autoscroll calculator.
 * - Base speed calculation with known duration and total scroll height.
 * - Fallback speed calculation when duration is 0 or too short.
 * - Multiplier clamping (0.5x .. 2.5x) and stepping (+/- 0.25x).
 * - Per-frame scroll step delta calculation.
 * - Line duration calculation with fallback.
 */
class ChordAutoscrollCalculatorTest {

    @Test
    fun test_calculate_base_speed_with_valid_duration_and_scroll_height() {
        val durationMs = 200_000L // 200 seconds (3 min 20 sec)
        val totalScrollPx = 4000f
        val linesCount = 60

        val speed = ChordAutoscrollCalculator.calculateBaseSpeedPxPerSec(
            durationMs = durationMs,
            linesCount = linesCount,
            totalScrollPx = totalScrollPx
        )

        // 4000 px / 200 sec = 20.0 px/sec
        assertEquals(20.0f, speed, 0.001f)
    }

    @Test
    fun test_calculate_base_speed_fallback_when_duration_zero_or_too_short() {
        val durationMs = 0L
        val linesCount = 50
        val totalScrollPx = 2500f

        val speed = ChordAutoscrollCalculator.calculateBaseSpeedPxPerSec(
            durationMs = durationMs,
            linesCount = linesCount,
            totalScrollPx = totalScrollPx
        )

        // Estimated duration = 50 lines * 2.8 sec = 140.0 sec
        // Speed = 2500 px / 140.0 sec ≈ 17.857143 px/sec
        val expectedDurationSec = 50 * ChordAutoscrollCalculator.DEFAULT_SECONDS_PER_LINE
        val expectedSpeed = totalScrollPx / expectedDurationSec
        assertEquals(expectedSpeed, speed, 0.01f)
        assertEquals(17.857f, speed, 0.01f)
    }

    @Test
    fun test_multiplier_clamping_and_stepping() {
        // Clamp boundaries
        assertEquals(0.5f, ChordAutoscrollCalculator.clampMultiplier(0.1f), 0.001f)
        assertEquals(2.5f, ChordAutoscrollCalculator.clampMultiplier(5.0f), 0.001f)
        assertEquals(1.25f, ChordAutoscrollCalculator.clampMultiplier(1.25f), 0.001f)

        // Stepping up and down
        assertEquals(1.25f, ChordAutoscrollCalculator.nextMultiplier(1.0f, increase = true), 0.001f)
        assertEquals(0.75f, ChordAutoscrollCalculator.nextMultiplier(1.0f, increase = false), 0.001f)

        // Stepping at limits
        assertEquals(2.5f, ChordAutoscrollCalculator.nextMultiplier(2.5f, increase = true), 0.001f)
        assertEquals(0.5f, ChordAutoscrollCalculator.nextMultiplier(0.5f, increase = false), 0.001f)
    }

    @Test
    fun test_step_delta_calculation_per_frame() {
        val speedPxPerSec = 30.0f
        val frameDeltaMs = 16L // Standard ~60fps frame delta

        val delta = ChordAutoscrollCalculator.calculateStepDelta(
            effectiveSpeedPxPerSec = speedPxPerSec,
            frameDeltaMs = frameDeltaMs
        )

        // 30.0f * (16 / 1000f) = 0.48f px
        assertEquals(0.48f, delta, 0.001f)

        // Edge cases
        assertEquals(0f, ChordAutoscrollCalculator.calculateStepDelta(0f, 16L), 0.001f)
        assertEquals(0f, ChordAutoscrollCalculator.calculateStepDelta(30f, 0L), 0.001f)
    }

    @Test
    fun test_line_duration_calculation() {
        // Known duration: 180s for 60 lines = 3000ms per line
        val durationMs = 180_000L
        val linesCount = 60
        assertEquals(3000L, ChordAutoscrollCalculator.calculateLineDurationMs(durationMs, linesCount))

        // Fallback when duration is 0
        assertEquals(2800L, ChordAutoscrollCalculator.calculateLineDurationMs(0L, linesCount))
    }
}
