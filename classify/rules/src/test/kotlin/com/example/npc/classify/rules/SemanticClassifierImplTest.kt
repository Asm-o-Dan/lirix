package com.example.npc.classify.rules

import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.model.classify.PackageGatedRouter
import com.example.npc.core.model.classify.SemanticClassifier
import com.example.npc.core.model.classify.UserPrototype
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SemanticClassifierImplTest {

    private val router: PackageGatedRouter = mockk(relaxed = true)
    private val ruleClassifier: RuleBasedCategoryClassifier = mockk(relaxed = true)
    private lateinit var semanticClassifier: SemanticClassifier

    private val now = Instant.parse("2026-09-27T12:00:00Z")
    private val sampleFp = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    private val sampleEvent = Event(
        id = 1L,
        rawId = 1L,
        ts = now,
        title = "InTour Тур агентство ПМР",
        text = "Вылет из Кишинева от 499 евро",
        normalizedText = "вылет из кишинева от 499 евро",
        lang = Lang.RU,
        threadKey = null,
        isUpdateOf = null
    )

    @BeforeEach
    fun setUp() {
        clearMocks(router, ruleClassifier)
        semanticClassifier = SemanticClassifierImpl(router, ruleClassifier)
    }

    @Test
    fun `classify uses Prototype-First when prototype has supportCount greater or equal to 2`() {
        val inTourPrototype = UserPrototype(
            id = 5L,
            packageName = "com.radolyn.ayugram",
            fingerprint = sampleFp,
            category = Category.ADVERTISEMENT,
            supportCount = 6, // InTour dogfooding feedback
            createdAt = now.minusSeconds(3600),
            lastSeenAt = now
        )

        val result = semanticClassifier.classify(sampleEvent, inTourPrototype)

        // Prototype-first short-circuits rule-based classifier!
        assertEquals(Category.ADVERTISEMENT, result.category)
        assertEquals(1.0, result.confidence)
        assertEquals(Engine.PROTOTYPE, result.engine)

        // Verify ruleClassifier was NEVER called
        verify(exactly = 0) { ruleClassifier.classifyByRules(any(), any(), any()) }
    }

    @Test
    fun `classify falls back to rule classifier when prototype is null`() {
        val expectedRuleResult = ClassificationResult(
            category = Category.COMMUNICATION,
            confidence = 0.90,
            engine = Engine.RULES,
            contentFingerprint = sampleFp
        )
        every { ruleClassifier.classifyByRules(any(), any(), any()) } returns expectedRuleResult

        val result = semanticClassifier.classify(sampleEvent, null)

        assertEquals(Category.COMMUNICATION, result.category)
        assertEquals(0.90, result.confidence)
        assertEquals(Engine.RULES, result.engine)

        verify(exactly = 1) { ruleClassifier.classifyByRules(any(), any(), any()) }
    }

    @Test
    fun `classify falls back to rule classifier when prototype supportCount is only 1`() {
        val unconfirmedPrototype = UserPrototype(
            id = 1L,
            packageName = "com.radolyn.ayugram",
            fingerprint = sampleFp,
            category = Category.ADVERTISEMENT,
            supportCount = 1,
            createdAt = now,
            lastSeenAt = now
        )

        val expectedRuleResult = ClassificationResult(
            category = Category.COMMUNICATION,
            confidence = 0.90,
            engine = Engine.RULES,
            contentFingerprint = sampleFp
        )
        every { ruleClassifier.classifyByRules(any(), any(), any()) } returns expectedRuleResult

        val result = semanticClassifier.classify(sampleEvent, unconfirmedPrototype)

        assertEquals(Category.COMMUNICATION, result.category)
        assertEquals(Engine.RULES, result.engine)

        verify(exactly = 1) { ruleClassifier.classifyByRules(any(), any(), any()) }
    }

    @Test
    fun `classify sets valid 64-char lowercase hex contentFingerprint on result`() {
        val expectedRuleResult = ClassificationResult(
            category = Category.FINANCE,
            confidence = 0.95,
            engine = Engine.RULES,
            contentFingerprint = sampleFp
        )
        every { ruleClassifier.classifyByRules(any(), any(), any()) } returns expectedRuleResult

        val result = semanticClassifier.classify(sampleEvent, null)

        assertEquals(64, result.contentFingerprint.length)
        assertEquals(result.contentFingerprint, result.contentFingerprint.lowercase())
    }
}
