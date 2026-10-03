package com.example.npc.core.storage.bank

import com.example.npc.core.storage.dao.DynamicTemplateDao
import com.example.npc.core.storage.entity.DynamicTemplateEntity
import com.example.npc.core.storage.entity.TemplateStatsEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap

class TemplateHealthMonitorTest {

    private lateinit var mockDao: DynamicTemplateDao
    private lateinit var mockBankManager: TemplateBankManager
    private lateinit var monitor: TemplateHealthMonitor

    private val statsDb = ConcurrentHashMap<String, TemplateStatsEntity>()
    private val templatesDb = ConcurrentHashMap<String, DynamicTemplateEntity>()

    @BeforeEach
    fun setup() {
        statsDb.clear()
        templatesDb.clear()

        mockDao = mockk(relaxed = true)
        mockBankManager = mockk(relaxed = true)

        coEvery { mockDao.getStats(any()) } answers {
            val id = firstArg<String>()
            statsDb[id]
        }

        coEvery { mockDao.insertStats(any()) } answers {
            val stats = firstArg<TemplateStatsEntity>()
            statsDb[stats.templateId] = stats
        }

        coEvery { mockDao.updateStats(any()) } answers {
            val stats = firstArg<TemplateStatsEntity>()
            statsDb[stats.templateId] = stats
        }

        coEvery { mockDao.getById(any()) } answers {
            val id = firstArg<String>()
            templatesDb[id]
        }

        monitor = TemplateHealthMonitor(
            templateDao = mockDao,
            bankManager = mockBankManager,
            timeoutThresholdNanos = 5_000_000L // 5ms
        )
    }

