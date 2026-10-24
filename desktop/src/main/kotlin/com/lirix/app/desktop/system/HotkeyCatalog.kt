package com.lirix.app.desktop.system

import java.awt.event.KeyEvent

enum class DesktopAction { HUD_TOGGLE, HUD_EDIT, PLAY_PAUSE, NEXT, PREVIOUS, SEEK_BACK, SEEK_FORWARD, SHOW_MAIN, MINI_PLAYER, HELP, HUD_LARGER, HUD_SMALLER, HUD_RESET }
data class HotkeyBinding(val action: DesktopAction, val keyCode: Int, val keys: String, private val sourceTitle: String) {
    val title: String get() = com.lirix.app.ui.i18n.LirixStrings.message(sourceTitle)
    val id: Int get() = 2001 + action.ordinal
}
object HotkeyCatalog {
    val global = listOf(
        HotkeyBinding(DesktopAction.HUD_TOGGLE, KeyEvent.VK_H, "Ctrl+Alt+H", "Показать / скрыть оверлей"),
        HotkeyBinding(DesktopAction.HUD_EDIT, KeyEvent.VK_L, "Ctrl+Alt+L", "Перемещать / зафиксировать оверлей"),
        HotkeyBinding(DesktopAction.PLAY_PAUSE, KeyEvent.VK_SPACE, "Ctrl+Alt+Space", "Воспроизведение / пауза"),
        HotkeyBinding(DesktopAction.NEXT, KeyEvent.VK_RIGHT, "Ctrl+Alt+→", "Следующий трек"),
        HotkeyBinding(DesktopAction.PREVIOUS, KeyEvent.VK_LEFT, "Ctrl+Alt+←", "Предыдущий трек"),
        HotkeyBinding(DesktopAction.SEEK_BACK, KeyEvent.VK_PAGE_UP, "Ctrl+Alt+PageUp", "Назад на 5 секунд"),
        HotkeyBinding(DesktopAction.SEEK_FORWARD, KeyEvent.VK_PAGE_DOWN, "Ctrl+Alt+PageDown", "Вперёд на 5 секунд"),
        HotkeyBinding(DesktopAction.SHOW_MAIN, KeyEvent.VK_M, "Ctrl+Alt+M", "Открыть главное окно"),
        HotkeyBinding(DesktopAction.MINI_PLAYER, KeyEvent.VK_W, "Ctrl+Alt+W", "Мини-плеер / полное окно"),
        HotkeyBinding(DesktopAction.HELP, KeyEvent.VK_K, "Ctrl+Alt+K", "Справка по горячим клавишам"),
        HotkeyBinding(DesktopAction.HUD_LARGER, KeyEvent.VK_ADD, "Ctrl+Alt+Num +", "Увеличить текст оверлея"),
        HotkeyBinding(DesktopAction.HUD_SMALLER, KeyEvent.VK_SUBTRACT, "Ctrl+Alt+Num −", "Уменьшить текст оверлея"),
        HotkeyBinding(DesktopAction.HUD_RESET, KeyEvent.VK_R, "Ctrl+Alt+R", "Вернуть оверлей на видимое место")
    )
    val local get() = listOf(
        "F1" to com.lirix.app.ui.i18n.LirixStrings.message("Справка по сочетаниям"),
        "Ctrl+1 / Ctrl+2 / Ctrl+3" to com.lirix.app.ui.i18n.LirixStrings.message("Плеер / медиатека / итоги"),
        "Ctrl+F" to com.lirix.app.ui.i18n.LirixStrings.message("Поиск в медиатеке"),
        "Ctrl+E" to com.lirix.app.ui.i18n.LirixStrings.message("Редактор текста текущего трека"),
        "Ctrl+T" to com.lirix.app.ui.i18n.LirixStrings.message("Tap-to-Sync текущего трека"),
        "Ctrl+Shift+S" to com.lirix.app.ui.i18n.LirixStrings.message("Экспорт постера"),
        "Ctrl+Shift+D" to com.lirix.app.ui.i18n.LirixStrings.message("Устройства Connect"),
        com.lirix.app.ui.i18n.LirixStrings.message("Space / Enter в студии") to com.lirix.app.ui.i18n.LirixStrings.message("Отметить начало строки"),
        com.lirix.app.ui.i18n.LirixStrings.message("Ctrl+Z в студии") to com.lirix.app.ui.i18n.LirixStrings.message("Отменить последнюю отметку")
    )
}
