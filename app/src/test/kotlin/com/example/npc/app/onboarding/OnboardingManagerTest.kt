package com.example.npc.app.onboarding

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Unit tests for [OnboardingState.isMandatorySetupComplete].
 *
 * Invariant (from spec §2):
 *   isMandatorySetupComplete = isNotificationListenerGranted
 *       && isBatteryOptimizationIgnored
 *       && isPostNotificationsGranted
 *       && (!isHyperOsDevice || isHyperOsAutostartAcknowledged)
 *
 * No Android APIs involved — pure Kotlin logic, no mocks needed.
 */
class OnboardingManagerTest {

    // ---------- helpers ----------

    private fun fullState(
        notificationListener: Boolean = true,
        batteryOptimization: Boolean = true,
        postNotifications: Boolean = true,
        sms: Boolean = true,
        calendar: Boolean = true,
        isHyperOs: Boolean = false,
        hyperOsAcknowledged: Boolean = false,
        recentsAcknowledged: Boolean = false
    ) = OnboardingState(
        isNotificationListenerGranted = notificationListener,
        isBatteryOptimizationIgnored = batteryOptimization,
        isPostNotificationsGranted = postNotifications,
        isSmsPermissionsGranted = sms,
        isCalendarPermissionGranted = calendar,
        isHyperOsDevice = isHyperOs,
        isHyperOsAutostartAcknowledged = hyperOsAcknowledged,
        isRecentsLockAcknowledged = recentsAcknowledged
    )

    // ---------- tests ----------

    @Test
    fun `all mandatory flags true on non-HyperOS device returns isMandatorySetupComplete true`() {
        val state = fullState(
            notificationListener = true,
            batteryOptimization = true,
            postNotifications = true,
            isHyperOs = false
        )
        state.isMandatorySetupComplete shouldBe true
    }

    @Test
    fun `notification listener not granted returns false`() {
        val state = fullState(notificationListener = false)
        state.isMandatorySetupComplete shouldBe false
    }

    @Test
    fun `battery optimization not ignored returns false`() {
        val state = fullState(batteryOptimization = false)
        state.isMandatorySetupComplete shouldBe false
    }

    @Test
    fun `post notifications not granted returns false`() {
        val state = fullState(postNotifications = false)
        state.isMandatorySetupComplete shouldBe false
    }

    @Test
    fun `HyperOS device without autostart acknowledged returns false`() {
        val state = fullState(
            isHyperOs = true,
            hyperOsAcknowledged = false
        )
        state.isMandatorySetupComplete shouldBe false
    }

    @Test
    fun `HyperOS device with autostart acknowledged returns true`() {
        val state = fullState(
            isHyperOs = true,
            hyperOsAcknowledged = true
        )
        state.isMandatorySetupComplete shouldBe true
    }

    @Test
    fun `non-HyperOS device with all mandatory flags true ignores autostart field`() {
        // isHyperOsAutostartAcknowledged = false should not block completion on non-HyperOS
        val state = fullState(
            isHyperOs = false,
            hyperOsAcknowledged = false
        )
        state.isMandatorySetupComplete shouldBe true
    }

    @Test
    fun `multiple mandatory flags missing returns false`() {
        val state = fullState(
            notificationListener = false,
            batteryOptimization = false,
            postNotifications = false
        )
        state.isMandatorySetupComplete shouldBe false
    }

    @Test
    fun `HyperOS device missing notification listener and autostart not acknowledged returns false`() {
        val state = fullState(
            notificationListener = false,
            isHyperOs = true,
            hyperOsAcknowledged = false
        )
        state.isMandatorySetupComplete shouldBe false
    }

    @Test
    fun `optional fields sms calendar recents do not affect isMandatorySetupComplete`() {
        // sms, calendar, recents are optional — setup should still be considered complete
        val state = fullState(
            sms = false,
            calendar = false,
            recentsAcknowledged = false
        )
        state.isMandatorySetupComplete shouldBe true
    }
}
