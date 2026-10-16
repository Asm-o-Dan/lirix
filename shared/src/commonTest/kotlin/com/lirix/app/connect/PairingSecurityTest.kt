package com.lirix.app.connect

import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.DeviceType
import com.lirix.app.connect.model.PairRequest
import com.lirix.app.connect.model.PairingPayload
import com.lirix.app.connect.model.asBytes
import com.lirix.app.connect.security.PairingSecurityManager
import com.lirix.app.connect.security.TrustedDevice
import com.lirix.app.connect.security.TrustedDeviceStore
import com.lirix.app.connect.security.VerificationResult
import com.lirix.app.connect.security.constantTimeEquals
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PairingSecurityTest {

    private val json = Json { ignoreUnknownKeys = true }

    // ── 1. Bytes tests ────────────────────────────────────────────────────────

    @Test
    fun testBytesContentEqualsAndHashCode() {
        val b1 = byteArrayOf(1, 2, 3, 4, 5).asBytes()
        val b2 = byteArrayOf(1, 2, 3, 4, 5).asBytes()
        val b3 = byteArrayOf(1, 2, 3, 4, 6).asBytes()

        assertEquals(b1, b2)
        assertEquals(b1.hashCode(), b2.hashCode())
        assertNotEquals(b1, b3)
        assertFalse(b1 == b3)
    }

    @Test
    fun testBytesHexConversion() {
        val bytes = byteArrayOf(0x00, 0x0f, 0x10, 0x7f, 0x80.toByte(), 0xff.toByte()).asBytes()
        val hex = bytes.toHex()
        assertEquals("000f107f80ff", hex)

        val restored = Bytes.fromHex(hex)
        assertEquals(bytes, restored)
    }

    @Test
    fun testBytesSerializationRoundtrip() {
        val bytes = byteArrayOf(10, 20, 30, 40).asBytes()
        val serialized = json.encodeToString(bytes)
        val deserialized = json.decodeFromString<Bytes>(serialized)
        assertEquals(bytes, deserialized)
    }

    // ── 2. PairingPayload tests ───────────────────────────────────────────────

    @Test
    fun testPairingPayloadGenerationDefaults() {
        val manager = PairingSecurityManager()
        val serverEndpointId = ByteArray(32) { it.toByte() }.asBytes()
        val currentEpochSec = 1700000000L

        val payload = manager.generatePairingPayload(
            endpointId = serverEndpointId,
            deviceName = "Windows Desktop",
            currentEpochSec = currentEpochSec
        )

        assertEquals(1, payload.version)
        assertEquals(serverEndpointId, payload.endpointId)
        assertEquals(16, payload.pairingSecret.size)
        assertEquals("Windows Desktop", payload.deviceName)
        assertEquals(currentEpochSec + 120L, payload.expiresAtEpochSec)
        assertEquals(0, manager.failedAttempts)
        assertNotNull(manager.activePayload)
    }

    @Test
    fun testPairingPayloadTtlClamping() {
        val manager = PairingSecurityManager()
        val serverEndpointId = ByteArray(32) { 0x01 }.asBytes()
        val now = 1000L

        // TTL > 300 should be clamped to 300
        val payload = manager.generatePairingPayload(
            endpointId = serverEndpointId,
            deviceName = "Host",
            currentEpochSec = now,
            ttlSec = 9999L
        )
        assertEquals(now + 300L, payload.expiresAtEpochSec)
    }

    @Test
    fun testPairingPayloadSerializationRoundtrip() {
        val payload = PairingPayload(
            version = 1,
            endpointId = ByteArray(32) { 0x42 }.asBytes(),
            pairingSecret = ByteArray(16) { 0x77 }.asBytes(),
            deviceName = "Pixel 8",
            addrs = listOf("192.168.1.100:4433"),
            relayUrl = "https://relay.lirix.app",
            expiresAtEpochSec = 1700000120L
        )

        val encoded = json.encodeToString(payload)
        val decoded = json.decodeFromString<PairingPayload>(encoded)
        assertEquals(payload, decoded)
    }

    // ── 3. HMAC-proof & Verification tests ───────────────────────────────────

    @Test
    fun testHmacProofMatchingClientAndServer() {
        val serverManager = PairingSecurityManager()
        val serverEndpointId = ByteArray(32) { 0x11 }.asBytes()
        val clientEndpointId = ByteArray(32) { 0x22 }.asBytes()
        val now = 1000L

        // Server displays QR
        val payload = serverManager.generatePairingPayload(
            endpointId = serverEndpointId,
            deviceName = "Living Room PC",
            currentEpochSec = now
        )

        // Client reads QR, creates nonce and computes proof
        val clientNonce = ByteArray(16) { 0x55 }.asBytes()
        val clientProof = serverManager.createProof(
            secret = payload.pairingSecret,
            nonce = clientNonce,
            clientEndpointId = clientEndpointId,
            serverEndpointId = serverEndpointId
        )

        val pairRequest = PairRequest(
            nonce = clientNonce,
            proof = clientProof,
            deviceName = "Daniil Phone",
            deviceType = DeviceType.ANDROID_PHONE
        )

        // Server verifies request
        val result = serverManager.verifyPairRequest(
            request = pairRequest,
            clientEndpointId = clientEndpointId,
            currentEpochSec = now + 10L
        )

        assertTrue(result is VerificationResult.Success)
        val expectedVerifyCode = serverManager.generateVerifyCode(clientEndpointId, serverEndpointId)
        assertEquals(expectedVerifyCode, result.verifyCode)
        assertEquals(6, result.verifyCode.length)
        assertTrue(result.verifyCode.all { it.isDigit() })
    }

    @Test
    fun testProofRejectionOnTamperedNonceOrEndpoints() {
        val serverManager = PairingSecurityManager()
        val serverEndpointId = ByteArray(32) { 0x11 }.asBytes()
        val clientEndpointId = ByteArray(32) { 0x22 }.asBytes()
        val fakeEndpointId = ByteArray(32) { 0x33 }.asBytes()
        val now = 1000L

        val payload = serverManager.generatePairingPayload(
            endpointId = serverEndpointId,
            deviceName = "Host",
            currentEpochSec = now
        )

        val nonce = ByteArray(16) { 0x77 }.asBytes()
        // Proof was computed for fake endpoint
        val badProof = serverManager.createProof(
            secret = payload.pairingSecret,
            nonce = nonce,
            clientEndpointId = fakeEndpointId,
            serverEndpointId = serverEndpointId
        )

        val request = PairRequest(nonce = nonce, proof = badProof, deviceName = "Client")
        val result = serverManager.verifyPairRequest(
            request = request,
            clientEndpointId = clientEndpointId,
            currentEpochSec = now + 5L
        )

        assertEquals(VerificationResult.InvalidProof, result)
        assertEquals(1, serverManager.failedAttempts)
    }

    // ── 4. Expiration (TTL) tests ─────────────────────────────────────────────

    @Test
    fun testExpiredPairingRejected() {
        val serverManager = PairingSecurityManager()
        val serverEndpointId = ByteArray(32) { 0x11 }.asBytes()
        val clientEndpointId = ByteArray(32) { 0x22 }.asBytes()
        val now = 1000L

        val payload = serverManager.generatePairingPayload(
            endpointId = serverEndpointId,
            deviceName = "Host",
            currentEpochSec = now,
            ttlSec = 120L
        )

        val nonce = ByteArray(16) { 0x33 }.asBytes()
        val proof = serverManager.createProof(
            secret = payload.pairingSecret,
            nonce = nonce,
            clientEndpointId = clientEndpointId,
            serverEndpointId = serverEndpointId
        )

        val request = PairRequest(nonce = nonce, proof = proof, deviceName = "Client")

        // TTL is 120s, current time is now + 121s (TTL + 1)
        val result = serverManager.verifyPairRequest(
            request = request,
            clientEndpointId = clientEndpointId,
            currentEpochSec = now + 121L
        )

        assertEquals(VerificationResult.Expired, result)
        assertNull(serverManager.activePayload)
    }

    // ── 5. Rate limiting tests (3 failed attempts) ────────────────────────────

    @Test
    fun testRateLimitingBlocksAfterThreeFailedAttempts() {
        val manager = PairingSecurityManager()
        val serverEndpointId = ByteArray(32) { 0x11 }.asBytes()
        val clientEndpointId = ByteArray(32) { 0x22 }.asBytes()
        val now = 1000L

        val payload = manager.generatePairingPayload(serverEndpointId, "Host", now)
        val nonce = ByteArray(16) { 0x01 }.asBytes()
        val wrongProof = ByteArray(32) { 0x99.toByte() }.asBytes()
        val badRequest = PairRequest(nonce = nonce, proof = wrongProof, deviceName = "Attacker")

        // 1st failed attempt
        val res1 = manager.verifyPairRequest(badRequest, clientEndpointId, now + 1)
        assertEquals(VerificationResult.InvalidProof, res1)
        assertEquals(1, manager.failedAttempts)

        // 2nd failed attempt
        val res2 = manager.verifyPairRequest(badRequest, clientEndpointId, now + 2)
        assertEquals(VerificationResult.InvalidProof, res2)
        assertEquals(2, manager.failedAttempts)

        // 3rd failed attempt -> invalidates secret
        val res3 = manager.verifyPairRequest(badRequest, clientEndpointId, now + 3)
        assertEquals(VerificationResult.InvalidProof, res3)
        assertEquals(3, manager.failedAttempts)
        assertNull(manager.activePayload)

        // 4th attempt -> TooManyAttempts
        val res4 = manager.verifyPairRequest(badRequest, clientEndpointId, now + 4)
        assertEquals(VerificationResult.TooManyAttempts, res4)

        // Even with valid proof now, blocked!
        val validProof = manager.createProof(payload.pairingSecret, nonce, clientEndpointId, serverEndpointId)
        val validRequest = PairRequest(nonce = nonce, proof = validProof, deviceName = "Legit")
        val resBlocked = manager.verifyPairRequest(
            request = validRequest,
            expectedSecret = payload.pairingSecret,
            serverEndpointId = serverEndpointId,
            clientEndpointId = clientEndpointId,
            currentEpochSec = now + 5
        )
        assertEquals(VerificationResult.TooManyAttempts, resBlocked)
    }

    // ── 6. One-time secret tests ─────────────────────────────────────────────

    @Test
    fun testSecretIsOneTimeUse() {
        val manager = PairingSecurityManager()
        val serverEndpointId = ByteArray(32) { 0x11 }.asBytes()
        val clientEndpointId = ByteArray(32) { 0x22 }.asBytes()
        val now = 1000L

        val payload = manager.generatePairingPayload(serverEndpointId, "Host", now)
        val nonce = ByteArray(16) { 0x05 }.asBytes()
        val proof = manager.createProof(payload.pairingSecret, nonce, clientEndpointId, serverEndpointId)
        val request = PairRequest(nonce = nonce, proof = proof, deviceName = "Client")

        // First verification -> Success
        val res1 = manager.verifyPairRequest(request, clientEndpointId, now + 5)
        assertTrue(res1 is VerificationResult.Success)
        assertNull(manager.activePayload)

        // Second verification with the same request/secret -> Expired / Rejected
        val res2 = manager.verifyPairRequest(
            request = request,
            expectedSecret = payload.pairingSecret,
            serverEndpointId = serverEndpointId,
            clientEndpointId = clientEndpointId,
            currentEpochSec = now + 6
        )
        assertEquals(VerificationResult.Expired, res2)
        assertFalse(res2.isSuccess)
    }

    // ── 7. VerifyCode calculation tests ──────────────────────────────────────

    @Test
    fun testVerifyCodeFormatAndDeterministicMatch() {
        val manager = PairingSecurityManager()
        val id1 = ByteArray(32) { (it * 3).toByte() }.asBytes()
        val id2 = ByteArray(32) { (it * 7).toByte() }.asBytes()

        val code1 = manager.generateVerifyCode(id1, id2)
        val code2 = manager.generateVerifyCode(id1, id2)

        assertEquals(code1, code2)
        assertEquals(6, code1.length)
        assertTrue(code1.all { it.isDigit() }, "Verify code must contain only decimal digits: $code1")
    }

    // ── 8. Constant time equals test ─────────────────────────────────────────

    @Test
    fun testConstantTimeEquals() {
        val a = byteArrayOf(1, 2, 3, 4)
        val b = byteArrayOf(1, 2, 3, 4)
        val c = byteArrayOf(1, 2, 3, 5)
        val d = byteArrayOf(1, 2, 3)

        assertTrue(constantTimeEquals(a, b))
        assertFalse(constantTimeEquals(a, c))
        assertFalse(constantTimeEquals(a, d))
    }

    // ── 9. TrustedDeviceStore tests ──────────────────────────────────────────

    @Test
    fun testTrustedDeviceStorePinningAndRevocation() {
        val store = TrustedDeviceStore.inMemory()
        val endpoint1 = ByteArray(32) { 0x01 }.asBytes()
        val endpoint2 = ByteArray(32) { 0x02 }.asBytes()

        val device1 = TrustedDevice(
            endpointId = endpoint1,
            deviceName = "Pixel 8",
            deviceType = DeviceType.ANDROID_PHONE,
            pairedAtEpochSec = 1700000000L,
            lastSeenEpochSec = 1700000500L
        )

        // Initially untrusted
        assertFalse(store.isTrusted(endpoint1))
        assertNull(store.getTrusted(endpoint1))
        assertEquals(0, store.getAll().size)

        // Pin endpoint1
        store.addTrusted(device1)
        assertTrue(store.isTrusted(endpoint1))
        assertEquals(device1, store.getTrusted(endpoint1))
        assertEquals(1, store.getAll().size)

        // Pinning test: endpoint2 is still untrusted
        assertFalse(store.isTrusted(endpoint2))

        // Revoke («Забыть устройство»)
        val removed = store.removeTrusted(endpoint1)
        assertTrue(removed)
        assertFalse(store.isTrusted(endpoint1))
        assertNull(store.getTrusted(endpoint1))
        assertEquals(0, store.getAll().size)

        // Removing already removed returns false
        assertFalse(store.removeTrusted(endpoint1))
    }
}
