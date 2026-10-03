package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Query

data class ReplayHistoricalEvent(
    val eventId: Long,
    val rawId: Long,
    val packageName: String,
    val title: String,
    val text: String,
    val postTime: Long,
    val historicalCategory: String,
    val historicalConfidence: Double,
    val historicalTransactionJson: String?
)

@Dao
interface ReplayEventSourceDao {

    /**
     * Постраничная выборка (Keyset Pagination) событий без блокировки базы.
     */
    @Query("""
        SELECT e.id AS eventId, e.raw_id AS rawId, r.package_name AS packageName,
               e.title AS title, e.text AS text, e.ts AS postTime,
               e.category AS historicalCategory, e.confidence AS historicalConfidence,
               ft.amount_minor AS historicalTransactionJson
        FROM event e
        JOIN raw_event r ON e.raw_id = r.id
        LEFT JOIN financial_transaction ft ON ft.event_id = e.id
        WHERE e.id > :afterId AND e.ts >= :startTime AND e.ts <= :endTime
        ORDER BY e.id ASC
        LIMIT :limit
    """)
    suspend fun getEventsAfterId(
        afterId: Long,
        startTime: Long,
        endTime: Long,
        limit: Int
    ): List<ReplayHistoricalEvent>

    /**
     * Быстрый подсчет общего количества событий в интервале для отображения прогресса.
     */
    @Query("""
        SELECT COUNT(e.id)
        FROM event e
        JOIN raw_event r ON e.raw_id = r.id
        WHERE e.ts >= :startTime AND e.ts <= :endTime
    """)
    suspend fun countEvents(
        startTime: Long,
        endTime: Long
    ): Int

    /**
     * Постраничная выборка событий для конкретного пакета источника (Backfill).
     */
    @Query("""
        SELECT e.id AS eventId, e.raw_id AS rawId, r.package_name AS packageName,
               e.title AS title, e.text AS text, e.ts AS postTime,
               e.category AS historicalCategory, e.confidence AS historicalConfidence,
               ft.amount_minor AS historicalTransactionJson
        FROM event e
        JOIN raw_event r ON e.raw_id = r.id
        LEFT JOIN financial_transaction ft ON ft.event_id = e.id
        WHERE r.package_name = :packageName
          AND e.id > :afterId 
          AND e.ts >= :startTime 
          AND e.ts <= :endTime
        ORDER BY e.id ASC
        LIMIT :limit
    """)
    suspend fun getEventsByPackageAfterId(
        packageName: String,
        afterId: Long,
        startTime: Long = 0L,
        endTime: Long = Long.MAX_VALUE,
        limit: Int = 150
    ): List<ReplayHistoricalEvent>

    /**
     * Подсчет количества событий для конкретного пакета источника.
     */
    @Query("""
        SELECT COUNT(e.id)
        FROM event e
        JOIN raw_event r ON e.raw_id = r.id
        WHERE r.package_name = :packageName
          AND e.ts >= :startTime 
          AND e.ts <= :endTime
    """)
    suspend fun countEventsByPackage(
        packageName: String,
        startTime: Long = 0L,
        endTime: Long = Long.MAX_VALUE
    ): Int
}
