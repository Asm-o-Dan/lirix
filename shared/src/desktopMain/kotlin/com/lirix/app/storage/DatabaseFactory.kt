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

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE lyrics_cache ADD COLUMN sourceAttributionJson TEXT DEFAULT NULL")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE music_listening_sessions ADD COLUMN listeningIntervalsJson TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_12_14 = object : Migration(12, 14) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE music_listening_sessions ADD COLUMN listeningIntervalsJson TEXT NOT NULL DEFAULT ''")
        connection.execSQL("ALTER TABLE lyrics_cache ADD COLUMN sourceAttributionJson TEXT DEFAULT NULL")
    }
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
        .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_12_14)
}

fun createDesktopDatabase(): AppDatabase {
    return getDesktopDatabaseBuilder().build()
}

actual fun getPlatformDatabase(context: Any): AppDatabase = createDesktopDatabase()
