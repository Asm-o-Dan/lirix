package com.lirix.app

import com.lirix.app.ingestion.CrossSourceCorrelator
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CrossSourceCorrelatorTest {

    @Before
    @After
    fun setup() {
        CrossSourceCorrelator.clear()
    }

    @Test
    fun testKnownMediaPackagesSuppression() {
        // Known media players should be suppressed to avoid duplicate events
        assertTrue(
            CrossSourceCorrelator.shouldSuppress(
                packageName = "com.spotify.music",
                isKnownPackage = true
            )
        )
        assertTrue(
            CrossSourceCorrelator.shouldSuppress(
                packageName = "com.google.android.apps.youtube.music",
                isKnownPackage = true
            )
        )
        assertTrue(
            CrossSourceCorrelator.shouldSuppress(
                packageName = "ru.yandex.music",
                isKnownPackage = true
            )
        )
        assertTrue(
            CrossSourceCorrelator.shouldSuppress(
                packageName = "app.revanced.android.apps.youtube.music",
                isKnownPackage = true
            )
        )
    }

    @Test
    fun testRegularAppNotificationsNotSuppressed() {
        // Standard communication / email / system apps should NEVER be suppressed
        assertFalse(
            CrossSourceCorrelator.shouldSuppress(
                packageName = "org.telegram.messenger",
                isKnownPackage = false
            )
        )
        assertFalse(
            CrossSourceCorrelator.shouldSuppress(
                packageName = "com.whatsapp",
                isKnownPackage = false
            )
        )
        assertFalse(
            CrossSourceCorrelator.shouldSuppress(
                packageName = "com.google.android.gm",
                isKnownPackage = false
            )
        )
    }

    @Test
    fun testActiveSessionSuppression() {
        val testPkg = "com.example.customplayer"

        // Initially not active
        assertFalse(CrossSourceCorrelator.isMediaActive(testPkg))
        assertFalse(
            CrossSourceCorrelator.shouldSuppress(
                packageName = testPkg,
                hasActiveSession = false
            )
        )

        // Register active session
        CrossSourceCorrelator.registerActiveMediaSession(testPkg, "Sample Song", "Sample Artist")
        assertTrue(CrossSourceCorrelator.isMediaActive(testPkg))
        assertTrue(
            CrossSourceCorrelator.shouldSuppress(
                packageName = testPkg,
                hasActiveSession = true
            )
        )

        // Unregister session
        CrossSourceCorrelator.unregisterMediaSession(testPkg)
        assertFalse(CrossSourceCorrelator.isMediaActive(testPkg))
    }

    @Test
    fun testMediaStyleExplicitSuppression() {
        // Any notification marked with MediaStyle should be suppressed regardless of package name
        val customPkg = "com.podcast.app"
        assertTrue(
            CrossSourceCorrelator.shouldSuppress(
                packageName = customPkg,
                isMediaStyle = true
            )
        )
    }
}
