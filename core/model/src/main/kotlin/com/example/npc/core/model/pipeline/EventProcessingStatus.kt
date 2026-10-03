package com.example.npc.core.model.pipeline

enum class EventProcessingStatus {
    /** Событие успешно классифицировано (и при необходимости извлечена транзакция) */
    COMPLETED,

    /** Событие пропущено (уже захвачено другим воркером или уже было обработано) */
    SKIPPED_ALREADY_CLAIMED,

    /** Событие не найдено в хранилище (аномалия целостности данных) */
    SKIPPED_NOT_FOUND,

    /** Обработка завершилась с ошибкой; событию присвоена fallback-категория OTHER */
    FAILED
}
