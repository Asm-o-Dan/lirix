package com.lirix.app.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit-tests for FloatingLyricsService, state model, and permission checks.
 * Spec: TASK-BUG-FLT-04 (.sdd/tasks/TASK-BUG-FLT-04.md)
 */
@RunWith(AndroidJUnit4::class)
class FloatingLyricsServiceTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testIsPermissionGranted_doesNotCrash() {
        // Under Robolectric, Settings.canDrawOverlays returns false or configured value without crashing
        val granted = FloatingLyricsService.isPermissionGranted(context)
        // Check that result is boolean
        assertTrue(granted == true || granted == false)
    }

    @Test
    fun testIsRunningStateFlow_initialState() {
        assertNotNull(FloatingLyricsService.isRunning)
        // Default state should be false unless explicitly started
        assertFalse(FloatingLyricsService.isRunning.value)
    }

    @Test
    fun testFloatingLyricsState_transitions() {
        val initial = FloatingLyricsState()
        assertFalse(initial.isExpanded)
        assertFalse(initial.isPlaying)
        assertFalse(initial.hasLyrics)
        assertFalse(initial.isSynced)
        assertEquals("", initial.activeLineText)

        val updated = initial.copy(
            isExpanded = true,
            isPlaying = true,
            title = "Starboy",
            artist = "The Weeknd",
            activeLineText = "I'm tryna put you in the worst mood, ah",
            previousLineText = "Intro beat",
            nextLineText = "P1 cleaner than your church shoes, ah",
            hasLyrics = true,
            isSynced = true
        )

        assertTrue(updated.isExpanded)
        assertTrue(updated.isPlaying)
        assertTrue(updated.hasLyrics)
        assertTrue(updated.isSynced)
        assertEquals("Starboy", updated.title)
        assertEquals("The Weeknd", updated.artist)
        assertEquals("I'm tryna put you in the worst mood, ah", updated.activeLineText)
        assertEquals("Intro beat", updated.previousLineText)
        assertEquals("P1 cleaner than your church shoes, ah", updated.nextLineText)
    }

    @Test
    fun testFloatingLyricsService_toggleLogic() {
        // Verify toggle does not throw
        assertFalse(FloatingLyricsService.isRunning.value)
        // If not running, toggle tries to start
        FloatingLyricsService.toggle(context)
        // After stop call
        FloatingLyricsService.stop(context)
        assertFalse(FloatingLyricsService.isRunning.value)
    }
}
