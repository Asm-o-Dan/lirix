package com.example.npc.ingest.notification.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.normalize.EventNormalizer
import com.example.npc.core.model.pipeline.EventProcessingOrchestrator
import com.example.npc.core.model.pipeline.OrchestratorProvider
import com.example.npc.core.storage.StorageGateway
import com.example.npc.core.storage.StorageGatewayProvider
import com.example.npc.ingest.notification.filter.NotificationFilter
import com.example.npc.ingest.notification.mapper.NotificationMapper
import com.example.npc.ingest.notification.model.NotificationRawPayload
import com.example.npc.ingest.notification.tracker.NotificationUpdateTracker
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

open class PipelineNotificationListenerService : NotificationListenerService {

    lateinit var storageGateway: StorageGateway
    lateinit var updateTracker: NotificationUpdateTracker
    var orchestrator: EventProcessingOrchestrator? = null
    var mediaObserver: com.example.npc.ingest.media.observer.MediaSessionObserver? = null

    val seqGenerator = AtomicLong(0L)
    val queueDepthCounter = AtomicInteger(0)
    val channel = Channel<NotificationRawPayload>(Channel.UNLIMITED)
    val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var consumerJob: Job? = null

    constructor() : super()

    constructor(
        storageGateway: StorageGateway,
        updateTracker: NotificationUpdateTracker
    ) : super() {
        this.storageGateway = storageGateway
        this.updateTracker = updateTracker
    }

    override fun onCreate() {
        super.onCreate()
        ensureDependencies()
    }

