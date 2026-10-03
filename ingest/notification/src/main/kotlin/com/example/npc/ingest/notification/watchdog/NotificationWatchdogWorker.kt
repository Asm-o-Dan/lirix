package com.example.npc.ingest.notification.watchdog

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.npc.ingest.notification.service.PipelineNotificationListenerService
import kotlinx.coroutines.delay

class NotificationWatchdogWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        if (!checkListenerPermission(applicationContext)) {
            showPermissionAlertNotification(applicationContext)
            return Result.success()
        }

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastPulseEpochMs = prefs.getLong(KEY_LAST_PULSE_EPOCH_MS, 0L)
        val nowMs = System.currentTimeMillis()
        val isAlive = checkHeartbeat(lastPulseEpochMs, nowMs)

        if (!isAlive) {
            Log.w(TAG, "Watchdog detected dead or disconnected listener. Starting recovery.")
            executeRebindStep(applicationContext)
            delay(2000L)
            val refreshedPulse = prefs.getLong(KEY_LAST_PULSE_EPOCH_MS, 0L)
            val isRecovered = checkHeartbeat(refreshedPulse, System.currentTimeMillis())
            if (!isRecovered) {
                executeComponentToggleStep(applicationContext)
            }
        }

        return Result.success()
    }

    fun checkListenerPermission(context: Context): Boolean {
        return try {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            enabledPackages.contains(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check listener permission", e)
            false
        }
    }

    fun checkHeartbeat(
        lastPulseEpochMs: Long,
        currentTimeEpochMs: Long,
        thresholdMs: Long = DEFAULT_THRESHOLD_MS
    ): Boolean = Companion.checkHeartbeat(lastPulseEpochMs, currentTimeEpochMs, thresholdMs)

    fun executeRebindStep(context: Context): Boolean {
        val component = ComponentName(context, PipelineNotificationListenerService::class.java)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                NotificationListenerService.requestRebind(component)
                Log.i(TAG, "Successfully invoked requestRebind for $component")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to invoke requestRebind", e)
            false
        }
    }

    fun executeComponentToggleStep(context: Context): Boolean {
        val pm = context.packageManager
        val component = ComponentName(context, PipelineNotificationListenerService::class.java)
        return try {
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "Service component toggled successfully (disable -> enable)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle component setting", e)
            false
        }
    }

    fun showPermissionAlertNotification(context: Context) {
        try {
            val notificationManager = NotificationManagerCompat.from(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ALERT_ID,
                    "Watchdog Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts about disabled notification listener permissions"
                }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.createNotificationChannel(channel)
            }

            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, flags)

            val notification = NotificationCompat.Builder(context, CHANNEL_ALERT_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Требуется доступ к уведомлениям")
                .setContentText("Сбор событий приостановлен. Нажмите для возобновления доступа в настройках.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing notification permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show permission alert notification", e)
        }
    }

    companion object {
        private const val TAG = "NotificationWatchdog"
        const val PREFS_NAME = "npc_watchdog_prefs"
        const val KEY_LAST_PULSE_EPOCH_MS = "key_last_pulse_epoch_ms"
        const val KEY_LISTENER_CONNECTED = "key_listener_connected"
        const val CHANNEL_ALERT_ID = "channel_watchdog_alerts"
        const val ALERT_NOTIFICATION_ID = 9001
        const val DEFAULT_THRESHOLD_MS = 1_800_000L

        fun checkHeartbeat(
            lastPulseEpochMs: Long,
            currentTimeEpochMs: Long,
            thresholdMs: Long = DEFAULT_THRESHOLD_MS
        ): Boolean {
            if (lastPulseEpochMs <= 0L) return false
            val delta = currentTimeEpochMs - lastPulseEpochMs
            if (delta < 0L) return true
            return delta <= thresholdMs
        }
    }
}
