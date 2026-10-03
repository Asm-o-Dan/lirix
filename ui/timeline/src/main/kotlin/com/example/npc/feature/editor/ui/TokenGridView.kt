package com.example.npc.feature.editor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.npc.feature.editor.model.EditorTokenUi
import com.example.npc.induction.SlotType
import com.example.npc.induction.TokenRole
import com.example.npc.induction.model.SlotRole
import kotlinx.collections.immutable.ImmutableList

/**
 * Состояние чипа токена в сетке токенов.
 */
@Immutable
data class TokenChipState(
    val tokenIndex: Int,
    val text: String,
    val role: SlotRole? = null,
    val isSelectable: Boolean = true,
    val isHighlighted: Boolean = false,
    val isAnchorKeyword: Boolean = false
) {
    val roleColor: Color
        get() = when (role) {
            SlotType.AMOUNT, SlotType.TX_AMOUNT -> Color(0xFF4CAF50) // Зеленый (Сумма)
            SlotType.CURRENCY  -> Color(0xFF009688) // Бирюзовый (Валюта)
            SlotType.BALANCE_CURRENCY -> Color(0xFF00BCD4) // Голубой (Валюта баланса)
            SlotType.CARD_MASK -> Color(0xFF9C27B0) // Пурпурный (Маска карты)
            SlotType.BALANCE   -> Color(0xFF2196F3) // Синий (Баланс)
            SlotType.MERCHANT  -> Color(0xFFE91E63) // Розовый (Мерчант)
            SlotType.FEE       -> Color(0xFFFF9800) // Оранжевый (Комиссия)
            SlotType.OTHER     -> Color(0xFF9E9E9E) // Серый
            null               -> Color(0xFFE0E0E0) // Нейтральный фон
        }
}

fun EditorTokenUi.toTokenChipState(): TokenChipState = TokenChipState(
    tokenIndex = index,
    text = text,
    role = assignedSlot,
    isSelectable = isSelectable,
    isHighlighted = isHighlighted,
    isAnchorKeyword = isAnchorKeyword
)

fun TokenChipState.toEditorTokenUi(): EditorTokenUi = EditorTokenUi(
    index = tokenIndex,
    text = text,
    role = if (role != null) TokenRole.SLOT else TokenRole.LITERAL,
    assignedSlot = role,
    isAnchorKeyword = isAnchorKeyword,
    isSelectable = isSelectable,
    isHighlighted = isHighlighted
)

/**
 * Интерактивная токенная сетка (FlowRow) кликабельных чипов
 * для разметки токенов LITERAL / SLOT в диалоге One-Tap редактора.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TokenGridView(
    chips: List<TokenChipState>,
    onChipClick: (tokenIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { chip ->
            val border = resolveChipBorder(chip.isHighlighted, chip.isAnchorKeyword)
            val contentColor = resolveContentColor(chip.roleColor)

            Surface(
                modifier = Modifier
                    .clickable(enabled = chip.isSelectable) {
                        onChipClick(chip.tokenIndex)
                    },
                shape = RoundedCornerShape(8.dp),
                color = chip.roleColor,
                border = border,
                shadowElevation = if (chip.isHighlighted) 3.dp else 0.dp
            ) {
                Text(
                    text = chip.text,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (chip.role != null || chip.isAnchorKeyword) FontWeight.SemiBold else FontWeight.Normal,
                    color = contentColor
                )
            }
        }
    }
}

/**
 * Перегрузка TokenGridView для прямого использования с ImmutableList<EditorTokenUi>.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TokenGridView(
    tokens: ImmutableList<EditorTokenUi>,
    onChipClick: (tokenIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val chips = tokens.map { it.toTokenChipState() }
    TokenGridView(
        chips = chips,
        onChipClick = onChipClick,
        modifier = modifier
    )
}

private fun resolveChipBorder(isHighlighted: Boolean, isAnchorKeyword: Boolean): BorderStroke? {
    return when {
        isHighlighted -> BorderStroke(2.dp, Color(0xFF1976D2))
        isAnchorKeyword -> BorderStroke(1.5.dp, Color(0xFF7B1FA2))
        else -> null
    }
}

private fun resolveContentColor(backgroundColor: Color): Color {
    // Нейтральный фон E0E0E0 требует темного текста, цветные слоты — белого
    return if (backgroundColor == Color(0xFFE0E0E0)) {
        Color(0xFF212121)
    } else {
        Color.White
    }
}
