package com.example.npc.ingest.sms.worker

import android.content.Context
import androidx.work.WorkerParameters
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.sms.controller.SmsIngestController
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.junit.jupiter.api.Test

class SmsWorkerTest {

    @Test
    fun `SmsBackfillWorker constructor has expected parameter types`() {
        val constructor = SmsBackfillWorker::class.java.constructors.first { it.parameterTypes.size >= 3 }
        constructor.parameterTypes[0] shouldBe Context::class.java
        constructor.parameterTypes[1] shouldBe WorkerParameters::class.java
        constructor.parameterTypes[2] shouldBe StorageGateway::class.java
    }

    @Test
    fun `SmsPollingWorker constructor has expected parameter types`() {
        val constructor = SmsPollingWorker::class.java.constructors.first { it.parameterTypes.size >= 3 }
        constructor.parameterTypes[0] shouldBe Context::class.java
        constructor.parameterTypes[1] shouldBe WorkerParameters::class.java
        constructor.parameterTypes[2] shouldBe StorageGateway::class.java
    }

    @Test
    fun `workers instantiate properly with injected storageGateway`() {
        val context: Context = mockk(relaxed = true)
        val params: WorkerParameters = mockk(relaxed = true)
        val storageGateway: StorageGateway = mockk(relaxed = true)
        val controller: SmsIngestController = mockk(relaxed = true)

        val backfillWorker = SmsBackfillWorker(context, params, storageGateway, controller)
        backfillWorker.storageGateway shouldBe storageGateway
        backfillWorker.smsIngestController shouldBe controller

        val pollingWorker = SmsPollingWorker(context, params, storageGateway, controller)
        pollingWorker.storageGateway shouldBe storageGateway
        pollingWorker.smsIngestController shouldBe controller
    }
}
