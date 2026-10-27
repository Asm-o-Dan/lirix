package com.lirix.app.ui

import kotlin.math.abs

/** Classifies one pointer stream after child controls have had first refusal. */
internal class TabSwipeGesture(
    private val touchSlop: Float,
    private val distanceThreshold: Float,
    private val allowedAtStart: Boolean
) {
    private var x = 0f
    private var y = 0f
    private var dragging = false
    private var cancelled = !allowedAtStart

    fun move(dx: Float, dy: Float, childConsumed: Boolean): Boolean {
        if (cancelled) return false
        if (childConsumed) { cancelled = true; return false }
        x += dx
        y += dy
        if (!dragging && abs(y) > touchSlop && abs(y) >= abs(x)) {
            cancelled = true
            return false
        }
        if (!dragging && abs(x) > touchSlop && abs(x) > abs(y) * 1.25f) dragging = true
        return dragging
    }

    fun destination(current: Int, pageCount: Int, rtl: Boolean): Int? {
        if (cancelled || !dragging || abs(x) < distanceThreshold) return null
        val direction = if (x < 0f) 1 else -1
        val next = current + if (rtl) -direction else direction
        return next.takeIf { it in 0 until pageCount }
    }
}

internal fun mayStartTabSwipe(
    blocked: Boolean, x: Float?, windowWidth: Float, leftEdge: Float, rightEdge: Float, protected: Boolean
): Boolean = !blocked && !protected && x != null && windowWidth > 0f &&
    x > leftEdge && x < windowWidth - rightEdge
