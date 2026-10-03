package com.example.npc.core.model.finance

/**
 * Провенанс экстрактора, извлекшего транзакцию.
 */
enum class ExtractorKind {
    STATIC,
    TEMPLATE,
    UNIVERSAL,
    MANUAL;

    companion object {
        fun fromStringOrDefault(value: String?, default: ExtractorKind = STATIC): ExtractorKind {
            if (value == null) return default
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: default
        }
    }
}
