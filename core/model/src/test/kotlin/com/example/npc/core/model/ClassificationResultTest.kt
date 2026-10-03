package com.example.npc.core.model

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.Confidence
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.classify.UserPrototype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class ClassificationResultTest {

    private val validHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun `ClassificationResult instantiates with valid parameters`() {
        val result = assertDoesNotThrow {
            ClassificationResult(
                category = Category.FINANCE,
                confidence = 0.95,
                engine = Engine.RULES,
                contentFingerprint = validHash
            )
        }

        assertEquals(Category.FINANCE, result.category)
        assertEquals(0.95, result.confidence)
        assertEquals(Engine.RULES, result.engine)
        assertEquals(validHash, result.contentFingerprint)
        assertEquals(Confidence(0.95), result.typedConfidence)
    }

    @Test
    fun `unclassified factory creates UNCLASSIFIED result with NONE engine and 0 confidence`() {
        val result = ClassificationResult.unclassified(validHash)

        assertEquals(Category.UNCLASSIFIED, result.category)
        assertEquals(0.0, result.confidence)
        assertEquals(Engine.NONE, result.engine)
        assertEquals(validHash, result.contentFingerprint)
        assertEquals(Confidence.ZERO, result.typedConfidence)
    }

    @Test
    fun `fromPrototype factory creates result with PROTOTYPE engine and 1_0 confidence`() {
        val proto = UserPrototype(
            packageName = "com.radolyn.ayugram",
            fingerprint = validHash,
            category = Category.ADVERTISEMENT,
            supportCount = 3,
            createdAt = Instant.parse("2026-09-27T10:00:00Z")
        )

        val result = ClassificationResult.fromPrototype(proto)

        assertEquals(Category.ADVERTISEMENT, result.category)
        assertEquals(1.0, result.confidence)
        assertEquals(Engine.PROTOTYPE, result.engine)
        assertEquals(validHash, result.contentFingerprint)
        assertEquals(Confidence.MAXIMUM, result.typedConfidence)
    }

    @Test
    fun `throws IllegalArgumentException on confidence outside 0_0 to 1_0`() {
        assertThrows<IllegalArgumentException> {
            ClassificationResult(
                category = Category.FINANCE,
                confidence = -0.01,
                engine = Engine.RULES,
                contentFingerprint = validHash
            )
        }

        assertThrows<IllegalArgumentException> {
            ClassificationResult(
                category = Category.FINANCE,
                confidence = 1.01,
                engine = Engine.RULES,
                contentFingerprint = validHash
            )
        }
    }

    @Test
    fun `throws IllegalArgumentException on invalid contentFingerprint`() {
        val invalidHashes = listOf(
            "",
            "   ",
            "not_a_hash",
            validHash.drop(1),
            validHash + "a",
            validHash.uppercase()
        )

        for (hash in invalidHashes) {
            assertThrows<IllegalArgumentException>("Expected throw for hash '$hash'") {
                ClassificationResult(
                    category = Category.FINANCE,
                    confidence = 0.5,
                    engine = Engine.RULES,
                    contentFingerprint = hash
                )
            }
        }
    }
}
