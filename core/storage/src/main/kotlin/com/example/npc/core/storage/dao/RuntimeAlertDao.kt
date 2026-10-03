package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.npc.core.storage.entity.RuntimeAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RuntimeAlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: RuntimeAlertEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlerts(alerts: List<RuntimeAlertEntity>)

    @Query("""
        SELECT * FROM runtime_alert 
        WHERE is_dismissed = 0 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    fun observeActiveAlerts(limit: Int = 50): Flow<List<RuntimeAlertEntity>>

    @Query("SELECT COUNT(*) FROM runtime_alert WHERE is_dismissed = 0")
    fun observeActiveAlertsCount(): Flow<Int>

    @Query("UPDATE runtime_alert SET is_dismissed = 1 WHERE id = :id")
    suspend fun dismissAlert(id: Long)

    @Query("UPDATE runtime_alert SET is_dismissed = 1 WHERE pipeline_id = :pipelineId AND is_dismissed = 0")
    suspend fun dismissAllForPipeline(pipelineId: String)

    @Query("DELETE FROM runtime_alert WHERE is_dismissed = 1 AND timestamp < :beforeTimestamp")
    suspend fun purgeDismissedAlerts(beforeTimestamp: Long): Int
}
