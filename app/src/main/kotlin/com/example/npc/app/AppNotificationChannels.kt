package com.example.npc.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object AppNotificationChannels {
    const val CHANNEL_SERVICE = "npc_service"
    const val CHANNEL_ALERTS = "npc_alerts"
    const val CHANNEL_ID_SERVICE = CHANNEL_SERVICE
    const val CHANNEL_ID_ALERTS = CHANNEL_ALERTS

    const val NOTIFICATION_ID_LIVE_FGS = 1001
    const val NOTIFICATION_ID_ABSENCE_ALERT = 2001
    const val NOTIFICATION_ID_WATCHDOG_PERMISSION = 3001

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                context.getString(R.string.channel_service_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.channel_service_desc)
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.channel_alerts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_alerts_desc)
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(listOf(serviceChannel, alertsChannel))
        }
    }
}
