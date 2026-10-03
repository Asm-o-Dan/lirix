package com.example.npc.core.storage

import app.cash.turbine.test
import com.example.npc.core.storage.dao.DynamicTemplateDao
import com.example.npc.core.storage.entity.DynamicTemplateEntity
import com.example.npc.core.storage.entity.TemplateBankMembershipEntity
import com.example.npc.core.storage.entity.TemplateBankVersionEntity
import com.example.npc.core.storage.entity.TemplateStatsEntity
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DynamicTemplateDaoTest {

    private val dao: DynamicTemplateDao = mockk(relaxed = true)

    private val sampleTemplate = DynamicTemplateEntity(
        id = "tmpl-maib-refund-001",
        sourceKey = "md.maib.maibank",
        tier = "FALLBACK",
        origin = "AUTO",
        state = "ACTIVE",
        priority = 100,
        pattern = """(?i)\Qrestituire\E\s+(?P<amount>[0-9.,]+)\s*(?P<curr>MDL)""",
        bindingsJson = """{"amount":"amount","currency":"curr"}""",
        constantsJson = """{"opType":"CREDIT","isRefund":true}""",
        amountFormatJson = """{"decimalSeparator":".","groupingSeparator":" "}""",
        specVersion = 1,
        compilerVersion = 1,
        canonicalHash = "sha256-hash-001",
        specificity = 0.95,
        parentTemplateId = null,
        sampleEventId = "evt-12345",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_000_000L,
        stateReason = "Verified via auto-induction"
    )

    private val sampleStats = TemplateStatsEntity(
        templateId = "tmpl-maib-refund-001",
        hits = 42L,
        parseFailures = 0L,
        userCorrections = 1L,
        shadowAgreements = 5L,
        shadowDisagreements = 0L,
        lastHitAt = 1_700_000_100_000L
    )

    private val sampleBankVersion = TemplateBankVersionEntity(
        version = 1L,
        parentVersion = null,
        membershipHash = "bank-sha256-001",
        createdAt = 1_700_000_000_000L,
        cause = "Initial template bank generation"
    )

    @Test
    fun `insert stores dynamic template and returns generated id`() = runTest {
        coEvery { dao.insert(sampleTemplate) } returns 1L

        val id = dao.insert(sampleTemplate)

        id shouldBe 1L
        coVerify(exactly = 1) { dao.insert(sampleTemplate) }
    }

    @Test
    fun `getById returns dynamic template by unique id`() = runTest {
        coEvery { dao.getById("tmpl-maib-refund-001") } returns sampleTemplate

        val result = dao.getById("tmpl-maib-refund-001")

        result shouldBe sampleTemplate
        result?.canonicalHash shouldBe "sha256-hash-001"
        result?.sourceKey shouldBe "md.maib.maibank"
        result?.state shouldBe "ACTIVE"
    }

    @Test
    fun `getByCanonicalHash finds template by unique canonical hash`() = runTest {
        coEvery { dao.getByCanonicalHash("sha256-hash-001") } returns sampleTemplate

        val result = dao.getByCanonicalHash("sha256-hash-001")

        result shouldBe sampleTemplate
        result?.id shouldBe "tmpl-maib-refund-001"
    }

    @Test
    fun `observeByState emits flow of templates filtered by state`() = runTest {
        every { dao.observeByState("ACTIVE") } returns flowOf(listOf(sampleTemplate))

        dao.observeByState("ACTIVE").test {
            val list = awaitItem()
            list.size shouldBe 1
            list[0].id shouldBe "tmpl-maib-refund-001"
            awaitComplete()
        }
    }

    @Test
    fun `updateState updates template state and reason`() = runTest {
        coEvery { dao.updateState("tmpl-maib-refund-001", "QUARANTINED", "High error rate", any()) } returns 1

        val updatedCount = dao.updateState("tmpl-maib-refund-001", "QUARANTINED", "High error rate", 1_700_000_200_000L)

        updatedCount shouldBe 1
        coVerify(exactly = 1) { dao.updateState("tmpl-maib-refund-001", "QUARANTINED", "High error rate", 1_700_000_200_000L) }
    }

    @Test
    fun `getStats returns execution metrics for template`() = runTest {
        coEvery { dao.getStats("tmpl-maib-refund-001") } returns sampleStats

        val stats = dao.getStats("tmpl-maib-refund-001")

        stats shouldBe sampleStats
        stats?.hits shouldBe 42L
        stats?.userCorrections shouldBe 1L
    }

    @Test
    fun `recordHit increments hits counter`() = runTest {
        coEvery { dao.recordHit("tmpl-maib-refund-001", any()) } returns 1

        val affected = dao.recordHit("tmpl-maib-refund-001", 1_700_000_300_000L)

        affected shouldBe 1
        coVerify(exactly = 1) { dao.recordHit("tmpl-maib-refund-001", 1_700_000_300_000L) }
    }

    @Test
    fun `template bank version and memberships flow`() = runTest {
        coEvery { dao.getLatestBankVersion() } returns sampleBankVersion
        coEvery { dao.getMembershipsForVersion(1L) } returns listOf(
            TemplateBankMembershipEntity(1L, "tmpl-maib-refund-001")
        )
        coEvery { dao.getTemplatesForBankVersion(1L) } returns listOf(sampleTemplate)

        val latest = dao.getLatestBankVersion()
        latest?.version shouldBe 1L

        val memberships = dao.getMembershipsForVersion(1L)
        memberships.size shouldBe 1
        memberships[0].templateId shouldBe "tmpl-maib-refund-001"

        val templates = dao.getTemplatesForBankVersion(1L)
        templates.size shouldBe 1
        templates[0].id shouldBe "tmpl-maib-refund-001"
    }
}
