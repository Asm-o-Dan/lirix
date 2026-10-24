package com.lirix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lirix.app.R
import com.lirix.app.ui.theme.LirixThemeTokens
import kotlin.math.roundToInt

/** Same 48 dp surfaces and 8 dp corners as timing correction, in both orientations. */
@Composable
fun TextSizeControls(scale: Float, onChange: (Float) -> Unit) {
    val colors = LirixThemeTokens.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        @Composable fun Step(label: String, description: String, delta: Float) {
            IconButton(onClick = { onChange((scale + delta).coerceIn(.8f, 1.5f)) },
                modifier = Modifier.size(48.dp).background(colors.surfaceElevated, RoundedCornerShape(8.dp))
                    .semantics { contentDescription = description }) {
                Text(label, color = colors.textPrimary)
            }
        }
        Step("−", stringResource(R.string.text_size_decrease), -.1f)
        Text("Aa ${(scale * 100).roundToInt()}%", color = colors.textSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.widthIn(min = 64.dp).padding(horizontal = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Step("+", stringResource(R.string.text_size_increase), .1f)
    }
}
