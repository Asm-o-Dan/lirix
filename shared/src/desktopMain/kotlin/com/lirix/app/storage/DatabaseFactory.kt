package com.lirix.app.storage

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

fun getDesktopDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val appData = System.getenv("APPDATA") ?: System.getProperty("user.home")
    val lirixDir = File(appData, "Lirix")
    if (!lirixDir.exists()) {
        lirixDir.mkdirs()
    }
    val dbFile = File(lirixDir, "lirix.db")
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.absolutePath
    )
        .setDriver(BundledSQLiteDriver())
        .fallbackToDestructiveMigration(dropAllTables = true)
}

fun createDesktopDatabase(): AppDatabase {
    return getDesktopDatabaseBuilder().build()
}

actual fun getPlatformDatabase(context: Any): AppDatabase = createDesktopDatabase()
