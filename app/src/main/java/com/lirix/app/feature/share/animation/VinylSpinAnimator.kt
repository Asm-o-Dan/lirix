package com.lirix.app.feature.share.animation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Animated poster renderer for "NowPlaying Vinyl" (SPEC-ANIM-POSTER-01 В§4.1).
 *
 * Timeline (t in [0.0 .. 1.0]):
 * - t in [0.0 .. 1.0]: Vinyl cover rotates 360В° continuously.
 * - t in [0.0 .. 0.15]: Fade-in background and radial glow.
 * - t in [0.15 .. 0.35]: Vinyl cover scale-in with overshoot (0 -> 1.05 -> 1.0).
 * - t in [0.35 .. 0.55]: Track title typewriter effect.
 * - t in [0.55 .. 0.70]: Artist name fade-in.
 * - t in [0.70 .. 1.0]: Lyric line typewriter effect + pulsating glow.
 * - Cursor '|' blinks when sin(t * 20) > 0 during text typing.
 */
class VinylSpinAnimator(
    palette: ThemeAnimationPalette,
    val albumArt: Bitmap?,
    val trackTitle: String,
    val artistName: String,
    val lyricLine: String?
) : PosterAnimator(palette) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val vinylBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF141416.toInt()
    }

    private val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0x22FFFFFF
    }

    private val spindlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val tonearmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFF888888.toInt()
    }

    private val tonearmHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFCCCCCC.toInt()
    }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        textAlign = Paint.Align.CENTER
    }

    private val clipPath = Path()
    private val srcRect = Rect()
    private val dstRect = RectF()

    /**
     * Calculates vinyl disc scale at progress [t].
     * 0f for t < 0.15, overshoot peak ~1.05 at t ~ 0.25, 1.0f for t >= 0.35.
     */
    fun getVinylScale(t: Float): Float {
        if (t < 0.15f) return 0f
        if (t >= 0.35f) return 1f
        val localT = (t - 0.15f) / 0.20f
        // Smooth overshoot: 0 -> 1.05 -> 1.0
        val overshoot = sin(localT * PI).toFloat() * 0.05f
        return localT + overshoot
    }

    /**
     * Calculates continuous rotation in degrees at progress [t] (360В° per loop).
     */
    fun getVinylRotation(t: Float): Float {
        return (t.coerceIn(0f, 1f) * 360f)
    }

    /**
     * Calculates visible title substring at progress [t].
     */
    fun getTitleText(t: Float): String {
        if (t < 0.35f || trackTitle.isEmpty()) return ""
        if (t >= 0.55f) return trackTitle
        val localT = (t - 0.35f) / 0.20f
        return graphemePrefix(trackTitle, localT)
    }

    /**
     * Calculates artist alpha in [0 .. 255] at progress [t].
     */
    fun getArtistAlpha(t: Float): Int {
        if (t < 0.55f) return 0
        if (t >= 0.70f) return 255
        val localT = (t - 0.55f) / 0.15f
        return (localT * 255f).roundToInt().coerceIn(0, 255)
    }

    /**
     * Calculates visible lyric line substring at progress [t].
     */
    fun getLyricText(t: Float): String {
        val lyric = lyricLine ?: return ""
        if (t < 0.70f || lyric.isEmpty()) return ""
        if (t >= 0.85f) return lyric
        val localT = (t - 0.70f) / 0.15f
        return graphemePrefix(lyric, localT)
    }

    /**
     * Blinking cursor state: true when sin(t * 20) > 0.
     */
    fun isCursorVisible(t: Float): Boolean {
        return sin(t * 20.0) > 0
    }

    override fun renderFrame(canvas: Canvas, t: Float, width: Int, height: Int) {
        val tClamped = t.coerceIn(0f, 1f)

        // 1. Solid background
        bgPaint.color = palette.background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        val minDim = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height * 0.38f
        val vinylRadius = minDim * 0.30f

        // 2. Radial glow behind vinyl (pulsing with sin(t * PI))
        val glowPulse = sin(tClamped * PI).toFloat().coerceIn(0f, 1f)
        val baseGlowAlpha = (palette.glowColor ushr 24) and 0xFF
        val currentGlowAlpha = (baseGlowAlpha * glowPulse).roundToInt()

        if (currentGlowAlpha > 0 && tClamped > 0f) {
            val glowColorWithAlpha = (palette.glowColor and 0x00FFFFFF) or (currentGlowAlpha shl 24)
            val glowRadius = vinylRadius * 1.6f
            glowPaint.shader = RadialGradient(
                cx, cy, glowRadius,
                intArrayOf(glowColorWithAlpha, palette.background and 0x00FFFFFF),
                floatArrayOf(0.2f, 1.0f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(cx, cy, glowRadius, glowPaint)
        }

        // 3. Vinyl disc & cover
        val scale = getVinylScale(tClamped)
        if (scale > 0f) {
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(scale, scale)
            canvas.rotate(getVinylRotation(tClamped))

            // Disc body
            canvas.drawCircle(0f, 0f, vinylRadius, vinylBodyPaint)

            // Concentric grooves
            val grooveStart = vinylRadius * 0.48f
            val grooveEnd = vinylRadius * 0.94f
            val grooveSteps = 6
            val step = (grooveEnd - grooveStart) / grooveSteps
            for (i in 0..grooveSteps) {
                canvas.drawCircle(0f, 0f, grooveStart + i * step, groovePaint)
            }

            // Center label / artwork
            val labelRadius = vinylRadius * 0.44f
            clipPath.reset()
            clipPath.addCircle(0f, 0f, labelRadius, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(clipPath)

            if (albumArt != null && !albumArt.isRecycled) {
                srcRect.set(0, 0, albumArt.width, albumArt.height)
                dstRect.set(-labelRadius, -labelRadius, labelRadius, labelRadius)
                canvas.drawBitmap(albumArt, srcRect, dstRect, null)
            } else {
                spindlePaint.color = palette.surface
                canvas.drawCircle(0f, 0f, labelRadius, spindlePaint)
                spindlePaint.color = palette.accent
                spindlePaint.style = Paint.Style.STROKE
                spindlePaint.strokeWidth = 3f
                canvas.drawCircle(0f, 0f, labelRadius * 0.8f, spindlePaint)
                spindlePaint.style = Paint.Style.FILL
            }
            canvas.restore()

            // Spindle center hole
            spindlePaint.color = palette.background
            canvas.drawCircle(0f, 0f, vinylRadius * 0.05f, spindlePaint)

            canvas.restore()

            // 4. Tonearm (static arc at ~25В° angle)
            drawTonearm(canvas, cx, cy, vinylRadius)
        }

        // 5. Typography section
        drawTypography(canvas, tClamped, width, height, cy + vinylRadius)
    }

    private fun drawTonearm(canvas: Canvas, cx: Float, cy: Float, vinylRadius: Float) {
        val armPivotX = cx + vinylRadius * 1.05f
        val armPivotY = cy - vinylRadius * 0.85f
        val armHeadX = cx + vinylRadius * 0.65f
        val armHeadY = cy + vinylRadius * 0.15f

        tonearmPaint.strokeWidth = 4f
        canvas.drawLine(armPivotX, armPivotY, armHeadX, armHeadY, tonearmPaint)

        // Pivot base
        tonearmHeadPaint.color = 0xFF555555.toInt()
        canvas.drawCircle(armPivotX, armPivotY, 8f, tonearmHeadPaint)

        // Cartridge / head
        tonearmHeadPaint.color = palette.accent
        canvas.drawCircle(armHeadX, armHeadY, 5f, tonearmHeadPaint)
    }

    private var typographySize: Pair<Int, Int>? = null
    private lateinit var titleLayout: PosterTextLayout
    private lateinit var artistLayout: PosterTextLayout
    private lateinit var lyricLayout: PosterTextLayout

    private fun drawTypography(canvas: Canvas, t: Float, width: Int, height: Int, topY: Float) {
        val minDim = minOf(width, height).toFloat()
        if (typographySize != (width to height)) {
            val top = topY + minDim * .05f
            val bottom = height * .97f
            val available = (bottom - top).coerceAtLeast(1f)
            val left = width * .08f
            val right = width * .92f
            titlePaint.color = palette.textPrimary
            titlePaint.textSize = minDim * .052f
            artistPaint.color = palette.textSecondary
            artistPaint.textSize = minDim * .038f
            lyricPaint.color = palette.accent
            lyricPaint.textSize = minDim * .042f
            titleLayout = PosterTextLayout(trackTitle, titlePaint, RectF(left, top, right, top + available * .34f))
            artistLayout = PosterTextLayout(artistName, artistPaint, RectF(left, top + available * .36f, right, top + available * .54f))
            lyricLayout = PosterTextLayout(lyricLine.orEmpty(), lyricPaint, RectF(left, top + available * .58f, right, bottom))
            typographySize = width to height
        }
        titleLayout.draw(canvas, getTitleText(t))
        artistLayout.draw(canvas, artistName, getArtistAlpha(t))
        lyricLayout.draw(canvas, getLyricText(t))
    }
}
