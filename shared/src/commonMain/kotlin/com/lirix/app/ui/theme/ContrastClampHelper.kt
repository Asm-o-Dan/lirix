package com.lirix.app.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.pow

/**
 * ContrastClampHelper implements WCAG 2.1 relative luminance and contrast ratio calculations,
 * and dynamically adapts dominant album cover colors to guarantee WCAG AA contrast (>= 4.5:1)
 * against the Lirix teleprompter backgrounds (Graphite #1B1E24 for Dark, Parchment #F5F2EB for Light).
 *
 * Spec: TASK-UI-PROMPTER-01 / ADR-022
 */
object ContrastClampHelper {

    val Graphite: Color = Color(0xFF1B1E24)
    val Parchment: Color = Color(0xFFF5F2EB)

    const val MIN_WCAG_AA_CONTRAST = 4.5

    /**
     * Converts a Compose [Color] into HSL components.
     * Returns a [FloatArray] of [hue (0..360), saturation (0..1), lightness (0..1)].
     */
    fun rgbToHsl(color: Color): FloatArray {
        val r = color.red
        val g = color.green
        val b = color.blue

        val max = maxOf(r, maxOf(g, b))
        val min = minOf(r, minOf(g, b))
        val delta = max - min

        val l = (max + min) / 2f
        val s = if (delta == 0f) {
            0f
        } else {
            delta / (1f - abs(2f * l - 1f))
        }

        val h = when {
            delta == 0f -> 0f
            max == r -> (((g - b) / delta) % 6f) * 60f
            max == g -> (((b - r) / delta) + 2f) * 60f
            else -> (((r - g) / delta) + 4f) * 60f
        }.let { if (it < 0f) it + 360f else it }

        return floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
    }

    /**
     * Converts HSL components back into a Compose [Color].
     */
    fun hslToRgb(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
        val c = (1f - abs(2f * l - 1f)) * s
        val hNormalized = (h % 360f + 360f) % 360f
        val x = c * (1f - abs((hNormalized / 60f) % 2f - 1f))
        val m = l - c / 2f

        val (rPrime, gPrime, bPrime) = when {
            hNormalized < 60f -> Triple(c, x, 0f)
            hNormalized < 120f -> Triple(x, c, 0f)
            hNormalized < 180f -> Triple(0f, c, x)
            hNormalized < 240f -> Triple(0f, x, c)
            hNormalized < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }

        return Color(
            red = (rPrime + m).coerceIn(0f, 1f),
            green = (gPrime + m).coerceIn(0f, 1f),
            blue = (bPrime + m).coerceIn(0f, 1f),
            alpha = alpha
        )
    }

    /**
     * Calculates WCAG 2.1 relative luminance for a given [Color].
     * Reference: https://www.w3.org/WAI/GL/wiki/Relative_luminance
     */
    fun calculateLuminance(color: Color): Double {
        fun linearize(c: Float): Double {
            val cd = c.toDouble()
            return if (cd <= 0.04045) {
                cd / 12.92
            } else {
                ((cd + 0.055) / 1.055).pow(2.4)
            }
        }

        val r = linearize(color.red)
        val g = linearize(color.green)
        val b = linearize(color.blue)

        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /**
     * Calculates the WCAG contrast ratio between two colors: (L1 + 0.05) / (L2 + 0.05).
     * Guaranteed result in range [1.0, 21.0].
     */
    fun calculateContrastRatio(colorA: Color, colorB: Color): Double {
        val lumA = calculateLuminance(colorA)
        val lumB = calculateLuminance(colorB)
        val maxLum = maxOf(lumA, lumB)
        val minLum = minOf(lumA, lumB)
        return (maxLum + 0.05) / (minLum + 0.05)
    }

    /**
     * Clamps an accent color extracted from album cover art to guarantee WCAG AA contrast (>= 4.5:1).
     *
     * 1. Dark theme (Graphite #1B1E24):
     *    - If L < 0.55, lightness is lifted to at least 0.55.
     *    - Lightness is then adjusted upward until contrast ratio >= 4.5:1.
     * 2. Light theme (Parchment #F5F2EB):
     *    - If L > 0.45, lightness is lowered to at most 0.45.
     *    - Lightness is then adjusted downward until contrast ratio >= 4.5:1.
     */
    fun clampAccentContrast(color: Color, isDark: Boolean): Color {
        val bg = if (isDark) Graphite else Parchment
        val hsl = rgbToHsl(color)
        val h = hsl[0]
        val s = hsl[1]
        var l = hsl[2]

        if (isDark) {
            // Target background is Graphite (#1B1E24)
            if (l < 0.55f) {
                l = 0.55f
            }
            var candidate = hslToRgb(h, s, l, color.alpha)
            if (calculateContrastRatio(candidate, bg) >= MIN_WCAG_AA_CONTRAST) {
                return candidate
            }

            // Binary search lightness between current l and 1.0
            var low = l
            var high = 1.0f
            for (step in 0 until 16) {
                val mid = (low + high) / 2f
                val testColor = hslToRgb(h, s, mid, color.alpha)
                if (calculateContrastRatio(testColor, bg) >= MIN_WCAG_AA_CONTRAST) {
                    high = mid
                    candidate = testColor
                } else {
                    low = mid
                }
            }
            return candidate
        } else {
            // Target background is Parchment (#F5F2EB)
            if (l > 0.45f) {
                l = 0.45f
            }
            var candidate = hslToRgb(h, s, l, color.alpha)
            if (calculateContrastRatio(candidate, bg) >= MIN_WCAG_AA_CONTRAST) {
                return candidate
            }

            // Binary search lightness between 0.0 and current l
            var low = 0.0f
            var high = l
            for (step in 0 until 16) {
                val mid = (low + high) / 2f
                val testColor = hslToRgb(h, s, mid, color.alpha)
                if (calculateContrastRatio(testColor, bg) >= MIN_WCAG_AA_CONTRAST) {
                    low = mid
                    candidate = testColor
                } else {
                    high = mid
                }
            }
            return candidate
        }
    }
}
