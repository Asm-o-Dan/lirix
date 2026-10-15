package com.lirix.app.desktop.system

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import java.awt.event.KeyEvent

/**
 * Global OS hotkey manager for Windows using Win32 RegisterHotKey via JNA.
 * Registers:
 * - Ctrl + Alt + H: Toggle HUD mode
 * - Ctrl + Alt + Space: Play/Pause
 * - Ctrl + Alt + Right: Next track
 * - Ctrl + Alt + Left: Previous track
 *
 * Spec: TASK-KMP-12 (.sdd/tasks/TASK-KMP-12.md)
 */
class GlobalHotkeysManager(
    private val onToggleHud: () -> Unit,
    private val onTogglePlayPause: () -> Unit,
    private val onSkipNext: () -> Unit,
    private val onSkipPrevious: () -> Unit
) {
    companion object {
        const val HOTKEY_TOGGLE_HUD = 2001
        const val HOTKEY_PLAY_PAUSE = 2002
        const val HOTKEY_SKIP_NEXT = 2003
        const val HOTKEY_SKIP_PREV = 2004

        const val MOD_ALT = 0x0001
        const val MOD_CONTROL = 0x0002
        const val MOD_NOREPEAT = 0x4000
    }

    @Volatile
    private var isRunning = false
    private var workerThread: Thread? = null
    private var threadId: Int = 0

    fun start() {
        if (isRunning) return
        isRunning = true

        workerThread = Thread({
            threadId = Kernel32.INSTANCE.GetCurrentThreadId()
            val modifiers = MOD_CONTROL or MOD_ALT or MOD_NOREPEAT

            // Register Hotkeys
            val regHud = User32.INSTANCE.RegisterHotKey(null, HOTKEY_TOGGLE_HUD, modifiers, KeyEvent.VK_H)
            val regPlay = User32.INSTANCE.RegisterHotKey(null, HOTKEY_PLAY_PAUSE, modifiers, KeyEvent.VK_SPACE)
            val regNext = User32.INSTANCE.RegisterHotKey(null, HOTKEY_SKIP_NEXT, modifiers, KeyEvent.VK_RIGHT)
            val regPrev = User32.INSTANCE.RegisterHotKey(null, HOTKEY_SKIP_PREV, modifiers, KeyEvent.VK_LEFT)

            if (!regHud || !regPlay) {
                System.err.println("[GlobalHotkeysManager] Some hotkeys could not be registered (may already be in use)")
            }

            val msg = WinUser.MSG()
            try {
                while (isRunning && User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
                    if (msg.message == WinUser.WM_HOTKEY) {
                        when (msg.wParam.toInt()) {
                            HOTKEY_TOGGLE_HUD -> onToggleHud()
                            HOTKEY_PLAY_PAUSE -> onTogglePlayPause()
                            HOTKEY_SKIP_NEXT -> onSkipNext()
                            HOTKEY_SKIP_PREV -> onSkipPrevious()
                        }
                    }
                    User32.INSTANCE.TranslateMessage(msg)
                    User32.INSTANCE.DispatchMessage(msg)
                }
            } catch (_: InterruptedException) {
            } finally {
                User32.INSTANCE.UnregisterHotKey(null, HOTKEY_TOGGLE_HUD)
                User32.INSTANCE.UnregisterHotKey(null, HOTKEY_PLAY_PAUSE)
                User32.INSTANCE.UnregisterHotKey(null, HOTKEY_SKIP_NEXT)
                User32.INSTANCE.UnregisterHotKey(null, HOTKEY_SKIP_PREV)
            }
        }, "Lirix-GlobalHotkeys").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        isRunning = false
        if (threadId != 0) {
            User32.INSTANCE.PostThreadMessage(threadId, WinUser.WM_QUIT, null, null)
        }
        workerThread?.interrupt()
        workerThread = null
    }
}
