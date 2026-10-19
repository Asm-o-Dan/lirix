package com.lirix.app.storage

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.lirix.app.translations.CREATE_SONG_TRANSLATIONS

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) { connection.execSQL(CREATE_SONG_TRANSLATIONS) }
}

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
        .addMigrations(MIGRATION_11_12)
}

fun createDesktopDatabase(): AppDatabase {
    return getDesktopDatabaseBuilder().build()
}

actual fun getPlatformDatabase(context: Any): AppDatabase = createDesktopDatabase()
