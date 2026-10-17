package com.lirix.app.desktop

import com.lirix.app.desktop.ui.components.DesktopSyncOffsetPreferences
import com.lirix.app.desktop.ui.components.formatOffsetString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for TASK-UI-PROMPTER-04:
 * - Validates sync offset formatting (minus, plus, zero with comma decimal)
 * - Validates sync offset persistence via DesktopSyncOffsetStore
 */
class DesktopTransportBarTest {

    @Test
    fun test_formatOffsetString() {
        assertEquals("−0,3 с", formatOffsetString(-300L))
        assertEquals("+0,1 с", formatOffsetString(100L))
        assertEquals("+1,5 с", formatOffsetString(1500L))
        assertEquals("0,0 с", formatOffsetString(0L))
        assertEquals("−1,0 с", formatOffsetString(-1000L))
    }

    @Test
    fun test_desktopSyncOffsetStore_persistence() {
        val preferences = MemoryPreferences()
        val store = DesktopSyncOffsetPreferences(preferences)
        assertEquals(-300L, store.getOffsetMs())
        store.setOffsetMs(-500L)
        assertEquals(-500L, store.getOffsetMs())
        store.setOffsetMs(200L)
        assertEquals(200L, DesktopSyncOffsetPreferences(preferences).getOffsetMs())
    }
}
