package com.lirix.app.desktop.system

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary
import java.awt.Window

/**
 * Utility to toggle the native Windows title bar dark mode using DwmSetWindowAttribute.
 * Supports Windows 10 (19/20) and Windows 11.
 * Spec: TASK-UI-PROMPTER-FIX-01 §8
 */
object WindowsTitleBarDarkTheme {

    private interface Dwmapi : StdCallLibrary {
        fun DwmSetWindowAttribute(hwnd: HWND, dwAttribute: Int, pvAttribute: Pointer, cbAttribute: Int): Int

        companion object {
            val INSTANCE: Dwmapi? = try {
                Native.load("dwmapi", Dwmapi::class.java)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_19 = 19
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE_20 = 20

    fun apply(window: Window?, isDark: Boolean) {
        if (window == null) return
        try {
            val os = System.getProperty("os.name")?.lowercase() ?: ""
            if (!os.contains("win")) return

            val hwnd = HWND(Native.getWindowPointer(window))
            val dwm = Dwmapi.INSTANCE ?: return

            val memory = Memory(4)
            memory.setInt(0, if (isDark) 1 else 0)

            // Attribute 20 is standard on Win11 & Win10 20H1+; fall back to 19 for older Win10
            val res20 = dwm.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_20, memory, 4)
            if (res20 != 0) {
                dwm.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE_19, memory, 4)
            }
        } catch (_: Throwable) {
            // Silently ignore if not supported or headless
        }
    }
}
