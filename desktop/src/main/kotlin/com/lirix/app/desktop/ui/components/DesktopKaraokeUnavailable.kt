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
                DesktopLyricsContent.IDLE -> com.lirix.app.ui.i18n.LirixStrings.message("Ничего не играет")
                DesktopLyricsContent.LOADING -> com.lirix.app.ui.i18n.LirixStrings.message("Поиск текста песни…")
                DesktopLyricsContent.UNSYNCED -> com.lirix.app.ui.i18n.LirixStrings.message("Для караоке нужна синхронизация")
                else -> com.lirix.app.ui.i18n.LirixStrings.message("Текст не найден")
            }, color = colors.textPrimary)
            Text(when (content) {
                DesktopLyricsContent.IDLE -> com.lirix.app.ui.i18n.LirixStrings.message("Запустите музыку в любом плеере")
                DesktopLyricsContent.LOADING -> com.lirix.app.ui.i18n.LirixStrings.message("Ищем текст и временные метки")
                DesktopLyricsContent.UNSYNCED -> com.lirix.app.ui.i18n.LirixStrings.message("У этого текста нет временных меток. Расставьте их в Tap-to-Sync или откройте текст.")
                else -> com.lirix.app.ui.i18n.LirixStrings.message("Попробуйте другой источник или добавьте текст через «Текст / LRC».")
            }, color = colors.textSecondary)
            if (content == DesktopLyricsContent.UNSYNCED) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpenTapSync, enabled = canTapSync) { Text("Tap-to-Sync") }
                TextButton(onClick = onShowText) { Text(com.lirix.app.ui.i18n.LirixStrings.message("Открыть текст")) }
            }
        }
    }
}
