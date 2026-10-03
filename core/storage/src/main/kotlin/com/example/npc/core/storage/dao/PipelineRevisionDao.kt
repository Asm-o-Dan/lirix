package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.npc.core.storage.entity.PipelineRevisionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PipelineRevisionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRevision(revision: PipelineRevisionEntity): Long

    @Query("SELECT * FROM pipeline_revision WHERE id = :id LIMIT 1")
    suspend fun getRevisionById(id: Long): PipelineRevisionEntity?

    @Query("SELECT * FROM pipeline_revision WHERE pipeline_id = :pipelineId AND revision_number = :revisionNumber LIMIT 1")
    suspend fun getRevisionByNumber(pipelineId: String, revisionNumber: Long): PipelineRevisionEntity?

    @Query("""
        SELECT * FROM pipeline_revision 
        WHERE pipeline_id = :pipelineId 
        ORDER BY revision_number DESC 
        LIMIT :limit
    """)
    fun observeRevisionsForPipeline(pipelineId: String, limit: Int = 10): Flow<List<PipelineRevisionEntity>>

    @Query("SELECT MAX(revision_number) FROM pipeline_revision WHERE pipeline_id = :pipelineId")
    suspend fun getLatestRevisionNumber(pipelineId: String): Long?

    @Query("SELECT COUNT(*) FROM pipeline_revision WHERE pipeline_id = :pipelineId")
    suspend fun getRevisionCount(pipelineId: String): Int

    @Query("""
        DELETE FROM pipeline_revision 
        WHERE pipeline_id = :pipelineId 
          AND id NOT IN (
              SELECT id FROM pipeline_revision 
              WHERE pipeline_id = :pipelineId 
              ORDER BY revision_number DESC 
              LIMIT :keepCount
          )
          AND id != (SELECT COALESCE(active_revision_id, -1) FROM pipeline_definition WHERE id = :pipelineId)
          AND id NOT IN (SELECT DISTINCT pipeline_revision_id FROM event WHERE pipeline_revision_id IS NOT NULL)
    """)
    suspend fun pruneOldRevisions(pipelineId: String, keepCount: Int = 10): Int
}
