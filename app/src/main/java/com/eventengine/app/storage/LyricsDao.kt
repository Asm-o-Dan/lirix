package com.eventengine.app.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for LyricsCacheEntity and CustomLyricsRuleEntity.
 * Spec: TASK-DB-01 / TASK-LYR-04-A
 */
@Dao
interface LyricsDao {

    @Query("SELECT * FROM lyrics_cache WHERE trackKey = :trackKey LIMIT 1")
    suspend fun getLyrics(trackKey: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLyrics(entity: LyricsCacheEntity)

    @Query("DELETE FROM lyrics_cache WHERE trackKey = :trackKey")
    suspend fun deleteCachedLyrics(trackKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRejection(rejection: LyricsRejectionEntity)

    @Query("SELECT sourceId FROM lyrics_rejections WHERE trackKey = :trackKey")
    suspend fun getRejectedSourceIds(trackKey: String): List<String>

    @Query("DELETE FROM lyrics_rejections WHERE trackKey = :trackKey AND sourceId = :sourceId")
    suspend fun deleteRejection(trackKey: String, sourceId: String): Int

    @Query("DELETE FROM lyrics_rejections WHERE trackKey = :trackKey")
    suspend fun clearRejectionsForTrack(trackKey: String): Int

    @Query("SELECT COUNT(*) FROM lyrics_rejections WHERE trackKey = :trackKey")
    suspend fun getRejectionsCountForTrack(trackKey: String): Int

    // --- Custom Lyrics Rules (TASK-LYR-04-A) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRule(rule: CustomLyricsRuleEntity)

    @Query("SELECT * FROM custom_lyrics_rules WHERE isEnabled = 1 ORDER BY priority ASC, createdAt DESC")
    suspend fun getActiveRules(): List<CustomLyricsRuleEntity>

    @Query("SELECT * FROM custom_lyrics_rules WHERE id = :id LIMIT 1")
    suspend fun getRuleById(id: String): CustomLyricsRuleEntity?

    @Query("SELECT * FROM custom_lyrics_rules WHERE domain = :domain")
    suspend fun getRulesForDomain(domain: String): List<CustomLyricsRuleEntity>

    @Query("SELECT * FROM custom_lyrics_rules ORDER BY priority ASC, createdAt DESC")
    fun observeAllRules(): Flow<List<CustomLyricsRuleEntity>>

    @Query("UPDATE custom_lyrics_rules SET isEnabled = :isEnabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setRuleEnabled(id: String, isEnabled: Boolean, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("DELETE FROM custom_lyrics_rules WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteRule(id: String): Int
}
