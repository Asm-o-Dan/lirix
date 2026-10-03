package com.example.npc.feature.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.feature.analytics.model.AnalyticsUiIntent
import com.example.npc.feature.analytics.model.CurrencyCardUi
import com.example.npc.feature.analytics.model.RefundCalculationMode
import com.example.npc.feature.analytics.model.TimePeriodSelection
import java.util.Locale

/**
 * Главный экран финансовой аналитики с мультивалютным дашбордом
 * (изолированные карточки для MDL, RUP, USD, EUR, RUB согласно ADR-305)
 * и аппаратным графиком тренда на Canvas (ADR-306, 60 FPS на Poco M7).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinancialAnalyticsScreen(
    viewModel: FinancialAnalyticsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Финансовая аналитика") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 1. Панель выбора временного периода
            PeriodFilterBar(
                selectedPeriod = state.period,
                onSelectPeriod = { viewModel.dispatch(AnalyticsUiIntent.SelectPeriod(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 2. Тумблер режима учета возвратов (ADR-306)
            RefundModeToggle(
                isReduceExpense = state.refundMode == RefundCalculationMode.REDUCE_EXPENSE,
                onToggle = { isChecked ->
                    viewModel.dispatch(AnalyticsUiIntent.ToggleRefundMode(isChecked))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Заголовок мультивалютных карточек
            Text(
                text = "Обороты по валютам региона (без кросс-курсов)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Горизонтальная карусель карточек валют
            if (state.isLoading && state.cards.all { it.transactionCount == 0 }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = state.cards,
                        key = { it.currency.value }
                    ) { card ->
                        val isSelected = state.activeCurrencyFilter == card.currency
                        CurrencyCardItem(
                            card = card,
                            isSelected = isSelected,
                            onClick = {
                                viewModel.dispatch(AnalyticsUiIntent.SelectCurrency(card.currency))
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. График тренда расходов
            val activeCurrency = state.activeCurrencyFilter?.value ?: "MDL"
            Text(
                text = "Динамика трат ($activeCurrency)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Text(
                text = "Легковесный рендеринг без аллокаций (60 FPS)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                AnalyticsTrendChart(
                    dataPoints = state.trendDataPoints,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PeriodFilterBar(
    selectedPeriod: TimePeriodSelection,
    onSelectPeriod: (TimePeriodSelection) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selectedPeriod == TimePeriodSelection.TODAY,
            onClick = { onSelectPeriod(TimePeriodSelection.TODAY) },
            label = { Text("Сегодня") }
        )
        FilterChip(
            selected = selectedPeriod == TimePeriodSelection.THIS_WEEK,
            onClick = { onSelectPeriod(TimePeriodSelection.THIS_WEEK) },
            label = { Text("Неделя") }
        )
        FilterChip(
            selected = selectedPeriod == TimePeriodSelection.THIS_MONTH,
            onClick = { onSelectPeriod(TimePeriodSelection.THIS_MONTH) },
            label = { Text("Месяц") }
        )
        FilterChip(
            selected = selectedPeriod == TimePeriodSelection.CUSTOM,
            onClick = { onSelectPeriod(TimePeriodSelection.CUSTOM) },
            label = { Text("Период") }
        )
    }
}

@Composable
private fun RefundModeToggle(
    isReduceExpense: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Возвраты уменьшают расход",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (isReduceExpense) "Возврат вычитается из расходов (ADR-306)" else "Возврат считается как доход",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Switch(
                checked = isReduceExpense,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun CurrencyCardItem(
    card: CurrencyCardUi,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = if (isSelected) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        modifier = modifier
            .width(170.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = border,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${card.currency.value} (${card.currency.symbol})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${card.transactionCount} оп.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Расход
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                    contentDescription = "Расход",
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatMinorAmount(card.totalExpenseMinor),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Доход
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = "Доход",
                    tint = Color(0xFF43A047),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatMinorAmount(card.totalIncomeMinor),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (card.totalRefundMinor > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Возвраты: ${formatMinorAmount(card.totalRefundMinor)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

private fun formatMinorAmount(minor: Long): String {
    val major = minor / 100.0
    return String.format(Locale.US, "%.2f", major)
}
