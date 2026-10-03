package com.example.npc.core.model.finance

/**
 * Статус банковской операции.
 */
enum class TransactionStatus {
    COMPLETED,  // Успешно выполнена / подтверждена
    DECLINED;   // Отказ в авторизации / отклонена банком

    companion object {
        val SUCCESS: TransactionStatus get() = COMPLETED

        fun fromStringOrDefault(raw: String?, default: TransactionStatus = COMPLETED): TransactionStatus {
            if (raw.isNullOrBlank()) return default
            val upper = raw.trim().uppercase()
            return when (upper) {
                "COMPLETED", "SUCCESS" -> COMPLETED
                "DECLINED", "REFUZATA", "REJECTED", "FAILED" -> DECLINED
                else -> entries.firstOrNull { it.name == upper } ?: default
            }
        }
    }
}
