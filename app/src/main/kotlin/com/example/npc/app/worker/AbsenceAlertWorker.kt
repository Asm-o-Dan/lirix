package com.example.npc.app.worker

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.npc.app.AppNotificationChannels
import com.example.npc.core.storage.StorageGateway
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class AbsenceAlertWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val storageGateway: StorageGateway,
    private val notificationManager: NotificationManagerCompat
) : CoroutineWorker(context, params) {

    private val logic = AbsenceAlertLogic()

    override suspend fun doWork(): Result {
        return try {
            val currentEpochMs = System.currentTimeMillis()
            val healthList = storageGateway.observeSourceHealth().first()
            val latestEventEpochMs = healthList.mapNotNull { it.lastEventAt?.toEpochMilli() }.maxOrNull()

            val config = AbsenceAlertConfig()
            val thresholdMs = config.thresholdHours * 3_600_000L

            val isAbsent = logic.checkAbsence(
                lastEventEpochMs = latestEventEpochMs,
                currentEpochMs = currentEpochMs,
                thresholdMs = thresholdMs
            )

            if (isAbsent) {
                val silenceHours = if (latestEventEpochMs != null) {
                    logic.silenceDurationHours(latestEventEpochMs, currentEpochMs)
                } else {
                    config.thresholdHours
                }
                postAbsenceNotification(silenceHours)
            } else {
                cancelAbsenceNotification()
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    fun checkAbsence(lastEventEpochMs: Long?, currentEpochMs: Long, thresholdMs: Long): Boolean {
        return logic.checkAbsence(lastEventEpochMs, currentEpochMs, thresholdMs)
    }

    @SuppressLint("MissingPermission")
    fun postAbsenceNotification(silenceDurationHours: Long) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pendingIntent = if (launchIntent != null) {
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else null

        val notification = NotificationCompat.Builder(context, AppNotificationChannels.CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Внимание: нет новых событий!")
            .setContentText("Нет новых событий более $silenceDurationHours ч. Проверьте разрешения автозапуска и листенера.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Нет новых событий уже $silenceDurationHours ч. Возможно, фоновые службы были остановлены системой. Проверьте разрешения автозапуска и работу листенера."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .apply {
                if (pendingIntent != null) setContentIntent(pendingIntent)
            }
            .build()

        try {
            notificationManager.notify(AppNotificationChannels.NOTIFICATION_ID_ABSENCE_ALERT, notification)
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }

    fun cancelAbsenceNotification() {
        notificationManager.cancel(AppNotificationChannels.NOTIFICATION_ID_ABSENCE_ALERT)
    }
}
