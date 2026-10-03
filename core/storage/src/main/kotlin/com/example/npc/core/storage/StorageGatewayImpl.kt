package com.example.npc.core.storage

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.Event
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.model.pipeline.EventProcessingTarget
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.ClassificationResult
import com.example.npc.core.model.classify.UserPrototype
import com.example.npc.core.model.finance.AggregatedSums
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.storage.dao.EventDao
import com.example.npc.core.storage.dao.FinancialTransactionDao
import com.example.npc.core.storage.dao.RawEventDao
import com.example.npc.core.storage.dao.SourceHealthDao
import com.example.npc.core.storage.dao.UserPrototypeDao
import com.example.npc.core.storage.entity.EventEntity
import com.example.npc.core.storage.entity.FinancialTransactionEntity
import com.example.npc.core.storage.entity.RawEventEntity
import com.example.npc.core.storage.entity.SourceHealthEntity
import com.example.npc.core.storage.mapper.EventMapper
import com.example.npc.core.storage.mapper.FinancialTransactionMapper
import com.example.npc.core.storage.mapper.RawEventMapper
import com.example.npc.core.storage.mapper.SourceHealthMapper
import com.example.npc.core.storage.mapper.UserPrototypeMapper
import com.example.npc.core.storage.dedup.FinancialTransactionDeduplicator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

