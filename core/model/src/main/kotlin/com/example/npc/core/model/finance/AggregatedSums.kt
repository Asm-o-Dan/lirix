package com.example.npc.core.model.finance

/**
 * Агрегированные суммы по финансовым операциям в рамках одной валюты.
 */
data class AggregatedSums(
    val expenseMinor: Long,
    val incomeMinor: Long,
    val refundMinor: Long,
    val count: Int
) {
    /**
     * Чистый расход с учетом режима обработки возвратов.
     */
    fun netExpenseMinor(reduceRefund: Boolean = true): Long =
        if (reduceRefund) expenseMinor - refundMinor else expenseMinor

    /**
     * Итоговый доход с учетом возвратов.
     */
    fun netIncomeMinor(includeRefund: Boolean = false): Long =
        if (includeRefund) incomeMinor + refundMinor else incomeMinor
}
