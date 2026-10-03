package com.example.npc.core.model.classify

/**
 * Доменные категории классификации событий и уведомлений.
 */
enum class Category {
    FINANCE,        // Банковские операции, SMS-банкинг, чеки, баланс, переводы
    COMMUNICATION,  // Личные и групповые мессенджеры, SMS-переписка, почта, звонки
    MUSIC,          // Мультимедиа, воспроизведение аудио/видео треков
    SERVICES,       // Сервисные уведомления, доставка, такси, системные статусы, утилиты
    ADVERTISEMENT,  // Реклама, промо-рассылки, маркетинговые акции (спам)
    OTHER,          // Прочие события, не подпадающие под основные категории
    UNCLASSIFIED;   // Начальное неклассифицированное состояние события

    companion object {
        fun fromStringOrUnclassified(raw: String?): Category {
            if (raw.isNullOrBlank()) return UNCLASSIFIED
            return entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: UNCLASSIFIED
        }

        fun fromStringOrUnknown(raw: String?): Category = fromStringOrUnclassified(raw)

        val UNKNOWN: Category get() = UNCLASSIFIED
    }
}