class StorageGatewayImpl(
    private val database: AppDatabase,
    private val rawEventDao: RawEventDao = database.rawEventDao(),
    private val eventDao: EventDao = database.eventDao(),
    private val sourceHealthDao: SourceHealthDao = database.sourceHealthDao(),
    private val transactionDao: FinancialTransactionDao = database.financialTransactionDao(),
    private val prototypeDao: UserPrototypeDao = database.userPrototypeDao(),
    private val deduplicator: FinancialTransactionDeduplicator = FinancialTransactionDeduplicator(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val transactionRunner: (suspend (suspend () -> Any?) -> Any?)? = null
) : StorageGateway {

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (transactionRunner != null) {
            transactionRunner.invoke(block as suspend () -> Any?) as T
        } else {
            database.withTransaction(block)
        }
    }

    override suspend fun insertRawEvent(event: RawEvent): Long = withContext(ioDispatcher) {
        val entity = RawEventMapper.toEntity(event)
        try {
            rawEventDao.insert(entity)
        } catch (e: SQLiteConstraintException) {
            rawEventDao.findIdByHash(event.hash.value) ?: -1L
        }
    }

    override suspend fun insertEvent(event: Event): Long = withContext(ioDispatcher) {
        val entity = EventMapper.toEntity(event)
        eventDao.insert(entity)
    }

    override suspend fun upsertSourceHealth(health: SourceHealth): Unit = withContext(ioDispatcher) {
        val entity = SourceHealthMapper.toEntity(health)
        sourceHealthDao.upsert(entity)
    }

    override suspend fun findDuplicate(key: DeduplicationKey): Long? = withContext(ioDispatcher) {
        rawEventDao.findIdByHash(key.value)
    }

    override suspend fun getRawEvent(id: Long): RawEvent? = withContext(ioDispatcher) {
        rawEventDao.getById(id)?.let { RawEventMapper.toDomain(it) }
    }

    override suspend fun getRawEventByEventId(eventId: Long): RawEvent? = withContext(ioDispatcher) {
        val eventEntity = eventDao.getById(eventId) ?: return@withContext null
        rawEventDao.getById(eventEntity.rawId)?.let { RawEventMapper.toDomain(it) }
    }

    override suspend fun insertTransaction(transaction: FinancialTransaction): Long = withContext(ioDispatcher) {
        runInTransaction {
            val eventId = transaction.eventId ?: 0L
            val isUpdateOf = if (eventId > 0L) eventDao.getById(eventId)?.isUpdateOf else null
            saveOrMergeTransaction(eventId, isUpdateOf, transaction)
        }
    }

    override suspend fun saveProcessedEvent(
        event: Event,
        classification: ClassificationResult,
        transaction: FinancialTransaction?
    ): Long = withContext(ioDispatcher) {
        runInTransaction {
            val eventEntity = EventMapper.toEntity(
                domain = event,
                category = classification.category.name,
                confidence = classification.confidence,
                engineUsed = classification.engine.name,
                contentFingerprint = classification.contentFingerprint
            )
            val eventId = eventDao.insert(eventEntity)

            if (transaction != null) {
                saveOrMergeTransaction(
                    eventId = eventId,
                    isUpdateOf = event.isUpdateOf,
                    transaction = transaction
                )
            }

            eventId
        }
    }

    override suspend fun recordUserCorrection(eventId: Long, category: Category): Unit = withContext(ioDispatcher) {
        runInTransaction {
            eventDao.updateCategoryFromUser(eventId, category.name)
        }
    }

    override suspend fun recordUserCorrection(
        eventId: Long,
        packageName: String,
        contentFingerprint: String,
        newCategory: Category,
        correctedAt: Instant
    ): Unit = withContext(ioDispatcher) {
        runInTransaction {
            eventDao.updateCategoryFromUser(eventId, newCategory.name)
            prototypeDao.recordCorrection(
                pkg = packageName,
                fp = contentFingerprint,
                category = newCategory.name,
                now = correctedAt.toEpochMilli()
            )
        }
    }

    override suspend fun findMatchingPrototype(
        packageName: String,
        fingerprint: String
    ): UserPrototype? = withContext(ioDispatcher) {
        prototypeDao.findByPackageAndFingerprint(packageName, fingerprint)?.let {
            UserPrototypeMapper.toDomain(it)
        }
    }

    override fun observeEvents(limit: Int): Flow<List<Event>> {
        require(limit > 0) { "limit must be greater than 0" }
        return eventDao.observeLatest(limit)
            .map { list -> list.map { EventMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override fun observeTransactions(limit: Int): Flow<List<FinancialTransaction>> {
        require(limit > 0) { "limit must be greater than 0" }
        return transactionDao.observeLatest(limit)
            .map { list -> list.map { FinancialTransactionMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override fun observeTransactionsByPeriod(
        from: Instant,
        to: Instant
    ): Flow<List<FinancialTransaction>> {
        return transactionDao.observeByPeriod(from.toEpochMilli(), to.toEpochMilli())
            .map { list -> list.map { FinancialTransactionMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override suspend fun getTransactionByEventId(eventId: Long): FinancialTransaction? = withContext(ioDispatcher) {
        transactionDao.getByEventId(eventId)?.let { FinancialTransactionMapper.toDomain(it) }
    }

    override suspend fun getAggregatedTotals(
        direction: TransactionType,
        from: Instant,
        to: Instant
    ): Map<CurrencyCode, Long> = withContext(ioDispatcher) {
        val dtos = transactionDao.getAggregatedTotalsByCurrency(
            direction = direction.name,
            fromEpochMs = from.toEpochMilli(),
            toEpochMs = to.toEpochMilli()
        )
        dtos.associate { dto ->
            val code = CurrencyCode.ofOrNull(dto.currency) ?: CurrencyCode.RUP
            code to dto.totalMinor
        }
    }

    override fun observeAggregatedTotals(
        from: Instant,
        to: Instant,
        direction: TransactionType?,
        reduceExpenseByRefund: Boolean,
        includeSuggested: Boolean
    ): Flow<Map<CurrencyCode, AggregatedSums>> {
        return transactionDao.observeAggregatedTotalsByCurrency(
            fromEpochMs = from.toEpochMilli(),
            toEpochMs = to.toEpochMilli(),
            direction = direction?.name
        ).map { rows ->
            rows.associate { row ->
                val code = CurrencyCode.ofOrNull(row.currency) ?: CurrencyCode.RUP
                code to AggregatedSums(
                    expenseMinor = row.expenseMinor,
                    incomeMinor = row.incomeMinor,
                    refundMinor = row.refundMinor,
                    count = row.txCount
                )
            }
        }.flowOn(ioDispatcher)
    }

    override fun observeSourceHealth(): Flow<List<SourceHealth>> {
        return sourceHealthDao.observeAll()
            .map { list -> list.map { SourceHealthMapper.toDomain(it) } }
            .flowOn(ioDispatcher)
    }

    override suspend fun exportAllToJson(): String = withContext(ioDispatcher) {
        val (rawList, eventList, healthList) = runInTransaction {
            Triple(
                rawEventDao.getAll(),
                eventDao.getAll().sortedBy { it.id },
                sourceHealthDao.getAll()
            )
        }

        val exportedAt = Instant.now().toString()
        buildString {
            append("{\"version\":1,\"exportedAt\":\"")
            append(exportedAt)
            append("\",\"rawEvents\":[")
            rawList.forEachIndexed { i, r ->
                if (i > 0) append(",")
                append(serializeRawEvent(r))
            }
            append("],\"events\":[")
            eventList.forEachIndexed { i, e ->
                if (i > 0) append(",")
                append(serializeEvent(e))
            }
            append("],\"sourceHealth\":[")
            healthList.forEachIndexed { i, h ->
                if (i > 0) append(",")
                append(serializeSourceHealth(h))
            }
            append("]}")
        }
    }

    override suspend fun clearAllData(): Unit = withContext(ioDispatcher) {
        runInTransaction {
            transactionDao.deleteAll()
            prototypeDao.deleteAll()
            eventDao.deleteAll()
            rawEventDao.deleteAll()
            sourceHealthDao.deleteAll()
        }
    }

    override suspend fun deleteAllData(): Unit = clearAllData()

    override suspend fun deleteAll(): Unit = clearAllData()

    override suspend fun tryClaimEvent(eventId: Long): Boolean = withContext(ioDispatcher) {
        eventDao.tryClaim(eventId) > 0
    }

    override suspend fun getEventWithPackage(eventId: Long): EventProcessingTarget? = withContext(ioDispatcher) {
        val eventEntity = eventDao.getById(eventId) ?: return@withContext null
        val rawEventEntity = rawEventDao.getById(eventEntity.rawId) ?: return@withContext null
        val domainEvent = EventMapper.toDomain(eventEntity)
        val sourceId = try {
            SourceId(rawEventEntity.source)
        } catch (_: Throwable) {
            SourceId.NOTIFICATION
        }

        EventProcessingTarget(
            event = domainEvent,
            packageName = rawEventEntity.packageName,
            sourceId = sourceId,
            rawPayloadJson = rawEventEntity.payloadJson
        )
    }

    override suspend fun completeEventProcessing(
        eventId: Long,
        classification: ClassificationResult,
        transaction: FinancialTransaction?
    ): Boolean = withContext(ioDispatcher) {
        try {
            runInTransaction {
                val rowsUpdated = eventDao.updateClassification(
                    id = eventId,
                    category = classification.category.name,
                    confidence = classification.confidence,
                    engineUsed = classification.engine.name,
                    fingerprint = classification.contentFingerprint
                )
                if (rowsUpdated == 0) {
                    throw IllegalStateException("Event $eventId not found during completion")
                }

                if (transaction != null) {
                    val currentEvent = eventDao.getById(eventId)
                    saveOrMergeTransaction(
                        eventId = eventId,
                        isUpdateOf = currentEvent?.isUpdateOf,
                        transaction = transaction
                    )
                }
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    private suspend fun saveOrMergeTransaction(
        eventId: Long,
        isUpdateOf: Long?,
        transaction: FinancialTransaction
    ): Long {
        // 0. Если для данного eventId уже сохранена транзакция
        if (eventId > 0L) {
            val existingForEvent = transactionDao.getByEventId(eventId)
            if (existingForEvent != null) {
                val existingDomain = FinancialTransactionMapper.toDomain(existingForEvent)
                val merged = deduplicator.merge(existingDomain, transaction)
                val mergedEntity = FinancialTransactionMapper.toEntity(merged).copy(
                    id = existingForEvent.id,
                    eventId = eventId
                )
                transactionDao.insert(mergedEntity)
                return existingForEvent.id
            }
        }

        // 1. isUpdateOf handling: если событие является обновлением существующего
        if (isUpdateOf != null && isUpdateOf > 0L) {
            val parentTxnEntity = transactionDao.getByEventId(isUpdateOf)
            if (parentTxnEntity != null) {
                val parentTxn = FinancialTransactionMapper.toDomain(parentTxnEntity)
                val merged = deduplicator.merge(parentTxn, transaction)
                val mergedEntity = FinancialTransactionMapper.toEntity(merged).copy(
                    id = parentTxnEntity.id,
                    eventId = parentTxnEntity.eventId
                )
                transactionDao.insert(mergedEntity)
                if (eventId > 0L) {
                    eventDao.updateIsUpdateOf(eventId, isUpdateOf)
                }
                return parentTxnEntity.id
            }
        }

        // 2. Семантическая дедупликация со скользящим 5-минутным окном
        val windowMs = deduplicator.windowSeconds * 1000L
        val occurredMs = transaction.occurredAt.toEpochMilli()
        val fromMs = occurredMs - windowMs
        val toMs = occurredMs + windowMs
        val recentCandidates = transactionDao.getByPeriod(fromMs, toMs)
            .map { FinancialTransactionMapper.toDomain(it) }

        val duplicate = deduplicator.findDuplicate(transaction, recentCandidates)
        if (duplicate != null) {
            val merged = deduplicator.merge(duplicate, transaction)
            val mergedEntity = FinancialTransactionMapper.toEntity(merged).copy(
                id = duplicate.id,
                eventId = duplicate.eventId ?: if (eventId > 0L) eventId else null
            )
            transactionDao.insert(mergedEntity)
            val dupEventId = duplicate.eventId
            if (dupEventId != null && eventId > 0L && eventId != dupEventId) {
                eventDao.updateIsUpdateOf(eventId, dupEventId)
            }
            return duplicate.id
        }

        // 3. Новая уникальная транзакция
        val txnEntity = FinancialTransactionMapper.toEntity(
            transaction.copy(eventId = if (eventId > 0L) eventId else transaction.eventId)
        )
        return transactionDao.insert(txnEntity)
    }

    override suspend fun collapseExistingDuplicates(): Int = withContext(ioDispatcher) {
        runInTransaction {
            val allEntities = transactionDao.getAll().sortedBy { it.occurredAt }
            if (allEntities.size <= 1) return@runInTransaction 0

            val keptList = mutableListOf<FinancialTransactionEntity>()
            var collapsedCount = 0

            for (current in allEntities) {
                val currentDomain = FinancialTransactionMapper.toDomain(current)

                val matchIndex = keptList.indexOfFirst { kept ->
                    val keptDomain = FinancialTransactionMapper.toDomain(kept)
                    deduplicator.isDuplicate(currentDomain, keptDomain) ||
                        (current.eventId != null && kept.eventId != null && isEventsLinked(current.eventId, kept.eventId))
                }

                if (matchIndex != -1) {
                    val kept = keptList[matchIndex]
                    val keptDomain = FinancialTransactionMapper.toDomain(kept)
                    val merged = deduplicator.merge(keptDomain, currentDomain)

                    val mergedEntity = FinancialTransactionMapper.toEntity(merged).copy(
                        id = kept.id,
                        eventId = kept.eventId ?: current.eventId
                    )
                    transactionDao.insert(mergedEntity)
                    keptList[matchIndex] = mergedEntity

                    transactionDao.deleteById(current.id)

                    if (current.eventId != null && kept.eventId != null && current.eventId != kept.eventId) {
                        eventDao.updateIsUpdateOf(current.eventId, kept.eventId)
                    }

                    collapsedCount++
                } else {
                    keptList.add(current)
                }
            }

            collapsedCount
        }
    }

    private suspend fun isEventsLinked(eventId1: Long, eventId2: Long): Boolean {
        return try {
            val e1 = eventDao.getById(eventId1)
            val e2 = eventDao.getById(eventId2)
            e1?.isUpdateOf == eventId2 || e2?.isUpdateOf == eventId1
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun markEventFailed(eventId: Long, reason: String): Boolean = withContext(ioDispatcher) {
        try {
            eventDao.updateClassification(
                id = eventId,
                category = Category.OTHER.name,
                confidence = 0.0,
                engineUsed = "NONE",
                fingerprint = null
            ) > 0
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun getPendingUnprocessedEventIds(limit: Int): List<Long> = withContext(ioDispatcher) {
        eventDao.getPendingUnprocessedIds(limit)
    }

    private fun serializeRawEvent(e: RawEventEntity): String = buildString {
        append("{")
        append("\"id\":").append(e.id).append(",")
        append("\"seq\":").append(e.seq).append(",")
        append("\"source\":\"").append(escapeJson(e.source)).append("\",")
        append("\"packageName\":\"").append(escapeJson(e.packageName)).append("\",")
        append("\"receivedAt\":").append(e.receivedAt).append(",")
        append("\"payloadJson\":\"").append(escapeJson(e.payloadJson)).append("\",")
        append("\"hash\":\"").append(escapeJson(e.hash)).append("\"")
        append("}")
    }

    private fun serializeEvent(e: EventEntity): String = buildString {
        append("{")
        append("\"id\":").append(e.id).append(",")
        append("\"rawId\":").append(e.rawId).append(",")
        append("\"ts\":").append(e.ts).append(",")
        append("\"title\":\"").append(escapeJson(e.title)).append("\",")
        append("\"text\":\"").append(escapeJson(e.text)).append("\",")
        append("\"normalizedText\":\"").append(escapeJson(e.normalizedText)).append("\",")
        append("\"lang\":\"").append(escapeJson(e.lang)).append("\",")
        append("\"threadKey\":").append(e.threadKey?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",")
        append("\"isUpdateOf\":").append(e.isUpdateOf ?: "null").append(",")
        append("\"category\":\"").append(escapeJson(e.category)).append("\",")
        append("\"confidence\":").append(e.confidence).append(",")
        append("\"engineUsed\":\"").append(escapeJson(e.engineUsed)).append("\",")
        append("\"isUserCorrected\":").append(e.isUserCorrected).append(",")
        append("\"contentFingerprint\":").append(e.contentFingerprint?.let { "\"${escapeJson(it)}\"" } ?: "null")
        append("}")
    }

    private fun serializeSourceHealth(h: SourceHealthEntity): String = buildString {
        append("{")
        append("\"source\":\"").append(escapeJson(h.source)).append("\",")
        append("\"lastEventAt\":").append(h.lastEventAt ?: "null").append(",")
        append("\"events24h\":").append(h.events24h).append(",")
        append("\"lastError\":").append(h.lastError?.let { "\"${escapeJson(it)}\"" } ?: "null").append(",")
        append("\"queueDepth\":").append(h.queueDepth)
        append("}")
    }

    private fun escapeJson(str: String): String {
        val sb = StringBuilder()
        for (c in str) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (c.code in 0..0x1F) {
                        sb.append(String.format("\\u%04x", c.code))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }
}
