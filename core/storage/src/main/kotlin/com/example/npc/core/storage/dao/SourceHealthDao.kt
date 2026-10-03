package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.npc.core.storage.entity.SourceHealthEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SourceHealthDao {
    @Upsert
    suspend fun upsert(entity: SourceHealthEntity)

    @Query("SELECT * FROM source_health ORDER BY source ASC")
    fun observeAll(): Flow<List<SourceHealthEntity>>

    @Query("SELECT * FROM source_health WHERE source = :source LIMIT 1")
    suspend fun getBySource(source: String): SourceHealthEntity?

    @Query("SELECT * FROM source_health ORDER BY source ASC")
    suspend fun getAll(): List<SourceHealthEntity>

    @Query("DELETE FROM source_health")
    suspend fun deleteAll(): Int
}
