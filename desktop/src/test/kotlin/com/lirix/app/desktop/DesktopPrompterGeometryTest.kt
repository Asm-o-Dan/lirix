package com.lirix.app.desktop

import com.lirix.app.desktop.ui.components.desktopPrompterAnchorPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPrompterGeometryTest {
    @Test fun shortOrUnmeasuredRowsKeepReadingAnchor() {
        assertEquals(200, desktopPrompterAnchorPx(500, 0, 16))
        assertEquals(200, desktopPrompterAnchorPx(500, 100, 16))
    }

    @Test fun wrappedRowMovesUpWithoutReducingTextSize() {
        val anchor = desktopPrompterAnchorPx(500, 340, 16)
        assertEquals(144, anchor)
        assertTrue(anchor + 340 <= 500 - 16)
        assertEquals(4, desktopPrompterAnchorPx(500, 480, 16))
    }

    @Test fun oversizedRowsStartAtTopAndRemainScrollable() {
        assertEquals(0, desktopPrompterAnchorPx(500, 500, 16))
        assertEquals(0, desktopPrompterAnchorPx(500, 900, 16))
        assertEquals(0, desktopPrompterAnchorPx(0, 100, 16))
    }

    @Test fun geometryScalesWithDensityWithoutChangingLogicalAnchor() {
        assertEquals(216, desktopPrompterAnchorPx(750, 510, 24))
        assertEquals(144, desktopPrompterAnchorPx(500, 340, 16))
    }
}
