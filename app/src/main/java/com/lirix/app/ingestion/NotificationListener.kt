package com.lirix.app.ingestion

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.lirix.app.storage.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * OS-level Notification Ingestion Service for Music & Lyrics Hub.
 * TASK-PURGE-01: Stripped of legacy classification, normalization,
 * finance, study, rules engine, and prototype bank calls.
 *
 * Retained: media notification detection → MusicLyricsNotificationManager.
 * Future SDD tasks will re-add targeted music ingestion logic.
 */
class NotificationListener : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val appNameCache = ConcurrentHashMap<String, String>()
    private val albumArtStorage by lazy { com.lirix.app.storage.AlbumArtStorage(applicationContext) }

    // Sliding window LRU for deduplication (1200ms window)
    private val recentFingerprints = ConcurrentHashMap<String, Long>()
    private var mediaSessionCollector: MediaSessionCollector? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        Timber.tag(TAG).i("NotificationListener connected — starting MediaSessionCollector listening…")
        try {
            mediaSessionCollector = MediaSessionCollector(applicationContext).apply {
                startListening()
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to start MediaSessionCollector in onListenerConnected")
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Timber.tag(TAG).w("onListenerDisconnected — requesting rebind…")
        mediaSessionCollector?.stopListening()
        mediaSessionCollector = null
        try {
            requestRebind(ComponentName(this, NotificationListener::class.java))
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to requestRebind")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.notification == null) return
        serviceScope.launch {
            try {
                processNotification(sbn)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error processing notification from %s", sbn.packageName)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        val pkg = sbn.packageName.orEmpty()
        if (CrossSourceCorrelator.isMediaActive(pkg) ||
            pkg.contains("music") || pkg.contains("spotify") || pkg.contains("audio")
        ) {
            com.lirix.app.feature.MusicLyricsNotificationManager.cancelLyricsPrompt(applicationContext)
        }
    }

    private suspend fun processNotification(sbn: StatusBarNotification) {
        // Strict Gatekeeper: discard any non-media notifications (banking, SMS, system, etc.)
        if (!com.lirix.app.service.MediaIngressFilter.isMediaNotification(sbn)) {
            return
        }

        val packageName = sbn.packageName.orEmpty()
        val notification = sbn.notification ?: return
        val extras = notification.extras

        val title = extractTitle(extras)
        val text = extractText(extras)
        if (title.isNullOrEmpty() && text.isNullOrEmpty()) return

        val isOngoing = (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0

        // Deduplication
        val fingerprint = "$packageName|${sbn.id}|${title.orEmpty()}|${text.orEmpty()}|$isOngoing"
        val now = System.currentTimeMillis()
        val lastSeen = recentFingerprints[fingerprint]
        if (lastSeen != null && (now - lastSeen) < DEDUP_WINDOW_MS) {
            Timber.tag(TAG).d("Dedup: %s | %s", packageName, title)
            return
        }
        recentFingerprints[fingerprint] = now
        cleanupFingerprints(now)

        // Suppress notifications already handled by MediaSessionCollector
        if (CrossSourceCorrelator.shouldSuppressNotification(sbn)) {
            Timber.tag(TAG).d("CrossSourceCorrelator suppressed: %s", packageName)
            return
        }

        if (isOngoing) {
            val parsed = com.lirix.app.feature.MusicTrackParser.parse(
                mediaTrack = null,
                mediaArtist = null,
                title = title,
                text = text
            )
            if (parsed.title.isNotBlank()) {
                val cleanArtist = parsed.artist.ifBlank { "Unknown Artist" }
                val trackKey = com.lirix.app.feature.MusicFeatureEngine.computeTrackKey(parsed.title, cleanArtist, parsed.album)

                val largeIconBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    notification.getLargeIcon()?.loadDrawable(applicationContext)?.let { drawable ->
                        (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    }
                } else {
                    @Suppress("DEPRECATION")
                    (extras?.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap)
                }

                val savedPath = if (largeIconBitmap != null) {
                    kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        albumArtStorage.saveBitmap(trackKey, largeIconBitmap)
                    }
                } else {
                    albumArtStorage.getAlbumArtPath(trackKey)
                }

                val db = com.lirix.app.storage.AppDatabase.getInstance(applicationContext)
                val musicEngine = com.lirix.app.feature.MusicFeatureEngine(db.musicDao(), db.lyricsDao())
                val recordedTrack = musicEngine.recordPlaybackSignal(
                    title = parsed.title,
                    artist = cleanArtist,
                    album = parsed.album,
                    sourcePackage = packageName,
                    playbackState = "PLAYING",
                    timestamp = now,
                    albumArtUri = savedPath
                )

                if (recordedTrack != null) {
                    musicEngine.prefetchLyricsAsync(
                        context = applicationContext,
                        scope = serviceScope,
                        track = recordedTrack
                    )
                }

                com.lirix.app.feature.MusicLyricsNotificationManager.showLyricsPrompt(
                    context = applicationContext,
                    title = parsed.title,
                    artist = parsed.artist,
                    album = parsed.album
                )
            }
        }

        Timber.tag(TAG).d("Notification processed: [%s] %s", packageName, title)
    }

    private fun extractTitle(extras: android.os.Bundle?): String? {
        if (extras == null) return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        if (!title.isNullOrEmpty()) return title
        val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        if (!convTitle.isNullOrEmpty()) return convTitle
        return extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
    }

    private fun extractText(extras: android.os.Bundle?): String? {
        if (extras == null) return null
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        if (!bigText.isNullOrEmpty()) return bigText
        return extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
    }

    private fun cleanupFingerprints(currentTime: Long) {
        if (recentFingerprints.size > 1000) {
            val iterator = recentFingerprints.entries.iterator()
            while (iterator.hasNext()) {
                if (currentTime - iterator.next().value > DEDUP_WINDOW_MS * 2) iterator.remove()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSessionCollector?.stopListening()
        mediaSessionCollector = null
        serviceScope.cancel()
    }

    companion object {
        private const val TAG = "NotificationListener"
        private const val DEDUP_WINDOW_MS = 1200L

        fun isPermissionGranted(context: Context): Boolean {
            val enabled = NotificationManagerCompat.getEnabledListenerPackages(context)
            return enabled.contains(context.packageName)
        }
    }
}
