package com.example.npc.ingest.sms.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.normalize.EventNormalizer
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.sms.controller.SmsIngestController
import com.example.npc.ingest.sms.mapper.SmsMapper
import com.example.npc.ingest.sms.model.SmsRawPayload
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.time.Instant

@HiltWorker
class SmsBackfillWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val injectedStorageGateway: StorageGateway,
    private val injectedSmsIngestController: SmsIngestController? = null
) : CoroutineWorker(appContext, workerParams) {

    var storageGateway: StorageGateway? = injectedStorageGateway
    var smsIngestController: SmsIngestController? = injectedSmsIngestController

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val gateway = storageGateway ?: defaultStorageGateway
        val controller = smsIngestController ?: defaultController

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
            try {
                gateway?.upsertSourceHealth(
                    SourceHealth(
                        source = SourceId.SMS,
                        lastEventAt = Instant.now(),
                        events24h = 0,
                        lastError = "READ_SMS permission not granted",
                        queueDepth = controller?.queueDepth ?: 0
                    )
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to update SourceHealth", e)
            }
            return@withContext Result.failure()
        }

        val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_BACKFILL_COMPLETED, false)) {
            return@withContext Result.success()
        }

        val sinceTimestamp = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        var maxTimestamp = sinceTimestamp
        var importedCount = 0
        var duplicateCount = 0

        try {
            val projection = arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            val selection = "${Telephony.Sms.DATE} > ?"
            val selectionArgs = arrayOf(sinceTimestamp.toString())
            val sortOrder = "${Telephony.Sms.DATE} ASC"

            applicationContext.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                while (coroutineContext.isActive && cursor.moveToNext()) {
                    val contract = SmsMapper.fromCursor(cursor)
                    val payloadJson = SmsMapper.toPayloadJson(
                        originAddress = contract.originAddress,
                        body = contract.body,
                        timestampMillis = contract.timestampMillis
                    )
                    val dedupKey = EventNormalizer.computeDeduplicationKey(
                        SourceId.SMS,
                        "android.telephony.sms",
                        payloadJson
                    )

                    val existing = gateway?.findDuplicate(dedupKey)
                    if (existing == null) {
                        val seq = controller?.nextSeq() ?: 0L
                        val rawPayload = SmsRawPayload(
                            seq = if (seq > 0L) seq else 1L,
                            originAddress = contract.originAddress,
                            body = contract.body,
                            timestampMillis = contract.timestampMillis,
                            receivedAt = Instant.now()
                        )
                        val rawEvent = SmsMapper.toRawEvent(rawPayload, dedupKey)
                        val rawId = gateway?.insertRawEvent(rawEvent) ?: 0L
                        val event = SmsMapper.toEvent(rawEvent.copy(id = rawId), contract)
                        gateway?.insertEvent(event)
                        importedCount++
                    } else {
                        duplicateCount++
                    }
                    maxTimestamp = maxOf(maxTimestamp, contract.timestampMillis)
                }
            }

            prefs.edit()
                .putBoolean(KEY_BACKFILL_COMPLETED, true)
                .putLong(KEY_LAST_SYNC_TIMESTAMP, maxTimestamp)
                .apply()

            try {
                gateway?.upsertSourceHealth(
                    SourceHealth(
                        source = SourceId.SMS,
                        lastEventAt = Instant.now(),
                        events24h = importedCount,
                        lastError = null,
                        queueDepth = controller?.queueDepth ?: 0
                    )
                )
            } catch (e: Throwable) {
                Log.e(TAG, "Failed to update SourceHealth after backfill", e)
            }

            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "Error during SMS backfill", t)
            Result.failure()
        }
    }

    companion object {
        private const val TAG = "SmsBackfillWorker"
        const val PREFS_NAME = "npc_sms_prefs"
        const val KEY_BACKFILL_COMPLETED = "key_sms_backfill_completed"
        const val KEY_LAST_SYNC_TIMESTAMP = "key_sms_last_sync_timestamp"

        var defaultStorageGateway: StorageGateway? = null
        var defaultController: SmsIngestController? = null
    }
}
