package com.example.npc.ingest.media.mapper

import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.ThreadKey
import com.example.npc.core.model.normalize.EventNormalizer
import com.example.npc.ingest.media.model.ActiveMediaSession
import com.example.npc.ingest.media.model.MediaMetadataSnapshot
import com.example.npc.ingest.media.model.MediaSessionEndReason
import com.example.npc.ingest.media.model.MediaSessionPayload
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Instant

class MediaPayloadMapperTest {

    @Test
    fun `toPayloadJson and fromPayloadJson round-trip with all fields populated`() {
        val payload = MediaSessionPayload(
            packageName = "com.spotify.music",
            trackTitle = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            trackDurationMs = 200_000L,
            sessionStartedAtEpochMs = 1_700_000_000_000L,
            sessionEndedAtEpochMs = 1_700_000_180_000L,
            effectiveDurationMs = 180_000L,
            isMicroSession = false,
            endReason = MediaSessionEndReason.STATE_STOPPED,
            lastError = null
        )

        val json = MediaPayloadMapper.toPayloadJson(payload)
        val deserialized = MediaPayloadMapper.fromPayloadJson(json)

        deserialized shouldBe payload
        json.contains("\"packageName\":\"com.spotify.music\"") shouldBe true
        json.contains("\"trackTitle\":\"Blinding Lights\"") shouldBe true
        json.contains("\"artist\":\"The Weeknd\"") shouldBe true
        json.contains("\"album\":\"After Hours\"") shouldBe true
        json.contains("\"trackDurationMs\":200000") shouldBe true
        json.contains("\"sessionStartedAtEpochMs\":1700000000000") shouldBe true
        json.contains("\"sessionEndedAtEpochMs\":1700000180000") shouldBe true
        json.contains("\"effectiveDurationMs\":180000") shouldBe true
        json.contains("\"isMicroSession\":false") shouldBe true
        json.contains("\"endReason\":\"STATE_STOPPED\"") shouldBe true
        json.contains("\"lastError\":null") shouldBe true
    }

    @Test
    fun `toPayloadJson and fromPayloadJson round-trip with nullable fields and lastError`() {
        val payload = MediaSessionPayload(
            packageName = "org.videolan.vlc",
            trackTitle = null,
            artist = null,
            album = null,
            trackDurationMs = -1L,
            sessionStartedAtEpochMs = 1_700_000_000_000L,
            sessionEndedAtEpochMs = 1_700_000_300_000L,
            effectiveDurationMs = 250_000L,
            isMicroSession = false,
            endReason = MediaSessionEndReason.HEARTBEAT_TIMEOUT,
            lastError = "heartbeat_timeout"
        )

        val json = MediaPayloadMapper.toPayloadJson(payload)
        val deserialized = MediaPayloadMapper.fromPayloadJson(json)

        deserialized shouldBe payload
        json.contains("\"trackTitle\":null") shouldBe true
        json.contains("\"artist\":null") shouldBe true
        json.contains("\"album\":null") shouldBe true
        json.contains("\"lastError\":\"heartbeat_timeout\"") shouldBe true
    }

    @Test
    fun `toPayloadJson properly escapes special characters and preserves them on round-trip`() {
        val payload = MediaSessionPayload(
            packageName = "com.player.app",
            trackTitle = "Track with \"quotes\" and \\backslash and \n newline and \t tab",
            artist = "Artist \"The Best\" \\ AC/DC",
            album = "Album: \r Special \t Chars",
            trackDurationMs = 120_000L,
            sessionStartedAtEpochMs = 1_700_000_000_000L,
            sessionEndedAtEpochMs = 1_700_000_060_000L,
            effectiveDurationMs = 60_000L,
            isMicroSession = false,
            endReason = MediaSessionEndReason.APP_RESTART,
            lastError = "error with \"quotes\" and \\backslash"
        )

        val json = MediaPayloadMapper.toPayloadJson(payload)
        val deserialized = MediaPayloadMapper.fromPayloadJson(json)

        deserialized.trackTitle shouldBe payload.trackTitle
        deserialized.artist shouldBe payload.artist
        deserialized.album shouldBe payload.album
        deserialized.lastError shouldBe payload.lastError
        deserialized shouldBe payload
    }

