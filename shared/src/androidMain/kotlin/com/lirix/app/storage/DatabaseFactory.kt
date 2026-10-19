package com.lirix.app.storage

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val AppDatabase.Companion.MIGRATION_11_12: Migration
    get() = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) { db.execSQL(com.lirix.app.translations.CREATE_SONG_TRANSLATIONS) }
    }

val AppDatabase.Companion.MIGRATION_5_6: Migration
    get() = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `lyrics_cache` (
                    `trackKey` TEXT NOT NULL,
                    `plainLyrics` TEXT,
                    `syncedLyricsLrc` TEXT,
                    `chordsAmDm` TEXT,
                    `userNotes` TEXT,
                    `provider` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`trackKey`)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `achievements` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `description` TEXT NOT NULL,
                    `iconRes` TEXT NOT NULL,
                    `isUnlocked` INTEGER NOT NULL DEFAULT 0,
                    `unlockedAt` INTEGER,
                    `currentProgress` INTEGER NOT NULL DEFAULT 0,
                    `maxProgress` INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent()
            )

            AppDatabase.seedDefaultAchievements(db)

            db.execSQL("DROP TABLE IF EXISTS `financial_transactions`")
            db.execSQL("DROP TABLE IF EXISTS `study_items`")
            db.execSQL("DROP TABLE IF EXISTS `rule_entities`")
            db.execSQL("DROP TABLE IF EXISTS `declarative_rules`")
            db.execSQL("DROP TABLE IF EXISTS `prototypes`")
            db.execSQL("DROP TABLE IF EXISTS `events`")
        }
    }

val AppDatabase.Companion.MIGRATION_6_7: Migration
    get() = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `music_tracks` ADD COLUMN `albumArtUri` TEXT DEFAULT NULL")
        }
    }

val AppDatabase.Companion.MIGRATION_7_8: Migration
    get() = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `achievements` ADD COLUMN `tier` TEXT NOT NULL DEFAULT 'BRONZE'")
            db.execSQL("ALTER TABLE `achievements` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'LISTENING'")

            AppDatabase.seed26Achievements(db)

            db.execSQL("UPDATE `achievements` SET `tier` = 'SILVER', `category` = 'EXPLORATION' WHERE `id` = 'CENTURY_CLUB'")
            db.execSQL("UPDATE `achievements` SET `tier` = 'SILVER', `category` = 'LISTENING' WHERE `id` = 'MARATHON_LISTENER'")
            db.execSQL("UPDATE `achievements` SET `tier` = 'GOLD', `category` = 'SPECIAL' WHERE `id` = 'NIGHT_OWL'")
            db.execSQL("UPDATE `achievements` SET `tier` = 'SILVER', `category` = 'EXPLORATION' WHERE `id` = 'GENRE_EXPLORER'")
            db.execSQL("UPDATE `achievements` SET `tier` = 'GOLD', `category` = 'LISTENING' WHERE `id` = 'REPEAT_FANATIC'")
        }
    }

val AppDatabase.Companion.MIGRATION_8_9: Migration
    get() = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `lyrics_rejections` (
                    `trackKey` TEXT NOT NULL,
                    `sourceId` TEXT NOT NULL,
                    `rejectedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`trackKey`, `sourceId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_lyrics_rejections_trackKey` ON `lyrics_rejections` (`trackKey`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_lyrics_rejections_sourceId` ON `lyrics_rejections` (`sourceId`)")
        }
    }

