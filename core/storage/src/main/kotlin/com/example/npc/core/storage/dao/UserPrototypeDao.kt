package com.example.npc.core.storage.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.npc.core.storage.entity.UserPrototypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class UserPrototypeDao {

    @Query("SELECT * FROM user_prototype WHERE package_name = :pkg AND fingerprint = :fp LIMIT 1")
    abstract suspend fun findByPackageAndFingerprint(pkg: String, fp: String): UserPrototypeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entity: UserPrototypeEntity): Long

    open suspend fun upsert(entity: UserPrototypeEntity): Long = insert(entity)

    @Query("UPDATE user_prototype SET support_count = support_count + 1, last_seen_at = :now WHERE id = :id")
    abstract suspend fun incrementSupport(id: Long, now: Long)

    open suspend fun incrementSupportCount(id: Long, lastSeenAt: Long) = incrementSupport(id, lastSeenAt)

    @Query("UPDATE user_prototype SET category = :category, support_count = 1, last_seen_at = :now WHERE id = :id")
    abstract suspend fun reassignCategory(id: Long, category: String, now: Long)

    @Transaction
    open suspend fun recordCorrection(pkg: String, fp: String, category: String, now: Long) {
        val existing = findByPackageAndFingerprint(pkg, fp)
        when {
            existing == null -> {
                insert(
                    UserPrototypeEntity(
                        packageName = pkg,
                        fingerprint = fp,
                        category = category,
                        supportCount = 1,
                        createdAt = now,
                        lastSeenAt = now
                    )
                )
            }
            existing.category == category -> {
                incrementSupport(existing.id, now)
            }
            else -> {
                reassignCategory(existing.id, category, now)
            }
        }
    }

    @Query("SELECT * FROM user_prototype ORDER BY last_seen_at DESC")
    abstract fun observeAll(): Flow<List<UserPrototypeEntity>>

    @Query("SELECT * FROM user_prototype ORDER BY support_count DESC, last_seen_at DESC")
    abstract suspend fun getAll(): List<UserPrototypeEntity>

    open suspend fun getAllPrototypes(): List<UserPrototypeEntity> = getAll()

    @Query("DELETE FROM user_prototype WHERE id = :id")
    abstract suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM user_prototype")
    abstract suspend fun deleteAll(): Int

    @Query("SELECT COUNT(*) FROM user_prototype")
    abstract suspend fun count(): Long
}