    @Test
    fun `toRawEvent generates RawEvent with correct DeduplicationKey and fields`() {
        val start = Instant.parse("2026-09-26T10:00:00Z")
        val end = Instant.parse("2026-09-26T10:03:00Z") // 180 seconds later
        val session = ActiveMediaSession(
            sessionId = "com.spotify.music:1790416800000",
            packageName = "com.spotify.music",
            metadata = MediaMetadataSnapshot(
                title = "Starman",
                artist = "David Bowie",
                album = "Ziggy Stardust",
                durationMs = 250_000L
            ),
            sessionStartedAt = start,
            lastActivePlayStartedAt = start,
            accumulatedPlayTimeMs = 0L,
            lastState = 3,
            lastEventAt = end
        )

        val rawEvent = MediaPayloadMapper.toRawEvent(
            session = session,
            seq = 42L,
            endReason = MediaSessionEndReason.STATE_STOPPED,
            endedAt = end
        )

        rawEvent.seq shouldBe 42L
        rawEvent.source shouldBe SourceId.MEDIA
        rawEvent.packageName shouldBe "com.spotify.music"
        rawEvent.receivedAt shouldBe end

        val expectedDedupKey = EventNormalizer.computeDeduplicationKey(
            SourceId.MEDIA,
            session.packageName,
            rawEvent.payloadJson
        )
        rawEvent.hash shouldBe expectedDedupKey

        val payload = MediaPayloadMapper.fromPayloadJson(rawEvent.payloadJson)
        payload.effectiveDurationMs shouldBe 180_000L
        payload.isMicroSession shouldBe false
        payload.endReason shouldBe MediaSessionEndReason.STATE_STOPPED
    }

    @Test
    fun `toDomainEvent returns null for micro-session shorter than 5000ms threshold`() {
        val start = Instant.parse("2026-09-26T10:00:00Z")
        val end = Instant.parse("2026-09-26T10:00:04Z") // 4 seconds total
        val session = ActiveMediaSession(
            sessionId = "com.spotify.music:1790416800000",
            packageName = "com.spotify.music",
            metadata = MediaMetadataSnapshot(
                title = "Short Skip",
                artist = "Artist",
                album = "Album",
                durationMs = 200_000L
            ),
            sessionStartedAt = start,
            lastActivePlayStartedAt = start,
            accumulatedPlayTimeMs = 0L,
            lastState = 3,
            lastEventAt = end
        )

        val rawEvent = MediaPayloadMapper.toRawEvent(
            session = session,
            seq = 1L,
            endReason = MediaSessionEndReason.TRACK_CHANGED,
            endedAt = end
        )

        val domainEvent = MediaPayloadMapper.toDomainEvent(
            rawEvent = rawEvent,
            session = session,
            endedAt = end
        )

        domainEvent shouldBe null
    }

    @Test
    fun `toDomainEvent returns null for paused micro-session shorter than 5000ms threshold`() {
        val start = Instant.parse("2026-09-26T10:00:00Z")
        val end = Instant.parse("2026-09-26T10:00:30Z")
        val session = ActiveMediaSession(
            sessionId = "com.spotify.music:1790416800000",
            packageName = "com.spotify.music",
            metadata = MediaMetadataSnapshot(
                title = "Short Play Then Paused",
                artist = "Artist",
                album = "Album",
                durationMs = 200_000L
            ),
            sessionStartedAt = start,
            lastActivePlayStartedAt = null, // Currently paused
            accumulatedPlayTimeMs = 4_999L, // exactly 4999 ms
            lastState = 2,
            lastEventAt = end
        )

        val rawEvent = MediaPayloadMapper.toRawEvent(
            session = session,
            seq = 2L,
            endReason = MediaSessionEndReason.STATE_STOPPED,
            endedAt = end
        )

        val domainEvent = MediaPayloadMapper.toDomainEvent(
            rawEvent = rawEvent,
            session = session,
            endedAt = end
        )

        domainEvent shouldBe null
    }

