package com.lirix.app.storage.blacklist

import androidx.room.migration.Migration
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.MIGRATION_10_11 as StorageMigration1011

val AppDatabase.Companion.MIGRATION_10_11: Migration get() = StorageMigration1011
