package com.lirix.app.connect

import com.lirix.app.connect.handoff.HandoffError
import com.lirix.app.connect.handoff.HandoffState
import com.lirix.app.connect.handoff.HandoffStateMachine
import com.lirix.app.connect.proto.DeviceRole
import com.lirix.app.connect.proto.HandoffFailure
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.proto.TrackRef
import com.lirix.app.connect.session.ConnectSessionManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandoffStateMachineTest {

    private fun createHandoffRequest(): LirixConnectMsg.HandoffRequest {
        return LirixConnectMsg.HandoffRequest(
            sessionId = "sess_test_1",
            newEpoch = 1L,
            track = TrackRef(
                provider = "local",
                trackId = "tr_001",
                title = "Hotel California",
                artist = "Eagles"
            ),
            positionMs = 30_000L,
            positionAtMonoMs = 1000L,
            durationMs = 390_000L,
            isPlaying = true
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testHappyPathHandoffWithCrossfadeToMuted() = runTest {
        val sessionManager = ConnectSessionManager(initialRole = DeviceRole.PLAYER)
        val fsm = HandoffStateMachine(
            scope = this,
            sessionManager = sessionManager
        )

        assertEquals(HandoffState.IDLE, fsm.state.value)
        assertTrue(fsm.isLocalPlayback.value)
        assertEquals(DeviceRole.PLAYER, sessionManager.currentRole)

        // 1. Initiate handoff
        val request = createHandoffRequest()
        fsm.initiateHandoff(request)
        assertEquals(HandoffState.REQUESTED, fsm.state.value)
        assertTrue(fsm.isLocalPlayback.value)

        // 2. Target sends Ack(accepted = true)
        val ack = LirixConnectMsg.HandoffAck(accepted = true, startedAtMonoMs = 1005L)
        assertTrue(fsm.onHandoffAck(ack))
        assertEquals(HandoffState.TARGET_STARTING, fsm.state.value)
        assertTrue(fsm.isLocalPlayback.value)

        // 3. Target notifies that it started playing -> crossfade starts
        val stateUpdate = LirixConnectMsg.RemoteStateUpdate(
            epoch = 1L,
            trackId = "tr_001",
            trackTitle = "Hotel California",
            trackArtist = "Eagles",
            durationMs = 390_000L,
            positionMs = 30_050L,
            positionAtMonoMs = 1050L,
            isPlaying = true
        )
        fsm.onTargetStateUpdate(stateUpdate)
        assertEquals(HandoffState.TARGET_PLAYING, fsm.state.value)
        assertTrue(fsm.isLocalPlayback.value) // Still playing locally during 300 ms crossfade

        // 4. Advance time past 300 ms crossfade
        advanceTimeBy(300L)
        runCurrent()
        assertEquals(HandoffState.SOURCE_MUTED, fsm.state.value)
        assertFalse(fsm.isLocalPlayback.value) // Local sound muted
        assertEquals(DeviceRole.CONTROLLER, sessionManager.currentRole)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testHandoffTimeoutRevertsToIdleAndKeepsSoundOnSource() = runTest {
        val fsm = HandoffStateMachine(scope = this, handoffTimeoutMs = 5000L)

        fsm.initiateHandoff(createHandoffRequest())
        assertEquals(HandoffState.REQUESTED, fsm.state.value)
        assertTrue(fsm.isLocalPlayback.value)

        // Advance 4999 ms -> still requested
        advanceTimeBy(4999L)
        runCurrent()
        assertEquals(HandoffState.REQUESTED, fsm.state.value)

        // Advance remaining 1 ms -> 5s timeout fires
        advanceTimeBy(1L)
        runCurrent()
        assertEquals(HandoffState.IDLE, fsm.state.value)
        assertTrue(fsm.isLocalPlayback.value)
        assertEquals(HandoffError.Timeout, fsm.lastError.value)
    }

    @Test
    fun testHandoffRejectedWithSuggestCast() {
        val fsm = HandoffStateMachine()

        fsm.initiateHandoff(createHandoffRequest())
        assertEquals(HandoffState.REQUESTED, fsm.state.value)

        val ack = LirixConnectMsg.HandoffAck(
            accepted = false,
            reason = HandoffFailure.NO_LOCAL_FILE,
            suggestCast = true
        )
        assertTrue(fsm.onHandoffAck(ack))
        assertEquals(HandoffState.SUGGEST_CAST, fsm.state.value)
        assertTrue(fsm.isLocalPlayback.value)

        val err = fsm.lastError.value
        assertIs<HandoffError.Rejected>(err)
        assertEquals(HandoffFailure.NO_LOCAL_FILE, err.reason)
        assertTrue(err.suggestCast)
    }

    @Test
    fun testHandoffRejectedWithoutCastGoesToFailed() {
        val fsm = HandoffStateMachine()

        fsm.initiateHandoff(createHandoffRequest())
        val ack = LirixConnectMsg.HandoffAck(
            accepted = false,
            reason = HandoffFailure.USER_DECLINED,
            suggestCast = false
        )
        fsm.onHandoffAck(ack)
        assertEquals(HandoffState.FAILED, fsm.state.value)
        assertTrue(fsm.isLocalPlayback.value)
    }

    @Test
    fun testHandoffReturnRestoresSoundLocallyAndSwitchesToPlayer() {
        val sessionManager = ConnectSessionManager(initialRole = DeviceRole.CONTROLLER)
        val fsm = HandoffStateMachine(sessionManager = sessionManager)

        // Manually step into SOURCE_MUTED
        fsm.initiateHandoff(createHandoffRequest())
        fsm.onHandoffAck(LirixConnectMsg.HandoffAck(accepted = true))
        fsm.onTargetStateUpdate(
            LirixConnectMsg.RemoteStateUpdate(
                epoch = 1L,
                trackId = "1",
                trackTitle = "A",
                trackArtist = "B",
                durationMs = 100,
                positionMs = 10,
                positionAtMonoMs = 10,
                isPlaying = true
            )
        )
        fsm.onCrossfadeComplete()

        assertEquals(HandoffState.SOURCE_MUTED, fsm.state.value)
        assertFalse(fsm.isLocalPlayback.value)

        // Remote host returns playback (HandoffReturn)
        fsm.onHandoffReturn(LirixConnectMsg.HandoffReturn(sessionId = "sess_test_1", newEpoch = 2L))

        assertEquals(HandoffState.IDLE, fsm.state.value)
        assertTrue(fsm.isLocalPlayback.value)
        assertEquals(DeviceRole.PLAYER, sessionManager.currentRole)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testConnectionLossWithRecoveryAndTimeout() = runTest {
        val fsm = HandoffStateMachine(scope = this, reconnectTimeoutMs = 10_000L)

        // Put into SOURCE_MUTED
        fsm.initiateHandoff(createHandoffRequest())
        fsm.onHandoffAck(LirixConnectMsg.HandoffAck(accepted = true))
        fsm.onTargetStateUpdate(
            LirixConnectMsg.RemoteStateUpdate(
                epoch = 1L,
                trackId = "1",
                trackTitle = "A",
                trackArtist = "B",
                durationMs = 100,
                positionMs = 10,
                positionAtMonoMs = 10,
                isPlaying = true
            )
        )
        fsm.onCrossfadeComplete()
        assertEquals(HandoffState.SOURCE_MUTED, fsm.state.value)

        // Scenario A: Connection lost and restored
        fsm.onConnectionLost()
        assertEquals(HandoffState.RECONNECTING, fsm.state.value)

        advanceTimeBy(5000L)
        runCurrent()
        fsm.onConnectionRestored()
        assertEquals(HandoffState.SOURCE_MUTED, fsm.state.value)

        // Scenario B: Connection lost and times out (10s)
        fsm.onConnectionLost()
        assertEquals(HandoffState.RECONNECTING, fsm.state.value)

        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(HandoffState.FAILED, fsm.state.value)
        assertEquals(HandoffError.ConnectionLost, fsm.lastError.value)

        // Emergency resume locally
        fsm.resumeLocally()
        assertEquals(HandoffState.IDLE, fsm.state.value)
        assertTrue(fsm.isLocalPlayback.value)
    }
}
