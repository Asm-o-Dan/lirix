package com.example.npc.app.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface AbsenceAlertScheduler {
    fun schedulePeriodicCheck(config: AbsenceAlertConfig = AbsenceAlertConfig())
    fun cancelPeriodicCheck()

    companion object {
        const val UNIQUE_WORK_NAME = "npc_absence_alert_worker"
        const val TAG = "npc_absence_alert_worker"

        fun schedule(context: Context, config: AbsenceAlertConfig = AbsenceAlertConfig()) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(false)
                .setRequiresDeviceIdle(false)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AbsenceAlertWorker>(
                30, TimeUnit.MINUTES,
                15, TimeUnit.MINUTES
            )
                .addTag(TAG)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }
    }
}

@Singleton
class AbsenceAlertSchedulerImpl @Inject constructor(
    private val workManager: WorkManager
) : AbsenceAlertScheduler {

    override fun schedulePeriodicCheck(config: AbsenceAlertConfig) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(false)
            .setRequiresDeviceIdle(false)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<AbsenceAlertWorker>(
            config.checkIntervalMinutes, TimeUnit.MINUTES,
            config.flexIntervalMinutes, TimeUnit.MINUTES
        )
            .addTag(AbsenceAlertScheduler.TAG)
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            AbsenceAlertScheduler.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    override fun cancelPeriodicCheck() {
        workManager.cancelUniqueWork(AbsenceAlertScheduler.UNIQUE_WORK_NAME)
    }
}
