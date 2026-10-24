package com.lirix.app.feature.share.animation

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.lirix.app.analytics.TimeOfDaySlot
import kotlin.math.roundToInt

/**
 * Animated poster renderer for "Wrapped Pulse / Bar Growth" (SPEC-ANIM-POSTER-01 §4.3).
 *
 * Timeline (t in [0.0 .. 1.0]):
 * - t in [0.00 .. 0.15]: Header "Когда звучала музыка" fade-in.
 * - t in [0.15 .. 0.70]: 4 vertical RoundRect bars grow with springBounce, stagger 0.10:
 *     - NIGHT: t in [0.15 .. 0.40]
 *     - MORNING: t in [0.25 .. 0.50]
 *     - AFTERNOON: t in [0.35 .. 0.60]
 *     - EVENING: t in [0.45 .. 0.70]
 * - t in [0.65 .. 0.80]: Percentage labels fade-in above bars.
 * - t in [0.80 .. 1.00]: Glow sweep line/band sweeps left-to-right across the bars.
 */
class PulseBarAnimator(
    palette: ThemeAnimationPalette,
    val distribution: Map<TimeOfDaySlot, Float>
) : PosterAnimator(palette) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barRect = RectF()

    fun getHeaderAlpha(t: Float): Int {
        if (t <= 0f) return 0
        if (t >= 0.15f) return 255
        return ((t / 0.15f) * 255f).roundToInt().coerceIn(0, 255)
    }

    fun getPercentAlpha(t: Float): Int {
        if (t <= 0.65f) return 0
        if (t >= 0.80f) return 255
        return (((t - 0.65f) / 0.15f) * 255f).roundToInt().coerceIn(0, 255)
    }

    fun getBarHeight(slot: TimeOfDaySlot, t: Float, maxH: Float): Float {
        val fraction = distribution[slot] ?: 0f
        val startT = when (slot) {
            TimeOfDaySlot.NIGHT -> 0.15f
            TimeOfDaySlot.MORNING -> 0.25f
            TimeOfDaySlot.AFTERNOON -> 0.35f
            TimeOfDaySlot.EVENING -> 0.45f
        }
        val endT = startT + 0.25f
        if (t <= startT) return 0f
        if (t >= endT) return fraction * maxH

        val localT = ((t - startT) / 0.25f).coerceIn(0f, 1f)
        val bounce = springBounce(localT)
        return fraction * maxH * bounce
    }

    fun getSweepProgress(t: Float): Float {
        if (t < 0.80f) return 0f
        if (t >= 1.00f) return 1f
        return (t - 0.80f) / 0.20f
    }

    override fun renderFrame(canvas: Canvas, t: Float, width: Int, height: Int) {
        val tClamped = t.coerceIn(0f, 1f)

        // 1. Background
        bgPaint.color = palette.background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (tClamped <= 0f) {
            // DoD: at t=0 nothing drawn except background
            return
        }

        val minDim = minOf(width, height).toFloat()
        val padX = width * 0.08f
        val headerY = height * 0.12f

        // 2. Header
        val headerAlpha = getHeaderAlpha(tClamped)
        if (headerAlpha > 0) {
            val baseTextPrimary = palette.textPrimary and 0x00FFFFFF
            headerPaint.color = baseTextPrimary or (headerAlpha shl 24)
            headerPaint.textSize = minDim * 0.065f
            canvas.drawText("Когда звучала музыка", padX, headerY, headerPaint)
        }

        // 3. Bars
        val slots = listOf(
            TimeOfDaySlot.NIGHT to "Ночью",
            TimeOfDaySlot.MORNING to "Утром",
            TimeOfDaySlot.AFTERNOON to "Днём",
            TimeOfDaySlot.EVENING to "Вечером"
        )

        val availableW = width - padX * 2f
        val spacing = minDim * 0.04f
        val barW = (availableW - spacing * (slots.size - 1)) / slots.size
        val baseLineY = height * 0.72f
        val maxBarH = height * 0.42f
        val cornerRadius = barW * 0.25f

        val percentAlpha = getPercentAlpha(tClamped)

        for (i in slots.indices) {
            val (slot, label) = slots[i]
            val left = padX + i * (barW + spacing)
            val right = left + barW
            val centerX = (left + right) / 2f

            // Background guide track
            barBgPaint.color = palette.surface
            barRect.set(left, baseLineY - maxBarH, right, baseLineY)
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barBgPaint)

            // Dynamic growing bar
            val currentH = getBarHeight(slot, tClamped, maxBarH).coerceAtLeast(0f)
            if (currentH > 0f) {
                val top = baseLineY - currentH
                barRect.set(left, top, right, baseLineY)

                barPaint.shader = LinearGradient(
                    centerX, top,
                    centerX, baseLineY,
                    palette.accent,
                    palette.surface,
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint)

                // Percent label above bar
                if (percentAlpha > 0) {
                    val fraction = distribution[slot] ?: 0f
                    val percentText = "${(fraction * 100).roundToInt()}%"
                    val baseAccent = palette.accent and 0x00FFFFFF
                    percentPaint.color = baseAccent or (percentAlpha shl 24)
                    percentPaint.textSize = minDim * 0.038f
                    canvas.drawText(percentText, centerX, top - minDim * 0.02f, percentPaint)
                }
            }

            // Subtitle label below bar
            labelPaint.color = palette.textSecondary
            labelPaint.textSize = minDim * 0.032f
            canvas.drawText(label, centerX, baseLineY + minDim * 0.05f, labelPaint)
        }

        // 4. Glow sweep
        if (tClamped in 0.80f..1.00f) {
            val sweepP = getSweepProgress(tClamped)
            val sweepX = padX + sweepP * availableW
            val sweepWidth = minDim * 0.12f

            sweepPaint.shader = LinearGradient(
                sweepX - sweepWidth, 0f,
                sweepX + sweepWidth, 0f,
                intArrayOf(palette.glowColor and 0x00FFFFFF, palette.glowColor, palette.glowColor and 0x00FFFFFF),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(sweepX - sweepWidth, baseLineY - maxBarH, sweepX + sweepWidth, baseLineY, sweepPaint)
        }
    }
}
