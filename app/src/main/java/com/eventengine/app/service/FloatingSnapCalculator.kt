package com.eventengine.app.service

/**
 * Pure calculator for floating overlay positioning, boundary clamping,
 * and kinetic magnetic snap to screen edges.
 * Spec: TASK-FLT-02 (.sdd/tasks/TASK-FLT-02.md)
 */
object FloatingSnapCalculator {

    const val DEFAULT_EDGE_MARGIN_DP = 16
    const val DEFAULT_TOP_MARGIN_DP = 48
    const val DEFAULT_BOTTOM_MARGIN_DP = 48

    /**
     * Calculates the horizontal magnetic snap target X position.
     * Snaps to either the left or right edge depending on which half of the screen
     * the view's center currently occupies.
     *
     * @param currentX Current left position of the floating view in pixels
     * @param viewWidth Width of the floating view in pixels
     * @param screenWidth Total screen width in pixels
     * @param marginPx Margin from screen edge in pixels
     * @return Target X coordinate to animate to
     */
    fun calculateSnapTargetX(
        currentX: Int,
        viewWidth: Int,
        screenWidth: Int,
        marginPx: Int = 32
    ): Int {
        if (screenWidth <= 0 || viewWidth <= 0) return currentX
        val viewCenterX = currentX + viewWidth / 2
        val screenCenterX = screenWidth / 2

        return if (viewCenterX < screenCenterX) {
            marginPx.coerceAtLeast(0)
        } else {
            (screenWidth - viewWidth - marginPx).coerceAtLeast(0)
        }
    }

    /**
     * Clamps vertical Y position within safe screen bounds (below status bar, above nav bar).
     */
    fun clampY(
        currentY: Int,
        viewHeight: Int,
        screenHeight: Int,
        topMarginPx: Int = 100,
        bottomMarginPx: Int = 120
    ): Int {
        if (screenHeight <= 0) return currentY
        val minY = topMarginPx.coerceAtLeast(0)
        val maxY = (screenHeight - viewHeight - bottomMarginPx).coerceAtLeast(minY)
        return currentY.coerceIn(minY, maxY)
    }

    /**
     * Clamps horizontal X position to prevent window from moving completely off-screen during drag.
     */
    fun clampX(
        currentX: Int,
        viewWidth: Int,
        screenWidth: Int,
        marginPx: Int = 0
    ): Int {
        if (screenWidth <= 0) return currentX
        val minX = marginPx
        val maxX = (screenWidth - viewWidth - marginPx).coerceAtLeast(minX)
        return currentX.coerceIn(minX, maxX)
    }

    /**
     * Evaluates if a gesture displacement exceeded the touch slop threshold.
     */
    fun isDragGesture(deltaX: Float, deltaY: Float, touchSlop: Float): Boolean {
        return (deltaX * deltaX + deltaY * deltaY) > (touchSlop * touchSlop)
    }
}
