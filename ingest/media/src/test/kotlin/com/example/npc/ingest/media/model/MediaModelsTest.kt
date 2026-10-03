package com.example.npc.ingest.media.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Instant

class MediaModelsTest {

    // --- MediaMetadataSnapshot Tests ---

    @Test
    fun `MediaMetadataSnapshot valid instance with all fields populated`() {
        val snapshot = MediaMetadataSnapshot(
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            durationMs = 230_000L
        )

        snapshot.title shouldBe "Starboy"
        snapshot.artist shouldBe "The Weeknd"
        snapshot.album shouldBe "Starboy"
        snapshot.durationMs shouldBe 230_000L
    }

    @Test
    fun `MediaMetadataSnapshot allows null strings and negative one duration`() {
        val snapshot = MediaMetadataSnapshot(
            title = null,
            artist = null,
            album = null,
            durationMs = -1L
        )

        snapshot.title shouldBe null
        snapshot.artist shouldBe null
        snapshot.album shouldBe null
        snapshot.durationMs shouldBe -1L
    }

    @Test
    fun `MediaMetadataSnapshot EMPTY companion constant satisfies invariants`() {
        val empty = MediaMetadataSnapshot.EMPTY

        empty.title shouldBe null
        empty.artist shouldBe null
        empty.album shouldBe null
        empty.durationMs shouldBe MediaMetadataSnapshot.DURATION_UNKNOWN
        MediaMetadataSnapshot.DURATION_UNKNOWN shouldBe -1L
    }

    @Test
    fun `MediaMetadataSnapshot rejects duration less than negative one`() {
        shouldThrow<IllegalArgumentException> {
            MediaMetadataSnapshot(
                title = "Title",
                artist = "Artist",
                album = "Album",
                durationMs = -2L
            )
        }
    }

    @Test
    fun `MediaMetadataSnapshot rejects blank title`() {
        shouldThrow<IllegalArgumentException> {
            MediaMetadataSnapshot(
                title = "   ",
                artist = "Artist",
                album = "Album",
                durationMs = 1000L
            )
        }
        shouldThrow<IllegalArgumentException> {
            MediaMetadataSnapshot(
                title = "",
                artist = "Artist",
                album = "Album",
                durationMs = 1000L
            )
        }
    }

    @Test
    fun `MediaMetadataSnapshot rejects blank artist`() {
        shouldThrow<IllegalArgumentException> {
            MediaMetadataSnapshot(
                title = "Title",
                artist = "   ",
                album = "Album",
                durationMs = 1000L
            )
        }
        shouldThrow<IllegalArgumentException> {
            MediaMetadataSnapshot(
                title = "Title",
                artist = "",
                album = "Album",
                durationMs = 1000L
            )
        }
    }

    @Test
    fun `MediaMetadataSnapshot rejects blank album`() {
        shouldThrow<IllegalArgumentException> {
            MediaMetadataSnapshot(
                title = "Title",
                artist = "Artist",
                album = "   ",
                durationMs = 1000L
            )
        }
        shouldThrow<IllegalArgumentException> {
            MediaMetadataSnapshot(
                title = "Title",
                artist = "Artist",
                album = "",
                durationMs = 1000L
            )
        }
    }

    // --- MediaPlaybackSnapshot Tests ---

    @Test
    fun `MediaPlaybackSnapshot valid instance`() {
        val snapshot = MediaPlaybackSnapshot(
            state = 3, // PlaybackStateCompat.STATE_PLAYING
            positionMs = 15_000L,
            playbackSpeed = 1.0f,
            updateTimeEpochMs = 1_700_000_000_000L
        )

        snapshot.state shouldBe 3
        snapshot.positionMs shouldBe 15_000L
        snapshot.playbackSpeed shouldBe 1.0f
        snapshot.updateTimeEpochMs shouldBe 1_700_000_000_000L
    }

    @Test
    fun `MediaPlaybackSnapshot EMPTY companion object values`() {
        val empty = MediaPlaybackSnapshot.EMPTY

        empty.state shouldBe 0
        empty.positionMs shouldBe 0L
        empty.playbackSpeed shouldBe 1.0f
        empty.updateTimeEpochMs shouldBe 0L
    }

    @Test
    fun `MediaPlaybackSnapshot rejects negative positionMs`() {
        shouldThrow<IllegalArgumentException> {
            MediaPlaybackSnapshot(
                state = 3,
                positionMs = -1L,
                playbackSpeed = 1.0f,
                updateTimeEpochMs = 1000L
            )
        }
    }

    @Test
    fun `MediaPlaybackSnapshot rejects negative playbackSpeed`() {
        shouldThrow<IllegalArgumentException> {
            MediaPlaybackSnapshot(
                state = 3,
                positionMs = 0L,
                playbackSpeed = -0.5f,
                updateTimeEpochMs = 1000L
            )
        }
    }

    @Test
    fun `MediaPlaybackSnapshot rejects negative updateTimeEpochMs`() {
        shouldThrow<IllegalArgumentException> {
            MediaPlaybackSnapshot(
                state = 3,
                positionMs = 0L,
                playbackSpeed = 1.0f,
                updateTimeEpochMs = -1L
            )
        }
    }

