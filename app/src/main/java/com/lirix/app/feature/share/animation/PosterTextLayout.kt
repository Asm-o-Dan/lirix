package com.lirix.app.feature.share.animation

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.icu.text.BreakIterator
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import java.util.Locale
import kotlin.math.roundToInt

internal fun graphemePrefix(text: String, progress: Float): String {
    if (progress <= 0f || text.isEmpty()) return ""
    if (progress >= 1f) return text
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(text)
    val ends = mutableListOf<Int>()
    var end = iterator.next()
    while (end != BreakIterator.DONE) { ends += end; end = iterator.next() }
    val count = (ends.size * progress).roundToInt().coerceIn(0, ends.size)
    return if (count == 0) "" else text.substring(0, ends[count - 1])
}

/** Fit against the complete text, so typing never changes line wraps or moves later blocks. */
internal class PosterTextLayout(val text: String, sourcePaint: Paint, val bounds: RectF) {
    private val paint = TextPaint(sourcePaint).apply { textAlign = Paint.Align.LEFT }
    val layout: StaticLayout
    init {
        var low = 0.25f
        var high = sourcePaint.textSize.coerceAtLeast(low)
        repeat(18) {
            val candidate = (low + high) / 2f
            paint.textSize = candidate
            val measured = build(text)
            if (measured.height <= bounds.height() && (0 until measured.lineCount).all {
                measured.getLineWidth(it) <= bounds.width() + 1f
            }) low = candidate else high = candidate
        }
        paint.textSize = low
        layout = build(text)
        check(layout.height <= bounds.height() + 1f) { "Poster text is too long for its safe area" }
    }

    private fun build(value: CharSequence): StaticLayout = StaticLayout.Builder.obtain(value, 0, value.length, paint,
        bounds.width().toInt().coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setIncludePad(false)
        .setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
        .build()

    fun draw(canvas: Canvas, visiblePrefix: String = text, alpha: Int = 255) {
        paint.alpha = alpha
        if (visiblePrefix.isEmpty() || alpha <= 0) return
        val content = SpannableString(text)
        if (visiblePrefix.length < text.length) content.setSpan(ForegroundColorSpan(Color.TRANSPARENT),
            visiblePrefix.length, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val display = build(content)
        canvas.save()
        canvas.translate(bounds.left, bounds.top)
        display.draw(canvas)
        canvas.restore()
    }
}
