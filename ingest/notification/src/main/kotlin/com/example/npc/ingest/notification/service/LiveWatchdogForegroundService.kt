package com.example.npc.ingest.notification.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class LiveWatchdogForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setContentTitle("Фоновый мониторинг")
            .setContentText("Служба перехвата уведомлений активна")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Watchdog Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains notification listener survivability"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "LiveWatchdogFgs"
        const val CHANNEL_ID = "channel_watchdog_live"
        const val NOTIFICATION_ID = 9002

        fun start(context: Context) {
            val intent = Intent(context, LiveWatchdogForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start LiveWatchdogForegroundService", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveWatchdogForegroundService::class.java)
            try {
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop LiveWatchdogForegroundService", e)
            }
        }
    }
}
