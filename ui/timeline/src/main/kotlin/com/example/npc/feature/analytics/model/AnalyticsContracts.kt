package com.example.npc.feature.analytics.model

import androidx.compose.runtime.Immutable
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.domain.mvi.UiEffect
import com.example.npc.domain.mvi.UiIntent
import com.example.npc.domain.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.time.Instant

/**
 * Период фильтрации аналитики.
 */
enum class TimePeriodSelection {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    CUSTOM
}

/**
 * Режим учета возвратов (ADR-306).
 */
enum class RefundCalculationMode {
    REDUCE_EXPENSE, // Возврат вычитается из расходов (по умолчанию)
    TREAT_AS_INCOME // Возврат прибавляется к доходам
}

/**
 * UI-модель карточки отдельной валюты (ADR-305: строго изолированные валюты).
 */
@Immutable
data class CurrencyCardUi(
    val currency: CurrencyCode,
    val totalExpenseMinor: Long,
    val totalIncomeMinor: Long,
    val totalRefundMinor: Long,
    val netMinor: Long,
    val transactionCount: Int
)

/**
 * MVI UI-состояние экрана финансовой аналитики.
 */
@Immutable
data class AnalyticsUiState(
    val period: TimePeriodSelection = TimePeriodSelection.THIS_MONTH,
    val customFrom: Instant? = null,
    val customTo: Instant? = null,
    val directionFilter: TransactionType? = null,
    val refundMode: RefundCalculationMode = RefundCalculationMode.REDUCE_EXPENSE,
    val includeSuggested: Boolean = false,
    val cards: ImmutableList<CurrencyCardUi> = persistentListOf(),
    val activeCurrencyFilter: CurrencyCode? = null,
    val trendDataPoints: ImmutableList<Long> = persistentListOf(),
    val isLoading: Boolean = false
) : UiState

/**
 * Намерения экрана финансовой аналитики.
 */
sealed interface AnalyticsUiIntent : UiIntent {
    data class SelectPeriod(val period: TimePeriodSelection) : AnalyticsUiIntent
    data class SetCustomRange(val from: Instant, val to: Instant) : AnalyticsUiIntent
    data class SetRefundMode(val mode: RefundCalculationMode) : AnalyticsUiIntent
    data class ToggleRefundMode(val isReduceExpense: Boolean) : AnalyticsUiIntent
    data class SelectCurrency(val currency: CurrencyCode?) : AnalyticsUiIntent
    data class SetDirectionFilter(val direction: TransactionType?) : AnalyticsUiIntent
    data class ToggleIncludeSuggested(val include: Boolean) : AnalyticsUiIntent
    data object Refresh : AnalyticsUiIntent
}

/**
 * Сайд-эффекты экрана финансовой аналитики.
 */
sealed interface AnalyticsUiEffect : UiEffect {
    data class ShowToast(val message: String) : AnalyticsUiEffect
    data class NavigateToTransaction(val transactionId: Long) : AnalyticsUiEffect
}
