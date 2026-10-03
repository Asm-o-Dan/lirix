package com.example.npc.ingest.sms.poller

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.normalize.EventNormalizer
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.sms.controller.SmsIngestController
import com.example.npc.ingest.sms.mapper.SmsMapper
import com.example.npc.ingest.sms.model.SmsRawPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class SmsPoller(
    private val context: Context,
    private val storageGateway: StorageGateway,
    private val smsIngestController: SmsIngestController
) {

    suspend fun pollNewMessages(sinceTimestamp: Long): Int = withContext(Dispatchers.IO) {
        val hasPermission = try {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_SMS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }

        if (!hasPermission) {
            return@withContext 0
        }

        var importedCount = 0

        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE
            )
            val selection = "${Telephony.Sms.DATE} > ?"
            val selectionArgs = arrayOf(sinceTimestamp.toString())
            val sortOrder = "${Telephony.Sms.DATE} ASC"

            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
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

                    val existingId = storageGateway.findDuplicate(dedupKey)
                    if (existingId == null) {
                        val seq = smsIngestController.nextSeq()
                        val rawPayload = SmsRawPayload(
                            seq = seq,
                            originAddress = contract.originAddress,
                            body = contract.body,
                            timestampMillis = contract.timestampMillis,
                            receivedAt = Instant.now()
                        )
                        val rawEvent = SmsMapper.toRawEvent(rawPayload, dedupKey)
                        val rawId = storageGateway.insertRawEvent(rawEvent)
                        val event = SmsMapper.toEvent(rawEvent.copy(id = rawId), contract)
                        storageGateway.insertEvent(event)
                        importedCount++
                    }
                }
            }
        } catch (se: SecurityException) {
            Log.e(TAG, "SecurityException while polling SMS inbox", se)
            val health = SourceHealth(
                source = SourceId.SMS,
                lastEventAt = Instant.now(),
                events24h = 0,
                lastError = se.message ?: "READ_SMS permission not granted",
                queueDepth = smsIngestController.queueDepth
            )
            storageGateway.upsertSourceHealth(health)
            return@withContext 0
        } catch (t: Throwable) {
            Log.e(TAG, "Error while polling SMS inbox", t)
        }

        importedCount
    }

    companion object {
        private const val TAG = "SmsPoller"
    }
}
