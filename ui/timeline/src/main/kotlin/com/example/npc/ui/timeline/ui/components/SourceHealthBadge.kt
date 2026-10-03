package com.example.npc.ui.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.npc.ui.timeline.model.SourceHealthStatus
import com.example.npc.ui.timeline.model.SourceHealthUiModel

@Composable
fun SourceHealthBadge(
    health: SourceHealthUiModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when (health.status) {
        SourceHealthStatus.GREEN -> Color(0xFF2E7D32)
        SourceHealthStatus.YELLOW -> Color(0xFFF57F17)
        SourceHealthStatus.RED -> Color(0xFFC62828)
    }

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = health.displayName,
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "(${health.events24hLabel})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (health.queueDepth > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "+${health.queueDepth}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        leadingIcon = {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            } else if (health.iconRes != 0) {
                Icon(
                    painter = painterResource(id = health.iconRes),
                    contentDescription = health.displayName,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        modifier = modifier
    )
}
