package com.example.npc.core.model.finance

/**
 * Статус подтверждения финансовой транзакции в конвейере.
 */
enum class TxStatus {
    CONFIRMED_AUTO,
    SUGGESTED,
    USER_CONFIRMED,
    USER_EDITED;

    val isUserProtected: Boolean
        get() = this == USER_CONFIRMED || this == USER_EDITED

    companion object {
        fun fromStringOrNull(value: String?): TxStatus? {
            if (value == null) return null
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
        }

        fun fromStringOrDefault(value: String?, default: TxStatus = CONFIRMED_AUTO): TxStatus {
            return fromStringOrNull(value) ?: default
        }
    }
}
