package com.example.npc.core.model

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.UserPrototype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class UserPrototypeTest {

    private val validSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    @Test
    fun `UserPrototype instantiates with valid parameters`() {
        val proto = assertDoesNotThrow {
            UserPrototype(
                id = 1L,
                packageName = "com.radolyn.ayugram",
                fingerprint = validSha256,
                category = Category.ADVERTISEMENT,
                supportCount = 2,
                createdAt = now,
                lastSeenAt = now.plusSeconds(60)
            )
        }

        assertEquals(1L, proto.id)
        assertEquals("com.radolyn.ayugram", proto.packageName)
        assertEquals(validSha256, proto.fingerprint)
        assertEquals(Category.ADVERTISEMENT, proto.category)
        assertEquals(2, proto.supportCount)
        assertEquals(now, proto.createdAt)
        assertEquals(now.plusSeconds(60), proto.lastSeenAt)
    }

    @Test
    fun `default id is 0 and lastSeenAt defaults to createdAt`() {
        val proto = UserPrototype(
            packageName = "com.apb.mobile",
            fingerprint = validSha256,
            category = Category.FINANCE,
            supportCount = 1,
            createdAt = now
        )

        assertEquals(0L, proto.id)
        assertEquals(now, proto.lastSeenAt)
    }

    @Test
    fun `throws IllegalArgumentException on negative id`() {
        assertThrows<IllegalArgumentException> {
            UserPrototype(
                id = -1L,
                packageName = "com.test",
                fingerprint = validSha256,
                category = Category.OTHER,
                supportCount = 1,
                createdAt = now
            )
        }
    }

    @Test
    fun `throws IllegalArgumentException on blank packageName`() {
        assertThrows<IllegalArgumentException> {
            UserPrototype(
                packageName = "   ",
                fingerprint = validSha256,
                category = Category.OTHER,
                supportCount = 1,
                createdAt = now
            )
        }
    }

    @Test
    fun `throws IllegalArgumentException on invalid fingerprint`() {
        val invalidFingerprints = listOf(
            "",
            "invalid_hash",
            "0123456789abcdef", // too short
            validSha256 + "a", // 65 chars
            validSha256.drop(1), // 63 chars
            validSha256.uppercase(), // uppercase hex
            "g".repeat(64) // non-hex
        )

        for (fp in invalidFingerprints) {
            val ex = assertThrows<IllegalArgumentException>("Expected throw for fingerprint '$fp'") {
                UserPrototype(
                    packageName = "com.test",
                    fingerprint = fp,
                    category = Category.OTHER,
                    supportCount = 1,
                    createdAt = now
                )
            }
            assertTrue(ex.message?.contains("fingerprint") == true)
        }
    }

    @Test
    fun `throws IllegalArgumentException on supportCount less than 1`() {
        assertThrows<IllegalArgumentException> {
            UserPrototype(
                packageName = "com.test",
                fingerprint = validSha256,
                category = Category.SERVICES,
                supportCount = 0,
                createdAt = now
            )
        }

        assertThrows<IllegalArgumentException> {
            UserPrototype(
                packageName = "com.test",
                fingerprint = validSha256,
                category = Category.SERVICES,
                supportCount = -5,
                createdAt = now
            )
        }
    }

    @Test
    fun `throws IllegalArgumentException when createdAt is after lastSeenAt`() {
        val ex = assertThrows<IllegalArgumentException> {
            UserPrototype(
                packageName = "com.test",
                fingerprint = validSha256,
                category = Category.COMMUNICATION,
                supportCount = 1,
                createdAt = now.plusSeconds(10),
                lastSeenAt = now
            )
        }
        assertTrue(ex.message?.contains("cannot be after lastSeenAt") == true)
    }

    @Test
    fun `isConfident is true only when supportCount is at or above CONFIDENCE_THRESHOLD`() {
        assertEquals(2, UserPrototype.CONFIDENCE_THRESHOLD)

        val unconfirmed = UserPrototype(
            packageName = "com.test",
            fingerprint = validSha256,
            category = Category.ADVERTISEMENT,
            supportCount = 1,
            createdAt = now
        )
        assertFalse(unconfirmed.isConfident)

        val confirmedThreshold = unconfirmed.copy(supportCount = 2)
        assertTrue(confirmedThreshold.isConfident)

        val intourCase = unconfirmed.copy(supportCount = 6)
        assertTrue(intourCase.isConfident)
    }
}
