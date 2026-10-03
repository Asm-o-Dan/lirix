package com.example.npc.core.model.backfill

/**
 * Критерии выборки исторических событий для бэкфилла шаблона.
 *
 * @property maxEventsLimit Максимальное число событий для обработки.
 * @property chunkSize Размер пачки для постраничной выборки (Keyset Pagination).
 * @property startTime Нижняя граница времени событий (epoch ms).
 * @property endTime Верхняя граница времени событий (epoch ms).
 */
data class BackfillCriteria(
    val maxEventsLimit: Int = 10_000,
    val chunkSize: Int = 150,
    val startTime: Long = 0L,
    val endTime: Long = Long.MAX_VALUE
) {
    init {
        require(maxEventsLimit > 0) { "maxEventsLimit must be > 0" }
        require(chunkSize > 0) { "chunkSize must be > 0" }
        require(startTime <= endTime) { "startTime must be <= endTime" }
    }

    companion object {
        val Default = BackfillCriteria()
    }
}

/**
 * Описание целевого шаблона для применения на историческом корпусе.
 */
data class BackfillTargetTemplate(
    val id: String,
    val sourcePackage: String,
    val pattern: String,
    val requiredLiterals: List<String> = emptyList(),
    val constants: Map<String, String> = emptyMap(),
    val priority: Int = 100
)

/**
 * Итоговый результат выполнения бэкфилла.
 *
 * @property templateId Идентификатор примененного шаблона.
 * @property processedCount Всего проанализировано событий.
 * @property backfilledCount Успешно извлечено и сохранено/обновлено транзакций.
 * @property skippedUserProtectedCount Пропущено из-за защиты пользователя (USER_EDITED / USER_CONFIRMED).
 * @property skippedNoMatchCount Шаблон не подошел к событию.
 * @property executionDurationMs Время выполнения бэкфилла в миллисекундах.
 */
data class BackfillResult(
    val templateId: String,
    val processedCount: Int,
    val backfilledCount: Int,
    val skippedUserProtectedCount: Int,
    val skippedNoMatchCount: Int = 0,
    val executionDurationMs: Long = 0L
) {
    val totalAnalyzedEvents: Int get() = processedCount
    val extractedTransactionsCount: Int get() = backfilledCount
    val skippedManualEditsCount: Int get() = skippedUserProtectedCount

    constructor(
        templateId: String,
        totalAnalyzedEvents: Int,
        extractedTransactionsCount: Int,
        skippedManualEditsCount: Int,
        executionDurationMs: Long = 0L
    ) : this(
        templateId = templateId,
        processedCount = totalAnalyzedEvents,
        backfilledCount = extractedTransactionsCount,
        skippedUserProtectedCount = skippedManualEditsCount,
        skippedNoMatchCount = (totalAnalyzedEvents - extractedTransactionsCount - skippedManualEditsCount).coerceAtLeast(0),
        executionDurationMs = executionDurationMs
    )
}

typealias BackfillReport = BackfillResult
