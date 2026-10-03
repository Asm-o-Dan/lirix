package com.example.npc.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.npc.app.worker.AbsenceAlertScheduler
import com.example.npc.ingest.notification.watchdog.IngestWatchdog
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject lateinit var ingestWatchdog: IngestWatchdog
    @Inject lateinit var absenceAlertScheduler: AbsenceAlertScheduler

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return

        Log.i("NPC_Boot", "System boot or package replacement detected: $action")

        if (::ingestWatchdog.isInitialized && ::absenceAlertScheduler.isInitialized) {
            BootCompletedHandler(ingestWatchdog, absenceAlertScheduler).handle(action)
        } else {
            if (action in BootCompletedHandler.HANDLED_ACTIONS) {
                AbsenceAlertScheduler.schedule(context)
            }
        }
    }
}
