package com.lirix.app.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lirix.app.domain.Category
import com.lirix.app.domain.EventSource
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for Event persistence and reactive timeline streams.
 */
@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT :limit")
    fun observeAllEvents(limit: Int = 200): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE source = :source ORDER BY timestamp DESC")
    fun observeEventsBySource(source: EventSource): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE category = :category ORDER BY timestamp DESC")
    fun observeEventsByCategory(category: Category): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE title LIKE '%' || :query || '%' OR text LIKE '%' || :query || '%' OR normalized_text LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT :limit")
    suspend fun searchFullText(query: String, limit: Int = 100): List<EventEntity>

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEvents(limit: Int = 500): List<EventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<EventEntity>)

    @Query("SELECT * FROM events WHERE content_fingerprint = :fingerprint AND timestamp >= :sinceTimestamp LIMIT 1")
    suspend fun getRecentByFingerprint(fingerprint: String, sinceTimestamp: Long): EventEntity?

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getEventById(id: String): EventEntity?

    @Query("SELECT COUNT(*) FROM events")
    suspend fun getEventCount(): Int

    @Query("DELETE FROM events")
    suspend fun clearAllEvents()

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEventById(id: String)

    @Query("UPDATE events SET category = :category, is_user_corrected = 1, confidence = :confidence WHERE id = :id")
    suspend fun updateEventCategory(id: String, category: Category, confidence: Float = 1.0f)
}
