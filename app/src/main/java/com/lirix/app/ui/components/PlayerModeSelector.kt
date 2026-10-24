package com.lirix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import com.lirix.app.ui.NowPlayingMode
import com.lirix.app.ui.theme.LirixThemeTokens

/** Equal targets grow with the tallest translated label, including system font scaling. */
@Composable
fun PlayerModeSelector(selected: NowPlayingMode, onSelect: (NowPlayingMode) -> Unit, modifier: Modifier = Modifier) {
    val colors = LirixThemeTokens.colors
    val shape = RoundedCornerShape(colors.pillCornerRadius)
    val modes = NowPlayingMode.entries
    val labels = modes.map { stringResource(it.labelRes) }
    val labelStyle = LocalTextStyle.current.merge(MaterialTheme.typography.labelMedium)
    val styles = modes.map { mode -> labelStyle.copy(
        fontWeight = if (selected == mode) FontWeight.Bold else FontWeight.Medium,
        textAlign = TextAlign.Center)
    }
    val textMeasurer = rememberTextMeasurer()
    Layout(modifier = modifier.fillMaxWidth()
        .testTag("player-mode-selector").selectableGroup().clip(shape)
        .background(colors.surfaceCard).padding(3.dp), content = {
        modes.forEachIndexed { index, mode ->
            val active = selected == mode
            Box(Modifier.clip(shape)
                .background(if (active) colors.accentPrimary else Color.Transparent)
                .selectable(selected = active, role = Role.Tab, onClick = { onSelect(mode) })
                .padding(horizontal = 4.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                // Keep the Text box as wide as the paragraph. Shrink-wrapping a centred
                // label can make Text.size narrower than its paragraph by a few pixels.
                Text(labels[index], modifier = Modifier.fillMaxWidth(), style = styles[index],
                    color = if (active) colors.appBackground else colors.textSecondary)
            }
        }
    }) { measurables, constraints ->
        val gap = 4.dp.roundToPx()
        val horizontalPadding = 4.dp.roundToPx()
        val verticalPadding = 12.dp.roundToPx()
        val targetWidth = ((constraints.maxWidth - gap * (modes.size - 1)) / modes.size).coerceAtLeast(0)
        val labelWidth = (targetWidth - horizontalPadding * 2).coerceAtLeast(0)
        // Use the actual styled text layout at its final width, not an intrinsic height.
        // TextMeasurer creates no UI/semantics nodes, so each tab has exactly one label.
        val labelHeight = labels.indices.maxOf { index ->
            textMeasurer.measure(text = labels[index], style = styles[index],
                constraints = Constraints(maxWidth = labelWidth)).size.height
        }
        val targetHeight = constraints.constrainHeight(maxOf(48.dp.roundToPx(), labelHeight + verticalPadding * 2))
        val targets = measurables.map { it.measure(Constraints.fixed(targetWidth, targetHeight)) }
        layout(constraints.maxWidth, targetHeight) {
            targets.forEachIndexed { index, target -> target.placeRelative(index * (targetWidth + gap), 0) }
        }
    }
}
