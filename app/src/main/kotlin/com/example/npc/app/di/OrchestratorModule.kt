package com.example.npc.app.di

import com.example.npc.app.pipeline.EventProcessingOrchestrator
import com.example.npc.app.pipeline.EventProcessingOrchestratorImpl
import com.example.npc.core.model.classify.PackageGatedRouter
import com.example.npc.core.model.classify.SemanticClassifier
import com.example.npc.core.storage.StorageGateway
import com.example.npc.extract.finance.CircuitBreaker
import com.example.npc.extract.finance.IsolatedExtractorRunner
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OrchestratorModule {

    @Provides
    @Singleton
    fun provideEventProcessingOrchestrator(
        storageGateway: StorageGateway,
        semanticClassifier: SemanticClassifier,
        packageGatedRouter: PackageGatedRouter,
        extractorRunner: IsolatedExtractorRunner,
        circuitBreaker: CircuitBreaker
    ): EventProcessingOrchestrator {
        return EventProcessingOrchestratorImpl(
            storageGateway = storageGateway,
            semanticClassifier = semanticClassifier,
            packageGatedRouter = packageGatedRouter,
            extractorRunner = extractorRunner,
            circuitBreaker = circuitBreaker,
            defaultDispatcher = Dispatchers.Default,
            ioDispatcher = Dispatchers.IO
        )
    }
}
