package com.lirix.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lirix.app.ui.theme.LirixThemeTokens

@Composable
fun PlayerConnectButton(connected: Boolean, onClick: () -> Unit) {
    val colors = LirixThemeTokens.colors
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(Icons.Default.DevicesOther, "Подключить устройство (ПК)",
            tint = if (connected) colors.accentPrimary else colors.textSecondary)
    }
}

/** Five equal slots keep playback centred, including when skip commands are disabled. */
@Composable
fun PlayerTransportRow(
    isPlaying: Boolean, connected: Boolean,
    onConnect: () -> Unit, onPrevious: () -> Unit, onTogglePlayback: () -> Unit,
    onNext: () -> Unit, onShare: () -> Unit,
    canPrevious: Boolean = true, canNext: Boolean = true
) {
    val colors = LirixThemeTokens.colors
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp).testTag("player-transport"),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { PlayerConnectButton(connected, onConnect) }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(onPrevious, enabled = canPrevious, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipPrevious, "Предыдущий", tint = if (canPrevious) colors.textPrimary else colors.textMuted, modifier = Modifier.size(30.dp))
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            FilledIconButton(onTogglePlayback, modifier = Modifier.size(56.dp), shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = colors.accentPrimary, contentColor = colors.appBackground)) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (isPlaying) "Пауза" else "Играть", modifier = Modifier.size(32.dp))
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(onNext, enabled = canNext, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipNext, "Следующий", tint = if (canNext) colors.textPrimary else colors.textMuted, modifier = Modifier.size(30.dp))
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(onShare, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Share, "Поделиться карточкой", tint = colors.textSecondary) }
        }
    }
}
