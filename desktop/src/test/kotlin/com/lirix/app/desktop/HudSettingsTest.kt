package com.lirix.app.desktop

import com.lirix.app.desktop.hud.*
import com.lirix.app.desktop.system.HotkeyCatalog
import java.util.prefs.Preferences
import org.junit.Assert.*
import org.junit.Test

class HudSettingsTest {
    @Test fun positionSizeAndEditModeSurviveStoreRecreation() {
        val preferences = Preferences.userRoot().node("com/lirix/tests/" + java.util.UUID.randomUUID())
        try {
            val store = HudSettingsStore(preferences)
            store.update(HudSettings(width = 910f, height = 320f, x = -500f, y = 120f, locked = true, textScale = 1.4f))
            val restored = HudSettingsStore(preferences).state.value
            assertEquals(store.state.value, restored)
            store.reset()
            assertNull(store.state.value.x)
            assertFalse(store.state.value.locked)
        } finally { preferences.removeNode() }
    }
    @Test fun hotkeysHaveUniqueBindingsAndExplainHowToUnlockHud() {
        assertEquals(HotkeyCatalog.global.size, HotkeyCatalog.global.map { it.keyCode }.distinct().size)
        assertEquals(HotkeyCatalog.global.size, HotkeyCatalog.global.map { it.id }.distinct().size)
        assertTrue(HotkeyCatalog.global.any { it.keys == "Ctrl+Alt+L" })
        assertTrue(HotkeyCatalog.global.size > 4)
    }
}
