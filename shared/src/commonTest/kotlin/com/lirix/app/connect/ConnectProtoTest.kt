package com.lirix.app.connect

import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.DeviceType
import com.lirix.app.connect.model.asBytes
import com.lirix.app.connect.proto.ByeReason
import com.lirix.app.connect.proto.Capabilities
import com.lirix.app.connect.proto.CastCodec
import com.lirix.app.connect.proto.ConnectMessageCodec
import com.lirix.app.connect.proto.DeviceRole
import com.lirix.app.connect.proto.Envelope
import com.lirix.app.connect.proto.ErrorCode
import com.lirix.app.connect.proto.HandoffFailure
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.proto.LyricsRef
import com.lirix.app.connect.proto.MediaAction
import com.lirix.app.connect.proto.QueueSnapshot
import com.lirix.app.connect.proto.RejectReason
import com.lirix.app.connect.proto.RepeatMode
import com.lirix.app.connect.proto.SessionSnapshot
import com.lirix.app.connect.proto.TrackRef
import com.lirix.app.connect.session.ConnectSessionManager
import com.lirix.app.connect.session.StaleEpochException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectProtoTest {

    private fun sampleTrackRef(): TrackRef = TrackRef(
        provider = "local_storage",
        trackId = "track-42",
        uri = "content://media/external/audio/media/42",
        contentHash = "a1b2c3d4e5f6",
        title = "Bohemian Rhapsody",
        artist = "Queen",
        album = "A Night at the Opera"
    )

    private fun sampleLyricsRef(): LyricsRef = LyricsRef(
        queryKey = "queen-bohemian-rhapsody",
        lrc = "[00:15.00]Is this the real life?\n[00:18.00]Is this just fantasy?",
        syncOffsetMs = 120L
    )

    // ── 1. Round-trip serialization for all message types ───────────────────

    @Test
    fun testAllMessageTypesRoundTripInEnvelope() {
        val messages: List<LirixConnectMsg> = listOf(
            LirixConnectMsg.Hello(
                supportedVersions = listOf(1),
                deviceName = "Pixel 8",
                deviceType = DeviceType.ANDROID_PHONE,
                capabilities = Capabilities(canPlayLocal = true, canReceiveCast = true, canCaptureAudio = true)
            ),
            LirixConnectMsg.Welcome(
                chosenVersion = 1,
                deviceName = "Desktop PC",
                capabilities = Capabilities(canPlayLocal = true, canReceiveCast = false, canCaptureAudio = true),
                activeSession = SessionSnapshot(
                    sessionId = "sess-1",
                    epoch = 3,
                    track = sampleTrackRef(),
                    isPlaying = true,
                    positionMs = 45000L
                )
            ),
            LirixConnectMsg.Reject(
                reason = RejectReason.UNTRUSTED_DEVICE,
                message = "Device not in trust store"
            ),
            LirixConnectMsg.Error(
                code = ErrorCode.STALE_EPOCH,
                message = "Epoch mismatch"
            ),
            LirixConnectMsg.Bye(
                reason = ByeReason.NORMAL
            ),
            LirixConnectMsg.PairRequest(
                nonce = ByteArray(16) { 0x01 }.asBytes(),
                proof = ByteArray(32) { 0x02 }.asBytes(),
                deviceName = "Client Phone",
                deviceType = DeviceType.ANDROID_PHONE
            ),
            LirixConnectMsg.PairResult(
                ok = true,
                verifyCode = "482910"
            ),
            LirixConnectMsg.Ping(t0 = 1000L),
            LirixConnectMsg.Pong(t0 = 1000L, t1 = 1010L, t2 = 1015L),
            LirixConnectMsg.DeviceHeartbeat(
                isAvailable = true,
                batteryPct = 85,
                nowPlaying = true
            ),
            LirixConnectMsg.HandoffRequest(
                sessionId = "sess-handoff-100",
                newEpoch = 12,
                track = sampleTrackRef(),
                queue = QueueSnapshot(items = listOf(sampleTrackRef()), index = 0, shuffle = false, repeat = RepeatMode.ALL),
                positionMs = 60000L,
                positionAtMonoMs = 1234567L,
                durationMs = 354000L,
                playbackRate = 1.0f,
                isPlaying = true,
                volume = 0.85f,
                lyrics = sampleLyricsRef()
            ),
            LirixConnectMsg.HandoffAck(
                accepted = true,
                reason = null,
                suggestCast = false,
                startedAtMonoMs = 1234600L
            ),
            LirixConnectMsg.HandoffReturn(
                sessionId = "sess-handoff-100",
                newEpoch = 13
            ),
            LirixConnectMsg.RemoteMediaAction(
                epoch = 12,
                action = MediaAction.SEEK_TO,
                seekToMs = 90000L,
                volume = 0.7f,
                queueIndex = 1
            ),
            LirixConnectMsg.RemoteStateUpdate(
                epoch = 12,
                trackId = "track-42",
                trackTitle = "Bohemian Rhapsody",
                trackArtist = "Queen",
                durationMs = 354000L,
                positionMs = 90500L,
                positionAtMonoMs = 1234700L,
                playbackRate = 1.0f,
                isPlaying = true,
                volume = 0.7f,
                muted = false,
                queueIndex = 0,
                lyrics = sampleLyricsRef()
            ),
            LirixConnectMsg.SetAudioCast(
                enabled = true,
                codec = CastCodec.OPUS,
                sampleRate = 48000,
                channels = 2,
                bitrateKbps = 192
            ),
            LirixConnectMsg.SetAudioCast(
                enabled = true,
                codec = CastCodec.PCM_S16LE,
                sampleRate = 48000,
                channels = 2,
                bitrateKbps = 1536
            ),
            LirixConnectMsg.CastStatus(
                receiverLatencyMs = 65,
                bufferMs = 45,
                lossPct = 0.01f
            )
        )

        for ((index, msg) in messages.withIndex()) {
            val envelope = Envelope(
                msgId = (index + 1).toLong(),
                inReplyTo = if (index > 0) index.toLong() else null,
                sessionId = "session-test",
                epoch = 5L,
                sentAtMonoMs = 100000L + index * 50L,
                body = msg
            )

            val encodedFrame = ConnectMessageCodec.encode(envelope)
            val decodedEnvelope = ConnectMessageCodec.decode(encodedFrame)

            assertEquals(envelope.msgId, decodedEnvelope.msgId)
            assertEquals(envelope.inReplyTo, decodedEnvelope.inReplyTo)
            assertEquals(envelope.sessionId, decodedEnvelope.sessionId)
            assertEquals(envelope.epoch, decodedEnvelope.epoch)
            assertEquals(envelope.sentAtMonoMs, decodedEnvelope.sentAtMonoMs)
            assertEquals(envelope.body, decodedEnvelope.body)
        }
    }

    // ── 2. Framing and 64 KB validation tests ────────────────────────────────

    @Test
    fun testCodecLengthPrefixLittleEndian() {
        val envelope = Envelope(
            msgId = 1L,
            sentAtMonoMs = 500L,
            body = LirixConnectMsg.Ping(t0 = 42L)
        )
        val frame = ConnectMessageCodec.encode(envelope)

        assertTrue(frame.size > 4)
        val payloadLen = frame.size - 4

        // Check little-endian decoding of first 4 bytes
        val b0 = frame[0].toInt() and 0xFF
        val b1 = frame[1].toInt() and 0xFF
        val b2 = frame[2].toInt() and 0xFF
        val b3 = frame[3].toInt() and 0xFF
        val decodedLen = b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)

        assertEquals(payloadLen, decodedLen)
    }

    @Test
    fun testDecodeRejectsIncompleteFrame() {
        val envelope = Envelope(
            msgId = 1L,
            sentAtMonoMs = 500L,
            body = LirixConnectMsg.Ping(t0 = 42L)
        )
        val fullFrame = ConnectMessageCodec.encode(envelope)

        // Buffer smaller than 4 bytes
        assertFailsWith<IllegalArgumentException> {
            ConnectMessageCodec.decode(ByteArray(3))
        }

        // Buffer truncated mid-payload
        val truncated = fullFrame.copyOfRange(0, fullFrame.size - 5)
        assertFailsWith<IllegalArgumentException> {
            ConnectMessageCodec.decode(truncated)
        }
    }

    @Test
    fun testCodecRejectsPayloadOver64Kb() {
        // Create an envelope whose serialized JSON exceeds 64 KB
        val largeLrc = "A".repeat(70 * 1024)
        val hugeMsg = LirixConnectMsg.RemoteStateUpdate(
            epoch = 1,
            trackId = "huge",
            trackTitle = "Huge Track",
            trackArtist = "Huge Artist",
            durationMs = 1000L,
            positionMs = 10L,
            positionAtMonoMs = 100L,
            isPlaying = true,
            lyrics = LyricsRef(queryKey = "huge", lrc = largeLrc)
        )
        val hugeEnvelope = Envelope(
            msgId = 999L,
            sentAtMonoMs = 12345L,
            body = hugeMsg
        )

        assertFailsWith<IllegalArgumentException> {
            ConnectMessageCodec.encode(hugeEnvelope)
        }
    }

    // ── 3. ConnectSessionManager & Epoch Arbitration tests ───────────────────

    @Test
    fun testSessionManagerCreationAndWrapEnvelope() {
        val manager = ConnectSessionManager(initialRole = DeviceRole.CONTROLLER)
        assertEquals(DeviceRole.CONTROLLER, manager.currentRole)
        assertEquals(0L, manager.epoch)
        assertNull(manager.sessionId)

        val env1 = manager.wrapEnvelope(LirixConnectMsg.Ping(10L))
        assertEquals(1L, env1.msgId)
        assertEquals(0L, env1.epoch)
        assertNull(env1.sessionId)

        val env2 = manager.wrapEnvelope(LirixConnectMsg.Ping(20L))
        assertEquals(2L, env2.msgId)
    }

    @Test
    fun testEpochArbitrationStaleEpochRejected() {
        val manager = ConnectSessionManager(initialRole = DeviceRole.PLAYER, initialEpoch = 10L)

        // Action with stale epoch (9 < 10) -> must be rejected with STALE_EPOCH
        val staleAction = LirixConnectMsg.RemoteMediaAction(
            epoch = 9L,
            action = MediaAction.PAUSE
        )
        val err = manager.validateRemoteAction(staleAction)
        assertNotNull(err)
        assertEquals(ErrorCode.STALE_EPOCH, err.code)

        val result = manager.handleRemoteAction(staleAction)
        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is StaleEpochException)
        assertEquals(ErrorCode.STALE_EPOCH, exception.errorCode)
        assertEquals(9L, exception.actionEpoch)
        assertEquals(10L, exception.currentEpoch)

        // Epoch should remain 10
        assertEquals(10L, manager.epoch)
    }

    @Test
    fun testEpochArbitrationMatchingAndGreaterEpochAccepted() {
        val manager = ConnectSessionManager(initialRole = DeviceRole.PLAYER, initialEpoch = 10L)

        // Action with equal epoch (10 == 10) -> accepted
        val equalAction = LirixConnectMsg.RemoteMediaAction(
            epoch = 10L,
            action = MediaAction.PLAY
        )
        assertNull(manager.validateRemoteAction(equalAction))
        assertTrue(manager.handleRemoteAction(equalAction).isSuccess)
        assertEquals(10L, manager.epoch)

        // Action with higher epoch (15 > 10) -> accepted and bumps epoch
        val higherAction = LirixConnectMsg.RemoteMediaAction(
            epoch = 15L,
            action = MediaAction.NEXT
        )
        assertTrue(manager.handleRemoteAction(higherAction).isSuccess)
        assertEquals(15L, manager.epoch)
    }

    @Test
    fun testHandoffLifecycleRolesAndEpoch() {
        val hostManager = ConnectSessionManager(initialRole = DeviceRole.PLAYER, initialEpoch = 5L)
        val clientManager = ConnectSessionManager(initialRole = DeviceRole.CONTROLLER, initialEpoch = 5L)

        // Host initiates handoff to client with newEpoch = 6
        val handoffReq = LirixConnectMsg.HandoffRequest(
            sessionId = "sess-handoff-xyz",
            newEpoch = 6L,
            track = sampleTrackRef(),
            positionMs = 12000L,
            positionAtMonoMs = 50000L,
            durationMs = 200000L
        )

        // Client accepts handoff -> becomes PLAYER
        val ack = clientManager.handleHandoffRequest(handoffReq)
        assertTrue(ack.accepted)
        assertEquals(DeviceRole.PLAYER, clientManager.currentRole)
        assertEquals(6L, clientManager.epoch)
        assertEquals("sess-handoff-xyz", clientManager.sessionId)

        // Host receives positive ack -> transitions to CONTROLLER
        hostManager.handleHandoffAck(ack)
        assertEquals(DeviceRole.CONTROLLER, hostManager.currentRole)

        // Stale handoff with lower epoch (4 < 6) is rejected by client
        val staleHandoff = LirixConnectMsg.HandoffRequest(
            sessionId = "sess-old",
            newEpoch = 4L,
            track = sampleTrackRef(),
            positionMs = 0L,
            positionAtMonoMs = 0L,
            durationMs = 1000L
        )
        val rejectAck = clientManager.handleHandoffRequest(staleHandoff)
        assertFalse(rejectAck.accepted)

        // Handoff return back to host
        val returnMsg = LirixConnectMsg.HandoffReturn(sessionId = "sess-handoff-xyz", newEpoch = 7L)
        hostManager.handleHandoffReturn(returnMsg)
        assertEquals(DeviceRole.PLAYER, hostManager.currentRole)
        assertEquals(7L, hostManager.epoch)
    }
}
