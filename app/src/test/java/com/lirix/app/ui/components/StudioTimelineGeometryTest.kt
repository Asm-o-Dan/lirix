package com.lirix.app.ui.components

import com.lirix.app.feature.lyrics.KaraokeStudioState
import com.lirix.app.feature.lyrics.StudioLine
import org.junit.Assert.*
import org.junit.Test

class StudioTimelineGeometryTest {
    @Test fun drawAndSeekUseTheSameCoordinatesAtDifferentZooms() {
        for (span in listOf(2000L, 15000L, 120000L)) {
            val geometry = StudioTimelineGeometry(180000, span, 600f, 240000)
            for (time in listOf(179500L, 180000L, 180750L)) {
                assertEquals(time, geometry.timeAt(geometry.xAt(time)))
            }
            assertEquals(300f, geometry.xAt(180000), 0f)
        }
    }

    @Test fun markerDragAndTrackPanMoveInOppositeDirectionsAndClampToSong() {
        val geometry = StudioTimelineGeometry(10000, 2000, 400f, 15000)
        assertEquals(10250, geometry.shifted(10000, 50f))
        assertEquals(9750, geometry.shifted(10000, -50f))
        assertEquals(0, geometry.timeAt(-10000f))
        assertEquals(15000, geometry.timeAt(10000f))
    }

    @Test fun selectedOccurrenceWinsWhenMultipleLinesHaveTheSameTimestamp() {
        val rows = listOf(StudioLine(0, "First", 1000), StudioLine(1, "Second", 1000), StudioLine(2, "No time", null))
        val state = KaraokeStudioState("song", rows, rows, 5000, selectedIndex = 1, cursorMs = 1000)
        val geometry = StudioTimelineGeometry(1000, 2000, 400f, 5000)
        assertEquals(1, geometry.nearest(state, 200f, 24f))
        assertEquals(0, geometry.nearest(state.copy(selectedIndex = 0), 200f, 24f))
        assertNull(geometry.nearest(state, 250f, 24f))
    }

    @Test fun nearestMarkerWinsOverSelectedDistantMarkerAndUnstampedRows() {
        val rows = listOf(StudioLine(0, "Selected", 1000), StudioLine(1, "Near", 1100), StudioLine(2, "Blank", null))
        val state = KaraokeStudioState("song", rows, rows, 5000, selectedIndex = 0)
        val geometry = StudioTimelineGeometry(1000, 2000, 400f, 5000)
        assertEquals(1, geometry.nearest(state, 220f, 24f))
        assertNull(geometry.nearest(state.copy(lines = emptyList()), 200f, 24f))
    }

    @Test fun subsecondLabelsRemainDistinctAcrossMinuteBoundary() {
        assertEquals("00:59.5", studioTickLabel(59500, 2000))
        assertEquals("01:00.0", studioTickLabel(60000, 2000))
        assertEquals("01:00.5", studioTickLabel(60500, 2000))
        assertEquals("01:00", studioTickLabel(60500, 15000))
    }

    @Test fun rulerSpacingAccommodatesLargeFontsInsteadOfOverlapping() {
        for (span in listOf(2000L, 15000L, 120000L)) for (labelWidth in listOf(40f, 90f, 150f)) {
            val step = studioTickStep(span, 320f, labelWidth)
            assertTrue("span=$span label=$labelWidth", step * 320.0 / span >= labelWidth + 12)
        }
    }

    @Test fun longTimelineDoesNotOverflowCoordinates() {
        val cursor = Long.MAX_VALUE - 10000
        val geometry = StudioTimelineGeometry(cursor, 2000, 400f, Long.MAX_VALUE)
        assertEquals(300f, geometry.xAt(cursor + 500), 0.5f)
        assertEquals(Long.MAX_VALUE, geometry.shifted(cursor, Float.MAX_VALUE))
    }
}