val AppDatabase.Companion.MIGRATION_9_10: Migration
    get() = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `custom_lyrics_rules` (
                    `id` TEXT NOT NULL,
                    `domain` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `ruleJson` TEXT NOT NULL,
                    `isEnabled` INTEGER NOT NULL DEFAULT 1,
                    `priority` INTEGER NOT NULL DEFAULT 100,
                    `isBuiltIn` INTEGER NOT NULL DEFAULT 0,
                    `author` TEXT NOT NULL DEFAULT 'local',
                    `version` INTEGER NOT NULL DEFAULT 1,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_lyrics_rules_domain` ON `custom_lyrics_rules` (`domain`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_lyrics_rules_isEnabled` ON `custom_lyrics_rules` (`isEnabled`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_lyrics_rules_priority` ON `custom_lyrics_rules` (`priority`)")
        }
    }

val AppDatabase.Companion.MIGRATION_10_11: Migration
    get() = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `blacklist_rules` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `ruleType` TEXT NOT NULL,
                    `pattern` TEXT NOT NULL,
                    `matchMode` TEXT NOT NULL,
                    `isEnabled` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_blacklist_rules_isEnabled` ON `blacklist_rules` (`isEnabled`)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_blacklist_rules_ruleType` ON `blacklist_rules` (`ruleType`)"
            )
        }
    }

fun AppDatabase.Companion.seedDefaultAchievements(db: SupportSQLiteDatabase) {
    val achievements = listOf(
        "('FIRST_TRACK', 'Первая нота', 'Прослушан 1 трек', 'ic_music_note', 0, NULL, 0, 1)",
        "('CENTURY_CLUB', 'Клуб сотни', '100 уникальных треков', 'ic_disc', 0, NULL, 0, 100)",
        "('MARATHON_LISTENER', 'Марафонец', 'Сессия > 2 часов', 'ic_timer', 0, NULL, 0, 1)",
        "('NIGHT_OWL', 'Ночная сова', 'Прослушивание между 02:00 и 05:00', 'ic_moon', 0, NULL, 0, 1)",
        "('GENRE_EXPLORER', 'Меломан', 'Более 10 разных исполнителей', 'ic_explore', 0, NULL, 0, 10)",
        "('REPEAT_FANATIC', 'На повторе', 'Один трек прослушан более 20 раз', 'ic_repeat', 0, NULL, 0, 20)",
        "('LYRICS_CONNOISSEUR', 'Знаток текстов', 'Открыто 10 текстов песен', 'ic_lyrics', 0, NULL, 0, 10)"
    )
    for (sqlValues in achievements) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO `achievements` 
            (`id`, `title`, `description`, `iconRes`, `isUnlocked`, `unlockedAt`, `currentProgress`, `maxProgress`)
            VALUES $sqlValues
            """.trimIndent()
        )
    }
}

fun AppDatabase.Companion.seed26Achievements(db: SupportSQLiteDatabase) {
    val achievements = listOf(
        // 🥉 Тир 1: Бронза (7 достижений)
        "('FIRST_TRACK', 'Первая нота', 'Прослушан 1 трек', 'ic_music_note', 0, NULL, 0, 1, 'BRONZE', 'LISTENING')",
        "('DISCOVERY_5', 'Первые шаги', 'Послушано 5 разных артистов', 'ic_explore', 0, NULL, 0, 5, 'BRONZE', 'EXPLORATION')",
        "('LYRICS_NOVICE', 'Подпевала', 'Открыт текст песни', 'ic_lyrics', 0, NULL, 0, 1, 'BRONZE', 'LYRICS')",
        "('CHORD_STRUMMER', 'Первый аккорд', 'Открыты аккорды к треку', 'ic_music_note', 0, NULL, 0, 1, 'BRONZE', 'LYRICS')",
        "('SHORT_SESSION', 'Разминка', '15 минут музыки за сессию', 'ic_timer', 0, NULL, 0, 1, 'BRONZE', 'LISTENING')",
        "('NOTE_TAKER', 'Заметки на полях', 'Добавлена первая личная заметка к треку', 'ic_edit', 0, NULL, 0, 1, 'BRONZE', 'SPECIAL')",
        "('STREAK_3', 'На волне', 'Слушайте музыку 3 дня подряд', 'ic_repeat', 0, NULL, 0, 3, 'BRONZE', 'LISTENING')",

        // 🥈 Тир 2: Серебро (8 достижений)
        "('CENTURY_CLUB', 'Клуб сотни', '100 уникальных треков в библиотеке', 'ic_disc', 0, NULL, 0, 100, 'SILVER', 'EXPLORATION')",
        "('ARTIST_DEVOTEE', 'Преданный фанат', 'Один исполнитель прослушан 15 раз', 'ic_star', 0, NULL, 0, 15, 'SILVER', 'LISTENING')",
        "('KARAOKE_REGULAR', 'Караоке-бар', '10 треков с караоке LRC', 'ic_lyrics', 0, NULL, 0, 10, 'SILVER', 'LYRICS')",
        "('STREAK_7', 'Музыкальная неделя', 'Слушайте музыку 7 дней подряд', 'ic_repeat', 0, NULL, 0, 7, 'SILVER', 'LISTENING')",
        "('MARATHON_LISTENER', 'Марафонец', 'Сессия прослушивания более 2 часов', 'ic_timer', 0, NULL, 0, 1, 'SILVER', 'LISTENING')",
        "('MORNING_ENERGY', 'Бодрое утро', '5 сессий с 06:00 до 10:00 утра', 'ic_sun', 0, NULL, 0, 5, 'SILVER', 'SPECIAL')",
        "('EVENING_CHILL', 'Вечерний релакс', '10 сессий вечером (18:00 - 23:00)', 'ic_moon', 0, NULL, 0, 10, 'SILVER', 'SPECIAL')",
        "('GENRE_EXPLORER', 'Меломан', 'Более 15 разных исполнителей', 'ic_explore', 0, NULL, 0, 15, 'SILVER', 'EXPLORATION')",

        // 🥇 Тир 3: Золото (7 достижений)
        "('LIBRARY_300', 'Золотая фонотека', '300 уникальных треков в библиотеке', 'ic_disc', 0, NULL, 0, 300, 'GOLD', 'EXPLORATION')",
        "('REPEAT_FANATIC', 'На повторе', 'Один трек прослушан более 30 раз', 'ic_repeat', 0, NULL, 0, 30, 'GOLD', 'LISTENING')",
        "('KARAOKE_MASTER', 'Звезда караоке', '25 треков с LRC-караоке', 'ic_lyrics', 0, NULL, 0, 25, 'GOLD', 'LYRICS')",
        "('NIGHT_OWL', 'Ночная сова', '10 сессий глубокой ночью (01:00 - 05:00)', 'ic_moon', 0, NULL, 0, 10, 'GOLD', 'SPECIAL')",
        "('STREAK_30', 'Железная привычка', '30 дней непрерывного прослушивания', 'ic_repeat', 0, NULL, 0, 30, 'GOLD', 'LISTENING')",
        "('MARATHON_5H', 'Аудио-марафон 5ч', 'Более 5 часов музыки за один день', 'ic_timer', 0, NULL, 0, 1, 'GOLD', 'LISTENING')",
        "('ALBUM_COLLECTOR', 'Хранитель винила', '20 треков с обложками альбомов', 'ic_disc', 0, NULL, 0, 20, 'GOLD', 'EXPLORATION')",

        // 💎 Тир 4: Платина / Секретные (4 достижения)
        "('SECRET_MIDNIGHT', 'Полуночная тайна', 'Включен трек в первые 5 минут полуночи (00:00 - 00:05)', 'ic_star', 0, NULL, 0, 1, 'PLATINUM', 'SPECIAL')",
        "('SECRET_REPEAT_DAY', 'Одержимость дня', 'Один трек прослушан 15 раз за одни сутки', 'ic_repeat', 0, NULL, 0, 1, 'PLATINUM', 'LISTENING')",
        "('DISCOGRAPHY_BINGE', 'Дискографический запой', '8 разных треков одного артиста за сутки', 'ic_explore', 0, NULL, 0, 8, 'PLATINUM', 'EXPLORATION')",
        "('PLATINUM_PERFECTION', 'Абсолютный слух', 'Разблокировано 20 любых достижений', 'ic_star', 0, NULL, 0, 20, 'PLATINUM', 'SPECIAL')"
    )

    for (sqlValues in achievements) {
        db.execSQL(
            """
            INSERT OR IGNORE INTO `achievements` 
            (`id`, `title`, `description`, `iconRes`, `isUnlocked`, `unlockedAt`, `currentProgress`, `maxProgress`, `tier`, `category`)
            VALUES $sqlValues
            """.trimIndent()
        )
    }
}
object DatabaseFactory {
    @Volatile
    private var instance: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return instance ?: synchronized(this) {
            instance ?: buildDatabase(context.applicationContext).also { instance = it }
        }
    }
}

private val _autoInit = run {
    AppDatabase.factory = { context ->
        DatabaseFactory.getDatabase(context as Context)
    }
}

actual fun getPlatformDatabase(context: Any): AppDatabase =
    DatabaseFactory.getDatabase(context as Context)

fun AppDatabase.Companion.getInstance(context: Context): AppDatabase =
    DatabaseFactory.getDatabase(context)

fun getAppDatabaseBuilder(context: Context): RoomDatabase.Builder<AppDatabase> {
    return Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        AppDatabase.DATABASE_NAME
    )
        .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addMigrations(
            AppDatabase.MIGRATION_5_6,
            AppDatabase.MIGRATION_6_7,
            AppDatabase.MIGRATION_7_8,
            AppDatabase.MIGRATION_8_9,
            AppDatabase.MIGRATION_9_10,
            AppDatabase.MIGRATION_10_11,
            AppDatabase.MIGRATION_11_12
        )
        .fallbackToDestructiveMigration(dropAllTables = false)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                AppDatabase.seed26Achievements(db)
            }
        })
}

private fun buildDatabase(context: Context): AppDatabase {
    return getAppDatabaseBuilder(context).build()
}
