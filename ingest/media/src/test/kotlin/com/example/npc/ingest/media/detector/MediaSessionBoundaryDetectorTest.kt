package com.example.npc.ingest.media.detector

import com.example.npc.ingest.media.model.MediaMetadataSnapshot
import com.example.npc.ingest.media.model.MediaPlaybackSnapshot
import com.example.npc.ingest.media.model.MediaSessionEndReason
import com.example.npc.ingest.media.model.SessionBoundaryDecision
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class MediaSessionBoundaryDetectorTest {

    private lateinit var detector: MediaSessionBoundaryDetector

    private val packageName = "com.spotify.music"
    private val t0 = Instant.parse("2026-09-26T10:00:00Z")

    private val statePlaying = MediaPlaybackSnapshot(
        state = 3, // PlaybackStateCompat.STATE_PLAYING
        positionMs = 0L,
        playbackSpeed = 1.0f,
        updateTimeEpochMs = t0.toEpochMilli()
    )

    private val statePaused = MediaPlaybackSnapshot(
        state = 2, // PlaybackStateCompat.STATE_PAUSED
        positionMs = 10_000L,
        playbackSpeed = 0.0f,
        updateTimeEpochMs = t0.toEpochMilli()
    )

    private val stateStopped = MediaPlaybackSnapshot(
        state = 1, // PlaybackStateCompat.STATE_STOPPED
        positionMs = 0L,
        playbackSpeed = 0.0f,
        updateTimeEpochMs = t0.toEpochMilli()
    )

    private val stateNone = MediaPlaybackSnapshot(
        state = 0, // PlaybackStateCompat.STATE_NONE
        positionMs = 0L,
        playbackSpeed = 0.0f,
        updateTimeEpochMs = t0.toEpochMilli()
    )

    @BeforeEach
    fun setUp() {
        detector = MediaSessionBoundaryDetector(
            minSessionThresholdMs = 5_000L,
            heartbeatTimeoutMs = 300_000L
        )
    }

    @Test
    fun `STATE_PLAYING opens new session when none is active`() {
        val decision = detector.onPlaybackStateChanged(packageName, statePlaying, t0)

        decision.shouldBeInstanceOf<SessionBoundaryDecision.OpenSession>()
        val session = decision.session
        session.packageName shouldBe packageName
        session.sessionId shouldBe "$packageName:${t0.toEpochMilli()}"
        session.sessionStartedAt shouldBe t0
        session.lastActivePlayStartedAt shouldBe t0
        session.accumulatedPlayTimeMs shouldBe 0L
        session.lastState shouldBe 3

        detector.getActiveSession(packageName) shouldBe session
    }

    @Test
    fun `subsequent STATE_PLAYING while playing emits NoOp and refreshes heartbeat`() {
        detector.onPlaybackStateChanged(packageName, statePlaying, t0)

        val t1 = t0.plusSeconds(10)
        val decision = detector.onPlaybackStateChanged(packageName, statePlaying, t1)

        decision shouldBe SessionBoundaryDecision.NoOp
        val active = detector.getActiveSession(packageName)
        active.shouldNotBeNull()
        active.lastEventAt shouldBe t1
        active.lastActivePlayStartedAt shouldBe t0
    }

    @Test
    fun `STATE_PAUSED does not close session and accumulates played time`() {
        detector.onPlaybackStateChanged(packageName, statePlaying, t0)

        val t1 = t0.plusSeconds(30)
        val decision = detector.onPlaybackStateChanged(packageName, statePaused, t1)

        decision shouldBe SessionBoundaryDecision.NoOp
        val active = detector.getActiveSession(packageName)
        active.shouldNotBeNull()
        active.lastState shouldBe 2 // STATE_PAUSED
        active.lastActivePlayStartedAt.shouldBeNull()
        active.accumulatedPlayTimeMs shouldBe 30_000L
        active.lastEventAt shouldBe t1

        // Repeated PAUSED event should be a no-op and not re-add delta
        val t2 = t1.plusSeconds(5)
        val repeatedDecision = detector.onPlaybackStateChanged(packageName, statePaused, t2)
        repeatedDecision shouldBe SessionBoundaryDecision.NoOp
        detector.getActiveSession(packageName)?.accumulatedPlayTimeMs shouldBe 30_000L
    }

    @Test
    fun `STATE_PAUSED when no session exists is NoOp`() {
        val decision = detector.onPlaybackStateChanged(packageName, statePaused, t0)
        decision shouldBe SessionBoundaryDecision.NoOp
        detector.getActiveSession(packageName).shouldBeNull()
    }

    @Test
    fun `resuming STATE_PLAYING after pause continues same session`() {
        detector.onPlaybackStateChanged(packageName, statePlaying, t0)

        val t1 = t0.plusSeconds(20)
        detector.onPlaybackStateChanged(packageName, statePaused, t1)

        val t2 = t1.plusSeconds(40) // paused for 40s
        val resumeDecision = detector.onPlaybackStateChanged(packageName, statePlaying, t2)

        resumeDecision shouldBe SessionBoundaryDecision.NoOp
        val active = detector.getActiveSession(packageName)
        active.shouldNotBeNull()
        active.sessionId shouldBe "$packageName:${t0.toEpochMilli()}"
        active.sessionStartedAt shouldBe t0
        active.lastActivePlayStartedAt shouldBe t2
        active.accumulatedPlayTimeMs shouldBe 20_000L
        active.lastState shouldBe 3
    }

    @Test
    fun `STATE_STOPPED closes session calculating effective duration without pause time`() {
        // Play 20s
        detector.onPlaybackStateChanged(packageName, statePlaying, t0)
        val t1 = t0.plusSeconds(20)
        detector.onPlaybackStateChanged(packageName, statePaused, t1)

        // Pause for 100s, then resume
        val t2 = t1.plusSeconds(100)
        detector.onPlaybackStateChanged(packageName, statePlaying, t2)

        // Play for 10s, then stop
        val t3 = t2.plusSeconds(10)
        val decision = detector.onPlaybackStateChanged(packageName, stateStopped, t3)

        decision.shouldBeInstanceOf<SessionBoundaryDecision.CloseSession>()
        val payload = decision.completedSession
        payload.packageName shouldBe packageName
        payload.sessionStartedAtEpochMs shouldBe t0.toEpochMilli()
        payload.sessionEndedAtEpochMs shouldBe t3.toEpochMilli()
        // Effective duration = 20s + 10s = 30s (total wall time is 130s)
        payload.effectiveDurationMs shouldBe 30_000L
        payload.isMicroSession shouldBe false
        payload.endReason shouldBe MediaSessionEndReason.STATE_STOPPED

        detector.getActiveSession(packageName).shouldBeNull()
    }

    @Test
    fun `STATE_NONE closes session with STATE_NONE endReason`() {
        detector.onPlaybackStateChanged(packageName, statePlaying, t0)
        val t1 = t0.plusSeconds(10)
        val decision = detector.onPlaybackStateChanged(packageName, stateNone, t1)

        decision.shouldBeInstanceOf<SessionBoundaryDecision.CloseSession>()
        decision.completedSession.endReason shouldBe MediaSessionEndReason.STATE_NONE
        decision.completedSession.effectiveDurationMs shouldBe 10_000L
        detector.getActiveSession(packageName).shouldBeNull()
    }

    @Test
    fun `short session under 5000ms closed with STATE_STOPPED marked as isMicroSession`() {
        detector.onPlaybackStateChanged(packageName, statePlaying, t0)
        val t1 = t0.plusSeconds(3)
        val decision = detector.onPlaybackStateChanged(packageName, stateStopped, t1)

        decision.shouldBeInstanceOf<SessionBoundaryDecision.CloseSession>()
        decision.completedSession.effectiveDurationMs shouldBe 3_000L
        decision.completedSession.isMicroSession shouldBe true
    }

    @Test
    fun `metadata change during playback triggers SwitchTrack closing old and opening new session`() {
        val meta1 = MediaMetadataSnapshot(
            title = "Track 1",
            artist = "Artist A",
            album = "Album A",
            durationMs = 180_000L
        )
        detector.onMetadataChanged(packageName, meta1, t0)
        detector.onPlaybackStateChanged(packageName, statePlaying, t0)

        // Play for 45s, then metadata changes to Track 2
        val t1 = t0.plusSeconds(45)
        val meta2 = MediaMetadataSnapshot(
            title = "Track 2",
            artist = "Artist B",
            album = "Album B",
            durationMs = 210_000L
        )
        val decision = detector.onMetadataChanged(packageName, meta2, t1)

        decision.shouldBeInstanceOf<SessionBoundaryDecision.SwitchTrack>()
        val closed = decision.previousSessionToClose
        closed.trackTitle shouldBe "Track 1"
        closed.artist shouldBe "Artist A"
        closed.effectiveDurationMs shouldBe 45_000L
        closed.endReason shouldBe MediaSessionEndReason.TRACK_CHANGED
        closed.sessionEndedAtEpochMs shouldBe t1.toEpochMilli()

        val opened = decision.newSessionToOpen
        opened.metadata.title shouldBe "Track 2"
        opened.metadata.artist shouldBe "Artist B"
        opened.sessionStartedAt shouldBe t1
        opened.lastActivePlayStartedAt shouldBe t1
        opened.accumulatedPlayTimeMs shouldBe 0L

        detector.getActiveSession(packageName) shouldBe opened
    }

    @Test
    fun `metadata change with identical title and artist updates metadata and returns NoOp`() {
        val meta1 = MediaMetadataSnapshot(
            title = "Song",
            artist = "Artist",
            album = null,
            durationMs = 200_000L
        )
        detector.onMetadataChanged(packageName, meta1, t0)
        detector.onPlaybackStateChanged(packageName, statePlaying, t0)

        // Album info is loaded later for the same track
        val t1 = t0.plusSeconds(5)
        val metaUpdated = MediaMetadataSnapshot(
            title = "Song",
            artist = "Artist",
            album = "Newly Fetched Album",
            durationMs = 200_000L
        )
        val decision = detector.onMetadataChanged(packageName, metaUpdated, t1)

        decision shouldBe SessionBoundaryDecision.NoOp
        val active = detector.getActiveSession(packageName)
        active.shouldNotBeNull()
        active.metadata.album shouldBe "Newly Fetched Album"
    }

    @Test
    fun `checkHeartbeats force-closes sessions in STATE_PLAYING exceeding 5 minute silence timeout`() {
        detector.onPlaybackStateChanged(packageName, statePlaying, t0)

        // At 4 minutes 59 seconds: not expired
        val tBeforeTimeout = t0.plusSeconds(299)
        detector.checkHeartbeats(tBeforeTimeout).shouldBeEmpty()
        detector.getActiveSession(packageName).shouldNotBeNull()

        // At 5 minutes + 1 second (301 seconds): expired
        val tAfterTimeout = t0.plusSeconds(301)
        val expired = detector.checkHeartbeats(tAfterTimeout)

        expired shouldHaveSize 1
        val closed = expired[0].completedSession
        closed.packageName shouldBe packageName
        closed.endReason shouldBe MediaSessionEndReason.HEARTBEAT_TIMEOUT
        closed.lastError shouldBe "heartbeat_timeout"
        // Session ended timestamp capped to lastEventAt (t0)
        closed.sessionEndedAtEpochMs shouldBe t0.toEpochMilli()
        closed.effectiveDurationMs shouldBe 0L
        closed.isMicroSession shouldBe true

        detector.getActiveSession(packageName).shouldBeNull()
    }

    @Test
    fun `checkHeartbeats does not close paused sessions even after long silence`() {
        detector.onPlaybackStateChanged(packageName, statePlaying, t0)
        val t1 = t0.plusSeconds(30)
        detector.onPlaybackStateChanged(packageName, statePaused, t1)

        // Silence for 2 hours while paused
        val tLater = t1.plusSeconds(7200)
        val expired = detector.checkHeartbeats(tLater)

        expired.shouldBeEmpty()
        detector.getActiveSession(packageName).shouldNotBeNull()
        detector.getActiveSession(packageName)?.lastState shouldBe 2 // STATE_PAUSED
    }

    @Test
    fun `onControllerDisconnected closes active session with CONTROLLER_DISCONNECTED`() {
        detector.onPlaybackStateChanged(packageName, statePlaying, t0)
        val t1 = t0.plusSeconds(50)

        val decision = detector.onControllerDisconnected(packageName, t1)

        decision.shouldBeInstanceOf<SessionBoundaryDecision.CloseSession>()
        val payload = decision.completedSession
        payload.packageName shouldBe packageName
        payload.effectiveDurationMs shouldBe 50_000L
        payload.endReason shouldBe MediaSessionEndReason.CONTROLLER_DISCONNECTED
        payload.sessionEndedAtEpochMs shouldBe t1.toEpochMilli()

        detector.getActiveSession(packageName).shouldBeNull()
    }

    @Test
    fun `onControllerDisconnected when no session active returns NoOp`() {
        val decision = detector.onControllerDisconnected("unknown.package", t0)
        decision shouldBe SessionBoundaryDecision.NoOp
    }
}
