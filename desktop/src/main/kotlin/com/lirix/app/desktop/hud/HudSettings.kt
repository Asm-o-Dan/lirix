package com.lirix.app.desktop.hud

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.prefs.Preferences

data class HudSettings(val width: Float = 680f, val height: Float = 240f, val x: Float? = null, val y: Float? = null,
                       val locked: Boolean = false, val textScale: Float = 1f, val opacity: Float = .9f) {
    fun normalized() = copy(width = width.coerceIn(380f, 1800f), height = height.coerceIn(180f, 900f),
        textScale = textScale.coerceIn(.7f, 2f), opacity = opacity.coerceIn(.35f, 1f))
}

class HudSettingsStore(private val preferences: Preferences = Preferences.userRoot().node("com/lirix/desktop/hud")) {
    private val _state = MutableStateFlow(HudSettings(
        width = preferences.getFloat("width", 680f), height = preferences.getFloat("height", 240f),
        x = preferences.get("x", null)?.toFloatOrNull(), y = preferences.get("y", null)?.toFloatOrNull(),
        locked = preferences.getBoolean("locked", false), textScale = preferences.getFloat("textScale", 1f),
        opacity = preferences.getFloat("opacity", .9f)).normalized())
    val state = _state.asStateFlow()
    fun update(value: HudSettings) {
        val next = value.normalized()
        _state.value = next
        preferences.putFloat("width", next.width); preferences.putFloat("height", next.height)
        if (next.x == null) preferences.remove("x") else preferences.putFloat("x", next.x)
        if (next.y == null) preferences.remove("y") else preferences.putFloat("y", next.y)
        preferences.putBoolean("locked", next.locked); preferences.putFloat("textScale", next.textScale)
        preferences.putFloat("opacity", next.opacity)
    }
    fun reset() = update(HudSettings())
}
