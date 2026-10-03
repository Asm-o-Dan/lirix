package com.example.npc.core.storage.repository

import androidx.room.withTransaction
import com.example.npc.core.storage.AppDatabase
import com.example.npc.core.storage.dao.PipelineDao
import com.example.npc.core.storage.dao.PipelineRevisionDao
import com.example.npc.core.storage.dao.RuntimeAlertDao
import com.example.npc.core.storage.entity.PipelineDefinitionEntity
import com.example.npc.core.storage.entity.PipelineRevisionEntity
import com.example.npc.core.storage.entity.RuntimeAlertEntity
import com.example.npc.pipeline.dsl.PipelineDefinition
import com.example.npc.pipeline.dsl.codec.PipelineJsonCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

class PipelineRepositoryImpl(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pipelineDao: PipelineDao = database.pipelineDao(),
    private val revisionDao: PipelineRevisionDao = database.pipelineRevisionDao(),
    private val alertDao: RuntimeAlertDao = database.runtimeAlertDao(),
    private val transactionRunner: (suspend (suspend () -> Any?) -> Any?)? = null
) : PipelineRepository {

    private val mutationMutex = Mutex()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (transactionRunner != null) {
            transactionRunner.invoke(block as suspend () -> Any?) as T
        } else {
            database.withTransaction(block)
        }
    }

    override suspend fun saveRevision(
        definition: PipelineDefinition,
        commitMessage: String?
    ): Long = withContext(dispatcher) {
        mutationMutex.withLock {
            val json = PipelineJsonCodec.encodeToString(definition)
            val sha256 = calculateSha256(json)
            val now = System.currentTimeMillis()

            runInTransaction {
                val currentMax = revisionDao.getLatestRevisionNumber(definition.id) ?: 0L
                val nextRevNumber = currentMax + 1L

                val existing = pipelineDao.getDefinitionById(definition.id)
                val whitelistJson = definition.packageWhitelist.sorted().joinToString(prefix = "[", postfix = "]") { "\"$it\"" }
                if (existing == null) {
                    pipelineDao.insertPipeline(
                        PipelineDefinitionEntity(
                            id = definition.id,
                            name = definition.name,
                            description = definition.description,
                            schemaVersion = definition.schemaVersion,
                            enabled = definition.enabled,
                            priority = definition.priority,
                            packageWhitelist = whitelistJson,
                            activeRevisionId = null,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                } else {
                    pipelineDao.updatePipeline(
                        existing.copy(
                            name = definition.name,
                            description = definition.description,
                            schemaVersion = definition.schemaVersion,
                            enabled = definition.enabled,
                            priority = definition.priority,
                            packageWhitelist = whitelistJson,
                            updatedAt = now
                        )
                    )
                }

                revisionDao.insertRevision(
                    PipelineRevisionEntity(
                        id = 0L,
                        pipelineId = definition.id,
                        revisionNumber = nextRevNumber,
                        definitionJson = json,
                        canonicalSha256 = sha256,
                        createdAt = now,
                        commitMessage = commitMessage
                    )
                )
            }
        }
    }

    override suspend fun activateRevision(pipelineId: String, revisionNumber: Long): Unit = withContext(dispatcher) {
        runInTransaction {
            val revision = revisionDao.getRevisionByNumber(pipelineId, revisionNumber)
                ?: throw IllegalArgumentException("Revision $revisionNumber for pipeline $pipelineId not found")
            val def = pipelineDao.getDefinitionById(pipelineId)
                ?: throw IllegalArgumentException("Pipeline $pipelineId not found")

            pipelineDao.updateActiveRevision(pipelineId, revision.id)
            revisionDao.pruneOldRevisions(pipelineId, keepCount = 10)
        }
    }

    override suspend fun getActiveDefinition(pipelineId: String): PipelineDefinition? = withContext(dispatcher) {
        val def = pipelineDao.getDefinitionById(pipelineId) ?: return@withContext null
        val activeRevId = def.activeRevisionId ?: return@withContext null
        val rev = revisionDao.getRevisionById(activeRevId) ?: return@withContext null
        PipelineJsonCodec.decodeFromString(rev.definitionJson)
    }

    override fun observeActiveDefinition(pipelineId: String): Flow<PipelineDefinition?> {
        return pipelineDao.getPipelineWithRevision(pipelineId).map { pwr ->
            pwr?.activeRevision?.let { rev ->
                try {
                    PipelineJsonCodec.decodeFromString(rev.definitionJson)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    override fun observeActivePipelines(): Flow<List<PipelineDefinition>> {
        return pipelineDao.observeActivePipelines().map { list ->
            list.mapNotNull { pwr ->
                val rev = pwr.activeRevision ?: return@mapNotNull null
                try {
                    PipelineJsonCodec.decodeFromString(rev.definitionJson)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    override suspend fun getRevision(pipelineId: String, revisionNumber: Long): PipelineDefinition? = withContext(dispatcher) {
        val rev = revisionDao.getRevisionByNumber(pipelineId, revisionNumber) ?: return@withContext null
        PipelineJsonCodec.decodeFromString(rev.definitionJson)
    }

    override suspend fun rollbackToRevision(pipelineId: String, revisionId: Long): Result<PipelineDefinition> = withContext(dispatcher) {
        runCatching {
            runInTransaction {
                val rev = revisionDao.getRevisionById(revisionId)
                    ?: throw IllegalArgumentException("Revision $revisionId not found")
                check(rev.pipelineId == pipelineId) {
                    "Revision $revisionId does not belong to pipeline $pipelineId"
                }
                val def = pipelineDao.getDefinitionById(pipelineId)
                    ?: throw IllegalArgumentException("Pipeline $pipelineId not found")

                pipelineDao.updateActiveRevision(pipelineId, revisionId)
                PipelineJsonCodec.decodeFromString(rev.definitionJson)
            }
        }
    }

    override suspend fun toggleEnabled(pipelineId: String, enabled: Boolean) = withContext(dispatcher) {
        pipelineDao.toggleEnabled(pipelineId, enabled)
    }

    override suspend fun gcOldRevisions(pipelineId: String, keepCount: Int) = withContext(dispatcher) {
        revisionDao.pruneOldRevisions(pipelineId, keepCount)
        Unit
    }

    override suspend fun recordAlert(
        pipelineId: String,
        nodeId: String?,
        level: String,
        code: String,
        message: String,
        payloadJson: String?
    ) = withContext(dispatcher) {
        alertDao.insertAlert(
            RuntimeAlertEntity(
                pipelineId = pipelineId,
                nodeId = nodeId,
                level = level,
                code = code,
                message = message,
                payloadJson = payloadJson,
                timestamp = System.currentTimeMillis()
            )
        )
        Unit
    }

    private fun calculateSha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
