package com.example.npc.ui.timeline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.npc.ui.timeline.model.FinancialTransactionUiModel
import com.example.npc.ui.timeline.ui.theme.CategoryColors

@Composable
fun TransactionCard(
    transaction: FinancialTransactionUiModel,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(10.dp)
    val amountColor = when {
        transaction.isDeclined -> CategoryColors.DeclinedBadgeContent
        transaction.isIncome -> CategoryColors.IncomeAmount
        transaction.isExpense -> CategoryColors.ExpenseAmount
        else -> CategoryColors.TransferAmount
    }

    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = cardShape
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Верхняя строка: Сумма и бейдж статуса/направления
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayAmount = if (transaction.currencySymbol.isNotBlank()) {
                    "${transaction.formattedAmount} ${transaction.currencySymbol}"
                } else {
                    transaction.formattedAmount
                }

                Text(
                    text = displayAmount,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = amountColor,
                    textDecoration = if (transaction.isDeclined) TextDecoration.LineThrough else TextDecoration.None
                )

                if (transaction.isDeclined) {
                    val badgeShape = RoundedCornerShape(4.dp)
                    Box(
                        modifier = Modifier
                            .clip(badgeShape)
                            .border(0.75.dp, CategoryColors.DeclinedBadgeBorder, badgeShape)
                            .background(CategoryColors.DeclinedBadgeContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ОТКАЗ",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = CategoryColors.DeclinedBadgeContent
                        )
                    }
                } else {
                    val directionLabel = when {
                        transaction.isIncome -> "Пополнение"
                        transaction.isExpense -> "Списание"
                        else -> "Перевод"
                    }
                    val badgeShape = RoundedCornerShape(4.dp)
                    Box(
                        modifier = Modifier
                            .clip(badgeShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = directionLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Средняя строка: Мерчант и маска счёта/карты
            if (!transaction.merchant.isNullOrBlank() || !transaction.accountMask.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!transaction.merchant.isNullOrBlank()) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = "Мерчант",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = transaction.merchant,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (!transaction.accountMask.isNullOrBlank()) {
                        if (!transaction.merchant.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = "Карта",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = transaction.accountMask,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Нижняя строка: Остаток баланса
            if (!transaction.formattedBalance.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = transaction.formattedBalance,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
