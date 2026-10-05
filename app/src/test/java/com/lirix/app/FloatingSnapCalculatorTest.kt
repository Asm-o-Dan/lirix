package com.lirix.app

import com.lirix.app.service.FloatingLyricsState
import com.lirix.app.service.FloatingSnapCalculator
import com.lirix.app.ui.KaraokeLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Floating Lyrics Overlay physics, boundary clamping and state model.
 * Spec: TASK-FLT-01, TASK-FLT-02, TASK-FLT-03
 */
class FloatingSnapCalculatorTest {

    @Test
    fun `calculateSnapTargetX snaps to left edge when view is on left half`() {
        val screenWidth = 1080
        val viewWidth = 400
        val marginPx = 48

        // View center = 100 + 200 = 300 < 540 (left half)
        val snapTarget = FloatingSnapCalculator.calculateSnapTargetX(
            currentX = 100,
            viewWidth = viewWidth,
            screenWidth = screenWidth,
            marginPx = marginPx
        )

        assertEquals(48, snapTarget)
    }

    @Test
    fun `calculateSnapTargetX snaps to right edge when view is on right half`() {
        val screenWidth = 1080
        val viewWidth = 400
        val marginPx = 48

        // View center = 600 + 200 = 800 >= 540 (right half)
        val snapTarget = FloatingSnapCalculator.calculateSnapTargetX(
            currentX = 600,
            viewWidth = viewWidth,
            screenWidth = screenWidth,
            marginPx = marginPx
        )

        // Expected: screenWidth - viewWidth - marginPx = 1080 - 400 - 48 = 632
        assertEquals(632, snapTarget)
    }

    @Test
    fun `calculateSnapTargetX preserves position on invalid dimensions`() {
        assertEquals(150, FloatingSnapCalculator.calculateSnapTargetX(150, 0, 1080))
        assertEquals(150, FloatingSnapCalculator.calculateSnapTargetX(150, 400, 0))
    }

    @Test
    fun `clampX keeps floating view strictly within screen width`() {
        val screenWidth = 1080
        val viewWidth = 300

        // Negative X clamped to 0
        assertEquals(0, FloatingSnapCalculator.clampX(-50, viewWidth, screenWidth, marginPx = 0))

        // Exceeding right edge clamped to screenWidth - viewWidth = 780
        assertEquals(780, FloatingSnapCalculator.clampX(900, viewWidth, screenWidth, marginPx = 0))

        // Within bounds untouched
        assertEquals(250, FloatingSnapCalculator.clampX(250, viewWidth, screenWidth, marginPx = 0))
    }

    @Test
    fun `clampY keeps floating view within status bar and navigation bar margins`() {
        val screenHeight = 2400
        val viewHeight = 200
        val topMargin = 120
        val bottomMargin = 150

        // Clamped at top margin
        assertEquals(120, FloatingSnapCalculator.clampY(30, viewHeight, screenHeight, topMargin, bottomMargin))

        // Clamped at bottom margin (2400 - 200 - 150 = 2050)
        assertEquals(2050, FloatingSnapCalculator.clampY(2200, viewHeight, screenHeight, topMargin, bottomMargin))

        // In between untouched
        assertEquals(800, FloatingSnapCalculator.clampY(800, viewHeight, screenHeight, topMargin, bottomMargin))
    }

    @Test
    fun `isDragGesture detects movements beyond touch slop`() {
        val touchSlop = 24f

        // Small tap jitter below slop -> false
        assertFalse(FloatingSnapCalculator.isDragGesture(10f, 10f, touchSlop))

        // Clear swipe exceeding slop -> true
        assertTrue(FloatingSnapCalculator.isDragGesture(25f, 0f, touchSlop))
        assertTrue(FloatingSnapCalculator.isDragGesture(0f, 30f, touchSlop))
        assertTrue(FloatingSnapCalculator.isDragGesture(20f, 20f, touchSlop))
    }

    @Test
    fun `FloatingLyricsState maintains immutability and default state`() {
        val defaultState = FloatingLyricsState()
        assertFalse(defaultState.isExpanded)
        assertFalse(defaultState.isPlaying)
        assertFalse(defaultState.hasLyrics)
        assertFalse(defaultState.isSynced)
        assertEquals("", defaultState.activeLineText)

        val updated = defaultState.copy(
            isExpanded = true,
            isPlaying = true,
            title = "Bohemian Rhapsody",
            artist = "Queen",
            activeLineText = "Is this the real life?",
            hasLyrics = true,
            isSynced = true
        )

        assertTrue(updated.isExpanded)
        assertTrue(updated.isPlaying)
        assertTrue(updated.hasLyrics)
        assertTrue(updated.isSynced)
        assertEquals("Bohemian Rhapsody", updated.title)
        assertEquals("Queen", updated.artist)
        assertEquals("Is this the real life?", updated.activeLineText)
    }

    @Test
    fun `active lyric line resolution accurately tracks timestamps`() {
        val lines = listOf(
            KaraokeLine(0L, "Intro"),
            KaraokeLine(5000L, "First verse begins"),
            KaraokeLine(12000L, "Second line sings"),
            KaraokeLine(20000L, "Chorus explodes")
        )

        // Before first line
        val idxBefore = lines.indexOfLast { it.timestampMs <= 2000L }
        assertEquals(0, idxBefore)
        assertEquals("Intro", lines[idxBefore].text)

        // Mid first verse
        val idxVerse = lines.indexOfLast { it.timestampMs <= 7500L }
        assertEquals(1, idxVerse)
        assertEquals("First verse begins", lines[idxVerse].text)

        // Chorus
        val idxChorus = lines.indexOfLast { it.timestampMs <= 25000L }
        assertEquals(3, idxChorus)
        assertEquals("Chorus explodes", lines[idxChorus].text)
    }
}
