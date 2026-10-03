package com.example.npc.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.npc.induction.SlotType
import com.example.npc.induction.TokenRole
import com.example.npc.induction.model.SlotRole

private data class RoleMenuItem(
    val title: String,
    val role: TokenRole,
    val slot: SlotType?,
    val color: Color
)

private val ROLE_MENU_ITEMS = listOf(
    RoleMenuItem("Сумма операции (TX_AMOUNT)", TokenRole.SLOT, SlotType.TX_AMOUNT, Color(0xFF4CAF50)),
    RoleMenuItem("Валюта (CURRENCY)", TokenRole.SLOT, SlotType.CURRENCY, Color(0xFF009688)),
    RoleMenuItem("Маска карты (CARD_MASK)", TokenRole.SLOT, SlotType.CARD_MASK, Color(0xFF9C27B0)),
    RoleMenuItem("Остаток (BALANCE)", TokenRole.SLOT, SlotType.BALANCE, Color(0xFF2196F3)),
    RoleMenuItem("Валюта баланса (BALANCE_CURRENCY)", TokenRole.SLOT, SlotType.BALANCE_CURRENCY, Color(0xFF00BCD4)),
    RoleMenuItem("Мерчант (MERCHANT)", TokenRole.SLOT, SlotType.MERCHANT, Color(0xFFE91E63)),
    RoleMenuItem("Комиссия (FEE)", TokenRole.SLOT, SlotType.FEE, Color(0xFFFF9800)),
    RoleMenuItem("Служебное (OTHER)", TokenRole.SLOT, SlotType.OTHER, Color(0xFF9E9E9E))
)

/**
 * Выпадающее меню выбора роли токена (LITERAL / SLOT) в One-Tap редакторе.
 */
@Composable
fun RoleSelectionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onRoleSelected: (role: TokenRole, slot: SlotType?) -> Unit,
    modifier: Modifier = Modifier,
    currentSlot: SlotType? = null
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        ROLE_MENU_ITEMS.forEach { item ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(item.color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.slot == currentSlot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                onClick = {
                    onRoleSelected(item.role, item.slot)
                    onDismissRequest()
                }
            )
        }

        HorizontalDivider()

        // Очистка роли / Сделать литералом
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0E0E0))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Очистить роль (Текст / Литерал)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            onClick = {
                onRoleSelected(TokenRole.LITERAL, null)
                onDismissRequest()
            }
        )
    }
}

/**
 * Перегрузка RoleSelectionMenu для сигнатуры (SlotRole?) -> Unit.
 */
@Composable
fun RoleSelectionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onSlotSelected: (slot: SlotRole?) -> Unit,
    modifier: Modifier = Modifier,
    currentSlot: SlotType? = null
) {
    RoleSelectionMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        onRoleSelected = { _, slot -> onSlotSelected(slot) },
        modifier = modifier,
        currentSlot = currentSlot
    )
}
