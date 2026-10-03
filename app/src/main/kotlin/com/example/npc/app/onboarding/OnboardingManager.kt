package com.example.npc.app.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnboardingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManagerCompat,
    private val powerManager: PowerManager
) {

    private val prefs = context.getSharedPreferences("npc_onboarding_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_HYPEROS_AUTOSTART_ACKNOWLEDGED = "key_hyperos_autostart_ack"
        private const val KEY_RECENTS_LOCK_ACKNOWLEDGED = "key_recents_lock_ack"
    }

    fun isNotificationListenerGranted(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun isHyperOsDevice(): Boolean {
        return HyperOsIntegration.isHyperOsOrMiui()
    }

    var isHyperOsAutostartAcknowledged: Boolean
        get() = prefs.getBoolean(KEY_HYPEROS_AUTOSTART_ACKNOWLEDGED, false)
        set(value) = prefs.edit().putBoolean(KEY_HYPEROS_AUTOSTART_ACKNOWLEDGED, value).apply()

    var isRecentsLockAcknowledged: Boolean
        get() = prefs.getBoolean(KEY_RECENTS_LOCK_ACKNOWLEDGED, false)
        set(value) = prefs.edit().putBoolean(KEY_RECENTS_LOCK_ACKNOWLEDGED, value).apply()

    fun getOnboardingState(): OnboardingState {
        val isPostNotificationsGranted = if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val isSmsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED

        return OnboardingState(
            isNotificationListenerGranted = isNotificationListenerGranted(),
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored(),
            isPostNotificationsGranted = isPostNotificationsGranted,
            isSmsPermissionsGranted = isSmsGranted,
            isCalendarPermissionGranted = false,
            isHyperOsDevice = isHyperOsDevice(),
            isHyperOsAutostartAcknowledged = isHyperOsAutostartAcknowledged,
            isRecentsLockAcknowledged = isRecentsLockAcknowledged
        )
    }

    fun createNotificationListenerSettingsIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createHyperOsAutostartIntent(): Intent {
        isHyperOsAutostartAcknowledged = true
        return HyperOsIntegration.getAutostartIntent(context)
    }

    fun createBatteryOptimizationIntent(): Intent {
        return try {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (_: Exception) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    fun getRequiredRuntimePermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= 33) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }
}
