package com.lirix.app.ui

import org.junit.Assert.*
import org.junit.Test

class TabSwipeGestureTest {
    @Test fun systemEdgesAndProtectedControlsNeverStart() {
        assertFalse(mayStartTabSwipe(false, 20f, 400f, 24f, 24f, false))
        assertFalse(mayStartTabSwipe(false, 380f, 400f, 24f, 24f, false))
        assertFalse(mayStartTabSwipe(false, 200f, 400f, 24f, 24f, true))
        assertFalse(mayStartTabSwipe(true, 200f, 400f, 24f, 24f, false))
        assertFalse(mayStartTabSwipe(false, null, 400f, 24f, 24f, false))
        assertTrue(mayStartTabSwipe(false, 200f, 400f, 24f, 24f, false))
    }

    @Test fun horizontalMotionMovesAtMostOnePageAndHonorsRtlAndBounds() {
        fun swipe(dx: Float): TabSwipeGesture = TabSwipeGesture(8f, 56f, true).also { assertTrue(it.move(dx, 2f, false)) }
        assertEquals(1, swipe(-80f).destination(0, 3, false))
        assertEquals(0, swipe(80f).destination(1, 3, false))
        assertEquals(2, swipe(80f).destination(1, 3, true))
        assertNull(swipe(-80f).destination(2, 3, false))
        assertNull(swipe(80f).destination(0, 3, false))
        assertNull(swipe(-30f).destination(0, 3, false))
    }

    @Test fun verticalChildConsumedCancelledAndBlockedStreamsDoNotNavigate() {
        val vertical = TabSwipeGesture(8f, 56f, true)
        assertFalse(vertical.move(2f, 20f, false))
        assertFalse(vertical.move(-90f, 1f, false))
        assertNull(vertical.destination(1, 3, false))
        val child = TabSwipeGesture(8f, 56f, true)
        assertFalse(child.move(-30f, 1f, true))
        assertNull(child.destination(1, 3, false))
        val laterChild = TabSwipeGesture(8f, 56f, true)
        assertTrue(laterChild.move(-60f, 1f, false))
        assertFalse(laterChild.move(-10f, 0f, true))
        assertNull(laterChild.destination(1, 3, false))
        val blocked = TabSwipeGesture(8f, 56f, false)
        assertFalse(blocked.move(-100f, 0f, false))
        assertNull(blocked.destination(1, 3, false))
    }
}