    @Test
    fun `toDomainEvent generates normalized Event with title and artist when duration is at least 5000ms`() {
        val start = Instant.parse("2026-09-26T10:00:00Z")
        val end = Instant.parse("2026-09-26T10:00:05Z") // Exactly 5000ms
        val session = ActiveMediaSession(
            sessionId = "com.spotify.music:1790416800000",
            packageName = "com.spotify.music",
            metadata = MediaMetadataSnapshot(
                title = "Heroes",
                artist = "David Bowie",
                album = "Heroes",
                durationMs = 360_000L
            ),
            sessionStartedAt = start,
            lastActivePlayStartedAt = start,
            accumulatedPlayTimeMs = 0L,
            lastState = 3,
            lastEventAt = end
        )

        val rawEvent = RawEvent(
            id = 100L,
            seq = 10L,
            source = SourceId.MEDIA,
            packageName = session.packageName,
            receivedAt = end,
            payloadJson = "{}",
            hash = EventNormalizer.computeDeduplicationKey(SourceId.MEDIA, session.packageName, "{}")
        )

        val domainEvent = MediaPayloadMapper.toDomainEvent(
            rawEvent = rawEvent,
            session = session,
            endedAt = end
        )

        domainEvent shouldNotBe null
        domainEvent!!.title shouldBe "Heroes"
        domainEvent.text shouldBe "David Bowie - Heroes"
        domainEvent.threadKey shouldBe ThreadKey("${session.packageName}:media:${session.sessionId}")
        domainEvent.rawId shouldBe 100L
    }

    @Test
    fun `toDomainEvent handles null title and artist gracefully`() {
        val start = Instant.parse("2026-09-26T10:00:00Z")
        val end = Instant.parse("2026-09-26T10:01:00Z") // 60 seconds
        val session = ActiveMediaSession(
            sessionId = "com.radio.app:1790416800000",
            packageName = "com.radio.app",
            metadata = MediaMetadataSnapshot(
                title = null,
                artist = null,
                album = null,
                durationMs = -1L
            ),
            sessionStartedAt = start,
            lastActivePlayStartedAt = start,
            accumulatedPlayTimeMs = 0L,
            lastState = 3,
            lastEventAt = end
        )

        val rawEvent = RawEvent(
            id = 200L,
            seq = 20L,
            source = SourceId.MEDIA,
            packageName = session.packageName,
            receivedAt = end,
            payloadJson = "{}",
            hash = EventNormalizer.computeDeduplicationKey(SourceId.MEDIA, session.packageName, "{}")
        )

        val domainEvent = MediaPayloadMapper.toDomainEvent(
            rawEvent = rawEvent,
            session = session,
            endedAt = end
        )

        domainEvent shouldNotBe null
        domainEvent!!.title shouldBe "Unknown Track"
        domainEvent.text shouldBe "Unknown Track"
    }

    @Test
    fun `extractMetadata cleans blank strings to null and normalizes duration`() {
        val meta1 = MediaPayloadMapper.extractMetadata(
            titleStr = "   ",
            artistStr = "",
            albumStr = "\t\n",
            durationMs = -5L
        )

        meta1.title shouldBe null
        meta1.artist shouldBe null
        meta1.album shouldBe null
        meta1.durationMs shouldBe MediaMetadataSnapshot.DURATION_UNKNOWN

        val meta2 = MediaPayloadMapper.extractMetadata(
            titleStr = "Valid Title",
            artistStr = "Valid Artist",
            albumStr = "Valid Album",
            durationMs = 120_000L
        )

        meta2.title shouldBe "Valid Title"
        meta2.artist shouldBe "Valid Artist"
        meta2.album shouldBe "Valid Album"
        meta2.durationMs shouldBe 120_000L
    }

    @Test
    fun `extractPlaybackState clamps negative position, speed, and time to zero`() {
        val state = MediaPayloadMapper.extractPlaybackState(
            state = 3,
            positionMs = -500L,
            playbackSpeed = -1.0f,
            updateTimeEpochMs = -100L
        )

        state.state shouldBe 3
        state.positionMs shouldBe 0L
        state.playbackSpeed shouldBe 0.0f
        state.updateTimeEpochMs shouldBe 0L
    }
}
