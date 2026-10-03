package com.example.npc.ingest.sms.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.sms.controller.SmsIngestController
import com.example.npc.ingest.sms.poller.SmsPoller
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SmsPollingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val injectedStorageGateway: StorageGateway,
    private val injectedSmsIngestController: SmsIngestController? = null
) : CoroutineWorker(appContext, workerParams) {

    var poller: SmsPoller? = null
    var storageGateway: StorageGateway? = injectedStorageGateway
    var smsIngestController: SmsIngestController? = injectedSmsIngestController

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val hasPermission = try {
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }

        if (!hasPermission) {
            Log.w(TAG, "READ_SMS permission not granted")
            return@withContext Result.failure()
        }

        val activePoller = poller ?: run {
            val gateway = storageGateway ?: defaultStorageGateway
            val controller = smsIngestController ?: defaultController
            if (gateway != null && controller != null) {
                SmsPoller(applicationContext, gateway, controller)
            } else {
                defaultPoller
            }
        }

        if (activePoller == null) {
            Log.w(TAG, "SmsPoller is not configured")
            return@withContext Result.failure()
        }

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sinceTimestamp = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)

        try {
            activePoller.pollNewMessages(sinceTimestamp)
            prefs.edit()
                .putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis())
                .apply()
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Error in SmsPollingWorker", t)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "SmsPollingWorker"
        const val PREFS_NAME = "npc_sms_prefs"
        const val KEY_LAST_SYNC_TIMESTAMP = "key_sms_last_sync_timestamp"

        var defaultPoller: SmsPoller? = null
        var defaultStorageGateway: StorageGateway? = null
        var defaultController: SmsIngestController? = null
    }
}
