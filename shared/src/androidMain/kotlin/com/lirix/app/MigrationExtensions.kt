package com.lirix.app

import androidx.room.migration.Migration
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.MIGRATION_5_6 as StorageMigration56
import com.lirix.app.storage.MIGRATION_6_7 as StorageMigration67
import com.lirix.app.storage.MIGRATION_7_8 as StorageMigration78
import com.lirix.app.storage.MIGRATION_8_9 as StorageMigration89
import com.lirix.app.storage.MIGRATION_9_10 as StorageMigration910
import com.lirix.app.storage.MIGRATION_10_11 as StorageMigration1011

val AppDatabase.Companion.MIGRATION_5_6: Migration get() = StorageMigration56
val AppDatabase.Companion.MIGRATION_6_7: Migration get() = StorageMigration67
val AppDatabase.Companion.MIGRATION_7_8: Migration get() = StorageMigration78
val AppDatabase.Companion.MIGRATION_8_9: Migration get() = StorageMigration89
val AppDatabase.Companion.MIGRATION_9_10: Migration get() = StorageMigration910
val AppDatabase.Companion.MIGRATION_10_11: Migration get() = StorageMigration1011
