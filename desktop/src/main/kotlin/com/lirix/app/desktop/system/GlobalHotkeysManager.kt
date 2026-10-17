package com.lirix.app.desktop.system

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** All registrations and cleanup execute on their owning Win32 message thread. */
class GlobalHotkeysManager(
    private val onToggleHud: () -> Unit,
    private val onTogglePlayPause: () -> Unit,
    private val onSkipNext: () -> Unit,
    private val onSkipPrevious: () -> Unit,
    private val extraActions: Map<DesktopAction, () -> Unit> = emptyMap()
) {
    private val callbacks = mapOf(
        DesktopAction.HUD_TOGGLE to onToggleHud, DesktopAction.PLAY_PAUSE to onTogglePlayPause,
        DesktopAction.NEXT to onSkipNext, DesktopAction.PREVIOUS to onSkipPrevious
    ) + extraActions
    private val _registrationFailures = MutableStateFlow<List<String>>(emptyList())
    val registrationFailures = _registrationFailures.asStateFlow()
    @Volatile private var running = false
    @Volatile private var threadId = 0
    private var worker: Thread? = null

    fun start() {
        if (running) return
        if (!System.getProperty("os.name").contains("Windows", true)) return
        running = true
        worker = Thread({
            threadId = Kernel32.INSTANCE.GetCurrentThreadId()
            // Ensure a message queue exists before another thread can post WM_QUIT.
            val msg = WinUser.MSG()
            User32.INSTANCE.PeekMessage(msg, null, 0, 0, 0)
            val registered = mutableListOf<HotkeyBinding>()
            val failures = mutableListOf<String>()
            try {
                for (binding in HotkeyCatalog.global.filter { it.action in callbacks }) {
                    if (User32.INSTANCE.RegisterHotKey(null, binding.id, 0x0002 or 0x0001 or 0x4000, binding.keyCode)) registered += binding
                    else failures += "${binding.keys} — занято другой программой"
                }
                _registrationFailures.value = failures
                while (running && User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
                    if (msg.message == WinUser.WM_HOTKEY) {
                        registered.firstOrNull { it.id == msg.wParam.toInt() }?.let { callbacks[it.action]?.invoke() }
                    }
                }
            } catch (error: Exception) {
                _registrationFailures.value = failures + "Глобальные клавиши недоступны: ${error.message}"
            } finally {
                registered.forEach { User32.INSTANCE.UnregisterHotKey(null, it.id) }
                threadId = 0
                running = false
            }
        }, "Lirix-GlobalHotkeys").apply { isDaemon = true; start() }
    }

    fun stop() {
        running = false
        val id = threadId
        if (id != 0) User32.INSTANCE.PostThreadMessage(id, WinUser.WM_QUIT, null, null)
        worker = null
    }
}
