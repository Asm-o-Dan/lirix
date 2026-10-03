package com.example.npc.core.storage.repository

import app.cash.turbine.test
import com.example.npc.core.storage.AppDatabase
import com.example.npc.core.storage.dao.PipelineDao
import com.example.npc.core.storage.dao.PipelineRevisionDao
import com.example.npc.core.storage.dao.RuntimeAlertDao
import com.example.npc.core.storage.entity.PipelineDefinitionEntity
import com.example.npc.core.storage.entity.PipelineRevisionEntity
import com.example.npc.core.storage.entity.RuntimeAlertEntity
import com.example.npc.core.storage.model.PipelineWithRevision
import com.example.npc.pipeline.dsl.ActionDefinition
import com.example.npc.pipeline.dsl.PipelineDefinition
import com.example.npc.pipeline.dsl.StageDefinition
import com.example.npc.pipeline.dsl.TriggerDefinition
import com.example.npc.pipeline.dsl.codec.PipelineJsonCodec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@OptIn(ExperimentalCoroutinesApi::class)
class PipelineRepositoryTest {

    private val database: AppDatabase = mockk(relaxed = true)
    private val pipelineDao: PipelineDao = mockk(relaxed = true)
    private val revisionDao: PipelineRevisionDao = mockk(relaxed = true)
    private val alertDao: RuntimeAlertDao = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: PipelineRepositoryImpl

    private val samplePipeline = PipelineDefinition(
        id = "test-pipeline",
        name = "Test Pipeline",
        description = "Test description",
        schemaVersion = 1,
        enabled = true,
        priority = 100,
        packageWhitelist = listOf("com.example.bank"),
        triggers = listOf(TriggerDefinition.Notification()),
        stages = listOf(
            StageDefinition(
                id = "stage-1",
                name = "Stage 1",
                enabled = true,
                actions = listOf(ActionDefinition.SaveToStorage())
            )
        )
    )

    @BeforeEach
    fun setUp() {
        repository = PipelineRepositoryImpl(
            database = database,
            dispatcher = testDispatcher,
            pipelineDao = pipelineDao,
            revisionDao = revisionDao,
            alertDao = alertDao,
            transactionRunner = { (it as suspend () -> Any?)() }
        )
    }

    @Test
    fun `saveRevision creates new definition and revision when first time saving`() = runTest(testDispatcher) {
        coEvery { revisionDao.getLatestRevisionNumber("test-pipeline") } returns null
        coEvery { pipelineDao.getDefinitionById("test-pipeline") } returns null
        coEvery { revisionDao.insertRevision(any()) } returns 101L

        val revSlot = slot<PipelineRevisionEntity>()
        val defSlot = slot<PipelineDefinitionEntity>()
        coEvery { revisionDao.insertRevision(capture(revSlot)) } returns 101L
        coEvery { pipelineDao.insertPipeline(capture(defSlot)) } returns Unit

        val revId = repository.saveRevision(samplePipeline, "Initial commit")

        revId shouldBe 101L
        revSlot.captured.pipelineId shouldBe "test-pipeline"
        revSlot.captured.revisionNumber shouldBe 1L
        revSlot.captured.commitMessage shouldBe "Initial commit"
        defSlot.captured.id shouldBe "test-pipeline"
        defSlot.captured.name shouldBe "Test Pipeline"
        defSlot.captured.activeRevisionId shouldBe null
    }