    private fun createTemplate(id: String, state: TemplateState): DynamicTemplateEntity {
        val entity = DynamicTemplateEntity(
            id = id,
            sourceKey = "md.maib.maibank",
            tier = "FALLBACK",
            origin = "AUTO",
            state = state.name,
            priority = 100,
            pattern = "test",
            bindingsJson = "{}",
            constantsJson = "{}",
            amountFormatJson = "{}",
            specVersion = 1,
            compilerVersion = 1,
            canonicalHash = "hash-$id",
            specificity = 0.9,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        templatesDb[id] = entity
        statsDb[id] = TemplateStatsEntity(templateId = id)
        return entity
    }

    @Test
    fun `recordFeedback accumulates hits and updates lastHitAt on success`() = runTest {
        val tmpl = createTemplate("tmpl-1", TemplateState.ACTIVE)

        monitor.recordFeedback(
            ExecutionFeedback(
                templateId = tmpl.id,
                isSuccess = true,
                executionDurationNanos = 1_000_000L, // 1ms
                isUserCorrection = false,
                isShadowAgreement = false
            )
        )

        val stats = statsDb[tmpl.id]!!
        stats.hits shouldBe 1L
        stats.parseFailures shouldBe 0L
        stats.lastHitAt shouldNotBe null
        monitor.getConsecutiveFailures(tmpl.id) shouldBe 0
    }

    @Test
    fun `two consecutive parse failures trigger auto-quarantine`() = runTest {
        val tmpl = createTemplate("tmpl-failing", TemplateState.ACTIVE)

        // Failure 1
        monitor.recordFeedback(
            ExecutionFeedback(
                templateId = tmpl.id,
                isSuccess = false,
                executionDurationNanos = 500_000L
            )
        )
        monitor.getConsecutiveFailures(tmpl.id) shouldBe 1
        coVerify(exactly = 0) { mockBankManager.quarantineTemplate(any(), any()) }

        // Failure 2 -> consecutive failures reach 2
        monitor.recordFeedback(
            ExecutionFeedback(
                templateId = tmpl.id,
                isSuccess = false,
                executionDurationNanos = 500_000L
            )
        )
        monitor.getConsecutiveFailures(tmpl.id) shouldBe 2
        coVerify(exactly = 1) {
            mockBankManager.quarantineTemplate(tmpl.id, match { it.contains("Consecutive parse failures") })
        }
    }

    @Test
    fun `success resets consecutive failure counter`() = runTest {
        val tmpl = createTemplate("tmpl-reset", TemplateState.ACTIVE)

        // Failure 1
        monitor.recordFeedback(ExecutionFeedback(templateId = tmpl.id, isSuccess = false))
        monitor.getConsecutiveFailures(tmpl.id) shouldBe 1

        // Success 1 -> resets consecutive failures to 0
        monitor.recordFeedback(ExecutionFeedback(templateId = tmpl.id, isSuccess = true))
        monitor.getConsecutiveFailures(tmpl.id) shouldBe 0

        // Failure 2 -> only 1 consecutive, no quarantine
        monitor.recordFeedback(ExecutionFeedback(templateId = tmpl.id, isSuccess = false))
        monitor.getConsecutiveFailures(tmpl.id) shouldBe 1
        coVerify(exactly = 0) { mockBankManager.quarantineTemplate(any(), any()) }
    }

    @Test
    fun `execution time exceeding 5ms is treated as failure`() = runTest {
        val tmpl = createTemplate("tmpl-slow", TemplateState.ACTIVE)

        // 6ms duration (threshold is 5ms)
        monitor.recordFeedback(
            ExecutionFeedback(
                templateId = tmpl.id,
                isSuccess = true, // Reported true, but execution time exceeded 5ms!
                executionDurationNanos = 6_000_000L
            )
        )

        monitor.getConsecutiveFailures(tmpl.id) shouldBe 1
        val stats = statsDb[tmpl.id]!!
        stats.parseFailures shouldBe 1L

        // Second slow execution -> auto quarantine
        monitor.recordFeedback(
            ExecutionFeedback(
                templateId = tmpl.id,
                isSuccess = true,
                executionDurationNanos = 7_000_000L
            )
        )
        monitor.getConsecutiveFailures(tmpl.id) shouldBe 2
        coVerify(exactly = 1) {
            mockBankManager.quarantineTemplate(tmpl.id, match { it.contains("Consecutive parse failures") })
        }
    }

    @Test
    fun `failure rate exceeding 5 percent after 20 hits triggers quarantine`() = runTest {
        val tmpl = createTemplate("tmpl-rate", TemplateState.ACTIVE)

        // 19 successes alternating with non-consecutive failures
        for (i in 1..18) {
            monitor.recordFeedback(ExecutionFeedback(templateId = tmpl.id, isSuccess = true))
        }

        // Fail 1
        monitor.recordFeedback(ExecutionFeedback(templateId = tmpl.id, isSuccess = false))
        // Success 1
        monitor.recordFeedback(ExecutionFeedback(templateId = tmpl.id, isSuccess = true))
        // Fail 2 (Total hits = 21, failures = 2 -> failureRate = 2/21 = 9.5% > 5%)
        monitor.recordFeedback(ExecutionFeedback(templateId = tmpl.id, isSuccess = false))

        coVerify(atLeast = 1) {
            mockBankManager.quarantineTemplate(tmpl.id, match { it.contains("Failure rate") })
        }
    }

    @Test
    fun `auto-promotion SHADOW to ACTIVE occurs at 5 shadow agreements with 0 failures`() = runTest {
        val tmpl = createTemplate("tmpl-shadow", TemplateState.SHADOW)

        // 4 shadow agreements
        for (i in 1..4) {
            monitor.recordFeedback(
                ExecutionFeedback(
                    templateId = tmpl.id,
                    isSuccess = true,
                    isShadowAgreement = true
                )
            )
        }
        coVerify(exactly = 0) { mockBankManager.activateTemplate(any()) }

        // 5th shadow agreement
        monitor.recordFeedback(
            ExecutionFeedback(
                templateId = tmpl.id,
                isSuccess = true,
                isShadowAgreement = true
            )
        )
        coVerify(exactly = 1) { mockBankManager.activateTemplate(tmpl.id) }

        val healthStatus = monitor.getHealthStatus(tmpl.id)
        assertNotNull(healthStatus)
        assertTrue(healthStatus!!.isPromotionReady)
        assertFalse(healthStatus.isQuarantineRecommended)
        healthStatus.failureRate shouldBe 0.0f
    }

    @Test
    fun `SHADOW template with failure is NOT promoted even with 5 agreements`() = runTest {
        val tmpl = createTemplate("tmpl-shadow-err", TemplateState.SHADOW)

        monitor.recordFeedback(
            ExecutionFeedback(
                templateId = tmpl.id,
                isSuccess = false,
                isShadowAgreement = false
            )
        )

        for (i in 1..5) {
            monitor.recordFeedback(
                ExecutionFeedback(
                    templateId = tmpl.id,
                    isSuccess = true,
                    isShadowAgreement = true
                )
            )
        }

        coVerify(exactly = 0) { mockBankManager.activateTemplate(tmpl.id) }
    }
}
