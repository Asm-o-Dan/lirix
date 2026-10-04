package com.eventengine.app.feature.lyrics

/**
 * Deterministic mathematical calculator for adaptive chord autoscroll speed and step deltas.
 * Spec: TASK-CHR-01 / .sdd/tasks/TASK-CHR-01.md
 */
object ChordAutoscrollCalculator {
    const val DEFAULT_SECONDS_PER_LINE = 2.8f
    const val MIN_MULTIPLIER = 0.5f
    const val MAX_MULTIPLIER = 2.5f
    const val STEP_MULTIPLIER = 0.25f

    fun calculateBaseSpeedPxPerSec(
        durationMs: Long,
        linesCount: Int,
        totalScrollPx: Float,
        estimatedLineHeightPx: Float = 50f
    ): Float {
        if (totalScrollPx <= 0f) return 0f
        if (durationMs > 10_000L) {
            val durationSec = durationMs / 1000f
            return totalScrollPx / durationSec
        }
        val safeLines = if (linesCount > 0) linesCount else (totalScrollPx / estimatedLineHeightPx).toInt().coerceAtLeast(1)
        val estimatedDurationSec = safeLines * DEFAULT_SECONDS_PER_LINE
        return totalScrollPx / estimatedDurationSec
    }

    fun calculateLineDurationMs(durationMs: Long, linesCount: Int): Long {
        if (durationMs > 10_000L && linesCount > 0) {
            return durationMs / linesCount
        }
        return (DEFAULT_SECONDS_PER_LINE * 1000).toLong()
    }

    fun clampMultiplier(multiplier: Float): Float {
        return multiplier.coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)
    }

    fun nextMultiplier(current: Float, increase: Boolean): Float {
        val next = if (increase) current + STEP_MULTIPLIER else current - STEP_MULTIPLIER
        return (Math.round(next * 100) / 100f).coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER)
    }

    fun calculateStepDelta(effectiveSpeedPxPerSec: Float, frameDeltaMs: Long): Float {
        if (effectiveSpeedPxPerSec <= 0f || frameDeltaMs <= 0L) return 0f
        return effectiveSpeedPxPerSec * (frameDeltaMs / 1000f)
    }
}