    // --- ActiveMediaSession Tests ---

    @Test
    fun `ActiveMediaSession valid instance with lastActivePlayStartedAt equal to sessionStartedAt`() {
        val now = Instant.parse("2026-09-26T12:00:00Z")
        val session = ActiveMediaSession(
            sessionId = "com.spotify.music:1790424000000",
            packageName = "com.spotify.music",
            metadata = MediaMetadataSnapshot.EMPTY,
            sessionStartedAt = now,
            lastActivePlayStartedAt = now,
            accumulatedPlayTimeMs = 0L,
            lastState = 3,
            lastEventAt = now
        )

        session.sessionId shouldBe "com.spotify.music:1790424000000"
        session.packageName shouldBe "com.spotify.music"
        session.accumulatedPlayTimeMs shouldBe 0L
        session.lastActivePlayStartedAt shouldBe now
    }

    @Test
    fun `ActiveMediaSession allows null lastActivePlayStartedAt when paused`() {
        val start = Instant.parse("2026-09-26T12:00:00Z")
        val later = Instant.parse("2026-09-26T12:01:00Z")
        val session = ActiveMediaSession(
            sessionId = "com.spotify.music:1790424000000",
            packageName = "com.spotify.music",
            metadata = MediaMetadataSnapshot.EMPTY,
            sessionStartedAt = start,
            lastActivePlayStartedAt = null,
            accumulatedPlayTimeMs = 60_000L,
            lastState = 2, // STATE_PAUSED
            lastEventAt = later
        )

        session.lastActivePlayStartedAt shouldBe null
        session.accumulatedPlayTimeMs shouldBe 60_000L
    }

    @Test
    fun `ActiveMediaSession allows lastActivePlayStartedAt after sessionStartedAt`() {
        val start = Instant.parse("2026-09-26T12:00:00Z")
        val resume = Instant.parse("2026-09-26T12:05:00Z")
        val session = ActiveMediaSession(
            sessionId = "com.spotify.music:1790424000000",
            packageName = "com.spotify.music",
            metadata = MediaMetadataSnapshot.EMPTY,
            sessionStartedAt = start,
            lastActivePlayStartedAt = resume,
            accumulatedPlayTimeMs = 120_000L,
            lastState = 3,
            lastEventAt = resume
        )

        session.lastActivePlayStartedAt shouldBe resume
    }

    @Test
    fun `ActiveMediaSession rejects blank sessionId`() {
        val now = Instant.now()
        shouldThrow<IllegalArgumentException> {
            ActiveMediaSession(
                sessionId = "   ",
                packageName = "com.spotify.music",
                metadata = MediaMetadataSnapshot.EMPTY,
                sessionStartedAt = now,
                lastActivePlayStartedAt = now,
                accumulatedPlayTimeMs = 0L,
                lastState = 3,
                lastEventAt = now
            )
        }
    }

    @Test
    fun `ActiveMediaSession rejects blank packageName`() {
        val now = Instant.now()
        shouldThrow<IllegalArgumentException> {
            ActiveMediaSession(
                sessionId = "session-1",
                packageName = "   ",
                metadata = MediaMetadataSnapshot.EMPTY,
                sessionStartedAt = now,
                lastActivePlayStartedAt = now,
                accumulatedPlayTimeMs = 0L,
                lastState = 3,
                lastEventAt = now
            )
        }
    }

    @Test
    fun `ActiveMediaSession rejects negative accumulatedPlayTimeMs`() {
        val now = Instant.now()
        shouldThrow<IllegalArgumentException> {
            ActiveMediaSession(
                sessionId = "session-1",
                packageName = "com.spotify.music",
                metadata = MediaMetadataSnapshot.EMPTY,
                sessionStartedAt = now,
                lastActivePlayStartedAt = now,
                accumulatedPlayTimeMs = -1L,
                lastState = 3,
                lastEventAt = now
            )
        }
    }

    @Test
    fun `ActiveMediaSession rejects lastActivePlayStartedAt earlier than sessionStartedAt`() {
        val start = Instant.parse("2026-09-26T12:00:00Z")
        val earlier = Instant.parse("2026-09-26T11:59:59Z")
        shouldThrow<IllegalArgumentException> {
            ActiveMediaSession(
                sessionId = "session-1",
                packageName = "com.spotify.music",
                metadata = MediaMetadataSnapshot.EMPTY,
                sessionStartedAt = start,
                lastActivePlayStartedAt = earlier,
                accumulatedPlayTimeMs = 0L,
                lastState = 3,
                lastEventAt = start
            )
        }
    }

    // --- MediaSessionPayload Tests ---

