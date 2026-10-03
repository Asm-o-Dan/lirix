package com.example.npc.core.storage.bank

import com.example.npc.core.storage.AppDatabase
import com.example.npc.core.storage.dao.DynamicTemplateDao
import com.example.npc.core.storage.entity.DynamicTemplateEntity
import com.example.npc.core.storage.entity.TemplateBankMembershipEntity
import com.example.npc.core.storage.entity.TemplateBankVersionEntity
import com.example.npc.core.storage.entity.TemplateStatsEntity
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class TemplateBankManagerTest {

    private lateinit var mockDb: AppDatabase
    private lateinit var mockDao: DynamicTemplateDao
    private lateinit var manager: TemplateBankManagerImpl

    private val templatesDb = ConcurrentHashMap<String, DynamicTemplateEntity>()
    private val statsDb = ConcurrentHashMap<String, TemplateStatsEntity>()
    private val versionsDb = mutableListOf<TemplateBankVersionEntity>()
    private val membershipsDb = mutableListOf<TemplateBankMembershipEntity>()

    @BeforeEach
    fun setup() {
        templatesDb.clear()
        statsDb.clear()
        versionsDb.clear()
        membershipsDb.clear()

        // Initial empty bank version 0
        versionsDb.add(
            TemplateBankVersionEntity(
                version = 0L,
                parentVersion = null,
                membershipHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                createdAt = 0L,
                cause = "INITIAL_EMPTY"
            )
        )

        mockDao = mockk(relaxed = true)

        coEvery { mockDao.insert(any()) } answers {
            val entity = firstArg<DynamicTemplateEntity>()
            templatesDb[entity.id] = entity
            1L
        }

        coEvery { mockDao.update(any()) } answers {
            val entity = firstArg<DynamicTemplateEntity>()
            templatesDb[entity.id] = entity
        }

        coEvery { mockDao.insertStats(any()) } answers {
            val stats = firstArg<TemplateStatsEntity>()
            statsDb[stats.templateId] = stats
        }

        coEvery { mockDao.getById(any()) } answers {
            val id = firstArg<String>()
            templatesDb[id]
        }

        coEvery { mockDao.getByCanonicalHash(any()) } answers {
            val hash = firstArg<String>()
            templatesDb.values.find { it.canonicalHash == hash }
        }

        coEvery { mockDao.getByState(any()) } answers {
            val state = firstArg<String>()
            templatesDb.values.filter { it.state == state }.sortedByDescending { it.priority }
        }

        coEvery { mockDao.countByState(any()) } answers {
            val state = firstArg<String>()
            templatesDb.values.count { it.state == state }
        }

        coEvery { mockDao.updateState(any(), any(), any(), any()) } answers {
            val id = firstArg<String>()
            val newState = secondArg<String>()
            val reason = thirdArg<String?>()
            val updated = it.invocation.args[3] as Long
            val existing = templatesDb[id]
            if (existing != null) {
                templatesDb[id] = existing.copy(state = newState, stateReason = reason, updatedAt = updated)
                1
            } else 0
        }

        coEvery { mockDao.getLatestBankVersion() } answers {
            versionsDb.maxByOrNull { it.version }
        }

        coEvery { mockDao.insertBankVersion(any()) } answers {
            val version = firstArg<TemplateBankVersionEntity>()
            versionsDb.add(version)
        }

        coEvery { mockDao.insertMemberships(any()) } answers {
            val list = firstArg<List<TemplateBankMembershipEntity>>()
            membershipsDb.addAll(list)
        }

        mockDb = mockk(relaxed = true)

        manager = TemplateBankManagerImpl(
            database = mockDb,
            templateDao = mockDao,
            transactionRunner = { it() }
        )
    }

    @Test
    fun `registerDraft creates template in DRAFT state with canonical hash`() = runTest {
        val draft = DynamicTemplateDraft(
            sourceKey = "md.maib.maibank",
            tier = "FALLBACK",
            origin = "SYSTEM",
            pattern = """Restituire\s+(?P<amount>\d+)""",
            bindingsJson = "{}",
            constantsJson = "{}",
            amountFormatJson = "{}",
            specificity = 0.8f,
            sampleEventId = "evt-1"
        )

        val id = manager.registerDraft(draft, initialState = TemplateState.DRAFT)
        assertNotNull(id)

        val saved = templatesDb[id]
        assertNotNull(saved)
        saved!!.state shouldBe TemplateState.DRAFT.name
        saved.sourceKey shouldBe "md.maib.maibank"
        saved.canonicalHash shouldHaveLength 64

        val expectedHash = TemplateBankManagerImpl.calculateCanonicalHash(draft.sourceKey, draft.pattern)
        saved.canonicalHash shouldBe expectedHash

        assertNotNull(statsDb[id])
    }

    @Test
    fun `registerDraft with existing canonical hash idempotently updates template and returns existing id`() = runTest {
        val draft = DynamicTemplateDraft(
            sourceKey = "md.maib.maibank",
            tier = "FALLBACK",
            origin = "SYSTEM",
            pattern = """Restituire\s+(?P<amount>\d+)""",
            bindingsJson = "{}",
            constantsJson = "{}",
            amountFormatJson = "{}",
            specificity = 0.8f,
            sampleEventId = "evt-1"
        )

        val id1 = manager.registerDraft(draft)
        val updatedDraft = draft.copy(bindingsJson = "{\"amount\": \"1\"}")
        val id2 = manager.registerDraft(updatedDraft)

        id2 shouldBe id1
        templatesDb[id1]?.bindingsJson shouldBe "{\"amount\": \"1\"}"
    }

    @Test
    fun `registerDraft rejects invalid regex pattern`() = runTest {
        val invalidDraft = DynamicTemplateDraft(
            sourceKey = "md.maib.maibank",
            tier = "FALLBACK",
            origin = "SYSTEM",
            pattern = """(?P<invalid""",
            bindingsJson = "{}",
            constantsJson = "{}",
            amountFormatJson = "{}",
            specificity = 0.8f,
            sampleEventId = null
        )

        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                manager.registerDraft(invalidDraft)
            }
        }
    }

    @Test
    fun `FSM lifecycle DRAFT to VALIDATED to ACTIVE with monotonic bankVersion increment`() = runTest {
        val draft = DynamicTemplateDraft(
            sourceKey = "md.maib.maibank",
            tier = "FALLBACK",
            origin = "SYSTEM",
            pattern = """Plata\s+(?P<amount>\d+)""",
            bindingsJson = "{}",
            constantsJson = "{}",
            amountFormatJson = "{}",
            specificity = 0.9f,
            sampleEventId = "evt-2"
        )

        val id = manager.registerDraft(draft, initialState = TemplateState.DRAFT)

        // 1. DRAFT -> VALIDATED (does not bump bankVersion since not active)
        val valResult = manager.validateTemplate(id)
        valResult.previousState shouldBe TemplateState.DRAFT
        valResult.newState shouldBe TemplateState.VALIDATED
        valResult.newBankVersion shouldBe 0L

        // 2. VALIDATED -> ACTIVE (bumps bankVersion 0 -> 1 and computes membershipHash)
        val actResult = manager.activateTemplate(id)
        actResult.previousState shouldBe TemplateState.VALIDATED
        actResult.newState shouldBe TemplateState.ACTIVE
        actResult.newBankVersion shouldBe 1L

        val latestVersion = manager.getLatestBankVersion()
        latestVersion shouldBe 1L

        val versionEntity = versionsDb.last()
        versionEntity.version shouldBe 1L
        versionEntity.parentVersion shouldBe null // from 0
        versionEntity.cause shouldBe "ACTIVATE"

        val expectedHash = TemplateBankManagerImpl.calculateMembershipHash(listOf(id))
        versionEntity.membershipHash shouldBe expectedHash

        membershipsDb.any { it.version == 1L && it.templateId == id } shouldBe true
    }

    @Test
    fun `quarantineTemplate moves active template to QUARANTINED and increments bankVersion`() = runTest {
        val draft = DynamicTemplateDraft(
            sourceKey = "com.apb.mobile",
            tier = "OVERRIDE",
            origin = "USER", // auto activates
            pattern = """Perevod\s+(?P<amount>\d+)""",
            bindingsJson = "{}",
            constantsJson = "{}",
            amountFormatJson = "{}",
            specificity = 0.95f,
            sampleEventId = "evt-apb"
        )

        val id = manager.registerDraft(draft) // USER origin -> ACTIVE, version bumps to 1
        manager.getLatestBankVersion() shouldBe 1L

        // Quarantine
        val qResult = manager.quarantineTemplate(id, "CircuitBreaker tripped")
        qResult.previousState shouldBe TemplateState.ACTIVE
        qResult.newState shouldBe TemplateState.QUARANTINED
        qResult.newBankVersion shouldBe 2L

        manager.getLatestBankVersion() shouldBe 2L
        val saved = templatesDb[id]
        saved!!.state shouldBe TemplateState.QUARANTINED.name
        saved.stateReason shouldBe "CircuitBreaker tripped"

        // Active templates count is 0 now
        val emptyHash = TemplateBankManagerImpl.calculateMembershipHash(emptyList())
        versionsDb.last().membershipHash shouldBe emptyHash
        versionsDb.last().cause shouldBe "QUARANTINE: CircuitBreaker tripped"
    }

    @Test
    fun `transitionToShadow moves validated template to SHADOW`() = runTest {
        val draft = DynamicTemplateDraft(
            sourceKey = "md.victoriabank",
            tier = "FALLBACK",
            origin = "AUTO",
            pattern = """Retragere\s+(?P<amount>\d+)""",
            bindingsJson = "{}",
            constantsJson = "{}",
            amountFormatJson = "{}",
            specificity = 0.85f,
            sampleEventId = null
        )

        // AUTO origin defaults to SHADOW
        val id = manager.registerDraft(draft)
        templatesDb[id]!!.state shouldBe TemplateState.SHADOW.name

        // Promote SHADOW -> ACTIVE
        val promResult = manager.promoteShadowTemplate(id)
        promResult.previousState shouldBe TemplateState.SHADOW
        promResult.newState shouldBe TemplateState.ACTIVE
        promResult.newBankVersion shouldBe 1L
    }

    @Test
    fun `illegal FSM transition throws IllegalStateException`() = runTest {
        val draft = DynamicTemplateDraft(
            sourceKey = "md.maib.maibank",
            tier = "FALLBACK",
            origin = "SYSTEM",
            pattern = """Test\s+(?P<amount>\d+)""",
            bindingsJson = "{}",
            constantsJson = "{}",
            amountFormatJson = "{}",
            specificity = 0.5f,
            sampleEventId = null
        )

        val id = manager.registerDraft(draft, initialState = TemplateState.DRAFT)
        manager.disableTemplate(id) // DRAFT -> DISABLED

        // DISABLED cannot directly become ACTIVE without DRAFT/VALIDATED
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                manager.activateTemplate(id)
            }
        }
    }

    @Test
    fun `nonexistent template throws NoSuchElementException`() = runTest {
        assertThrows(NoSuchElementException::class.java) {
            kotlinx.coroutines.runBlocking {
                manager.activateTemplate("non-existent-id")
            }
        }
    }

    @Test
    fun `membershipHash is deterministic and matches empty bank constant`() {
        val emptyHash = TemplateBankManagerImpl.calculateMembershipHash(emptyList())
        emptyHash shouldBe "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        val hash1 = TemplateBankManagerImpl.calculateMembershipHash(listOf("id-b", "id-a"))
        val hash2 = TemplateBankManagerImpl.calculateMembershipHash(listOf("id-a", "id-b"))
        hash1 shouldBe hash2 // Order-independent sorting
    }
}
