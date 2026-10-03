package com.example.npc.core.storage.repository

import com.example.npc.pipeline.dsl.PipelineDefinition
import kotlinx.coroutines.flow.Flow

interface PipelineRepository {

    /**
     * Сохраняет новую ревизию конвейера в БД.
     * Автоматически сериализует AST через [PipelineJsonCodec] и вычисляет SHA-256.
     *
     * @param definition Определение конвейера.
     * @param commitMessage Описание изменений.
     * @return Первичный ключ созданной записи ревизии.
     */
    suspend fun saveRevision(
        definition: PipelineDefinition,
        commitMessage: String? = null
    ): Long

    /**
     * Активирует указанную ревизию конвейера как рабочую.
     */
    suspend fun activateRevision(pipelineId: String, revisionNumber: Long)

    /**
     * Загружает текущую активную ревизию конвейера.
     */
    suspend fun getActiveDefinition(pipelineId: String): PipelineDefinition?

    /**
     * Наблюдает за изменением активной версии конвейера.
     */
    fun observeActiveDefinition(pipelineId: String): Flow<PipelineDefinition?>

    /**
     * Наблюдает за всеми активными конвейерами (для рантайма горячей подмены).
     */
    fun observeActivePipelines(): Flow<List<PipelineDefinition>>

    /**
     * Получает конкретную ревизию конвейера по номеру.
     */
    suspend fun getRevision(pipelineId: String, revisionNumber: Long): PipelineDefinition?

    /**
     * Откатывает конвейер на историческую ревизию.
     */
    suspend fun rollbackToRevision(pipelineId: String, revisionId: Long): Result<PipelineDefinition>

    /**
     * Включение / выключение конвейера.
     */
    suspend fun toggleEnabled(pipelineId: String, enabled: Boolean)

    /**
     * Очищает старые неактивные ревизии, оставляя не более [keepCount] последних версий.
     */
    suspend fun gcOldRevisions(pipelineId: String, keepCount: Int = 10)

    /**
     * Сохраняет рантайм-алерт о сбое или инциденте.
     */
    suspend fun recordAlert(
        pipelineId: String,
        nodeId: String?,
        level: String,
        code: String,
        message: String,
        payloadJson: String? = null
    )
}
