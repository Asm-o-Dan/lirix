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

val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(Schema15.ADD_FAVORITE_USER_SET_AT)
        Schema15.seedMessengerRulesSql(System.currentTimeMillis()).forEach(connection::execSQL)
    }
}

/** Fresh databases get the same built-in rules that the 14→15 migration adds. */
val SEED_BUILTIN_RULES_CALLBACK = object : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        Schema15.seedMessengerRulesSql(System.currentTimeMillis()).forEach(connection::execSQL)
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
        .addMigrations(MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_12_14, MIGRATION_14_15)
        .addCallback(SEED_BUILTIN_RULES_CALLBACK)
}

fun createDesktopDatabase(): AppDatabase {
    return getDesktopDatabaseBuilder().build()
}

actual fun getPlatformDatabase(context: Any): AppDatabase = createDesktopDatabase()
