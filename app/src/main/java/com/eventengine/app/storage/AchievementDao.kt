package com.eventengine.app.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for AchievementEntity.
 * Spec: TASK-DB-01 / .sdd/specs/media-core/overview.md v1
 */
@Dao
interface AchievementDao {

    @Query("SELECT * FROM achievements ORDER BY isUnlocked DESC, id ASC")
    fun observeAchievements(): Flow<List<AchievementEntity>>

    @Query("SELECT * FROM achievements WHERE id = :id LIMIT 1")
    suspend fun getAchievement(id: String): AchievementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(achievement: AchievementEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(achievements: List<AchievementEntity>)
}
