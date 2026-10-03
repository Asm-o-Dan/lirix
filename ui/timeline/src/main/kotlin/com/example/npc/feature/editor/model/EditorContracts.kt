package com.example.npc.feature.editor.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.npc.domain.mvi.UiEffect
import com.example.npc.domain.mvi.UiIntent
import com.example.npc.domain.mvi.UiState
import com.example.npc.induction.SlotType
import com.example.npc.induction.TokenRole
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * UI-модель отображаемого токена в One-Tap редакторе шаблонов.
 */
@Immutable
data class EditorTokenUi(
    val index: Int,
    val text: String,
    val role: TokenRole = TokenRole.LITERAL,
    val assignedSlot: SlotType? = null,
    val isAnchorKeyword: Boolean = false,
    val isSelectable: Boolean = true,
    val isHighlighted: Boolean = false
) {
    val roleColor: Color
        get() = when (assignedSlot) {
            SlotType.AMOUNT, SlotType.TX_AMOUNT -> Color(0xFF4CAF50) // Зеленый (Сумма)
            SlotType.CURRENCY -> Color(0xFF009688) // Бирюзовый (Валюта)
            SlotType.BALANCE_CURRENCY -> Color(0xFF00BCD4) // Голубой (Валюта баланса)
            SlotType.CARD_MASK -> Color(0xFF9C27B0) // Пурпурный (Карта)
            SlotType.BALANCE -> Color(0xFF2196F3) // Синий (Баланс)
            SlotType.MERCHANT -> Color(0xFFE91E63) // Розовый (Мерчант)
            SlotType.FEE -> Color(0xFFFF9800) // Оранжевый (Комиссия)
            SlotType.OTHER -> Color(0xFF795548) // Коричневый
            null -> when (role) {
                TokenRole.SLOT -> Color(0xFF8BC34A)
                TokenRole.VARIABLE -> Color(0xFF607D8B)
                TokenRole.WHITESPACE -> Color.Transparent
                TokenRole.LITERAL -> Color(0xFFE0E0E0) // Нейтральный
            }
        }
}

/**
 * MVI UI-состояние One-Tap редактора шаблонов.
 */
@Immutable
data class EditorUiState(
    val eventId: Long,
    val packageName: String,
    val rawText: String = "",
    val tokens: ImmutableList<EditorTokenUi> = persistentListOf(),
    val detectedOpType: String = "AUTO",
    val isRefund: Boolean = false,
    val isValidationInProgress: Boolean = false,
    val candidatePattern: String? = null,
    val positiveMatchesCount: Int = 0,
    val conflictCount: Int = 0,
    val validationError: String? = null,
    val canSave: Boolean = false,
    val isSaving: Boolean = false,
    val canUndo: Boolean = false
) : UiState

/**
 * Намерения пользователя в One-Tap редакторе.
 */
sealed interface EditorUiIntent : UiIntent {
    data class AssignTokenRole(val tokenIndex: Int, val role: TokenRole, val slot: SlotType?) : EditorUiIntent
    data class ToggleRefund(val isRefund: Boolean) : EditorUiIntent
    data class ChangeOpType(val opType: String) : EditorUiIntent
    data object SaveAndActivate : EditorUiIntent
    data object Undo : EditorUiIntent
    data object Dismiss : EditorUiIntent
}

/**
 * Одноразовые сайд-эффекты One-Tap редактора.
 */
sealed interface EditorUiEffect : UiEffect {
    data class SavedSuccessfully(val templateId: String, val affectedEventsCount: Int) : EditorUiEffect
    data class ShowToast(val message: String) : EditorUiEffect
    data object CloseSheet : EditorUiEffect
}
