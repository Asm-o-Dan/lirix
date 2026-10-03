package com.example.npc.core.model.pipeline

interface OrchestratorProvider {
    fun provideOrchestrator(): EventProcessingOrchestrator
}
