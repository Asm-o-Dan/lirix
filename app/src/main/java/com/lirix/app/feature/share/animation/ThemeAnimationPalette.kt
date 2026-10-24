package com.lirix.app.feature.share.animation

import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import com.lirix.app.ui.theme.LirixColors
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Specification for animated poster video generation (SPEC-ANIM-POSTER-01 §3.1).
 *
 * @param durationMs Length of the animation loop in milliseconds (default 4000ms = 4s).
 * @param fps Frame rate in frames per second (default 24fps).
 * @param width Video width in pixels (1080 for square & stories).
 * @param height Video height in pixels (1080 for square, 1920 for stories).
 * @param loopable Whether the animation loops seamlessly.
 */
data class AnimationSpec(
    val durationMs: Long = 4000L,
    val fps: Int = 24,
    val width: Int = 1080,
    val height: Int = 1080,
    val loopable: Boolean = true
) {
    val totalFrames: Int get() = (durationMs * fps / 1000).toInt()

    companion object {
        val SQUARE = AnimationSpec(width = 1080, height = 1080)
        val STORIES = AnimationSpec(width = 1080, height = 1920)
    }
}

/**
 * Immutable snapshot of [LirixColors] semantic tokens represented as ARGB 32-bit integers
 * suitable for direct hardware Canvas and background video encoding (SPEC-ANIM-POSTER-01 §3.4).
 *
 * @property background Canvas background color (from [LirixColors.surfacePrimary]).
 * @property surface Card / container surface color (from [LirixColors.surfaceCard]).
 * @property accent Primary accent color (from [LirixColors.accentPrimary]).
 * @property textPrimary Primary typography color (from [LirixColors.textPrimary]).
 * @property textSecondary Secondary typography / metadata color (from [LirixColors.textSecondary]).
 * @property border Subtle border / divider color (from [LirixColors.borderSubtle]).
 * @property glowColor Radial / sweep glow color derived from [accent] with ~40% alpha.
 */
data class ThemeAnimationPalette(
    val background: Int,
    val surface: Int,
    val accent: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val border: Int,
    val glowColor: Int
) {
    companion object {
        private const val DEFAULT_GLOW_ALPHA_RATIO = 0.40f

        /**
         * Safely extracts and creates a [ThemeAnimationPalette] from [LirixColors].
         */
        fun fromColors(colors: LirixColors): ThemeAnimationPalette {
            val accentArgb = colors.accentPrimary.toSafeArgb()
            val glow = computeGlowColor(accentArgb, DEFAULT_GLOW_ALPHA_RATIO)
            return ThemeAnimationPalette(
                background = colors.surfacePrimary.toSafeArgb(),
                surface = colors.surfaceCard.toSafeArgb(),
                accent = accentArgb,
                textPrimary = colors.textPrimary.toSafeArgb(),
                textSecondary = colors.textSecondary.toSafeArgb(),
                border = colors.borderSubtle.toSafeArgb(),
                glowColor = glow
            )
        }

        fun fromTokens(tokens: LirixColors): ThemeAnimationPalette = fromColors(tokens)

        /**
         * Computes an ARGB color with specified alpha ratio (0.0 .. 1.0) applied to the RGB channels.
         */
        fun computeGlowColor(rgbColor: Int, alphaRatio: Float = DEFAULT_GLOW_ALPHA_RATIO): Int {
            val alphaByte = (alphaRatio.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
            return (rgbColor and 0x00FFFFFF) or (alphaByte shl 24)
        }
    }
}

/**
 * Safely converts Compose [Color] into a 32-bit ARGB [Int].
 * Returns [fallback] if [Color.isSpecified] is false or if conversion fails.
 */
fun Color.toSafeArgb(fallback: Int = 0): Int {
    return if (isSpecified) {
        try {
            toArgb()
        } catch (_: Throwable) {
            fallback
        }
    } else {
        fallback
    }
}

/**
 * Convenient extension to convert [LirixColors] to [ThemeAnimationPalette].
 */
fun LirixColors.toThemeAnimationPalette(): ThemeAnimationPalette =
    ThemeAnimationPalette.fromColors(this)

/**
 * Base class for all animated poster frame renderers (SPEC-ANIM-POSTER-01 §3.2).
 *
 * @property palette Active theme colors mapped to immutable ARGB ints.
 */
abstract class PosterAnimator(val palette: ThemeAnimationPalette) {
    /**
     * Renders a single frame of the animation to the provided hardware [Canvas].
     *
     * @param canvas Hardware or software Canvas to draw upon.
     * @param t Normalized animation progress: t in [0.0 .. 1.0].
     * @param width Width of the frame in pixels.
     * @param height Height of the frame in pixels.
     */
    abstract fun renderFrame(canvas: Canvas, t: Float, width: Int, height: Int)

    /**
     * Renders a single frame using dimension defined in [spec].
     */
    open fun renderFrame(canvas: Canvas, t: Float, spec: AnimationSpec) {
        renderFrame(canvas, t, spec.width, spec.height)
    }
}

/**
 * Spring easing function with decaying oscillations (SPEC-ANIM-POSTER-01 §4.3).
 * Formula: f(t) = 1 - e^(-6t) * cos(4πt)
 *
 * Characteristics:
 * - f(0) = 0.0
 * - Overshoots > 1.0 in the middle (peak ~1.22 around t = 0.25)
 * - Settles to approximately 1.0 at t = 1.0 (~0.9975)
 *
 * @param t Normalized progress in range [0.0 .. 1.0].
 * @return Eased progress with elastic bounce.
 */
fun springBounce(t: Float): Float {
    if (t <= 0f) return 0f
    val decay = exp(-6.0 * t)
    val oscillation = cos(4.0 * Math.PI * t)
    return (1.0 - decay * oscillation).toFloat()
}
