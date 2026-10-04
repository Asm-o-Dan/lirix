package com.eventengine.app.ingestion

import android.app.Notification
import android.service.notification.StatusBarNotification
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber

/**
 * Cross-Source Correlator.
 * Suppresses redundant Status Bar Notifications emitted by media players
 * when MediaSession updates are active or notification has media playback style.
 */
object CrossSourceCorrelator {

    private const val TAG = "CrossSourceCorrelator"
    private const val ACTIVE_SESSION_EXPIRATION_MS = 15_000L

    val KNOWN_MEDIA_PACKAGES = setOf(
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.google.android.youtube",
        "app.revanced.android.apps.youtube.music",
        "app.revanced.android.youtube",
        "com.vanced.android.apps.youtube.music",
        "ru.yandex.music",
        "ru.vk.music",
        "org.videolan.vlc",
        "com.apple.android.music",
        "deezer.android.app",
        "com.soundcloud.android",
        "fm.castbox.audiobook.radio.podcast",
        "com.pocketcasts.android"
    )

    // Tracks package -> last active timestamp
    private val activeMediaSessions = ConcurrentHashMap<String, Long>()

    fun registerActiveMediaSession(packageName: String, title: String? = null, artist: String? = null) {
        val pkg = packageName.trim().lowercase()
        activeMediaSessions[pkg] = System.currentTimeMillis()
        Timber.tag(TAG).d("Registered active media session for [%s]: %s - %s", pkg, artist, title)
    }

    fun unregisterMediaSession(packageName: String) {
        val pkg = packageName.trim().lowercase()
        activeMediaSessions.remove(pkg)
        Timber.tag(TAG).d("Unregistered media session for [%s]", pkg)
    }

    fun isMediaActive(packageName: String): Boolean {
        val pkg = packageName.trim().lowercase()
        val lastSeen = activeMediaSessions[pkg] ?: return false
        val isActive = (System.currentTimeMillis() - lastSeen) < ACTIVE_SESSION_EXPIRATION_MS
        if (!isActive) {
            activeMediaSessions.remove(pkg)
        }
        return isActive
    }

    /**
     * Determines whether an incoming StatusBarNotification should be suppressed
     * to avoid duplicate entries alongside MediaSessionCollector.
     */
    fun shouldSuppressNotification(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val extras = notification.extras

        // NEVER suppress chat / messaging notifications from any app (Telegram, WhatsApp, AyuGram, SMS)
        if (notification.category == Notification.CATEGORY_MESSAGE ||
            notification.category == Notification.CATEGORY_CALL ||
            extras?.containsKey("android.messagingStyleUser") == true ||
            extras?.containsKey(Notification.EXTRA_MESSAGES) == true ||
            extras?.getString("android.template")?.contains("MessagingStyle", ignoreCase = true) == true
        ) {
            return false
        }

        val pkg = sbn.packageName?.trim()?.lowercase().orEmpty()

        // Check for Android MediaStyle extras or mediaSession token
        val hasMediaSessionToken = extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true ||
                extras?.containsKey("android.mediaSession") == true

        val template = extras?.getString("android.template")
        val isMediaStyle = template?.contains("MediaStyle", ignoreCase = true) == true

        // Check category & known media player packages
        val isKnownMediaPkg = KNOWN_MEDIA_PACKAGES.contains(pkg)
        val isTransportCategory = notification.category == Notification.CATEGORY_TRANSPORT

        // If this notification is NOT a media playback notification, never suppress it
        if (!isMediaStyle && !hasMediaSessionToken && !isTransportCategory && !isKnownMediaPkg) {
            return false
        }

        return shouldSuppress(
            packageName = pkg,
            template = template,
            isMediaStyle = isMediaStyle || hasMediaSessionToken,
            isTransportCategory = isTransportCategory,
            isKnownPackage = isKnownMediaPkg,
            hasActiveSession = isMediaActive(pkg)
        )
    }

    /**
     * Pure testable decision function for cross-source suppression.
     */
    fun shouldSuppress(
        packageName: String,
        template: String? = null,
        isMediaStyle: Boolean = false,
        isTransportCategory: Boolean = false,
        isKnownPackage: Boolean = false,
        hasActiveSession: Boolean = false
    ): Boolean {
        val pkg = packageName.trim().lowercase()
        if (hasActiveSession) return true
        if (isMediaStyle) return true
        if (isKnownPackage && (isTransportCategory || template?.contains("Media", ignoreCase = true) == true)) return true
        if (isKnownPackage && KNOWN_MEDIA_PACKAGES.contains(pkg)) return true
        return false
    }

    fun clear() {
        activeMediaSessions.clear()
    }
}
