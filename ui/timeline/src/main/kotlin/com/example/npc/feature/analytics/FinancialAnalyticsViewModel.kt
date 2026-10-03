package com.example.npc.feature.analytics

import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.storage.StorageGateway
import com.example.npc.domain.mvi.BaseViewModel
import com.example.npc.feature.analytics.model.AnalyticsUiEffect
import com.example.npc.feature.analytics.model.AnalyticsUiIntent
import com.example.npc.feature.analytics.model.AnalyticsUiState
import com.example.npc.feature.analytics.model.CurrencyCardUi
import com.example.npc.feature.analytics.model.RefundCalculationMode
import com.example.npc.feature.analytics.model.TimePeriodSelection
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

/**
 * MVI ViewModel экрана финансовой аналитики.
 * Реактивно агрегирует мультивалютные итоги через StorageGateway.observeAggregatedTotals.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FinancialAnalyticsViewModel(
    private val storageGateway: StorageGateway,
    coroutineScope: CoroutineScope? = null
) : BaseViewModel<AnalyticsUiState, AnalyticsUiIntent, AnalyticsUiEffect>(
    initialState = AnalyticsUiState(
        period = TimePeriodSelection.THIS_MONTH,
        refundMode = RefundCalculationMode.REDUCE_EXPENSE,
        isLoading = true
    ),
    coroutineScope = coroutineScope
) {

    private val supportedCurrencies = listOf(
        CurrencyCode.MDL,
        CurrencyCode.RUP,
        CurrencyCode.USD,
        CurrencyCode.EUR,
        CurrencyCode.RUB
    )

    private val queryParamsFlow = MutableStateFlow(
        QueryParams(
            period = TimePeriodSelection.THIS_MONTH,
            customFrom = null,
            customTo = null,
            refundMode = RefundCalculationMode.REDUCE_EXPENSE,
            includeSuggested = false,
            directionFilter = null,
            activeCurrency = null
        )
    )

    private data class QueryParams(
        val period: TimePeriodSelection,
        val customFrom: Instant?,
        val customTo: Instant?,
        val refundMode: RefundCalculationMode,
        val includeSuggested: Boolean,
        val directionFilter: TransactionType?,
        val activeCurrency: CurrencyCode?
    )

    private val observationJob = viewModelScope.launch {
        queryParamsFlow
            .flatMapLatest { params ->
                    val (from, to) = resolveTimeRange(params.period, params.customFrom, params.customTo)
                    val reduceRefund = params.refundMode == RefundCalculationMode.REDUCE_EXPENSE

                    combine(
                        storageGateway.observeAggregatedTotals(
                            from = from,
                            to = to,
                            direction = params.directionFilter,
                            reduceExpenseByRefund = reduceRefund,
                            includeSuggested = params.includeSuggested
                        ),
                        storageGateway.observeTransactionsByPeriod(from, to)
                    ) { totals, transactions ->
                        // 1. Агрегация мультивалютных карточек
                        val cards = supportedCurrencies.map { currency ->
                            val sums = totals[currency]
                            val rawExpense = sums?.expenseMinor ?: 0L
                            val rawIncome = sums?.incomeMinor ?: 0L
                            val rawRefund = sums?.refundMinor ?: 0L
                            val count = sums?.count ?: 0

                            val netExpense = if (reduceRefund) {
                                maxOf(0L, rawExpense - rawRefund)
                            } else {
                                rawExpense
                            }

                            val netIncome = if (!reduceRefund) {
                                rawIncome + rawRefund
                            } else {
                                rawIncome
                            }

                            val net = netIncome - netExpense

                            CurrencyCardUi(
                                currency = currency,
                                totalExpenseMinor = netExpense,
                                totalIncomeMinor = netIncome,
                                totalRefundMinor = rawRefund,
                                netMinor = net,
                                transactionCount = count
                            )
                        }.toPersistentList()

                        // 2. Расчет точек графика тренда трат
                        val selectedCurr = params.activeCurrency ?: CurrencyCode.MDL
                        val filteredTx = transactions.filter {
                            it.money.currency == selectedCurr &&
                                (params.directionFilter == null || it.type == params.directionFilter)
                        }

                        val dailyPoints = filteredTx
                            .sortedBy { it.timestamp }
                            .map { it.money.amountMinor }
                            .toPersistentList()

                        Triple(cards, dailyPoints, false)
                    }
                }
                .collect { (cards, points, loading) ->
                    updateState {
                        it.copy(
                            cards = cards,
                            trendDataPoints = points,
                            isLoading = loading
                        )
                    }
                }
        }

    fun send(intent: AnalyticsUiIntent) = dispatch(intent)

    override fun handleIntent(intent: AnalyticsUiIntent) {
        when (intent) {
            is AnalyticsUiIntent.SelectPeriod -> {
                updateState { it.copy(period = intent.period, isLoading = true) }
                updateParams { copy(period = intent.period) }
            }

            is AnalyticsUiIntent.SetCustomRange -> {
                updateState {
                    it.copy(
                        period = TimePeriodSelection.CUSTOM,
                        customFrom = intent.from,
                        customTo = intent.to,
                        isLoading = true
                    )
                }
                updateParams {
                    copy(
                        period = TimePeriodSelection.CUSTOM,
                        customFrom = intent.from,
                        customTo = intent.to
                    )
                }
            }

            is AnalyticsUiIntent.SetRefundMode -> {
                updateState { it.copy(refundMode = intent.mode, isLoading = true) }
                updateParams { copy(refundMode = intent.mode) }
            }

            is AnalyticsUiIntent.ToggleRefundMode -> {
                val mode = if (intent.isReduceExpense) {
                    RefundCalculationMode.REDUCE_EXPENSE
                } else {
                    RefundCalculationMode.TREAT_AS_INCOME
                }
                updateState { it.copy(refundMode = mode, isLoading = true) }
                updateParams { copy(refundMode = mode) }
            }

            is AnalyticsUiIntent.SelectCurrency -> {
                val newCurrency = if (currentState.activeCurrencyFilter == intent.currency) {
                    null // повторный тап сбрасывает фильтр
                } else {
                    intent.currency
                }
                updateState { it.copy(activeCurrencyFilter = newCurrency) }
                updateParams { copy(activeCurrency = newCurrency) }
            }

            is AnalyticsUiIntent.SetDirectionFilter -> {
                updateState { it.copy(directionFilter = intent.direction, isLoading = true) }
                updateParams { copy(directionFilter = intent.direction) }
            }

            is AnalyticsUiIntent.ToggleIncludeSuggested -> {
                updateState { it.copy(includeSuggested = intent.include, isLoading = true) }
                updateParams { copy(includeSuggested = intent.include) }
            }

            is AnalyticsUiIntent.Refresh -> {
                updateState { it.copy(isLoading = true) }
                queryParamsFlow.value = queryParamsFlow.value.copy()
            }
        }
    }

    private fun updateParams(transform: QueryParams.() -> QueryParams) {
        queryParamsFlow.value = queryParamsFlow.value.transform()
    }

    override fun close() {
        observationJob.cancel()
        super.close()
    }

    companion object {
        fun resolveTimeRange(
            period: TimePeriodSelection,
            customFrom: Instant?,
            customTo: Instant?
        ): Pair<Instant, Instant> {
            val now = Instant.now()
            val today = LocalDate.now(ZoneOffset.UTC)

            return when (period) {
                TimePeriodSelection.TODAY -> {
                    val start = today.atStartOfDay().toInstant(ZoneOffset.UTC)
                    Pair(start, now)
                }

                TimePeriodSelection.THIS_WEEK -> {
                    val monday = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
                    val start = monday.atStartOfDay().toInstant(ZoneOffset.UTC)
                    Pair(start, now)
                }

                TimePeriodSelection.THIS_MONTH -> {
                    val firstDay = today.with(TemporalAdjusters.firstDayOfMonth())
                    val start = firstDay.atStartOfDay().toInstant(ZoneOffset.UTC)
                    Pair(start, now)
                }

                TimePeriodSelection.CUSTOM -> {
                    val from = customFrom ?: now.minusSeconds(86400 * 30)
                    val to = customTo ?: now
                    Pair(from, to)
                }
            }
        }
    }
}
