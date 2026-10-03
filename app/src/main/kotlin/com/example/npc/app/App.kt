package com.example.npc.app

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.npc.app.pipeline.EventProcessingOrchestrator
import com.example.npc.app.pipeline.OrchestratorProvider
import com.example.npc.core.storage.StorageGateway
import com.example.npc.core.storage.StorageGatewayProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider, StorageGatewayProvider, OrchestratorProvider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var storageGateway: StorageGateway
    @Inject lateinit var orchestrator: EventProcessingOrchestrator

    override fun provideStorageGateway(): StorageGateway = storageGateway
    override fun provideOrchestrator(): EventProcessingOrchestrator = orchestrator

    private var isSqlCipherLoaded: Boolean = false

    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("sqlcipher")
            isSqlCipherLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            Log.e("NPC_App", "Fatal: Failed to load libsqlcipher.so", e)
            throw e
        }
        AppNotificationChannels.createAll(this)
        orchestrator.start()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .build()
}
