package com.lirix.app.desktop.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lirix.app.ui.theme.LirixThemeTokens

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortcutButton(label: String, keys: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LirixThemeTokens.colors
    TooltipArea(tooltip = { Surface(color = colors.surfaceElevated) {
        Text("$label · $keys", color = colors.textPrimary, modifier = Modifier.padding(10.dp))
    } }, delayMillis = 450) {
        TextButton(onClick = onClick, enabled = enabled) { Text(label) }
    }
}
