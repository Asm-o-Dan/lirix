package com.example.npc.classify.rules

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.classify.UserPrototype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class PrototypeStageTest {

    private val sampleFp = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val now = Instant.parse("2026-09-27T12:00:00Z")

    @Test
    fun `match returns null when prototype is null`() {
        val result = PrototypeStage.match(null)
        assertNull(result, "Null prototype must return null to allow fallback to rule classifier")
    }

    @Test
    fun `match returns null when supportCount is 1 (below confidence threshold)`() {
        val unconfirmedProto = UserPrototype(
            id = 1L,
            packageName = "com.radolyn.ayugram",
            fingerprint = sampleFp,
            category = Category.ADVERTISEMENT,
            supportCount = 1,
            createdAt = now,
            lastSeenAt = now
        )

        val result = PrototypeStage.match(unconfirmedProto)
        assertNull(result, "supportCount < 2 is unconfirmed and must not trigger Prototype-First override")
    }

    @Test
    fun `match returns confident ClassificationResult when supportCount is 2`() {
        val confirmedProto = UserPrototype(
            id = 2L,
            packageName = "com.apb.mobile",
            fingerprint = sampleFp,
            category = Category.FINANCE,
            supportCount = 2,
            createdAt = now,
            lastSeenAt = now
        )

        val result = PrototypeStage.match(confirmedProto)
        assertNotNull(result)
        assertEquals(Category.FINANCE, result?.category)
        assertEquals(1.0, result?.confidence)
        assertEquals(Engine.PROTOTYPE, result?.engine)
        assertEquals(sampleFp, result?.contentFingerprint)
    }

    @Test
    fun `match handles InTour case with supportCount 6 and guarantees ADVERTISEMENT category`() {
        val inTourProto = UserPrototype(
            id = 10L,
            packageName = "com.radolyn.ayugram",
            fingerprint = sampleFp,
            category = Category.ADVERTISEMENT,
            supportCount = 6, // 6 user corrections in Poco M7 dogfooding!
            createdAt = now.minusSeconds(86400),
            lastSeenAt = now
        )

        val result = PrototypeStage.match(inTourProto)
        assertNotNull(result)
        assertEquals(Category.ADVERTISEMENT, result?.category)
        assertEquals(1.0, result?.confidence)
        assertEquals(Engine.PROTOTYPE, result?.engine)
        assertEquals(sampleFp, result?.contentFingerprint)
    }
}
