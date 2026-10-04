package com.eventengine.app.service

import android.app.Notification
import android.service.notification.StatusBarNotification

/**
 * Strict gatekeeper for incoming OS notifications.
 * Filters out all non-media notifications (banking, SMS, messengers, system)
 * before any parsing or database interaction occurs.
 *
 * Spec: TASK-ING-01 / .sdd/specs/media-ingress/overview.md#isMediaNotification (v1)
 */
object MediaIngressFilter {

    private val KNOWN_MEDIA_PLAYERS = setOf(
        "com.spotify.music",
        "ru.yandex.music",
        "com.vkontakte.android",
        "com.google.android.apps.youtube.music",
        "com.vanced.android.apps.youtube.music",
        "app.revanced.android.apps.youtube.music",
        "com.aimp.player",
        "com.apple.android.music",
        "deezer.android.app",
        "com.soundcloud.android",
        "com.maxmpz.audioplayer"
    )

    fun isMediaNotification(sbn: StatusBarNotification?): Boolean {
        if (sbn == null) return false
        val notification = sbn.notification ?: return false
        val packageName = sbn.packageName?.lowercase() ?: return false
        if (packageName.isBlank()) return false

        return runCatching {
            // 1. Known players whitelist
            if (KNOWN_MEDIA_PLAYERS.contains(packageName)) {
                return@runCatching true
            }

            val extras = notification.extras

            // 2. Extra media session keys
            if (extras != null) {
                if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
                    extras.containsKey("android.mediaSession")
                ) {
                    return@runCatching true
                }

                // 3. MediaStyle template in extras
                val template = extras.getString("android.template")
                if (template?.contains("MediaStyle", ignoreCase = true) == true) {
                    return@runCatching true
                }
            }

            // 4. Category transport
            if (notification.category == Notification.CATEGORY_TRANSPORT) {
                return@runCatching true
            }

            false
        }.getOrDefault(false)
    }
}

/**
 * Top-level signature per TASK-ING-01 contract specification.
 */
fun isMediaNotification(sbn: StatusBarNotification): Boolean =
    MediaIngressFilter.isMediaNotification(sbn)