    @Test
    fun `MediaSessionPayload valid full session`() {
        val payload = MediaSessionPayload(
            packageName = "com.spotify.music",
            trackTitle = "Song Title",
            artist = "Artist Name",
            album = "Album Name",
            trackDurationMs = 180_000L,
            sessionStartedAtEpochMs = 1_000_000L,
            sessionEndedAtEpochMs = 1_060_000L,
            effectiveDurationMs = 50_000L,
            isMicroSession = false,
            endReason = MediaSessionEndReason.STATE_STOPPED,
            lastError = null
        )

        payload.packageName shouldBe "com.spotify.music"
        payload.effectiveDurationMs shouldBe 50_000L
        payload.isMicroSession shouldBe false
        payload.endReason shouldBe MediaSessionEndReason.STATE_STOPPED
    }

    @Test
    fun `MediaSessionPayload valid micro session under 5000ms threshold`() {
        val payload = MediaSessionPayload(
            packageName = "com.spotify.music",
            trackTitle = "Short Track",
            artist = null,
            album = null,
            trackDurationMs = 60_000L,
            sessionStartedAtEpochMs = 1_000_000L,
            sessionEndedAtEpochMs = 1_004_000L,
            effectiveDurationMs = 4_000L,
            isMicroSession = true,
            endReason = MediaSessionEndReason.TRACK_CHANGED,
            lastError = null
        )

        payload.isMicroSession shouldBe true
        payload.effectiveDurationMs shouldBe 4_000L
        MediaSessionPayload.MICRO_SESSION_THRESHOLD_MS shouldBe 5000L
    }

    @Test
    fun `MediaSessionPayload exactly 5000ms is not micro session`() {
        val payload = MediaSessionPayload(
            packageName = "com.spotify.music",
            trackTitle = null,
            artist = null,
            album = null,
            trackDurationMs = -1L,
            sessionStartedAtEpochMs = 1_000_000L,
            sessionEndedAtEpochMs = 1_005_000L,
            effectiveDurationMs = 5_000L,
            isMicroSession = false,
            endReason = MediaSessionEndReason.STATE_STOPPED,
            lastError = null
        )

        payload.isMicroSession shouldBe false
    }

    @Test
    fun `MediaSessionPayload rejects sessionEndedAt earlier than sessionStartedAt`() {
        shouldThrow<IllegalArgumentException> {
            MediaSessionPayload(
                packageName = "com.spotify.music",
                trackTitle = null,
                artist = null,
                album = null,
                trackDurationMs = 0L,
                sessionStartedAtEpochMs = 2_000L,
                sessionEndedAtEpochMs = 1_000L,
                effectiveDurationMs = 0L,
                isMicroSession = true,
                endReason = MediaSessionEndReason.STATE_STOPPED,
                lastError = null
            )
        }
    }

    @Test
    fun `MediaSessionPayload rejects negative effectiveDurationMs`() {
        shouldThrow<IllegalArgumentException> {
            MediaSessionPayload(
                packageName = "com.spotify.music",
                trackTitle = null,
                artist = null,
                album = null,
                trackDurationMs = 0L,
                sessionStartedAtEpochMs = 1_000L,
                sessionEndedAtEpochMs = 2_000L,
                effectiveDurationMs = -1L,
                isMicroSession = true,
                endReason = MediaSessionEndReason.STATE_STOPPED,
                lastError = null
            )
        }
    }

    @Test
    fun `MediaSessionPayload rejects effectiveDurationMs exceeding session wall time`() {
        shouldThrow<IllegalArgumentException> {
            MediaSessionPayload(
                packageName = "com.spotify.music",
                trackTitle = null,
                artist = null,
                album = null,
                trackDurationMs = 100_000L,
                sessionStartedAtEpochMs = 10_000L,
                sessionEndedAtEpochMs = 20_000L, // wall time is 10_000ms
                effectiveDurationMs = 15_000L, // exceeds wall time!
                isMicroSession = false,
                endReason = MediaSessionEndReason.STATE_STOPPED,
                lastError = null
            )
        }
    }

    @Test
    fun `MediaSessionPayload rejects isMicroSession mismatch when effectiveDurationMs less than 5000ms but isMicroSession is false`() {
        shouldThrow<IllegalArgumentException> {
            MediaSessionPayload(
                packageName = "com.spotify.music",
                trackTitle = null,
                artist = null,
                album = null,
                trackDurationMs = 100_000L,
                sessionStartedAtEpochMs = 10_000L,
                sessionEndedAtEpochMs = 20_000L,
                effectiveDurationMs = 4_999L,
                isMicroSession = false, // INVALID: should be true
                endReason = MediaSessionEndReason.STATE_STOPPED,
                lastError = null
            )
        }
    }

    @Test
    fun `MediaSessionPayload rejects isMicroSession mismatch when effectiveDurationMs at least 5000ms but isMicroSession is true`() {
        shouldThrow<IllegalArgumentException> {
            MediaSessionPayload(
                packageName = "com.spotify.music",
                trackTitle = null,
                artist = null,
                album = null,
                trackDurationMs = 100_000L,
                sessionStartedAtEpochMs = 10_000L,
                sessionEndedAtEpochMs = 20_000L,
                effectiveDurationMs = 5_000L,
                isMicroSession = true, // INVALID: should be false
                endReason = MediaSessionEndReason.STATE_STOPPED,
                lastError = null
            )
        }
    }
}
