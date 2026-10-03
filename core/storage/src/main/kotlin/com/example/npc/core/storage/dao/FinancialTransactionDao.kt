package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.npc.core.storage.entity.FinancialTransactionEntity
import kotlinx.coroutines.flow.Flow

data class CurrencyTotalDto(
    val currency: String,
    val totalMinor: Long,
    val transactionCount: Int
)

data class AggregatedTotalsRow(
    val currency: String,
    val expenseMinor: Long,
    val incomeMinor: Long,
    val refundMinor: Long,
    val txCount: Int
)

@Dao
interface FinancialTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: FinancialTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<FinancialTransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: FinancialTransactionEntity): Int

    @Query("SELECT * FROM financial_transaction WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): FinancialTransactionEntity?

    @Query("SELECT * FROM financial_transaction WHERE event_id = :eventId LIMIT 1")
    suspend fun getByEventId(eventId: Long): FinancialTransactionEntity?

    suspend fun findByEventId(eventId: Long): FinancialTransactionEntity? = getByEventId(eventId)

    @Query("""
        SELECT * FROM financial_transaction
        WHERE NOT EXISTS (
              SELECT 1 FROM event e
              JOIN financial_transaction parent_ft ON parent_ft.event_id = e.is_update_of
              WHERE e.id = financial_transaction.event_id
                AND parent_ft.status = financial_transaction.status
          )
          AND NOT EXISTS (
              SELECT 1 FROM financial_transaction ft2
              WHERE ft2.id != financial_transaction.id
                AND ft2.status = financial_transaction.status
                AND ft2.direction IN ('DEBIT', 'EXPENSE', 'CREDIT', 'INCOME', 'TRANSFER')
                AND ft2.currency = financial_transaction.currency
                AND ft2.amount_minor = financial_transaction.amount_minor
                AND (
                    ft2.direction = financial_transaction.direction 
                    OR (ft2.direction IN ('DEBIT', 'EXPENSE') AND financial_transaction.direction IN ('DEBIT', 'EXPENSE'))
                    OR (ft2.direction IN ('CREDIT', 'INCOME') AND financial_transaction.direction IN ('CREDIT', 'INCOME'))
                )
                AND (
                    ft2.bank = financial_transaction.bank
                    OR (ft2.bank IN ('APB', 'com.apb.mobile', 'Агропромбанк', 'АПБ') AND financial_transaction.bank IN ('APB', 'com.apb.mobile', 'Агропромбанк', 'АПБ'))
                    OR (ft2.bank IN ('PRISBANK', 'com.prisbank', 'Приднестровский Сбербанк') AND financial_transaction.bank IN ('PRISBANK', 'com.prisbank', 'Приднестровский Сбербанк'))
                )
                AND ft2.occurred_at BETWEEN (financial_transaction.occurred_at - 300000) AND (financial_transaction.occurred_at + 300000)
                AND (
                    ft2.account_mask IS NULL 
                    OR financial_transaction.account_mask IS NULL
                    OR SUBSTR(ft2.account_mask, -4) = SUBSTR(financial_transaction.account_mask, -4)
                )
                AND (
                    (
                        (ft2.templateId IS NOT NULL OR ft2.extractorKind = 'TEMPLATE')
                        AND (financial_transaction.templateId IS NULL AND financial_transaction.extractorKind != 'TEMPLATE')
                    )
                    OR (
                        (
                            ((ft2.templateId IS NOT NULL OR ft2.extractorKind = 'TEMPLATE') AND (financial_transaction.templateId IS NOT NULL OR financial_transaction.extractorKind = 'TEMPLATE'))
                            OR
                            ((ft2.templateId IS NULL AND ft2.extractorKind != 'TEMPLATE') AND (financial_transaction.templateId IS NULL AND financial_transaction.extractorKind != 'TEMPLATE'))
                        )
                        AND ft2.id < financial_transaction.id
                    )
                )
          )
        ORDER BY occurred_at DESC, id DESC LIMIT :limit
    """)
    fun observeLatest(limit: Int): Flow<List<FinancialTransactionEntity>>

    fun observeRecent(limit: Int): Flow<List<FinancialTransactionEntity>> = observeLatest(limit)

    @Query("""
        SELECT * FROM financial_transaction
        WHERE occurred_at BETWEEN :fromEpochMs AND :toEpochMs
          AND NOT EXISTS (
              SELECT 1 FROM event e
              JOIN financial_transaction parent_ft ON parent_ft.event_id = e.is_update_of
              WHERE e.id = financial_transaction.event_id
                AND parent_ft.status = financial_transaction.status
          )
          AND NOT EXISTS (
              SELECT 1 FROM financial_transaction ft2
              WHERE ft2.id != financial_transaction.id
                AND ft2.status = financial_transaction.status
                AND ft2.direction IN ('DEBIT', 'EXPENSE', 'CREDIT', 'INCOME', 'TRANSFER')
                AND ft2.currency = financial_transaction.currency
                AND ft2.amount_minor = financial_transaction.amount_minor
                AND (
                    ft2.direction = financial_transaction.direction 
                    OR (ft2.direction IN ('DEBIT', 'EXPENSE') AND financial_transaction.direction IN ('DEBIT', 'EXPENSE'))
                    OR (ft2.direction IN ('CREDIT', 'INCOME') AND financial_transaction.direction IN ('CREDIT', 'INCOME'))
                )
                AND (
                    ft2.bank = financial_transaction.bank
                    OR (ft2.bank IN ('APB', 'com.apb.mobile', 'Агропромбанк', 'АПБ') AND financial_transaction.bank IN ('APB', 'com.apb.mobile', 'Агропромбанк', 'АПБ'))
                    OR (ft2.bank IN ('PRISBANK', 'com.prisbank', 'Приднестровский Сбербанк') AND financial_transaction.bank IN ('PRISBANK', 'com.prisbank', 'Приднестровский Сбербанк'))
                )
                AND ft2.occurred_at BETWEEN (financial_transaction.occurred_at - 300000) AND (financial_transaction.occurred_at + 300000)
                AND (
                    ft2.account_mask IS NULL 
                    OR financial_transaction.account_mask IS NULL
                    OR SUBSTR(ft2.account_mask, -4) = SUBSTR(financial_transaction.account_mask, -4)
                )
                AND (
                    (
                        (ft2.templateId IS NOT NULL OR ft2.extractorKind = 'TEMPLATE')
                        AND (financial_transaction.templateId IS NULL AND financial_transaction.extractorKind != 'TEMPLATE')
                    )
                    OR (
                        (
                            ((ft2.templateId IS NOT NULL OR ft2.extractorKind = 'TEMPLATE') AND (financial_transaction.templateId IS NOT NULL OR financial_transaction.extractorKind = 'TEMPLATE'))
                            OR
                            ((ft2.templateId IS NULL AND ft2.extractorKind != 'TEMPLATE') AND (financial_transaction.templateId IS NULL AND financial_transaction.extractorKind != 'TEMPLATE'))
                        )
                        AND ft2.id < financial_transaction.id
                    )
                )
          )
        ORDER BY occurred_at DESC
    """)
    fun observeByPeriod(fromEpochMs: Long, toEpochMs: Long): Flow<List<FinancialTransactionEntity>>

    @Query("SELECT * FROM financial_transaction WHERE occurred_at BETWEEN :fromEpochMs AND :toEpochMs ORDER BY occurred_at ASC")
    suspend fun getByPeriod(fromEpochMs: Long, toEpochMs: Long): List<FinancialTransactionEntity>

    suspend fun findBetween(fromMs: Long, toMs: Long): List<FinancialTransactionEntity> = getByPeriod(fromMs, toMs)

    @Query("SELECT * FROM financial_transaction WHERE bank = :bank ORDER BY occurred_at DESC LIMIT :limit")
    fun observeByBank(bank: String, limit: Int): Flow<List<FinancialTransactionEntity>>

    @Query("""
        SELECT currency, SUM(amount_minor) AS totalMinor, COUNT(*) AS transactionCount 
        FROM financial_transaction 
        WHERE direction = :direction AND occurred_at BETWEEN :fromEpochMs AND :toEpochMs
          AND status = 'COMPLETED'
          AND direction IN ('DEBIT', 'EXPENSE', 'CREDIT', 'INCOME', 'TRANSFER')
          AND NOT EXISTS (
              SELECT 1 FROM event e
              JOIN financial_transaction parent_ft ON parent_ft.event_id = e.is_update_of
              WHERE e.id = financial_transaction.event_id
                AND parent_ft.status = financial_transaction.status
          )
          AND NOT EXISTS (
              SELECT 1 FROM financial_transaction ft2
              WHERE ft2.id != financial_transaction.id
                AND ft2.status = financial_transaction.status
                AND ft2.direction IN ('DEBIT', 'EXPENSE', 'CREDIT', 'INCOME', 'TRANSFER')
                AND ft2.currency = financial_transaction.currency
                AND ft2.amount_minor = financial_transaction.amount_minor
                AND (
                    ft2.direction = financial_transaction.direction 
                    OR (ft2.direction IN ('DEBIT', 'EXPENSE') AND financial_transaction.direction IN ('DEBIT', 'EXPENSE'))
                    OR (ft2.direction IN ('CREDIT', 'INCOME') AND financial_transaction.direction IN ('CREDIT', 'INCOME'))
                )
                AND (
                    ft2.bank = financial_transaction.bank
                    OR (ft2.bank IN ('APB', 'com.apb.mobile', 'Агропромбанк', 'АПБ') AND financial_transaction.bank IN ('APB', 'com.apb.mobile', 'Агропромбанк', 'АПБ'))
                    OR (ft2.bank IN ('PRISBANK', 'com.prisbank', 'Приднестровский Сбербанк') AND financial_transaction.bank IN ('PRISBANK', 'com.prisbank', 'Приднестровский Сбербанк'))
                )
                AND ft2.occurred_at BETWEEN (financial_transaction.occurred_at - 300000) AND (financial_transaction.occurred_at + 300000)
                AND (
                    ft2.account_mask IS NULL 
                    OR financial_transaction.account_mask IS NULL
                    OR SUBSTR(ft2.account_mask, -4) = SUBSTR(financial_transaction.account_mask, -4)
                )
                AND (
                    (
                        (ft2.templateId IS NOT NULL OR ft2.extractorKind = 'TEMPLATE')
                        AND (financial_transaction.templateId IS NULL AND financial_transaction.extractorKind != 'TEMPLATE')
                    )
                    OR (
                        (
                            ((ft2.templateId IS NOT NULL OR ft2.extractorKind = 'TEMPLATE') AND (financial_transaction.templateId IS NOT NULL OR financial_transaction.extractorKind = 'TEMPLATE'))
                            OR
                            ((ft2.templateId IS NULL AND ft2.extractorKind != 'TEMPLATE') AND (financial_transaction.templateId IS NULL AND financial_transaction.extractorKind != 'TEMPLATE'))
                        )
                        AND ft2.id < financial_transaction.id
                    )
                )
          )
        GROUP BY currency
    """)
    suspend fun getAggregatedTotalsByCurrency(
        direction: String,
        fromEpochMs: Long,
        toEpochMs: Long
    ): List<CurrencyTotalDto>

    @Query("""
        SELECT 
            currency,
            SUM(CASE WHEN (direction = 'DEBIT' OR direction = 'EXPENSE') THEN amount_minor ELSE 0 END) AS expenseMinor,
            SUM(CASE WHEN (direction = 'CREDIT' OR direction = 'INCOME') AND (isRefund = 0) THEN amount_minor ELSE 0 END) AS incomeMinor,
            SUM(CASE WHEN isRefund = 1 OR ((direction = 'CREDIT' OR direction = 'INCOME') AND isRefund = 1) THEN amount_minor ELSE 0 END) AS refundMinor,
            COUNT(*) AS txCount
        FROM financial_transaction
        WHERE occurred_at >= :fromEpochMs AND occurred_at < :toEpochMs
          AND status = 'COMPLETED'
          AND direction IN ('DEBIT', 'EXPENSE', 'CREDIT', 'INCOME', 'TRANSFER')
          AND (:direction IS NULL OR direction = :direction)
          AND NOT EXISTS (
              SELECT 1 FROM event e
              JOIN financial_transaction parent_ft ON parent_ft.event_id = e.is_update_of
              WHERE e.id = financial_transaction.event_id
                AND parent_ft.status = financial_transaction.status
          )
          AND NOT EXISTS (
              SELECT 1 FROM financial_transaction ft2
              WHERE ft2.id != financial_transaction.id
                AND ft2.status = financial_transaction.status
                AND ft2.direction IN ('DEBIT', 'EXPENSE', 'CREDIT', 'INCOME', 'TRANSFER')
                AND ft2.currency = financial_transaction.currency
                AND ft2.amount_minor = financial_transaction.amount_minor
                AND (
                    ft2.direction = financial_transaction.direction 
                    OR (ft2.direction IN ('DEBIT', 'EXPENSE') AND financial_transaction.direction IN ('DEBIT', 'EXPENSE'))
                    OR (ft2.direction IN ('CREDIT', 'INCOME') AND financial_transaction.direction IN ('CREDIT', 'INCOME'))
                )
                AND (
                    ft2.bank = financial_transaction.bank
                    OR (ft2.bank IN ('APB', 'com.apb.mobile', 'Агропромбанк', 'АПБ') AND financial_transaction.bank IN ('APB', 'com.apb.mobile', 'Агропромбанк', 'АПБ'))
                    OR (ft2.bank IN ('PRISBANK', 'com.prisbank', 'Приднестровский Сбербанк') AND financial_transaction.bank IN ('PRISBANK', 'com.prisbank', 'Приднестровский Сбербанк'))
                )
                AND ft2.occurred_at BETWEEN (financial_transaction.occurred_at - 300000) AND (financial_transaction.occurred_at + 300000)
                AND (
                    ft2.account_mask IS NULL 
                    OR financial_transaction.account_mask IS NULL
                    OR SUBSTR(ft2.account_mask, -4) = SUBSTR(financial_transaction.account_mask, -4)
                )
                AND (
                    (
                        (ft2.templateId IS NOT NULL OR ft2.extractorKind = 'TEMPLATE')
                        AND (financial_transaction.templateId IS NULL AND financial_transaction.extractorKind != 'TEMPLATE')
                    )
                    OR (
                        (
                            ((ft2.templateId IS NOT NULL OR ft2.extractorKind = 'TEMPLATE') AND (financial_transaction.templateId IS NOT NULL OR financial_transaction.extractorKind = 'TEMPLATE'))
                            OR
                            ((ft2.templateId IS NULL AND ft2.extractorKind != 'TEMPLATE') AND (financial_transaction.templateId IS NULL AND financial_transaction.extractorKind != 'TEMPLATE'))
                        )
                        AND ft2.id < financial_transaction.id
                    )
                )
          )
        GROUP BY currency
    """)
    fun observeAggregatedTotalsByCurrency(
        fromEpochMs: Long,
        toEpochMs: Long,
        direction: String? = null
    ): Flow<List<AggregatedTotalsRow>>

    @Query("""
        DELETE FROM financial_transaction
        WHERE id IN (
            SELECT ft.id FROM financial_transaction ft
            WHERE EXISTS (
                SELECT 1 FROM event e
                JOIN financial_transaction parent_ft ON parent_ft.event_id = e.is_update_of
                WHERE e.id = ft.event_id
                  AND parent_ft.status = ft.status
            )
            OR EXISTS (
                SELECT 1 FROM financial_transaction ft2
                WHERE ft2.id != ft.id
                  AND ft2.status = ft.status
                  AND ft2.direction IN ('DEBIT', 'EXPENSE', 'CREDIT', 'INCOME', 'TRANSFER')
                  AND ft2.currency = ft.currency
                  AND ft2.amount_minor = ft.amount_minor
                  AND (
                      ft2.direction = ft.direction 
                      OR (ft2.direction IN ('DEBIT', 'EXPENSE') AND ft.direction IN ('DEBIT', 'EXPENSE'))
                      OR (ft2.direction IN ('CREDIT', 'INCOME') AND ft.direction IN ('CREDIT', 'INCOME'))
                  )
                  AND (
                      ft2.bank = ft.bank
                      OR (ft2.bank IN ('APB', 'com.apb.mobile', 'Агропромбанк', 'АПБ') AND ft.bank IN ('APB', 'com.apb.mobile', 'Агропромбанк', 'АПБ'))
                      OR (ft2.bank IN ('PRISBANK', 'com.prisbank', 'Приднестровский Сбербанк') AND ft.bank IN ('PRISBANK', 'com.prisbank', 'Приднестровский Сбербанк'))
                  )
                  AND ft2.occurred_at BETWEEN (ft.occurred_at - 300000) AND (ft.occurred_at + 300000)
                  AND (
                      ft2.account_mask IS NULL 
                      OR ft.account_mask IS NULL
                      OR SUBSTR(ft2.account_mask, -4) = SUBSTR(ft.account_mask, -4)
                  )
                  AND (
                      (
                          (ft2.templateId IS NOT NULL OR ft2.extractorKind = 'TEMPLATE')
                          AND (ft.templateId IS NULL AND ft.extractorKind != 'TEMPLATE')
                      )
                      OR (
                          (
                              ((ft2.templateId IS NOT NULL OR ft2.extractorKind = 'TEMPLATE') AND (ft.templateId IS NOT NULL OR ft.extractorKind = 'TEMPLATE'))
                              OR
                              ((ft2.templateId IS NULL AND ft2.extractorKind != 'TEMPLATE') AND (ft.templateId IS NULL AND ft.extractorKind != 'TEMPLATE'))
                          )
                          AND ft2.id < ft.id
                      )
                  )
            )
        )
    """)
    suspend fun deleteDuplicates(): Int

    @Query("SELECT * FROM financial_transaction ORDER BY occurred_at DESC, id DESC")
    suspend fun getAll(): List<FinancialTransactionEntity>

    @Query("DELETE FROM financial_transaction WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM financial_transaction")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM financial_transaction")
    suspend fun count(): Long
}
