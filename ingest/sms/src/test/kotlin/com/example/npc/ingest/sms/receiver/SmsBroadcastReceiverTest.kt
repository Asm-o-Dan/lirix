package com.example.npc.ingest.sms.receiver

import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.sms.controller.SmsIngestController
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SmsBroadcastReceiverTest {

    private lateinit var controller: SmsIngestController
    private lateinit var storageGateway: StorageGateway
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        controller = mockk(relaxed = true)
        storageGateway = mockk(relaxed = true)
        context = mockk(relaxed = true)
        SmsBroadcastReceiver.defaultController = null
        SmsBroadcastReceiver.defaultStorageGateway = null
    }

    @Test
    fun `ignores intent when action is not SMS_RECEIVED`() {
        val receiver = SmsBroadcastReceiver(controller, storageGateway)
        val intent: Intent = mockk(relaxed = true) {
            every { action } returns "android.intent.action.BOOT_COMPLETED"
        }

        receiver.onReceive(context, intent)
    }

    @Test
    fun `ignores intent when context or intent is null`() {
        val receiver = SmsBroadcastReceiver(controller, storageGateway)
        val intent: Intent = mockk(relaxed = true) {
            every { action } returns Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        }

        receiver.onReceive(null, null)
        receiver.onReceive(context, null)
        receiver.onReceive(null, intent)
    }

    @Test
    fun `SmsReceiverEntryPoint interface exposes controller and gateway`() {
        val methods = SmsReceiverEntryPoint::class.java.methods.map { it.name }
        methods.contains("smsIngestController") shouldBe true
        methods.contains("storageGateway") shouldBe true
    }

    @Test
    fun `receiver can be constructed with null controller and falls back gracefully`() {
        val receiver = SmsBroadcastReceiver()
        receiver.smsIngestController shouldBe null
        receiver.storageGateway shouldBe null

        val intent: Intent = mockk(relaxed = true) {
            every { action } returns Telephony.Sms.Intents.SMS_RECEIVED_ACTION
        }
        receiver.onReceive(context, intent)
    }
}
