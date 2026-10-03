package com.example.npc.ui.timeline.model

import androidx.compose.runtime.Immutable
import com.example.npc.core.model.finance.Direction

@Immutable
data class FinancialTransactionUiModel(
    val id: Long,
    val bank: String,
    val direction: Direction,
    val formattedAmount: String,
    val formattedBalance: String?,
    val merchant: String?,
    val accountMask: String?,
    val status: TransactionStatusUi,
    val isDeclined: Boolean = status == TransactionStatusUi.DECLINED,
    val currencyCode: String = "",
    val currencySymbol: String = "",
    val extractorInfo: String = ""
) {
    val isExpense: Boolean get() = direction == Direction.DEBIT
    val isIncome: Boolean get() = direction == Direction.CREDIT
    val isUnknown: Boolean get() = direction == Direction.UNKNOWN
    val isTransfer: Boolean get() = direction == Direction.TRANSFER
}
