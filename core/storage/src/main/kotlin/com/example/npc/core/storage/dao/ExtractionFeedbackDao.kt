package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.npc.core.storage.entity.ExtractionFeedbackEntity

@Dao
interface ExtractionFeedbackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExtractionFeedbackEntity): Long

    @Query("SELECT * FROM extraction_feedback WHERE event_id = :eventId LIMIT 1")
    suspend fun getFeedbackForEvent(eventId: Long): ExtractionFeedbackEntity?

    @Query("SELECT * FROM extraction_feedback ORDER BY created_at DESC LIMIT :limit")
    suspend fun getRecentFeedback(limit: Int): List<ExtractionFeedbackEntity>
}
