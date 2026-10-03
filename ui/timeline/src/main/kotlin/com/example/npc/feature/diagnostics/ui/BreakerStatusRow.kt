package com.example.npc.feature.diagnostics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.npc.feature.diagnostics.model.BreakerInfoUi
import com.example.npc.feature.diagnostics.model.BreakerUiState

/**
 * Цвета индикатора состояния предохранителя NodeCircuitBreaker согласно ТЗ:
 * Зеленый - CLOSED (нормальная работа)
 * Желтый - HALF_OPEN (пробные запросы)
 * Красный - OPEN (сработал предохранитель)
 */
val BreakerColorClosed = Color(0xFF4CAF50)
val BreakerColorHalfOpen = Color(0xFFFFB300)
val BreakerColorOpen = Color(0xFFE53935)

fun breakerStateColor(state: BreakerUiState): Color = when (state) {
    BreakerUiState.CLOSED -> BreakerColorClosed
    BreakerUiState.HALF_OPEN -> BreakerColorHalfOpen
    BreakerUiState.OPEN -> BreakerColorOpen
}

fun breakerStateLabel(state: BreakerUiState): String = when (state) {
    BreakerUiState.CLOSED -> "CLOSED"
    BreakerUiState.HALF_OPEN -> "HALF_OPEN"
    BreakerUiState.OPEN -> "OPEN"
}

@Composable
fun BreakerStatusRow(
    breaker: BreakerInfoUi,
    onResetClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val indicatorColor = breakerStateColor(breaker.state)
    val stateText = breakerStateLabel(breaker.state)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("breaker_row_${breaker.nodeId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Левая часть: Индикатор статуса и название узла
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                        .testTag("breaker_indicator_${breaker.nodeId}")
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = breaker.nodeId,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = stateText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = indicatorColor
                            ),
                            modifier = Modifier.testTag("breaker_state_${breaker.nodeId}")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Ошибок: ${breaker.failureCount}",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("breaker_failures_${breaker.nodeId}")
                        )
                    }
                }
            }

            // Правая часть: Кнопка ручного сброса «Reset»
            OutlinedButton(
                onClick = { onResetClick(breaker.nodeId) },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (breaker.state == BreakerUiState.OPEN) BreakerColorOpen else MaterialTheme.colorScheme.primary
                ),
                contentPadding = ButtonDefaults.ContentPadding,
                modifier = Modifier.testTag("breaker_reset_button_${breaker.nodeId}")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Сброс предохранителя",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Reset", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
