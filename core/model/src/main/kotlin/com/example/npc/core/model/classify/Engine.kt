package com.example.npc.core.model.classify

/**
 * Механизм / движок, назначивший категорию событию.
 */
enum class Engine {
    NONE,       // Категория не назначена (по умолчанию)
    PROTOTYPE,  // Назначено по базе подтвержденных пользовательских прототипов (supportCount >= 2)
    RULES,      // Назначено статическим детерминированным правилом (эвристика)
    USER;       // Назначено прямой ручной правкой пользователя в UI (Feedback Loop)

    companion object {
        fun fromStringOrDefault(raw: String?, default: Engine = NONE): Engine {
            if (raw.isNullOrBlank()) return default
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: default
        }
    }
}
