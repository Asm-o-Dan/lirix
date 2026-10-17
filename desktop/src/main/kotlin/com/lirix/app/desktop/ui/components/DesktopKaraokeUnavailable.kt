package com.lirix.app.desktop.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lirix.app.ui.theme.LirixThemeTokens

@Composable
fun DesktopKaraokeUnavailable(
    content: DesktopLyricsContent, canTapSync: Boolean, onOpenTapSync: () -> Unit,
    onShowText: () -> Unit, modifier: Modifier = Modifier
) {
    val colors = LirixThemeTokens.colors
    Box(modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (content == DesktopLyricsContent.LOADING) CircularProgressIndicator(color = colors.accentPrimary, modifier = Modifier.size(28.dp))
            Text(when (content) {
                DesktopLyricsContent.IDLE -> "Ничего не играет"
                DesktopLyricsContent.LOADING -> "Поиск текста песни…"
                DesktopLyricsContent.UNSYNCED -> "Для караоке нужна синхронизация"
                else -> "Текст не найден"
            }, color = colors.textPrimary)
            Text(when (content) {
                DesktopLyricsContent.IDLE -> "Запустите музыку в любом плеере"
                DesktopLyricsContent.LOADING -> "Ищем текст и временные метки"
                DesktopLyricsContent.UNSYNCED -> "У этого текста нет временных меток. Расставьте их в Tap-to-Sync или откройте текст."
                else -> "Попробуйте другой источник или добавьте текст через «Текст / LRC»."
            }, color = colors.textSecondary)
            if (content == DesktopLyricsContent.UNSYNCED) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenTapSync, enabled = canTapSync) { Text("Tap-to-Sync") }
                TextButton(onClick = onShowText) { Text("Открыть текст") }
            }
        }
    }
}