    @Test
    fun `saveRevision increments revision number and updates definition when exists`() = runTest(testDispatcher) {
        val existing = PipelineDefinitionEntity(
            id = "test-pipeline",
            name = "Old Name",
            description = null,
            schemaVersion = 1,
            enabled = true,
            priority = 50,
            packageWhitelist = "[]",
            activeRevisionId = 1L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        coEvery { revisionDao.getLatestRevisionNumber("test-pipeline") } returns 2L
        coEvery { pipelineDao.getDefinitionById("test-pipeline") } returns existing
        val defUpdateSlot = slot<PipelineDefinitionEntity>()
        val revSlot = slot<PipelineRevisionEntity>()
        coEvery { pipelineDao.updatePipeline(capture(defUpdateSlot)) } returns Unit
        coEvery { revisionDao.insertRevision(capture(revSlot)) } returns 102L

        val revId = repository.saveRevision(samplePipeline, "Update 3")

        revId shouldBe 102L
        revSlot.captured.revisionNumber shouldBe 3L
        defUpdateSlot.captured.name shouldBe "Test Pipeline"
        defUpdateSlot.captured.activeRevisionId shouldBe 1L
    }

    @Test
    fun `activateRevision updates active revision and prunes old revisions`() = runTest(testDispatcher) {
        val rev = PipelineRevisionEntity(
            id = 55L,
            pipelineId = "test-pipeline",
            revisionNumber = 3L,
            definitionJson = "{}",
            canonicalSha256 = "abc",
            createdAt = 1000L
        )
        val def = PipelineDefinitionEntity(
            id = "test-pipeline",
            name = "Test",
            description = null,
            schemaVersion = 1,
            enabled = true,
            priority = 100,
            packageWhitelist = "[]",
            activeRevisionId = null,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        coEvery { revisionDao.getRevisionByNumber("test-pipeline", 3L) } returns rev
        coEvery { pipelineDao.getDefinitionById("test-pipeline") } returns def
        coEvery { pipelineDao.updateActiveRevision("test-pipeline", 55L, any()) } returns Unit
        coEvery { revisionDao.pruneOldRevisions("test-pipeline", 10) } returns 2

        repository.activateRevision("test-pipeline", 3L)

        coVerify { pipelineDao.updateActiveRevision("test-pipeline", 55L, any()) }
        coVerify { revisionDao.pruneOldRevisions("test-pipeline", 10) }
    }

    @Test
    fun `activateRevision throws when revision not found`() = runTest(testDispatcher) {
        coEvery { revisionDao.getRevisionByNumber("test-pipeline", 99L) } returns null

        assertThrows<IllegalArgumentException> {
            repository.activateRevision("test-pipeline", 99L)
        }
    }

    @Test
    fun `getActiveDefinition returns decoded pipeline when active revision exists`() = runTest(testDispatcher) {
        val json = PipelineJsonCodec.encodeToString(samplePipeline)
        val def = PipelineDefinitionEntity(
            id = "test-pipeline",
            name = "Test",
            description = null,
            schemaVersion = 1,
            enabled = true,
            priority = 100,
            packageWhitelist = "[]",
            activeRevisionId = 10L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        val rev = PipelineRevisionEntity(
            id = 10L,
            pipelineId = "test-pipeline",
            revisionNumber = 1L,
            definitionJson = json,
            canonicalSha256 = "abc",
            createdAt = 1000L
        )
        coEvery { pipelineDao.getDefinitionById("test-pipeline") } returns def
        coEvery { revisionDao.getRevisionById(10L) } returns rev

        val result = repository.getActiveDefinition("test-pipeline")

        result shouldBe samplePipeline
    }

    @Test
    fun `rollbackToRevision reverts active revision and returns decoded definition`() = runTest(testDispatcher) {
        val json = PipelineJsonCodec.encodeToString(samplePipeline)
        val rev = PipelineRevisionEntity(
            id = 7L,
            pipelineId = "test-pipeline",
            revisionNumber = 1L,
            definitionJson = json,
            canonicalSha256 = "abc",
            createdAt = 1000L
        )
        val def = PipelineDefinitionEntity(
            id = "test-pipeline",
            name = "Test",
            description = null,
            schemaVersion = 1,
            enabled = true,
            priority = 100,
            packageWhitelist = "[]",
            activeRevisionId = 15L,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        coEvery { revisionDao.getRevisionById(7L) } returns rev
        coEvery { pipelineDao.getDefinitionById("test-pipeline") } returns def
        coEvery { pipelineDao.updateActiveRevision("test-pipeline", 7L, any()) } returns Unit

        val result = repository.rollbackToRevision("test-pipeline", 7L)

        result.isSuccess shouldBe true
        result.getOrNull() shouldBe samplePipeline
        coVerify { pipelineDao.updateActiveRevision("test-pipeline", 7L, any()) }
    }

    @Test
    fun `rollbackToRevision fails when revision belongs to different pipeline`() = runTest(testDispatcher) {
        val rev = PipelineRevisionEntity(
            id = 7L,
            pipelineId = "other-pipeline",
            revisionNumber = 1L,
            definitionJson = "{}",
            canonicalSha256 = "abc",
            createdAt = 1000L
        )
        coEvery { revisionDao.getRevisionById(7L) } returns rev

        val result = repository.rollbackToRevision("test-pipeline", 7L)

        result.isFailure shouldBe true
    }

    @Test
    fun `toggleEnabled and gcOldRevisions delegate to DAOs`() = runTest(testDispatcher) {
        coEvery { pipelineDao.toggleEnabled("test-pipeline", false, any()) } returns Unit
        coEvery { revisionDao.pruneOldRevisions("test-pipeline", 5) } returns 3

        repository.toggleEnabled("test-pipeline", false)
        repository.gcOldRevisions("test-pipeline", 5)

        coVerify { pipelineDao.toggleEnabled("test-pipeline", false, any()) }
        coVerify { revisionDao.pruneOldRevisions("test-pipeline", 5) }
    }

    @Test
    fun `recordAlert inserts RuntimeAlertEntity into alertDao`() = runTest(testDispatcher) {
        val alertSlot = slot<RuntimeAlertEntity>()
        coEvery { alertDao.insertAlert(capture(alertSlot)) } returns 1L

        repository.recordAlert(
            pipelineId = "test-pipeline",
            nodeId = "node-1",
            level = "ERROR",
            code = "TIMEOUT",
            message = "Execution timed out",
            payloadJson = "{\"durationMs\": 150}"
        )

        alertSlot.captured.pipelineId shouldBe "test-pipeline"
        alertSlot.captured.nodeId shouldBe "node-1"
        alertSlot.captured.level shouldBe "ERROR"
        alertSlot.captured.code shouldBe "TIMEOUT"
        alertSlot.captured.message shouldBe "Execution timed out"
        alertSlot.captured.payloadJson shouldBe "{\"durationMs\": 150}"
    }

    @Test
    fun `observeActivePipelines streams valid decoded pipelines`() = runTest(testDispatcher) {
        val json = PipelineJsonCodec.encodeToString(samplePipeline)
        val pwr = PipelineWithRevision(
            definition = PipelineDefinitionEntity(
                id = "test-pipeline",
                name = "Test",
                description = null,
                schemaVersion = 1,
                enabled = true,
                priority = 100,
                packageWhitelist = "[]",
                activeRevisionId = 1L,
                createdAt = 1000L,
                updatedAt = 1000L
            ),
            activeRevision = PipelineRevisionEntity(
                id = 1L,
                pipelineId = "test-pipeline",
                revisionNumber = 1L,
                definitionJson = json,
                canonicalSha256 = "abc",
                createdAt = 1000L
            )
        )
        every { pipelineDao.observeActivePipelines() } returns flowOf(listOf(pwr))

        repository.observeActivePipelines().test {
            val list = awaitItem()
            list.size shouldBe 1
            list[0] shouldBe samplePipeline
            awaitComplete()
        }
    }
}
