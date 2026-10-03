package com.example.npc.ingest.notification.watchdog

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.npc.ingest.notification.service.LiveWatchdogForegroundService
import java.util.concurrent.TimeUnit

open class IngestWatchdog(
    private val context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context)
) {

    open fun schedulePeriodicCheck() {
        val request = PeriodicWorkRequestBuilder<NotificationWatchdogWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        ).build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.i(TAG, "Periodic watchdog check scheduled (15 min interval)")
    }

    open fun cancelPeriodicCheck() {
        workManager.cancelUniqueWork(WORK_NAME)
        Log.i(TAG, "Periodic watchdog check cancelled")
    }

    open fun setHighSurvivabilityMode(enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HIGH_SURVIVABILITY_ENABLED, enabled).apply()

        if (enabled) {
            LiveWatchdogForegroundService.start(context)
            Log.i(TAG, "High survivability mode ENABLED (Special-Use FGS started)")
        } else {
            LiveWatchdogForegroundService.stop(context)
            Log.i(TAG, "High survivability mode DISABLED (Special-Use FGS stopped)")
        }
    }

    companion object {
        private const val TAG = "IngestWatchdog"
        const val WORK_NAME = "notification_ingest_watchdog"
        const val PREFS_NAME = "npc_watchdog_prefs"
        const val KEY_HIGH_SURVIVABILITY_ENABLED = "key_high_survivability_enabled"
    }
}
