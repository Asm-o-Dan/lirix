package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.npc.core.storage.entity.RawEventEntity

@Dao
interface RawEventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: RawEventEntity): Long

    @Query("SELECT * FROM raw_event WHERE hash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): RawEventEntity?

    @Query("SELECT id FROM raw_event WHERE hash = :hash LIMIT 1")
    suspend fun findIdByHash(hash: String): Long?

    @Query("SELECT * FROM raw_event WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RawEventEntity?

    @Query("SELECT * FROM raw_event ORDER BY id ASC")
    suspend fun getAll(): List<RawEventEntity>

    @Query("DELETE FROM raw_event")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM raw_event")
    suspend fun count(): Long
}
