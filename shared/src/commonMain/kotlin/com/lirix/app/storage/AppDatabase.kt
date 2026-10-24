package com.lirix.app.storage

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.lirix.app.storage.blacklist.BlacklistDao
import com.lirix.app.storage.blacklist.BlacklistRuleEntity

/**
 * Room Multiplatform Database for Music & Lyrics Hub.
 * Version 14: Adds optional lyrics/chord attribution; version 13 listening intervals remain intact.
 * Spec: TASK-KMP-06 / SPEC-BLK-01 / ADR-020
 */
@Database(
    entities = [
        TrackEntity::class,
        ListeningSessionEntity::class,
        LyricsCacheEntity::class,
        AchievementEntity::class,
        LyricsRejectionEntity::class,
        CustomLyricsRuleEntity::class,
        BlacklistRuleEntity::class,
        com.lirix.app.translations.SongTranslationEntity::class
    ],
    version = 14,
    exportSchema = false
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun musicDao(): MusicDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun achievementDao(): AchievementDao
    abstract fun blacklistDao(): BlacklistDao
    abstract fun songTranslationDao(): com.lirix.app.translations.SongTranslationDao

    companion object {
        const val DATABASE_NAME = "event_engine.db"

        var factory: ((Any) -> AppDatabase)? = null
        private var instance: AppDatabase? = null

        fun getInstance(context: Any): AppDatabase {
            val current = instance
            if (current != null) return current
            return synchronized(this) {
                instance ?: ((factory?.invoke(context) ?: getPlatformDatabase(context)).also { instance = it })
            }
        }
    }
}

expect fun getPlatformDatabase(context: Any): AppDatabase

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
