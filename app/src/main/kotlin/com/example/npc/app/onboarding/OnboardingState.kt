package com.example.npc.app.onboarding

data class OnboardingState(
    val isNotificationListenerGranted: Boolean,
    val isBatteryOptimizationIgnored: Boolean,
    val isPostNotificationsGranted: Boolean,
    val isSmsPermissionsGranted: Boolean,
    val isCalendarPermissionGranted: Boolean,
    val isHyperOsDevice: Boolean,
    val isHyperOsAutostartAcknowledged: Boolean,
    val isRecentsLockAcknowledged: Boolean
) {
    val isMandatorySetupComplete: Boolean
        get() = isNotificationListenerGranted &&
                isBatteryOptimizationIgnored &&
                isPostNotificationsGranted &&
                (!isHyperOsDevice || isHyperOsAutostartAcknowledged)
}
