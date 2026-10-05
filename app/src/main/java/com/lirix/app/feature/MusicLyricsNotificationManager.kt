package com.lirix.app.feature

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lirix.app.MainActivity
import timber.log.Timber

/**
 * Manages the persistent/quick notification in the Android status bar (шторка)
 * proposing lyrics & synced karaoke lookup when active music playback is detected.
 */
object MusicLyricsNotificationManager {

    const val CHANNEL_ID = "music_lyrics_prompt_channel"
    private const val CHANNEL_NAME = "Поиск текстов песен"
    private const val CHANNEL_DESC = "Предложение поиска текста и караоке для играющего трека"
    const val NOTIFICATION_ID = 40404

    const val ACTION_VIEW_LYRICS = "com.lirix.app.ACTION_VIEW_LYRICS"
    const val EXTRA_TRACK_TITLE = "extra_track_title"
    const val EXTRA_TRACK_ARTIST = "extra_track_artist"
    const val EXTRA_TRACK_ALBUM = "extra_track_album"

    private var lastShownTitle: String? = null
    private var lastShownArtist: String? = null

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                setShowBadge(false)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    fun showLyricsPrompt(
        context: Context,
        title: String,
        artist: String,
        album: String = ""
    ) {
        if (title.isBlank()) return

        // Prevent spamming notification updates if exact same track is already shown
        if (title == lastShownTitle && artist == lastShownArtist) {
            return
        }

        // On Android 13+, check POST_NOTIFICATIONS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
            if (permission != PackageManager.PERMISSION_GRANTED) {
                Timber.tag("LyricsNotification").w("POST_NOTIFICATIONS permission not granted yet")
                return
            }
        }

        try {
            initChannel(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_VIEW_LYRICS
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TRACK_TITLE, title)
                putExtra(EXTRA_TRACK_ARTIST, artist)
                putExtra(EXTRA_TRACK_ALBUM, album)
            }

            val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val contentPendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                openIntent,
                pendingIntentFlags
            )

            val contentText = if (artist.isNotBlank()) {
                "$artist • Нажмите, чтобы открыть текст"
            } else {
                "Нажмите, чтобы открыть текст и караоке"
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("🎵 $title")
                .setContentText(contentText)
                .setSubText("Текст песни")
                .setContentIntent(contentPendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(
                    android.R.drawable.ic_menu_search,
                    "Найти текст",
                    contentPendingIntent
                )
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            lastShownTitle = title
            lastShownArtist = artist
            Timber.tag("LyricsNotification").i("Displayed lyrics prompt notification: %s - %s", artist, title)
        } catch (e: Exception) {
            Timber.tag("LyricsNotification").e(e, "Failed to display lyrics prompt notification")
        }
    }

    fun cancelLyricsPrompt(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            lastShownTitle = null
            lastShownArtist = null
            Timber.tag("LyricsNotification").i("Cancelled lyrics prompt notification")
        } catch (e: Exception) {
            Timber.tag("LyricsNotification").e(e, "Failed to cancel lyrics prompt notification")
        }
    }
}
