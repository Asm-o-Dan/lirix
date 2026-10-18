package com.lirix.app.desktop.ui.components

import kotlin.math.roundToInt

internal fun desktopPrompterAnchorPx(
    viewportHeightPx: Int,
    activeRowHeightPx: Int,
    bottomMarginPx: Int
): Int {
    val height = viewportHeightPx.coerceAtLeast(0)
    val preferred = (height * 0.4f).roundToInt()
    if (activeRowHeightPx <= 0) return preferred
    val margin = bottomMarginPx.coerceIn(0, height)
    return minOf(preferred, (height.toLong() - activeRowHeightPx - margin).coerceAtLeast(0).toInt())
}
