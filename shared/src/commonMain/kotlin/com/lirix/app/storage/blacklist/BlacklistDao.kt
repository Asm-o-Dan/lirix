package com.lirix.app.storage.blacklist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BlacklistDao {
    @Query("SELECT * FROM blacklist_rules ORDER BY createdAt DESC")
    fun getAllRulesFlow(): Flow<List<BlacklistRuleEntity>>

    @Query("SELECT * FROM blacklist_rules ORDER BY createdAt DESC")
    suspend fun getAllRules(): List<BlacklistRuleEntity>

    @Query("SELECT * FROM blacklist_rules WHERE isEnabled = 1 ORDER BY createdAt DESC")
    suspend fun getActiveRules(): List<BlacklistRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: BlacklistRuleEntity)

    @Update
    suspend fun updateRule(rule: BlacklistRuleEntity)

    @Query("DELETE FROM blacklist_rules WHERE id = :ruleId")
    suspend fun deleteRule(ruleId: String)

    @Query("SELECT * FROM blacklist_rules WHERE id = :ruleId LIMIT 1")
    suspend fun getRuleById(ruleId: String): BlacklistRuleEntity?

    @Query("SELECT COUNT(*) FROM blacklist_rules")
    suspend fun countRules(): Int
}
