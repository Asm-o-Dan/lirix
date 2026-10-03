package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.npc.core.storage.entity.DynamicTemplateEntity
import com.example.npc.core.storage.entity.TemplateBankMembershipEntity
import com.example.npc.core.storage.entity.TemplateBankVersionEntity
import com.example.npc.core.storage.entity.TemplateStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DynamicTemplateDao {

    // =================================================================
    // 1. DynamicTemplate operations
    // =================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(template: DynamicTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(templates: List<DynamicTemplateEntity>): List<Long>

    @Update
    suspend fun update(template: DynamicTemplateEntity)

    @Query("SELECT * FROM dynamic_template WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DynamicTemplateEntity?

    @Query("SELECT * FROM dynamic_template WHERE canonicalHash = :canonicalHash LIMIT 1")
    suspend fun getByCanonicalHash(canonicalHash: String): DynamicTemplateEntity?

    @Query("SELECT * FROM dynamic_template WHERE sourceKey = :sourceKey AND state = :state ORDER BY priority DESC")
    suspend fun getBySourceKeyAndState(sourceKey: String, state: String): List<DynamicTemplateEntity>

    @Query("SELECT * FROM dynamic_template WHERE sourceKey = :sourceKey AND state = :state ORDER BY priority DESC")
    fun observeBySourceKeyAndState(sourceKey: String, state: String): Flow<List<DynamicTemplateEntity>>

    @Query("SELECT * FROM dynamic_template WHERE state = :state ORDER BY priority DESC")
    suspend fun getByState(state: String): List<DynamicTemplateEntity>

    @Query("SELECT * FROM dynamic_template WHERE state = :state ORDER BY priority DESC")
    fun observeByState(state: String): Flow<List<DynamicTemplateEntity>>

    @Query("SELECT * FROM dynamic_template ORDER BY priority DESC, updatedAt DESC")
    suspend fun getAll(): List<DynamicTemplateEntity>

    @Query("SELECT * FROM dynamic_template ORDER BY priority DESC, updatedAt DESC")
    fun observeAll(): Flow<List<DynamicTemplateEntity>>

    @Query("UPDATE dynamic_template SET state = :newState, stateReason = :reason, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateState(
        id: String,
        newState: String,
        reason: String? = null,
        updatedAt: Long = System.currentTimeMillis()
    ): Int

    @Query("DELETE FROM dynamic_template WHERE id = :id")
    suspend fun deleteById(id: String): Int

    @Query("DELETE FROM dynamic_template")
    suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM dynamic_template")
    suspend fun count(): Long

    @Query("SELECT COUNT(*) FROM dynamic_template WHERE state = :state")
    suspend fun countByState(state: String): Int

    // =================================================================
    // 2. TemplateStats operations
    // =================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStats(stats: TemplateStatsEntity)

    @Update
    suspend fun updateStats(stats: TemplateStatsEntity)

    @Query("SELECT * FROM template_stats WHERE templateId = :templateId LIMIT 1")
    suspend fun getStats(templateId: String): TemplateStatsEntity?

    @Query("SELECT * FROM template_stats")
    suspend fun getAllStats(): List<TemplateStatsEntity>

    @Query("UPDATE template_stats SET hits = hits + 1, lastHitAt = :hitAt WHERE templateId = :templateId")
    suspend fun recordHit(templateId: String, hitAt: Long = System.currentTimeMillis()): Int

    @Query("UPDATE template_stats SET parseFailures = parseFailures + 1 WHERE templateId = :templateId")
    suspend fun recordParseFailure(templateId: String): Int

    @Query("UPDATE template_stats SET userCorrections = userCorrections + 1 WHERE templateId = :templateId")
    suspend fun recordUserCorrection(templateId: String): Int

    @Query("UPDATE template_stats SET shadowAgreements = shadowAgreements + 1 WHERE templateId = :templateId")
    suspend fun recordShadowAgreement(templateId: String): Int

    @Query("UPDATE template_stats SET shadowDisagreements = shadowDisagreements + 1 WHERE templateId = :templateId")
    suspend fun recordShadowDisagreement(templateId: String): Int

    // =================================================================
    // 3. TemplateBankVersion & Membership operations
    // =================================================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankVersion(version: TemplateBankVersionEntity)

    @Query("SELECT * FROM template_bank_version ORDER BY version DESC LIMIT 1")
    suspend fun getLatestBankVersion(): TemplateBankVersionEntity?

    @Query("SELECT * FROM template_bank_version WHERE version = :version LIMIT 1")
    suspend fun getBankVersion(version: Long): TemplateBankVersionEntity?

    @Query("SELECT * FROM template_bank_version ORDER BY version DESC")
    suspend fun getAllBankVersions(): List<TemplateBankVersionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemberships(memberships: List<TemplateBankMembershipEntity>)

    @Query("SELECT * FROM template_bank_membership WHERE version = :version")
    suspend fun getMembershipsForVersion(version: Long): List<TemplateBankMembershipEntity>

    @Query("SELECT templateId FROM template_bank_membership WHERE version = :version")
    suspend fun getTemplateIdsForVersion(version: Long): List<String>

    @Query("""
        SELECT t.* FROM dynamic_template t
        INNER JOIN template_bank_membership m ON t.id = m.templateId
        WHERE m.version = :version
        ORDER BY t.priority DESC
    """)
    suspend fun getTemplatesForBankVersion(version: Long): List<DynamicTemplateEntity>

    @Query("DELETE FROM template_bank_version WHERE version = :version")
    suspend fun deleteBankVersion(version: Long): Int
}