    fun ensureDependencies() {
        if (!::storageGateway.isInitialized) {
            val provider = try {
                applicationContext as? StorageGatewayProvider
            } catch (_: Throwable) {
                null
            }
            if (provider != null) {
                val gateway = provider.provideStorageGateway()
                this.storageGateway = gateway
                if (!::updateTracker.isInitialized) {
                    this.updateTracker = NotificationUpdateTracker(gateway)
                }
            } else {
                Log.w(TAG, "StorageGatewayProvider is not available via applicationContext")
            }
        }
        if (orchestrator == null) {
            val orchProvider = try {
                applicationContext as? OrchestratorProvider
            } catch (_: Throwable) {
                null
            }
            orchestrator = orchProvider?.provideOrchestrator()
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return
            ensureDependencies()
            val receivedAt = Instant.now()
            val seq = seqGenerator.incrementAndGet()
            val payload = NotificationMapper.extractPayload(sbn, seq, receivedAt)
            val sendResult = channel.trySend(payload)
            if (sendResult.isSuccess) {
                queueDepthCounter.incrementAndGet()
            } else {
                Log.e(TAG, "Notification channel is closed. Dropping payload seq=$seq")
            }
            try {
                getSharedPreferences("npc_watchdog_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putLong("key_last_pulse_epoch_ms", receivedAt.toEpochMilli())
                    .apply()
            } catch (_: Throwable) {
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onNotificationPosted", t)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return
            ensureDependencies()
            if (::updateTracker.isInitialized) {
                updateTracker.evictNotification(sbn.key)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onNotificationRemoved", t)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        ensureDependencies()
        orchestrator?.triggerRecoverySweep()
        if (mediaObserver == null && ::storageGateway.isInitialized) {
            try {
                val mediaComponents = com.example.npc.ingest.media.di.MediaIngestModule.create(
                    context = applicationContext,
                    storageGateway = storageGateway,
                    coroutineScope = serviceScope,
                    orchestrator = this.orchestrator
                )
                mediaObserver = mediaComponents.observer
                mediaObserver?.start()
                Log.i(TAG, "MediaSessionObserver started successfully")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to start MediaSessionObserver", t)
            }
        }
        try {
            getSharedPreferences("npc_watchdog_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("key_listener_connected", true)
                .putLong("key_last_pulse_epoch_ms", System.currentTimeMillis())
                .apply()
        } catch (_: Throwable) {
        }
        startConsumerLoop()
        serviceScope.launch {
            try {
                if (::storageGateway.isInitialized) {
                    storageGateway.upsertSourceHealth(
                        SourceHealth(
                            source = SourceId.NOTIFICATION,
                            lastEventAt = null,
                            events24h = 0,
                            lastError = null,
                            queueDepth = queueDepthCounter.get()
                        )
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to update source health on connected", t)
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        try {
            getSharedPreferences("npc_watchdog_prefs", android.content.Context.MODE_PRIVATE)
                .edit()
                .putBoolean("key_listener_connected", false)
                .apply()
        } catch (_: Throwable) {
        }
        val disconnectedAt = Instant.now()
        serviceScope.launch {
            try {
                if (::storageGateway.isInitialized) {
                    storageGateway.upsertSourceHealth(
                        SourceHealth(
                            source = SourceId.NOTIFICATION,
                            lastEventAt = null,
                            events24h = 0,
                            lastError = "Listener disconnected by OS at $disconnectedAt",
                            queueDepth = queueDepthCounter.get()
                        )
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to update source health on disconnected", t)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaObserver?.stop()
        channel.close()
        serviceScope.cancel()
    }

    fun startConsumerLoop() {
        if (consumerJob == null || consumerJob?.isActive == false) {
            consumerJob = serviceScope.launch {
                consumePayloads()
            }
        }
    }

    internal suspend fun consumePayloads() {
        for (payload in channel) {
            queueDepthCounter.decrementAndGet()
            ensureDependencies()
            if (!::storageGateway.isInitialized) {
                Log.w(TAG, "StorageGateway is not initialized, dropping payload seq=${payload.seq}")
                continue
            }
            try {
                processSinglePayload(payload)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process payload seq=${payload.seq}", e)
                try {
                    if (::storageGateway.isInitialized) {
                        storageGateway.upsertSourceHealth(
                            SourceHealth(
                                source = SourceId.NOTIFICATION,
                                lastEventAt = payload.receivedAt,
                                events24h = 0,
                                lastError = "Error processing seq ${payload.seq}: ${e.message}".take(1000),
                                queueDepth = queueDepthCounter.get()
                            )
                        )
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Failed to update source health on error", t)
                }
            }
        }
    }

    internal suspend fun processSinglePayload(payload: NotificationRawPayload) {
        if (!::updateTracker.isInitialized && ::storageGateway.isInitialized) {
            updateTracker = NotificationUpdateTracker(storageGateway)
        }

        // ШАГ 1: Немедленная персистенция RawEvent (до нормализации и фильтрации)
        val jsonString = NotificationMapper.toPayloadJson(payload)
        val dedupKey = EventNormalizer.computeDeduplicationKey(SourceId.NOTIFICATION, payload.packageName, jsonString)
        val rawEvent = RawEvent(
            id = 0L,
            seq = payload.seq,
            source = SourceId.NOTIFICATION,
            packageName = payload.packageName,
            receivedAt = payload.receivedAt,
            payloadJson = jsonString,
            hash = dedupKey
        )
        val rawEventId = storageGateway.insertRawEvent(rawEvent)
        if (rawEventId == -1L) {
            updateSourceHealthOnPayload(payload.receivedAt)
            return
        }

        // ШАГ 2: Фильтрация паразитных событий
        val hostPackage = try { packageName } catch (_: Throwable) { "" } ?: ""
        val filterDecision = NotificationFilter.evaluate(payload, hostPackageName = hostPackage)
        if (!filterDecision.isAccepted) {
            updateSourceHealthOnPayload(payload.receivedAt)
            return
        }

        // ШАГ 3: Склейка обновлений
        val threadKey = updateTracker.computeThreadKey(payload.packageName, payload.id, payload.tag, payload.extras)
        val previousEventId = updateTracker.resolvePreviousEventId(threadKey, payload.key)

        // ШАГ 4: Нормализация и вставка Event
        val (effectiveTitle, effectiveText) = when {
            payload.extras.messagingStyle != null -> {
                val title = payload.extras.messagingStyle.conversationTitle
                    ?: payload.extras.title
                    ?: ""
                val text = payload.extras.messagingStyle.messages.lastOrNull()?.text ?: ""
                title to text
            }
            payload.extras.bigText != null -> {
                (payload.extras.title ?: "") to payload.extras.bigText
            }
            payload.extras.textLines.isNotEmpty() -> {
                (payload.extras.title ?: "") to payload.extras.textLines.joinToString("\n")
            }
            else -> {
                (payload.extras.title ?: "") to (payload.extras.text ?: "")
            }
        }

        val domainEvent = EventNormalizer.normalize(
            rawEvent = rawEvent.copy(id = rawEventId),
            title = effectiveTitle,
            text = effectiveText,
            threadKey = threadKey,
            isUpdateOf = previousEventId
        )
        val savedEventId = storageGateway.insertEvent(domainEvent)
        updateTracker.recordEventMapping(threadKey, payload.key, savedEventId)

        // ШАГ 4.1: Неблокирующая отправка в семантический контур (< 0.1 мс)
        orchestrator?.submit(savedEventId)

        // ШАГ 5: Обновление source_health
        updateSourceHealthOnPayload(payload.receivedAt)
    }

    private suspend fun updateSourceHealthOnPayload(receivedAt: Instant) {
        if (::storageGateway.isInitialized) {
            storageGateway.upsertSourceHealth(
                SourceHealth(
                    source = SourceId.NOTIFICATION,
                    lastEventAt = receivedAt,
                    events24h = 0,
                    lastError = null,
                    queueDepth = queueDepthCounter.get()
                )
            )
        }
    }

    companion object {
        private const val TAG = "PipelineNotification"
    }
}
