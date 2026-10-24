package com.lirix.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.lirix.app.storage.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/** Independent identity contract taken from the device metadata before installation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Schema14CompatibilityTest {
    @Test fun direct12to14MigrationPreservesRowsAndReopensWithInstalledDeviceIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "schema14-${UUID.randomUUID()}.db"
        try {
            val helper = FrameworkSQLiteOpenHelperFactory().create(
                SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                    .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            requireNotNull(javaClass.getResourceAsStream("/translations/schema-v11.sql")).bufferedReader().useLines { lines ->
                                lines.filter { it.isNotBlank() }.forEach { db.execSQL(it) }
                            }
                            AppDatabase.MIGRATION_11_12.migrate(db)
                            db.execSQL("INSERT INTO music_tracks VALUES ('key','User title','User artist','','test',7,12345,1,2,'User note',1,'[00:01.00] Original','Original',NULL)")
                            db.execSQL("INSERT INTO lyrics_cache VALUES ('key','Original','[00:01.00] Original','Am','User note','USER',2)")
                            db.execSQL("INSERT INTO music_listening_sessions VALUES (1,'key','test',1,1001,8000,1)")
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("fixture only")
                    }).build())
            helper.writableDatabase
            helper.close()
            repeat(2) {
                val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .addMigrations(AppDatabase.MIGRATION_12_14).build()
                try {
                    val db = room.openHelper.writableDatabase
                    assertEquals(14, db.version)
                    db.query("SELECT identity_hash FROM room_master_table WHERE id=42").use { c ->
                        assertTrue(c.moveToFirst()); assertEquals("af51698a84bb8d10ace2e9856ddf8742", c.getString(0))
                    }
                    db.query("SELECT userNotes,isFavorite,playCount FROM music_tracks WHERE trackKey='key'").use { c ->
                        assertTrue(c.moveToFirst()); assertEquals("User note",c.getString(0));assertEquals(1,c.getInt(1));assertEquals(7,c.getInt(2))
                    }
                    db.query("SELECT plainLyrics,chordsAmDm,sourceAttributionJson FROM lyrics_cache WHERE trackKey='key'").use { c ->
                        assertTrue(c.moveToFirst());assertEquals("Original",c.getString(0));assertEquals("Am",c.getString(1));assertTrue(c.isNull(2))
                    }
                    db.query("SELECT durationMs,listeningIntervalsJson FROM music_listening_sessions").use { c ->
                        assertTrue(c.moveToFirst());assertEquals(8000L,c.getLong(0));assertEquals("",c.getString(1));assertFalse(c.moveToNext())
                    }
                } finally { room.close() }
            }
        } finally { context.deleteDatabase(name) }
    }
}
