package com.example.npc.app.di

import com.example.npc.core.model.pipeline.EventProcessingOrchestrator
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.sms.controller.SmsIngestController
import com.example.npc.ingest.sms.controller.SmsIngestControllerImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SmsIngestModule {

    @Provides
    @Singleton
    fun provideSmsIngestController(
        storageGateway: StorageGateway,
        orchestrator: EventProcessingOrchestrator
    ): SmsIngestController {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val controller = SmsIngestControllerImpl(
            storageGateway = storageGateway,
            scope = scope,
            ioDispatcher = Dispatchers.IO,
            orchestrator = orchestrator
        )
        controller.start()
        return controller
    }
}
