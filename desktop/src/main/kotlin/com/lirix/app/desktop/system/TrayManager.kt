package com.lirix.app.desktop.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Tray
import java.awt.image.BufferedImage
import java.awt.RenderingHints
import androidx.compose.ui.graphics.toComposeImageBitmap

/**
 * System tray integration for Windows Desktop using Compose Desktop Tray API.
 * Spec: TASK-KMP-12 (.sdd/tasks/TASK-KMP-12.md)
 */
@Composable
fun ApplicationScope.LirixTray(
    onOpenMainWindow: () -> Unit,
    onToggleHud: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onExit: () -> Unit,
    isPlaying: Boolean,
    trackTitle: String? = null
) {
    val trayIcon = rememberTrayIcon()

    val tooltipText = remember(trackTitle, isPlaying) {
        if (!trackTitle.isNullOrBlank()) {
            "Lirix: $trackTitle"
        } else {
            "Lirix Music Hub"
        }
    }

    Tray(
        icon = trayIcon,
        tooltip = tooltipText,
        onAction = onOpenMainWindow,
        menu = {
            Item("Открыть Lirix", onClick = onOpenMainWindow)
            Item("Переключить HUD (Ctrl+Alt+H)", onClick = onToggleHud)
            Separator()
            Item(if (isPlaying) "⏸ Пауза" else "▶ Играть", onClick = onTogglePlayPause)
            Item("⏭ Следующий трек", onClick = onSkipNext)
            Separator()
            Item("Выход", onClick = onExit)
        }
    )
}

@Composable
private fun rememberTrayIcon(): Painter {
    return remember {
        // Draw crisp 32x32 Lirix disc tray icon
        val img = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        // Dark circular vinyl base
        g.color = java.awt.Color(18, 18, 18)
        g.fillOval(2, 2, 28, 28)

        // Neon accent ring
        g.color = java.awt.Color(29, 185, 84) // Lirix Spotify Emerald
        g.drawOval(2, 2, 27, 27)
        g.drawOval(7, 7, 17, 17)

        // Center spindle
        g.color = java.awt.Color(255, 255, 255)
        g.fillOval(13, 13, 6, 6)

        g.dispose()

        BitmapPainter(img.toComposeImageBitmap())
    }
}
