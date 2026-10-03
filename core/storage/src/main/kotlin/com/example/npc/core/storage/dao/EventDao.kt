package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.npc.core.storage.entity.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EventEntity): Long

    @Query("SELECT * FROM event WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EventEntity?

    suspend fun findById(id: Long): EventEntity? = getById(id)

    @Query("SELECT * FROM event ORDER BY ts DESC, id DESC LIMIT :limit")
    fun observeLatest(limit: Int): Flow<List<EventEntity>>

    fun observeRecent(limit: Int): Flow<List<EventEntity>> = observeLatest(limit)

    @Query("SELECT * FROM event WHERE category = :category ORDER BY ts DESC, id DESC LIMIT :limit")
    fun observeByCategory(category: String, limit: Int): Flow<List<EventEntity>>

    @Query("SELECT * FROM event WHERE content_fingerprint = :fingerprint ORDER BY ts DESC")
    suspend fun findByFingerprint(fingerprint: String): List<EventEntity>

    @Query("SELECT * FROM event ORDER BY ts DESC, id DESC")
    suspend fun getAll(): List<EventEntity>

    @Query("SELECT COUNT(*) FROM event WHERE ts >= :sinceEpochMs")
    suspend fun countSince(sinceEpochMs: Long): Int

    @Query("UPDATE event SET category = :category, is_user_corrected = 1, engine_used = 'USER' WHERE id = :id")
    suspend fun updateCategoryFromUser(id: Long, category: String): Int

    @Query("UPDATE event SET is_update_of = :isUpdateOf WHERE id = :id")
    suspend fun updateIsUpdateOf(id: Long, isUpdateOf: Long): Int

    suspend fun recordUserCorrection(eventId: Long, category: String) {
        updateCategoryFromUser(eventId, category)
    }

    @Query("DELETE FROM event")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM event")
    suspend fun count(): Long

    @Query("""
        UPDATE event 
        SET category = 'PROCESSING' 
        WHERE id = :id AND (category = 'UNCLASSIFIED' OR category = 'PROCESSING')
    """)
    suspend fun tryClaim(id: Long): Int

    @Query("""
        UPDATE event 
        SET category = :category,
            confidence = :confidence,
            engine_used = :engineUsed,
            content_fingerprint = :fingerprint
        WHERE id = :id
    """)
    suspend fun updateClassification(
        id: Long,
        category: String,
        confidence: Double,
        engineUsed: String,
        fingerprint: String?
    ): Int

    @Query("""
        SELECT id FROM event 
        WHERE category = 'UNCLASSIFIED' OR category = 'PROCESSING' 
        ORDER BY id ASC 
        LIMIT :limit
    """)
    suspend fun getPendingUnprocessedIds(limit: Int): List<Long>
}
