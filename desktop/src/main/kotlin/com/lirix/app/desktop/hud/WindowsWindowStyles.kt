package com.lirix.app.desktop.hud

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import java.awt.Window

/**
 * Windows JNA helper for advanced window extended styles (WS_EX_LAYERED, WS_EX_TRANSPARENT).
 * Enables seamless click-through overlays over fullscreen games and desktop apps.
 * Spec: TASK-KMP-11 (.sdd/tasks/TASK-KMP-11.md)
 */
object WindowsWindowStyles {
    const val GWL_EXSTYLE = -20
    const val WS_EX_LAYERED = 0x00080000
    const val WS_EX_TRANSPARENT = 0x00000020
    const val WS_EX_TOPMOST = 0x00000008
    const val WS_EX_TOOLWINDOW = 0x00000080

    /**
     * Resolves Win32 HWND from AWT/Compose Window.
     */
    fun getHwnd(window: Window): WinDef.HWND? {
        return try {
            val pointer = Native.getWindowPointer(window) ?: Native.getComponentPointer(window)
            if (pointer != null) WinDef.HWND(pointer) else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Toggles click-through (transparent to mouse events) mode.
     * When [clickThrough] is true, mouse clicks pass directly to the application underneath.
     */
    fun setClickThrough(window: Window, clickThrough: Boolean) {
        val hwnd = getHwnd(window) ?: return
        try {
            val currentStyle = User32.INSTANCE.GetWindowLong(hwnd, GWL_EXSTYLE)
            val newStyle = if (clickThrough) {
                currentStyle or WS_EX_LAYERED or WS_EX_TRANSPARENT
            } else {
                (currentStyle or WS_EX_LAYERED) and WS_EX_TRANSPARENT.inv()
            }
            User32.INSTANCE.SetWindowLong(hwnd, GWL_EXSTYLE, newStyle)
        } catch (e: Throwable) {
            System.err.println("[WindowsWindowStyles] Failed to set click-through ($clickThrough): ${e.message}")
        }
    }
}
