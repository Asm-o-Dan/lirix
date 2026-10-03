package com.example.npc.ingest.media.observer

import android.content.Context
import android.util.Log
import com.example.npc.core.model.pipeline.EventProcessingOrchestrator
import com.example.npc.core.model.pipeline.OrchestratorStatus
import com.example.npc.core.storage.StorageGateway
import com.example.npc.ingest.media.detector.MediaSessionBoundaryDetector
import com.example.npc.ingest.media.model.ActiveMediaSession
import com.example.npc.ingest.media.model.MediaMetadataSnapshot
import com.example.npc.ingest.media.model.MediaSessionEndReason
import com.example.npc.ingest.media.model.MediaSessionPayload
import com.example.npc.ingest.media.model.SessionBoundaryDecision
import com.example.npc.ingest.media.recovery.MediaSessionRecoveryManager
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MediaSessionObserverTest {

    private val context: Context = mockk(relaxed = true)
    private val boundaryDetector: MediaSessionBoundaryDetector = mockk(relaxed = true)
    private val recoveryManager: MediaSessionRecoveryManager = mockk(relaxed = true)
    private val storageGateway: StorageGateway = mockk(relaxed = true)
    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeOrchestrator : EventProcessingOrchestrator {
        val submittedIds = mutableListOf<Long>()
        override fun start() {}
        override fun submit(eventId: Long): Boolean {
            submittedIds.add(eventId)
            return true
        }
        override fun stop() {}
        override fun getStatus(): OrchestratorStatus = error("not needed")
        override fun triggerRecoverySweep(): kotlinx.coroutines.Job = kotlinx.coroutines.Job().apply { complete() }
    }

    private fun createActiveSession(
        packageName: String = "com.spotify.music",
        title: String = "Starboy",
        artist: String = "The Weeknd",
        album: String = "Starboy"
    ): ActiveMediaSession {
        val now = Instant.now()
        return ActiveMediaSession(
            sessionId = "$packageName:${now.toEpochMilli()}",
            packageName = packageName,
            metadata = MediaMetadataSnapshot(
                title = title,
                artist = artist,
                album = album,
                durationMs = 230_000L
            ),
            sessionStartedAt = now,
            lastActivePlayStartedAt = now,
            accumulatedPlayTimeMs = 0L,
            lastState = 3,
            lastEventAt = now
        )
    }

    private fun createPayload(
        packageName: String = "com.spotify.music",
        title: String = "Starboy",
        artist: String = "The Weeknd",
        album: String = "Starboy",
        endReason: MediaSessionEndReason = MediaSessionEndReason.CONTROLLER_DISCONNECTED
    ): MediaSessionPayload {
        return MediaSessionPayload(
            packageName = packageName,
            trackTitle = title,
            artist = artist,
            album = album,
            trackDurationMs = 230_000L,
            sessionStartedAtEpochMs = 1_000_000L,
            sessionEndedAtEpochMs = 1_200_000L,
            effectiveDurationMs = 200_000L,
            isMicroSession = false,
            endReason = endReason,
            lastError = null
        )
    }

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.i(any(), any<String>()) } returns 0
        every { Log.d(any(), any<String>()) } returns 0
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `recordTrackStarted inserts event and submits savedEventId to orchestrator`() = runTest(testDispatcher) {
        val orchestrator = FakeOrchestrator()
        val observer = MediaSessionObserver(
            context = context,
            boundaryDetector = boundaryDetector,
            recoveryManager = recoveryManager,
            storageGateway = storageGateway,
            coroutineScope = this,
            ioDispatcher = testDispatcher,
            orchestrator = orchestrator
        )

        coEvery { storageGateway.insertRawEvent(any()) } returns 10L
        coEvery { storageGateway.insertEvent(any()) } returns 101L

        val session = createActiveSession()
        observer.recordTrackStarted(session)

        orchestrator.submittedIds shouldContainExactly listOf(101L)
    }

    @Test
    fun `processSessionClose inserts event and submits savedEventId to orchestrator`() = runTest(testDispatcher) {
        val orchestrator = FakeOrchestrator()
        val observer = MediaSessionObserver(
            context = context,
            boundaryDetector = boundaryDetector,
            recoveryManager = recoveryManager,
            storageGateway = storageGateway,
            coroutineScope = this,
            ioDispatcher = testDispatcher,
            orchestrator = orchestrator
        )

        coEvery { storageGateway.insertRawEvent(any()) } returns 20L
        coEvery { storageGateway.insertEvent(any()) } returns 202L

        val payload = createPayload()
        observer.processSessionClose(payload)

        orchestrator.submittedIds shouldContainExactly listOf(202L)
    }

    @Test
    fun `processFsmDecision OpenSession triggers recordTrackStarted and submits to orchestrator`() = runTest(testDispatcher) {
        val orchestrator = FakeOrchestrator()
        val observer = MediaSessionObserver(
            context = context,
            boundaryDetector = boundaryDetector,
            recoveryManager = recoveryManager,
            storageGateway = storageGateway,
            coroutineScope = this,
            ioDispatcher = testDispatcher,
            orchestrator = orchestrator
        )

        coEvery { storageGateway.insertRawEvent(any()) } returns 30L
        coEvery { storageGateway.insertEvent(any()) } returns 303L

        val session = createActiveSession(
            packageName = "com.apple.android.music",
            title = "Blinding Lights",
            album = "After Hours"
        )

        observer.processFsmDecision(SessionBoundaryDecision.OpenSession(session))

        orchestrator.submittedIds shouldContainExactly listOf(303L)
    }

    @Test
    fun `processFsmDecision CloseSession triggers processSessionClose and submits to orchestrator`() = runTest(testDispatcher) {
        val orchestrator = FakeOrchestrator()
        val observer = MediaSessionObserver(
            context = context,
            boundaryDetector = boundaryDetector,
            recoveryManager = recoveryManager,
            storageGateway = storageGateway,
            coroutineScope = this,
            ioDispatcher = testDispatcher,
            orchestrator = orchestrator
        )

        coEvery { storageGateway.insertRawEvent(any()) } returns 40L
        coEvery { storageGateway.insertEvent(any()) } returns 404L

        val payload = createPayload(
            packageName = "com.apple.android.music",
            title = "Blinding Lights",
            album = "After Hours",
            endReason = MediaSessionEndReason.STATE_STOPPED
        )

        observer.processFsmDecision(SessionBoundaryDecision.CloseSession(payload))

        orchestrator.submittedIds shouldContainExactly listOf(404L)
    }

    @Test
    fun `null orchestrator does not throw exception during track start or session close`() = runTest(testDispatcher) {
        val observer = MediaSessionObserver(
            context = context,
            boundaryDetector = boundaryDetector,
            recoveryManager = recoveryManager,
            storageGateway = storageGateway,
            coroutineScope = this,
            ioDispatcher = testDispatcher,
            orchestrator = null
        )

        coEvery { storageGateway.insertRawEvent(any()) } returns 50L
        coEvery { storageGateway.insertEvent(any()) } returns 505L

        val session = createActiveSession()
        observer.recordTrackStarted(session)

        val payload = createPayload()
        observer.processSessionClose(payload)
    }
}
