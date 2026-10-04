package com.eventengine.app

import android.app.Application
import com.eventengine.app.ingestion.MediaSessionCollector
import com.eventengine.app.storage.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Application entry point for Android Event Engine.
 * Initializes logging, database pre-warming, and media session tracking.
 */
class EventEngineApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var mediaCollector: MediaSessionCollector

    override fun onCreate() {
        super.onCreate()

        // Structured logging
        Timber.plant(Timber.DebugTree())
        Timber.i("Android Event Engine application initialized.")

        // Pre-warm Room database connection
        appScope.launch {
            val db = AppDatabase.getInstance(this@EventEngineApp)
            val count = db.musicDao().getRecentTracks(1).size
            Timber.i("Room Database warmed up. Stored recent tracks: %d", count)
        }

        // Initialize MediaSession collector
        mediaCollector = MediaSessionCollector(this)
        mediaCollector.startListening()

        // Create lyrics notification channel
        com.eventengine.app.feature.MusicLyricsNotificationManager.initChannel(this)
    }
}
