package com.example.npc.app.receiver

import com.example.npc.app.worker.AbsenceAlertScheduler
import com.example.npc.ingest.notification.watchdog.IngestWatchdog

/**
 * Testable core of BootCompletedReceiver — extracted from the @AndroidEntryPoint
 * BroadcastReceiver so the scheduling logic can be unit-tested without Hilt or Context.
 *
 * The real BootCompletedReceiver delegates to this after validating the intent action.
 */
class BootCompletedHandler(
    private val ingestWatchdog: IngestWatchdog,
    private val absenceAlertScheduler: AbsenceAlertScheduler
) {

    companion object {
        val HANDLED_ACTIONS = setOf(
            "android.intent.action.BOOT_COMPLETED",
            "android.intent.action.MY_PACKAGE_REPLACED"
        )
    }

    /**
     * Handles a boot/package-replaced event.
     *
     * @param action The intent action received by the BroadcastReceiver.
     * @return true if the action was handled; false if it was ignored.
     */
    fun handle(action: String?): Boolean {
        if (action !in HANDLED_ACTIONS) return false
        ingestWatchdog.schedulePeriodicCheck()
        absenceAlertScheduler.schedulePeriodicCheck()
        return true
    }
}
