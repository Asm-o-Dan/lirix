package com.lirix.app.connect

import com.lirix.app.connect.model.Bytes
import com.lirix.app.connect.model.PairingPayload
import com.lirix.app.connect.model.asBytes
import com.lirix.app.connect.transport.PairingExpiredException
import com.lirix.app.connect.transport.PairingPayloadCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PairingPayloadCodecTest {

    private fun createValidPayload(
        version: Int = 1,
        endpointId: Bytes = ByteArray(32) { (it * 3).toByte() }.asBytes(),
        pairingSecret: Bytes = ByteArray(16) { (it * 7).toByte() }.asBytes(),
        deviceName: String = "Test Device",
        addrs: List<String> = listOf("192.168.1.100:4433", "10.0.0.5:4433"),
        relayUrl: String? = "https://relay.lirix.app",
        expiresAtEpochSec: Long = 1700000120L
    ): PairingPayload {
        return PairingPayload(
            version = version,
            endpointId = endpointId,
            pairingSecret = pairingSecret,
            deviceName = deviceName,
            addrs = addrs,
            relayUrl = relayUrl,
            expiresAtEpochSec = expiresAtEpochSec
        )
    }

    @Test
    fun testEncodeProducesExpectedUriScheme() {
        val payload = createValidPayload()
        val uri = PairingPayloadCodec.encode(payload)

        assertTrue(uri.startsWith("lirix://pair?d="))
        // Base64Url data part must not contain '=' padding
        val dataPart = uri.substringAfter("d=")
        assertFalse(dataPart.contains('='))
        // Base64Url must not contain standard '+' or '/' characters
        assertFalse(dataPart.contains('+'))
        assertFalse(dataPart.contains('/'))
    }

    @Test
    fun testEncodeDecodeRoundTripPreservesAllFields() {
        val original = createValidPayload(
            deviceName = "Living Room TV 4K",
            addrs = listOf("192.168.0.10:4433"),
            relayUrl = "https://eu-central-relay.lirix.app",
            expiresAtEpochSec = 1700000500L
        )

        val uri = PairingPayloadCodec.encode(original)
        val decoded = PairingPayloadCodec.decode(uri, currentEpochSec = 1700000000L)

        assertEquals(original.version, decoded.version)
        assertEquals(original.endpointId, decoded.endpointId)
        assertEquals(original.pairingSecret, decoded.pairingSecret)
        assertEquals(original.deviceName, decoded.deviceName)
        assertEquals(original.addrs, decoded.addrs)
        assertEquals(original.relayUrl, decoded.relayUrl)
        assertEquals(original.expiresAtEpochSec, decoded.expiresAtEpochSec)
        assertEquals(original, decoded)
    }

    @Test
    fun testDecodeRejectsBlankOrEmptyUri() {
        assertFailsWith<IllegalArgumentException> {
            PairingPayloadCodec.decode("")
        }
        assertFailsWith<IllegalArgumentException> {
            PairingPayloadCodec.decode("   ")
        }
    }

    @Test
    fun testDecodeRejectsWrongScheme() {
        val payload = createValidPayload()
        val validUri = PairingPayloadCodec.encode(payload)
        val wrongSchemeUri = validUri.replace("lirix://pair", "http://pair")

        assertFailsWith<IllegalArgumentException> {
            PairingPayloadCodec.decode(wrongSchemeUri)
        }
    }

    @Test
    fun testDecodeRejectsMissingOrEmptyDataParameter() {
        assertFailsWith<IllegalArgumentException> {
            PairingPayloadCodec.decode("lirix://pair?d=")
        }
        assertFailsWith<IllegalArgumentException> {
            PairingPayloadCodec.decode("lirix://pair?d=   ")
        }
        assertFailsWith<IllegalArgumentException> {
            PairingPayloadCodec.decode("lirix://pair?foo=bar")
        }
    }

    @Test
    fun testDecodeRejectsMalformedBase64() {
        assertFailsWith<IllegalArgumentException> {
            PairingPayloadCodec.decode("lirix://pair?d=@@@INVALID_BASE64###")
        }
    }

    @Test
    fun testDecodeRejectsInvalidJsonInBase64() {
        // "not a json object" encoded to base64url
        val notJson = "lirix://pair?d=bm90IGEganNvbiBvYmplY3Q"
        assertFailsWith<IllegalArgumentException> {
            PairingPayloadCodec.decode(notJson)
        }
    }

    @Test
    fun testEncodeRejectsUnsupportedVersion() {
        val invalidPayload = createValidPayload(version = 2)
        assertFailsWith<IllegalArgumentException> {
            PairingPayloadCodec.encode(invalidPayload)
        }
    }

    @Test
    fun testEncodeRejectsInvalidEndpointIdLength() {
        val shortEndpoint = createValidPayload(endpointId = ByteArray(16) { 0x01 }.asBytes())
        val longEndpoint = createValidPayload(endpointId = ByteArray(33) { 0x01 }.asBytes())

        assertFailsWith<IllegalArgumentException> {
            PairingPayloadCodec.encode(shortEndpoint)
        }
        assertFailsWith<IllegalArgumentException> {
            PairingPayloadCodec.encode(longEndpoint)
        }
    }

    @Test
    fun testEncodeRejectsInvalidPairingSecretLength() {
        val shortSecret = createValidPayload(pairingSecret = ByteArray(8) { 0x02 }.asBytes())
        val longSecret = createValidPayload(pairingSecret = ByteArray(32) { 0x02 }.asBytes())

        assertFailsWith<IllegalArgumentException> {
            PairingPayloadCodec.encode(shortSecret)
        }
        assertFailsWith<IllegalArgumentException> {
            PairingPayloadCodec.encode(longSecret)
        }
    }

    @Test
    fun testDecodeDetectsExpiredPayload() {
        val payload = createValidPayload(expiresAtEpochSec = 1700000100L)
        val uri = PairingPayloadCodec.encode(payload)

        // currentEpochSec is past expiresAtEpochSec -> must throw PairingExpiredException
        val exception = assertFailsWith<PairingExpiredException> {
            PairingPayloadCodec.decode(uri, currentEpochSec = 1700000101L)
        }
        assertEquals(1700000100L, exception.expiresAtEpochSec)
        assertEquals(1700000101L, exception.currentEpochSec)
    }

    @Test
    fun testDecodeAcceptsNonExpiredPayload() {
        val payload = createValidPayload(expiresAtEpochSec = 1700000100L)
        val uri = PairingPayloadCodec.encode(payload)

        // Exactly at boundary
        val atBoundary = PairingPayloadCodec.decode(uri, currentEpochSec = 1700000100L)
        assertNotNull(atBoundary)

        // Before expiry
        val beforeExpiry = PairingPayloadCodec.decode(uri, currentEpochSec = 1700000050L)
        assertNotNull(beforeExpiry)

        // Without time check (currentEpochSec = null)
        val withoutCheck = PairingPayloadCodec.decode(uri, currentEpochSec = null)
        assertNotNull(withoutCheck)
    }
}
