package com.lirix.app.connect

import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.DeviceType
import com.lirix.app.connect.model.asBytes
import com.lirix.app.connect.proto.LirixConnectMsg
import com.lirix.app.connect.proto.SessionSnapshot
import com.lirix.app.connect.proto.TrackRef
import com.lirix.app.connect.registry.DeviceHeartbeatMonitor
import com.lirix.app.connect.registry.DeviceRegistry
import com.lirix.app.connect.security.InMemoryTrustedDeviceStore
import com.lirix.app.connect.security.TrustedDevice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceRegistryTest {

    private val endpoint1 = ByteArray(32) { 1 }.asBytes()
    private val endpoint2 = ByteArray(32) { 2 }.asBytes()

    @Test
    fun testSyncWithTrustedStoreInitializesOfflineDevices() {
        val store = InMemoryTrustedDeviceStore()
        store.addTrusted(
            TrustedDevice(
                endpointId = endpoint1,
                deviceName = "Pixel 8 Pro",
                deviceType = DeviceType.ANDROID_PHONE,
                pairedAtEpochSec = 1700000000L,
                lastSeenEpochSec = 1700000050L
            )
        )

        val registry = DeviceRegistry(trustedDeviceStore = store)
        val devices = registry.devices.value

        assertEquals(1, devices.size)
        val dev = devices.first()
        assertEquals(endpoint1.toHex(), dev.deviceId)
        assertEquals("Pixel 8 Pro", dev.deviceName)
        assertEquals(DeviceType.ANDROID_PHONE, dev.deviceType)
        assertFalse(dev.isOnline)
        assertFalse(dev.isPlaying)
        assertEquals(1700000050000L, dev.lastSeenEpochMs)
    }

    @Test
    fun testOnHeartbeatReceivedUpdatesStatusAndPlayingState() {
        val registry = DeviceRegistry()

        val heartbeat = LirixConnectMsg.DeviceHeartbeat(
            isAvailable = true,
            batteryPct = 85,
            nowPlaying = true
        )

        registry.onHeartbeatReceived(endpoint1, heartbeat, nowEpochMs = 10_000L)

        val dev = registry.getDevice(endpoint1)
        assertNotNull(dev)
        assertTrue(dev.isOnline)
        assertTrue(dev.isPlaying)
        assertEquals(85, dev.batteryPct)
        assertEquals(10_000L, dev.lastSeenEpochMs)

        // Query by hex
        assertEquals(dev, registry.getDevice(endpoint1.toHex()))
    }

    @Test
    fun testHeartbeatTimeoutSwitchesDeviceToOffline() {
        val registry = DeviceRegistry(heartbeatTimeoutMs = 25_000L)

        // Device seen at t = 10_000
        val heartbeat = LirixConnectMsg.DeviceHeartbeat(
            isAvailable = true,
            batteryPct = 90,
            nowPlaying = true
        )
        registry.onHeartbeatReceived(endpoint1, heartbeat, nowEpochMs = 10_000L)
        assertTrue(registry.getDevice(endpoint1)!!.isOnline)

        // Within 25s timeout (t = 30_000, delta = 20s <= 25s) -> still online
        registry.checkTimeouts(nowEpochMs = 30_000L)
        assertTrue(registry.getDevice(endpoint1)!!.isOnline)

        // Exceeding 25s timeout (t = 35_001, delta = 25_001 > 25_000) -> offline
        registry.checkTimeouts(nowEpochMs = 35_001L)
        assertFalse(registry.getDevice(endpoint1)!!.isOnline)
        assertFalse(registry.getDevice(endpoint1)!!.isPlaying)
    }

    @Test
    fun testOnRemoteStateUpdateUpdatesMetadataAndStatus() {
        val registry = DeviceRegistry()

        val update = LirixConnectMsg.RemoteStateUpdate(
            epoch = 1L,
            trackId = "track_123",
            trackTitle = "Bohemian Rhapsody",
            trackArtist = "Queen",
            durationMs = 354_000L,
            positionMs = 60_000L,
            positionAtMonoMs = 1234L,
            isPlaying = true
        )

        registry.onRemoteStateUpdate(endpoint1, update, nowEpochMs = 50_000L)

        val dev = registry.getDevice(endpoint1)
        assertNotNull(dev)
        assertTrue(dev.isOnline)
        assertTrue(dev.isPlaying)
        assertNotNull(dev.currentTrack)
        assertEquals("Bohemian Rhapsody", dev.currentTrack?.title)
        assertEquals("Queen", dev.currentTrack?.artist)
        assertEquals("track_123", dev.currentTrack?.trackId)
        assertEquals(50_000L, dev.lastSeenEpochMs)
    }

    @Test
    fun testOnSessionSnapshotUpdatesTrack() {
        val registry = DeviceRegistry()

        val track = TrackRef(
            provider = "spotify",
            trackId = "spot_1",
            title = "Starboy",
            artist = "The Weeknd"
        )
        val snapshot = SessionSnapshot(
            sessionId = "sess_xyz",
            epoch = 5L,
            track = track,
            isPlaying = false,
            positionMs = 15_000L
        )

        registry.onSessionSnapshot(endpoint2, snapshot, nowEpochMs = 70_000L)

        val dev = registry.getDevice(endpoint2)
        assertNotNull(dev)
        assertTrue(dev.isOnline)
        assertFalse(dev.isPlaying)
        assertEquals(track, dev.currentTrack)
    }

    @Test
    fun testOnDeviceDisconnectedAndRemoval() {
        val registry = DeviceRegistry()
        registry.onDeviceSeen(endpoint1, "Test Device", DeviceType.WINDOWS_PC, nowEpochMs = 1000L)
        assertTrue(registry.getDevice(endpoint1)!!.isOnline)

        registry.onDeviceDisconnected(endpoint1)
        assertFalse(registry.getDevice(endpoint1)!!.isOnline)

        assertTrue(registry.removeDevice(endpoint1))
        assertNull(registry.getDevice(endpoint1))
        assertEquals(0, registry.devices.value.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testDeviceHeartbeatMonitorPeriodicExecution() = runTest {
        val registry = DeviceRegistry(heartbeatTimeoutMs = 25_000L)
        registry.onDeviceSeen(endpoint1, "Remote Android", DeviceType.ANDROID_PHONE, nowEpochMs = 10_000L)

        val dispatchedHeartbeats = mutableListOf<Pair<Bytes, LirixConnectMsg.DeviceHeartbeat>>()

        var mockCurrentTime = 10_000L
        val monitor = DeviceHeartbeatMonitor(
            registry = registry,
            heartbeatIntervalMs = 10_000L,
            heartbeatTimeoutMs = 25_000L,
            timeProvider = { mockCurrentTime },
            heartbeatSender = { peer, hb -> dispatchedHeartbeats.add(peer to hb) }
        )

        monitor.start(
            scope = this,
            activePeersProvider = { listOf(endpoint1) },
            nowPlayingProvider = { true },
            batteryPctProvider = { 95 }
        )
        assertTrue(monitor.isRunning)

        // Advance 10s: heartbeat tick fires
        mockCurrentTime += 10_000L
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(1, dispatchedHeartbeats.size)
        assertEquals(endpoint1, dispatchedHeartbeats[0].first)
        assertTrue(dispatchedHeartbeats[0].second.nowPlaying)
        assertEquals(95, dispatchedHeartbeats[0].second.batteryPct)

        // Device remains active because mockCurrentTime - 10_000 = 10_000 <= 25_000
        assertTrue(registry.getDevice(endpoint1)!!.isOnline)

        // Advance time past 25s timeout (current = 36_000, delta = 26_000 > 25_000)
        mockCurrentTime = 36_000L
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(2, dispatchedHeartbeats.size)

        // Registry should have flipped device to offline
        assertFalse(registry.getDevice(endpoint1)!!.isOnline)

        monitor.stop()
        assertFalse(monitor.isRunning)
    }
}
