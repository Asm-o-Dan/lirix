package com.example.npc.core.model.finance

/**
 * Направление финансовой операции.
 */
enum class TransactionType {
    DEBIT,      // Списание / Покупка / Оплата услуг (Расход)
    CREDIT,     // Пополнение / Зарплата / Входящий перевод (Доход)
    TRANSFER;   // Перевод между своими счетами / P2P-перевод

    companion object {
        val EXPENSE: TransactionType get() = DEBIT
        val INCOME: TransactionType get() = CREDIT

        fun fromStringOrNull(raw: String?): TransactionType? {
            if (raw.isNullOrBlank()) return null
            val upper = raw.trim().uppercase()
            return when (upper) {
                "DEBIT", "EXPENSE" -> DEBIT
                "CREDIT", "INCOME" -> CREDIT
                "TRANSFER" -> TRANSFER
                else -> entries.firstOrNull { it.name == upper }
            }
        }
    }
}
