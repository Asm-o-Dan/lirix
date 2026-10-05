package com.lirix.app

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.service.MediaIngressFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Constructor

/**
 * TDD Unit-tests for MediaIngressFilter (TASK-ING-01).
 * Validates isolation: strict rejection of banking, SMS, and messenger notifications,
 * and deterministic passing of media notifications.
 */
@RunWith(AndroidJUnit4::class)
class MediaIngressFilterTest {

    private fun createMockSbn(
        pkg: String?,
        category: String? = null,
        extras: Bundle? = Bundle()
    ): StatusBarNotification {
        val notification = Notification().apply {
            this.category = category
            if (extras != null) {
                this.extras = extras
            }
        }

        // StatusBarNotification constructor via reflection for unit test environment
        val constructors = StatusBarNotification::class.java.declaredConstructors
        val constructor = constructors.firstOrNull { it.parameterTypes.size >= 8 }
            ?: constructors.first()
        constructor.isAccessible = true

        val userHandleConstructor = android.os.UserHandle::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
        userHandleConstructor.isAccessible = true
        val defaultUserHandle = userHandleConstructor.newInstance(0)

        val params = Array<Any?>(constructor.parameterTypes.size) { index ->
            when (constructor.parameterTypes[index]) {
                String::class.java -> if (index == 0) pkg else "tag"
                Int::class.javaPrimitiveType -> 100
                Long::class.javaPrimitiveType -> System.currentTimeMillis()
                Notification::class.java -> notification
                android.os.UserHandle::class.java -> defaultUserHandle
                else -> null
            }
        }
        return constructor.newInstance(*params) as StatusBarNotification
    }

    @Test
    fun test_isMediaNotification_rejectsNonMediaPackages() {
        val bankingSbn = createMockSbn("com.tcsbank.mobile")
        assertFalse("Banking notifications must be rejected", MediaIngressFilter.isMediaNotification(bankingSbn))

        val smsSbn = createMockSbn("com.google.android.apps.messaging")
        assertFalse("SMS notifications must be rejected", MediaIngressFilter.isMediaNotification(smsSbn))

        val sberSbn = createMockSbn("ru.sberbankmobile")
        assertFalse("Sberbank notifications must be rejected", MediaIngressFilter.isMediaNotification(sberSbn))

        val apbSbn = createMockSbn("com.apb.mobile")
        assertFalse("Agroprombank notifications must be rejected", MediaIngressFilter.isMediaNotification(apbSbn))
    }

    @Test
    fun test_isMediaNotification_acceptsWhitelistedPlayers() {
        val spotifySbn = createMockSbn("com.spotify.music")
        assertTrue("Spotify must be accepted", MediaIngressFilter.isMediaNotification(spotifySbn))

        val yandexMusicSbn = createMockSbn("ru.yandex.music")
        assertTrue("Yandex Music must be accepted", MediaIngressFilter.isMediaNotification(yandexMusicSbn))

        val vkSbn = createMockSbn("com.vkontakte.android")
        assertTrue("VK Music must be accepted", MediaIngressFilter.isMediaNotification(vkSbn))

        val ytMusicSbn = createMockSbn("com.google.android.apps.youtube.music")
        assertTrue("YouTube Music must be accepted", MediaIngressFilter.isMediaNotification(ytMusicSbn))

        val revancedSbn = createMockSbn("app.revanced.android.apps.youtube.music")
        assertTrue("ReVanced Music must be accepted", MediaIngressFilter.isMediaNotification(revancedSbn))

        val aimpSbn = createMockSbn("com.aimp.player")
        assertTrue("AIMP must be accepted", MediaIngressFilter.isMediaNotification(aimpSbn))
    }

    @Test
    fun test_isMediaNotification_acceptsCategoryTransportOrMediaSessionExtra() {
        val customTransportSbn = createMockSbn("com.unknown.player", category = Notification.CATEGORY_TRANSPORT)
        assertTrue("Category transport must be accepted", MediaIngressFilter.isMediaNotification(customTransportSbn))

        val extrasWithMediaSession = Bundle().apply {
            putString(Notification.EXTRA_MEDIA_SESSION, "session_token")
        }
        val customMediaSessionSbn = createMockSbn("com.unknown.podcast", extras = extrasWithMediaSession)
        assertTrue("EXTRA_MEDIA_SESSION extra must be accepted", MediaIngressFilter.isMediaNotification(customMediaSessionSbn))

        val extrasWithTemplate = Bundle().apply {
            putString("android.template", "androidx.media.app.NotificationCompat\$MediaStyle")
        }
        val customStyleSbn = createMockSbn("com.unknown.audioplayer", extras = extrasWithTemplate)
        assertTrue("MediaStyle template must be accepted", MediaIngressFilter.isMediaNotification(customStyleSbn))
    }

    @Test
    fun test_isMediaNotification_safeOnNullOrEmpty() {
        // Safe check without throwing exceptions
        assertFalse("null SBN must safely return false", MediaIngressFilter.isMediaNotification(null))

        val emptyPkgSbn = createMockSbn("")
        assertFalse("Empty package SBN must return false", MediaIngressFilter.isMediaNotification(emptyPkgSbn))
    }
}
