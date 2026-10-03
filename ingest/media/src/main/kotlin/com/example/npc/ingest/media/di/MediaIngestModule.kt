package com.example.npc.ingest.media.di

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.example.npc.core.model.pipeline.EventProcessingOrchestrator
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.media.detector.MediaSessionBoundaryDetector
import com.example.npc.ingest.media.observer.MediaSessionObserver
import com.example.npc.ingest.media.recovery.MediaSessionRecoveryManager
import kotlinx.coroutines.CoroutineScope

private val Context.mediaSessionDataStore by preferencesDataStore(name = "npc_media_sessions")

/**
 * Manual DI factory for :ingest:media module components.
 *
 * Creates and wires together:
 *  - [MediaSessionBoundaryDetector] (pure stateful FSM, no Android deps)
 *  - [MediaSessionRecoveryManager] (DataStore-backed crash recovery)
 *  - [MediaSessionObserver] (system-level media session listener)
 *
 * Usage (from Application or a DI container):
 * ```kotlin
 * val mediaComponents = MediaIngestModule.create(context, storageGateway, applicationScope)
 * mediaComponents.observer.start()
 * ```
 */
object MediaIngestModule {

    data class Components(
        val detector: MediaSessionBoundaryDetector,
        val recoveryManager: MediaSessionRecoveryManager,
        val observer: MediaSessionObserver
    )

    /**
     * Creates and wires all :ingest:media components.
     *
     * @param context Application context (for DataStore and MediaSessionManager).
     * @param storageGateway Storage persistence layer.
     * @param coroutineScope Scope for observer background coroutines (should be application-scoped).
     * @param orchestrator Optional event pipeline orchestrator to dispatch saved events.
     */
    fun create(
        context: Context,
        storageGateway: StorageGateway,
        coroutineScope: CoroutineScope,
        orchestrator: EventProcessingOrchestrator? = null
    ): Components {
        val detector = MediaSessionBoundaryDetector(
            minSessionThresholdMs = 5_000L,
            heartbeatTimeoutMs = 300_000L
        )

        val recoveryManager = MediaSessionRecoveryManager(
            dataStore = context.mediaSessionDataStore,
            storageGateway = storageGateway
        )

        val observer = MediaSessionObserver(
            context = context,
            boundaryDetector = detector,
            recoveryManager = recoveryManager,
            storageGateway = storageGateway,
            coroutineScope = coroutineScope,
            orchestrator = orchestrator
        )

        return Components(
            detector = detector,
            recoveryManager = recoveryManager,
            observer = observer
        )
    }
}
