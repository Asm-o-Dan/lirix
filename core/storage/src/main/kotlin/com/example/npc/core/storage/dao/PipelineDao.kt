package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.npc.core.storage.entity.PipelineDefinitionEntity
import com.example.npc.core.storage.model.PipelineWithRevision
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelineDao {

    @Transaction
    @Query("""
        SELECT * FROM pipeline_definition
        WHERE enabled = 1 AND active_revision_id IS NOT NULL
        ORDER BY priority DESC, updated_at DESC
    """)
    fun observeActivePipelines(): Flow<List<PipelineWithRevision>>

    @Transaction
    @Query("SELECT * FROM pipeline_definition ORDER BY priority DESC, updated_at DESC")
    fun observeAllPipelines(): Flow<List<PipelineWithRevision>>

    @Transaction
    @Query("SELECT * FROM pipeline_definition WHERE id = :id LIMIT 1")
    fun getPipelineWithRevision(id: String): Flow<PipelineWithRevision?>

    @Query("SELECT * FROM pipeline_definition WHERE id = :id LIMIT 1")
    suspend fun getDefinitionById(id: String): PipelineDefinitionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPipeline(pipeline: PipelineDefinitionEntity)

    @Update
    suspend fun updatePipeline(pipeline: PipelineDefinitionEntity)

    @Query("UPDATE pipeline_definition SET enabled = :enabled, updated_at = :updatedAt WHERE id = :id")
    suspend fun toggleEnabled(id: String, enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE pipeline_definition SET active_revision_id = :revisionId, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateActiveRevision(id: String, revisionId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM pipeline_definition WHERE id = :id")
    suspend fun deletePipeline(id: String)
}
