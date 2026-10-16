package com.lirix.app.connect

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lirix.app.connect.handoff.HandoffState
import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.DeviceType
import com.lirix.app.connect.model.PairingPayload
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.proto.TrackRef
import com.lirix.app.connect.transport.PairingExpiredException
import com.lirix.app.connect.transport.PairingPayloadCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Unit-tests for AndroidConnectManager and AndroidPreferencesTrustedDeviceStore (TASK-CON-UI-07).
 */
@RunWith(AndroidJUnit4::class)
class AndroidConnectManagerTest {

    private lateinit var context: Context
    private lateinit var connectManager: AndroidConnectManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        connectManager = AndroidConnectManager.getInstance(context)
        connectManager.trustedStore.clear()
        connectManager.deviceRegistry.clear()
        connectManager.handoffStateMachine.reset()
    }

    @Test
    fun testPreferencesTrustedDeviceStore_persistAndLoad() {
        val store1 = AndroidPreferencesTrustedDeviceStore(context)
        store1.clear()

        val endpoint = Bytes(ByteArray(32) { (it + 1).toByte() })
        val device = com.lirix.app.connect.security.TrustedDevice(
            endpointId = endpoint,
            deviceName = "Тестовый ПК",
            deviceType = DeviceType.WINDOWS_PC.name,
            pairedAtEpochSec = 1000L,
            lastSeenEpochSec = 2000L
        )

        store1.addTrusted(device)
        assertTrue(store1.isTrusted(endpoint))
        assertEquals("Тестовый ПК", store1.getTrusted(endpoint)?.deviceName)

        // Read from second instance backed by same SharedPreferences
        val store2 = AndroidPreferencesTrustedDeviceStore(context)
        assertTrue(store2.isTrusted(endpoint))
        val loaded = store2.getTrusted(endpoint)
        assertNotNull(loaded)
        assertEquals("Тестовый ПК", loaded?.deviceName)
        assertEquals(DeviceType.WINDOWS_PC.name, loaded?.deviceType)

        store2.removeTrusted(endpoint)
        assertFalse(store2.isTrusted(endpoint))
    }

    @Test
    fun testPairWithPayload_registersInStoreAndRegistry() {
        val endpoint = Bytes(ByteArray(32) { 7.toByte() })
        val payload = PairingPayload(
            version = 1,
            endpointId = endpoint,
            pairingSecret = Bytes(ByteArray(16) { 3.toByte() }),
            deviceName = "Danya Desktop",
            addrs = listOf("192.168.1.100:4433"),
            expiresAtEpochSec = System.currentTimeMillis() / 1000L + 120L
        )

        val trusted = connectManager.pairWithPayload(payload)
        assertEquals("Danya Desktop", trusted.deviceName)
        assertTrue(connectManager.trustedStore.isTrusted(endpoint))

        val regDevice = connectManager.deviceRegistry.getDevice(endpoint)
        assertNotNull(regDevice)
        assertEquals("Danya Desktop", regDevice?.deviceName)
        assertTrue(regDevice?.isOnline == true)

        // Test rename
        connectManager.renameDevice(endpoint, "Danya PC (Work)")
        assertEquals("Danya PC (Work)", connectManager.trustedStore.getTrusted(endpoint)?.deviceName)
        assertEquals("Danya PC (Work)", connectManager.deviceRegistry.getDevice(endpoint)?.deviceName)

        // Test forget
        connectManager.forgetDevice(endpoint)
        assertFalse(connectManager.trustedStore.isTrusted(endpoint))
        assertNull(connectManager.deviceRegistry.getDevice(endpoint))
    }

    @Test
    fun testPairingPayloadCodec_validAndExpiredTTL() {
        val endpoint = Bytes(ByteArray(32) { 9.toByte() })
        val payload = PairingPayload(
            version = 1,
            endpointId = endpoint,
            pairingSecret = Bytes(ByteArray(16) { 4.toByte() }),
            deviceName = "Lirix Windows",
            addrs = listOf("127.0.0.1:4433"),
            expiresAtEpochSec = 5000L
        )

        val uri = PairingPayloadCodec.encode(payload)
        assertTrue(uri.startsWith("lirix://pair?d="))

        // Valid decode when within TTL
        val decoded = PairingPayloadCodec.decode(uri, currentEpochSec = 4900L)
        assertEquals(endpoint, decoded.endpointId)
        assertEquals("Lirix Windows", decoded.deviceName)

        // Expired exception when currentEpochSec > expiresAtEpochSec
        var expiredThrown = false
        try {
            PairingPayloadCodec.decode(uri, currentEpochSec = 5001L)
        } catch (_: PairingExpiredException) {
            expiredThrown = true
        }
        assertTrue(expiredThrown)
    }

    @Test
    fun testHandoffStateMachine_flow() {
        val fsm = connectManager.handoffStateMachine
        assertEquals(HandoffState.IDLE, fsm.state.value)
        assertTrue(fsm.isLocalPlayback.value)

        val req = LirixConnectMsg.HandoffRequest(
            sessionId = "s-1",
            newEpoch = 1L,
            track = TrackRef(provider = "test", title = "Song", artist = "Artist"),
            positionMs = 12000L,
            positionAtMonoMs = 1000L,
            durationMs = 180000L
        )

        fsm.initiateHandoff(req, "endpoint-1")
        assertEquals(HandoffState.REQUESTED, fsm.state.value)

        fsm.onHandoffAck(LirixConnectMsg.HandoffAck(accepted = true))
        assertEquals(HandoffState.TARGET_STARTING, fsm.state.value)

        fsm.onTargetStateUpdate(
            LirixConnectMsg.RemoteStateUpdate(
                epoch = 1L,
                trackId = "t1",
                trackTitle = "Song",
                trackArtist = "Artist",
                durationMs = 180000L,
                positionMs = 12000L,
                positionAtMonoMs = 1000L,
                isPlaying = true
            )
        )
        assertEquals(HandoffState.TARGET_PLAYING, fsm.state.value)

        // Complete crossfade transition
        fsm.onCrossfadeComplete()
        assertEquals(HandoffState.SOURCE_MUTED, fsm.state.value)
        assertFalse(fsm.isLocalPlayback.value)

        // Return playback to local
        fsm.resumeLocally()
        assertEquals(HandoffState.IDLE, fsm.state.value)
        assertTrue(fsm.isLocalPlayback.value)
    }
}
