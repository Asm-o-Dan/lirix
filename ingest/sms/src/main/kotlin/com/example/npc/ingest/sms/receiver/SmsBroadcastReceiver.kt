package com.example.npc.ingest.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.sms.controller.SmsIngestController
import com.example.npc.ingest.sms.mapper.SmsMapper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SmsReceiverEntryPoint {
    fun smsIngestController(): SmsIngestController
    fun storageGateway(): StorageGateway
}

open class SmsBroadcastReceiver(
    var smsIngestController: SmsIngestController? = null,
    var storageGateway: StorageGateway? = null
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()
        val controller = smsIngestController ?: defaultController ?: run {
            try {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    SmsReceiverEntryPoint::class.java
                ).smsIngestController()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to resolve SmsIngestController from Hilt EntryPoint", t)
                null
            }
        }
        val gateway = storageGateway ?: defaultStorageGateway ?: run {
            try {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    SmsReceiverEntryPoint::class.java
                ).storageGateway()
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to resolve StorageGateway from Hilt EntryPoint", t)
                null
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (controller == null) {
                    Log.w(TAG, "SmsIngestController is not initialized")
                    return@launch
                }

                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                val contract = SmsMapper.fromSmsMessages(messages) ?: return@launch
                val subId = intent.getIntExtra(
                    "subscription",
                    SubscriptionManager.INVALID_SUBSCRIPTION_ID
                ).let { if (it != SubscriptionManager.INVALID_SUBSCRIPTION_ID) it else null }

                controller.enqueueSms(contract, subId)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to process incoming SMS broadcast", t)
                try {
                    gateway?.upsertSourceHealth(
                        SourceHealth(
                            source = SourceId.SMS,
                            lastEventAt = Instant.now(),
                            events24h = 0,
                            lastError = t.message ?: "Unknown broadcast error",
                            queueDepth = controller?.queueDepth ?: 0
                        )
                    )
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to update SourceHealth on error", e)
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        private const val TAG = "SmsBroadcastReceiver"
        var defaultController: SmsIngestController? = null
        var defaultStorageGateway: StorageGateway? = null
    }
}
