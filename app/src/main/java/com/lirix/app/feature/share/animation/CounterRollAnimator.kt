package com.lirix.app.feature.share.animation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Animated poster renderer for "Wrapped Counter / Odometer" (SPEC-ANIM-POSTER-01 §4.2).
 *
 * Timeline (t in [0.0 .. 1.0]):
 * - t in [0.0 .. 0.10]: Fade-in of header "Итоги"
 * - t in [0.10 .. 0.25]: Period label slide-in from left (translateX from -width to 0 with ease-out)
 * - t in [0.25 .. 0.75]: Three odometers roll simultaneously:
 *     - Minutes: ease-out-cubic (quick stop)
 *     - Tracks: ease-out-quad (medium)
 *     - Artists: ease-out-sine (gentle)
 * - t in [0.75 .. 0.85]: Top 3 tracks cascade in from bottom (translateY +100 to 0, staggered)
 * - t in [0.85 .. 1.00]: Glow pulse on accent numbers
 */
class CounterRollAnimator(
    palette: ThemeAnimationPalette,
    val totalMinutes: Int,
    val uniqueTracks: Int,
    val uniqueArtists: Int,
    val periodLabel: String,
    val topTracks: List<Triple<String, String, Int>>,
    val labels: CounterRollLabels = CounterRollLabels.ENGLISH
) : PosterAnimator(palette) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val periodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    private val counterNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val counterLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val trackRankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val trackTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private val trackSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    private val clipRect = Rect()

    fun getHeaderAlpha(t: Float): Int {
        if (t <= 0f) return 0
        if (t >= 0.10f) return 255
        return ((t / 0.10f) * 255f).roundToInt().coerceIn(0, 255)
    }

    fun getPeriodSlideOffset(t: Float, width: Float): Float {
        if (t <= 0.10f) return -width
        if (t >= 0.25f) return 0f
        val localT = (t - 0.10f) / 0.15f
        // Ease-out quad: 1 - (1 - p)^2
        val easeOut = 1f - (1f - localT) * (1f - localT)
        return -width * (1f - easeOut)
    }

    fun getMinutesProgress(t: Float): Float {
        if (t <= 0.25f) return 0f
        if (t >= 0.75f) return 1f
        val localT = (t - 0.25f) / 0.50f
        // Ease-out cubic: 1 - (1 - p)^3
        val inv = 1f - localT
        return 1f - inv * inv * inv
    }

    fun getTracksProgress(t: Float): Float {
        if (t <= 0.25f) return 0f
        if (t >= 0.75f) return 1f
        val localT = (t - 0.25f) / 0.50f
        // Ease-out quad: 1 - (1 - p)^2
        val inv = 1f - localT
        return 1f - inv * inv
    }

    fun getArtistsProgress(t: Float): Float {
        if (t <= 0.25f) return 0f
        if (t >= 0.75f) return 1f
        val localT = (t - 0.25f) / 0.50f
        // Ease-out sine: sin(p * PI / 2)
        return sin(localT * (PI / 2.0)).toFloat()
    }

    fun getTopTrackCascade(t: Float, rankIndex: Int): Pair<Float, Int> {
        val startT = 0.75f + rankIndex * 0.03f
        val endT = (startT + 0.07f).coerceAtMost(1f)
        if (t <= startT) return Pair(100f, 0)
        if (t >= endT) return Pair(0f, 255)
        val localT = (t - startT) / (endT - startT)
        val easeOut = 1f - (1f - localT) * (1f - localT)
        val translateY = 100f * (1f - easeOut)
        val alpha = (localT * 255f).roundToInt().coerceIn(0, 255)
        return Pair(translateY, alpha)
    }

    fun getGlowPulse(t: Float): Float {
        if (t < 0.85f) return 0f
        val localT = (t - 0.85f) / 0.15f
        return sin(localT * PI).toFloat().coerceIn(0f, 1f)
    }

    /**
     * Decomposes [number] into digits with current roll offset (DoD).
     * For progress = 1.0f, offset is 0.0f and digit equals final target digit.
     */
    fun getOdometerDigits(number: Int, progress: Float, rotations: Int = 2): List<Pair<Int, Float>> {
        val numStr = number.toString()
        val result = mutableListOf<Pair<Int, Float>>()

        for (i in numStr.indices) {
            val targetDigit = numStr[i].digitToInt()
            val totalSteps = targetDigit + rotations * 10
            val currentPos = progress.coerceIn(0f, 1f) * totalSteps
            val displayedDigit = (floor(currentPos).toInt()) % 10
            val fractionalOffset = currentPos - floor(currentPos)
            result.add(Pair(displayedDigit, fractionalOffset))
        }
        return result
    }

    override fun renderFrame(canvas: Canvas, t: Float, width: Int, height: Int) {
        val tClamped = t.coerceIn(0f, 1f)

        // 1. Solid background
        bgPaint.color = palette.background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        if (tClamped <= 0f) {
            // DoD: at t=0 nothing except background
            return
        }

        val minDim = minOf(width, height).toFloat()
        val padX = width * 0.08f
        var currentY = height * 0.09f

        // 2. Header "Итоги" fade-in
        val headerAlpha = getHeaderAlpha(tClamped)
        if (headerAlpha > 0) {
            val baseTextPrimary = palette.textPrimary and 0x00FFFFFF
            titlePaint.color = baseTextPrimary or (headerAlpha shl 24)
            titlePaint.textSize = minDim * 0.075f
            drawFittedLine(canvas, labels.header, padX, currentY, width - 2 * padX, titlePaint)
        }

        // 3. Period label slide-in
        val slideX = getPeriodSlideOffset(tClamped, width.toFloat())
        if (tClamped > 0.10f) {
            currentY += minDim * 0.06f
            val baseTextSecondary = palette.textSecondary and 0x00FFFFFF
            periodPaint.color = baseTextSecondary or 0xFF000000.toInt()
            periodPaint.textSize = minDim * 0.038f
            drawFittedLine(canvas, periodLabel, padX + slideX, currentY, width - 2 * padX, periodPaint)
        } else {
            currentY += minDim * 0.06f
        }

        // 4. Three Odometer counters
        currentY += minDim * 0.06f
        val countersY = currentY
        val cardW = (width - padX * 2f - minDim * 0.04f * 2f) / 3f
        val cardH = minDim * 0.22f

        val pulse = getGlowPulse(tClamped)
        val numColor = if (pulse > 0f) {
            val r = (palette.accent shr 16) and 0xFF
            val g = (palette.accent shr 8) and 0xFF
            val b = palette.accent and 0xFF
            val newR = (r + (255 - r) * pulse * 0.4f).roundToInt().coerceIn(0, 255)
            val newG = (g + (255 - g) * pulse * 0.4f).roundToInt().coerceIn(0, 255)
            val newB = (b + (255 - b) * pulse * 0.4f).roundToInt().coerceIn(0, 255)
            (0xFF shl 24) or (newR shl 16) or (newG shl 8) or newB
        } else {
            palette.accent
        }

        val counters = listOf(
            Triple(totalMinutes, labels.minutes, getMinutesProgress(tClamped)),
            Triple(uniqueTracks, labels.tracks, getTracksProgress(tClamped)),
            Triple(uniqueArtists, labels.artists, getArtistsProgress(tClamped))
        )

        for (i in counters.indices) {
            val (num, label, progress) = counters[i]
            val left = padX + i * (cardW + minDim * 0.04f)
            val right = left + cardW
            val top = countersY
            val bottom = top + cardH

            // Card surface
            cardPaint.color = palette.surface
            canvas.drawRoundRect(RectF(left, top, right, bottom), 16f, 16f, cardPaint)
            borderPaint.color = palette.border
            canvas.drawRoundRect(RectF(left, top, right, bottom), 16f, 16f, borderPaint)

            if (tClamped >= 0.20f) {
                // Draw odometer digits inside clip
                val centerX = (left + right) / 2f
                val digitY = top + cardH * 0.50f
                counterNumPaint.color = numColor
                counterNumPaint.textSize = minDim * 0.065f

                val digits = getOdometerDigits(num, progress)
                val digitWidth = minDim * 0.042f
                val totalDigitsWidth = digits.size * digitWidth
                var digitStartX = centerX - totalDigitsWidth / 2f + digitWidth / 2f

                for ((d, fraction) in digits) {
                    canvas.save()
                    clipRect.set(
                        (digitStartX - digitWidth / 2f).toInt(),
                        (top + 8f).toInt(),
                        (digitStartX + digitWidth / 2f).toInt(),
                        (top + cardH * 0.65f).toInt()
                    )
                    canvas.clipRect(clipRect)

                    val yOffset = fraction * counterNumPaint.textSize
                    canvas.drawText(d.toString(), digitStartX, digitY - yOffset, counterNumPaint)
                    if (fraction > 0f) {
                        val nextDigit = (d + 1) % 10
                        canvas.drawText(nextDigit.toString(), digitStartX, digitY - yOffset + counterNumPaint.textSize, counterNumPaint)
                    }
                    canvas.restore()
                    digitStartX += digitWidth
                }

                // Subtitle
                counterLabelPaint.color = palette.textSecondary
                counterLabelPaint.textSize = minDim * 0.030f
                canvas.drawText(label, centerX, top + cardH * 0.82f, counterLabelPaint)
            }
        }

        // 5. Top 3 tracks cascade
        currentY = countersY + cardH + minDim * 0.08f
        if (tClamped >= 0.75f && topTracks.isNotEmpty()) {
            titlePaint.color = palette.textPrimary
            titlePaint.textSize = minDim * 0.046f
            drawFittedLine(canvas, labels.topTracks, padX, currentY, width - 2 * padX, titlePaint)

            currentY += minDim * 0.04f
            val maxToShow = minOf(3, topTracks.size)
            for (i in 0 until maxToShow) {
                val (title, artist, count) = topTracks[i]
                val (translateY, alpha) = getTopTrackCascade(tClamped, i)
                if (alpha > 0) {
                    val rowY = currentY + (i + 1) * minDim * 0.085f + translateY
                    val alphaMask = alpha shl 24

                    // Rank
                    trackRankPaint.color = (palette.accent and 0x00FFFFFF) or alphaMask
                    trackRankPaint.textSize = minDim * 0.042f
                    canvas.drawText("${i + 1}", padX, rowY, trackRankPaint)

                    // Title
                    trackTitlePaint.color = (palette.textPrimary and 0x00FFFFFF) or alphaMask
                    trackTitlePaint.textSize = minDim * 0.038f
                    drawFittedLine(canvas, title, padX + minDim * 0.07f, rowY, width - 2 * padX - minDim * .07f, trackTitlePaint)

                    // Artist & count
                    trackSubPaint.color = (palette.textSecondary and 0x00FFFFFF) or alphaMask
                    trackSubPaint.textSize = minDim * 0.028f
                    drawFittedLine(canvas, "$artist · $count ${labels.plays}", padX + minDim * 0.07f, rowY + minDim * 0.032f, width - 2 * padX - minDim * .07f, trackSubPaint)
                }
            }
        }
    }
    private fun drawFittedLine(canvas: Canvas, text: String, x: Float, baseline: Float, maxWidth: Float, paint: Paint) {
        val originalSize = paint.textSize
        val measured = paint.measureText(text)
        if (measured > maxWidth) paint.textSize *= maxWidth / measured
        canvas.drawText(text, x, baseline, paint)
        paint.textSize = originalSize
    }

}
