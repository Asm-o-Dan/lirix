package com.lirix.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.storage.AppDatabase
import com.lirix.app.storage.MIGRATION_11_12
import com.lirix.app.storage.MIGRATION_12_13
import com.lirix.app.storage.MIGRATION_13_14
import com.lirix.app.translations.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SongTranslationMigrationTest {
    @Test fun androidMigrationAndOfflineReopenPreserveExistingLyrics() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "translation-test-${UUID.randomUUID()}.db"
        try {
            val config = SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        requireNotNull(SongTranslationMigrationTest::class.java.getResourceAsStream("/translations/schema-v11.sql")).bufferedReader().use { reader ->
                            reader.readLines().filter { it.isNotBlank() }.forEach { db.execSQL(it) }
                        }
                        db.execSQL("INSERT INTO music_tracks VALUES ('key','Title','Artist','','test',7,12345,1,2,'notes',1,'[00:01.00] Original','Original',NULL)")
                        db.execSQL("INSERT INTO lyrics_cache VALUES ('key','Original','[00:01.00] Original','Am','notes','USER',2)")
                        db.execSQL("INSERT INTO music_listening_sessions VALUES (1,'key','test',1,1001,8000,1)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("fixture only")
                }).build()
            val helper = FrameworkSQLiteOpenHelperFactory().create(config)
            helper.writableDatabase
            helper.close()
            fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_11_12, AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14).build()
            var db = open()
            val translation = SongTranslation("https://www.amalgama-lab.com/songs/a/artist/example.html", "Example", "Artist", "Пример", "Автор",
                listOf(TranslationBlock("Synthetic first line", "Искусственная первая строка")))
            try {
                assertEquals(7, db.musicDao().getTrackByKey("key")!!.playCount)
                assertEquals("notes", db.musicDao().getTrackByKey("key")!!.userNotes)
                assertEquals(8000L, db.musicDao().getAllSessions().single().durationMs)
                assertEquals("", db.musicDao().getAllSessions().single().listeningIntervalsJson)
                val automaticRow = SongTranslationEntity("key", "ru", translation.sourceUrl, TranslationDocuments.encode(translation), 1)
                assertTrue(db.songTranslationDao().saveAutomaticIfEmpty(automaticRow))
                assertFalse(db.songTranslationDao().saveAutomaticIfEmpty(automaticRow.copy(sourceUrl = translation.sourceUrl.replace("example.html", "other.html"))))
            } finally { db.close() }
            db = open()
            try {
                assertEquals(translation, TranslationDocuments.decode(db.songTranslationDao().get("key").single().documentJson))
                assertEquals("Original", db.lyricsDao().getLyrics("key")!!.plainLyrics)
                assertEquals("Am", db.lyricsDao().getLyrics("key")!!.chordsAmDm)
                assertEquals("USER", db.lyricsDao().getLyrics("key")!!.provider)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
