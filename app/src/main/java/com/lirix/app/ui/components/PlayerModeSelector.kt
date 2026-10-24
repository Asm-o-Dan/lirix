package com.lirix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lirix.app.ui.NowPlayingMode
import com.lirix.app.ui.theme.LirixThemeTokens

/** Equal targets grow with the tallest translated label, including system font scaling. */
@Composable
fun PlayerModeSelector(selected: NowPlayingMode, onSelect: (NowPlayingMode) -> Unit, modifier: Modifier = Modifier) {
    val colors = LirixThemeTokens.colors
    val shape = RoundedCornerShape(colors.pillCornerRadius)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).heightIn(min = 48.dp)
        .testTag("player-mode-selector").selectableGroup().clip(shape).background(colors.surfaceCard).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        NowPlayingMode.entries.forEach { mode ->
            val active = selected == mode
            Box(Modifier.weight(1f).heightIn(min = 48.dp).fillMaxHeight().clip(shape)
                .background(if (active) colors.accentPrimary else Color.Transparent)
                .selectable(selected = active, role = Role.Tab, onClick = { onSelect(mode) })
                .padding(horizontal = 4.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(mode.labelRes), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) colors.appBackground else colors.textSecondary)
            }
        }
    }
}
