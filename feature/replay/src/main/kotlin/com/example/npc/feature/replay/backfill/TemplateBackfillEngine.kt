package com.example.npc.feature.replay.backfill

import com.example.npc.core.model.backfill.BackfillCriteria
import com.example.npc.core.model.backfill.BackfillReport
import com.example.npc.core.model.backfill.BackfillTargetTemplate
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.ExtractorKind
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.model.finance.TxStatus
import com.example.npc.core.storage.StorageGateway
import com.example.npc.core.storage.dao.EventDao
import com.example.npc.core.storage.dao.FinancialTransactionDao
import com.example.npc.core.storage.dao.ReplayEventSourceDao
import com.example.npc.core.storage.dao.ReplayHistoricalEvent
import com.example.npc.core.storage.entity.DynamicTemplateEntity
import com.example.npc.core.storage.entity.FinancialTransactionEntity
import com.example.npc.domain.usecase.TemplateBackfillEngine as IDomainTemplateBackfillEngine
import com.example.npc.feature.replay.engine.VirtualEffectEvaluator
import com.example.npc.pipeline.compiler.Signal
import com.example.npc.pipeline.nodes.api.effect.EffectBuffer
import com.example.npc.pipeline.nodes.api.effect.EffectKindId
import com.example.npc.pipeline.runtime.hotswap.CompiledTemplate
import com.google.re2j.Matcher
import com.google.re2j.Pattern
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Прогресс выполнения бэкфилла.
 */
sealed interface BackfillProgress {
    data class InProgress(
        val analyzedCount: Int,
        val totalCount: Int,
        val extractedCount: Int,
        val skippedCount: Int,
        val currentEventId: Long
    ) : BackfillProgress

    data class Completed(val report: BackfillReport) : BackfillProgress
    data class Failed(val error: Throwable, val analyzedSoFar: Int) : BackfillProgress
}

/**
 * Движок применения нового скомпилированного шаблона к историческому архиву событий (Backfill Execution Engine).
 *
 * Архитектурные инварианты:
 * 1. Идемпотентность: повторный запуск бэкфилла не дублирует записи транзакций.
 * 2. Неприкосновенность пользовательских правок (CRITICAL SAFETY GATE): события и транзакции со статусом
 *    USER_EDITED или USER_CONFIRMED ни при каких обстоятельствах не перезаписываются автоматическим бэкфиллом.
 * 3. Изоляция через Sandbox: сопоставление выполняется через [VirtualEffectEvaluator].
 * 4. Провенанс: при обновлении транзакции фиксируется extractorKind = TEMPLATE, новый templateId и status = CONFIRMED_AUTO.
 */
class TemplateBackfillEngine(
    private val eventSourceDao: ReplayEventSourceDao? = null,
    private val storageGateway: StorageGateway? = null,
    private val transactionDao: FinancialTransactionDao? = null,
    private val eventDao: EventDao? = null,
    private val effectEvaluator: VirtualEffectEvaluator = VirtualEffectEvaluator(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : IDomainTemplateBackfillEngine {

    override suspend fun executeBackfill(
        template: BackfillTargetTemplate,
        criteria: BackfillCriteria
    ): BackfillReport = withContext(dispatcher) {
        val compiled = toCompiledTemplate(template)
        executeBackfill(compiled, criteria)
    }

    suspend fun executeBackfill(
        template: DynamicTemplateEntity,
        criteria: BackfillCriteria = BackfillCriteria.Default
    ): BackfillReport = withContext(dispatcher) {
        val compiled = toCompiledTemplate(template)
        executeBackfill(compiled, criteria)
    }

    suspend fun executeBackfill(
        template: CompiledTemplate,
        criteria: BackfillCriteria = BackfillCriteria.Default
    ): BackfillReport = withContext(dispatcher) {
        val startTimeMs = System.currentTimeMillis()
        var analyzedEvents = 0
        var extractedTransactions = 0
        var skippedManualEdits = 0
        var lastSeenId = 0L

        val effectBuffer = EffectBuffer()

        while (analyzedEvents < criteria.maxEventsLimit) {
            currentCoroutineContext().ensureActive()
            val fetchLimit = minOf(criteria.chunkSize, criteria.maxEventsLimit - analyzedEvents)

            val batch = fetchHistoricalBatch(
                sourcePackage = template.sourceKey,
                afterId = lastSeenId,
                startTime = criteria.startTime,
                endTime = criteria.endTime,
                limit = fetchLimit
            )
            if (batch.isEmpty()) break

            for (event in batch) {
                currentCoroutineContext().ensureActive()
                lastSeenId = event.eventId

                // Игнорируем события других пакетов, если выборка была общей
                if (event.packageName != template.sourceKey) {
                    continue
                }

                analyzedEvents++

                // 1. Проверяем наличие существующей финансовой транзакции
                val existingEntity = findExistingTransaction(event.eventId)

                // 2. КРИТИЧЕСКАЯ ЗАЩИТА: проверка на ручные правки пользователя (USER_EDITED, USER_CONFIRMED)
                if (isUserProtected(event, existingEntity)) {
                    skippedManualEdits++
                    continue
                }

                // 3. Фильтр применимости: бэкфилл применяется ТОЛЬКО к событиям без транзакции или со статусом SUGGESTED
                if (!isEligibleForBackfill(event, existingEntity)) {
                    continue
                }

                // 4. Прогон шаблона через Sandbox (VirtualEffectEvaluator)
                val t0 = System.nanoTime()
                val candidateTx = matchTemplate(template, event.text, event.packageName, event.postTime)
                val durationNanos = System.nanoTime() - t0

                effectBuffer.reset()
                if (candidateTx != null) {
                    effectBuffer.begin(EffectKindId.SET_CATEGORY)
                    effectBuffer.putLong(Category.FINANCE.ordinal.toLong())
                    effectBuffer.putLong(1.0.toRawBits())
                    effectBuffer.end()

                    effectBuffer.begin(EffectKindId.CREATE_FINANCIAL_TRANSACTION)
                    effectBuffer.putRef(candidateTx)
                    effectBuffer.end()
                }

                val outcome = effectEvaluator.evaluate(
                    signal = if (candidateTx != null) Signal.PASS else Signal.DROP,
                    effectBuffer = effectBuffer,
                    durationNanos = durationNanos
                )

                // 5. Идемпотентный upsert в таблицу financial_transactions при успешном извлечении
                val matchedTransaction = outcome.transaction
                if (matchedTransaction != null) {
                    upsertTransaction(
                        eventId = event.eventId,
                        existingTx = existingEntity,
                        matchedTx = matchedTransaction,
                        templateId = template.id,
                        priority = template.priority,
                        isRefund = template.constants["isRefund"] == "true"
                    )
                    extractedTransactions++
                }

                if (analyzedEvents >= criteria.maxEventsLimit) break
            }
        }

        BackfillReport(
            templateId = template.id,
            totalAnalyzedEvents = analyzedEvents,
            extractedTransactionsCount = extractedTransactions,
            skippedManualEditsCount = skippedManualEdits,
            executionDurationMs = System.currentTimeMillis() - startTimeMs
        )
    }

    fun backfillFlow(
        template: CompiledTemplate,
        criteria: BackfillCriteria = BackfillCriteria.Default
    ): Flow<BackfillProgress> = flow {
        val totalInDb = countHistoricalEvents(template.sourceKey, criteria.startTime, criteria.endTime)
        val targetLimit = minOf(totalInDb, criteria.maxEventsLimit)

        var analyzedEvents = 0
        var extractedTransactions = 0
        var skippedManualEdits = 0
        var lastSeenId = 0L
        val startTimeMs = System.currentTimeMillis()
        val effectBuffer = EffectBuffer()

        try {
            while (analyzedEvents < targetLimit) {
                currentCoroutineContext().ensureActive()
                val fetchLimit = minOf(criteria.chunkSize, targetLimit - analyzedEvents)

                val batch = fetchHistoricalBatch(
                    sourcePackage = template.sourceKey,
                    afterId = lastSeenId,
                    startTime = criteria.startTime,
                    endTime = criteria.endTime,
                    limit = fetchLimit
                )
                if (batch.isEmpty()) break

                for (event in batch) {
                    currentCoroutineContext().ensureActive()
                    lastSeenId = event.eventId
                    if (event.packageName != template.sourceKey) continue

                    analyzedEvents++
                    val existingEntity = findExistingTransaction(event.eventId)

                    if (isUserProtected(event, existingEntity)) {
                        skippedManualEdits++
                        continue
                    }

                    if (!isEligibleForBackfill(event, existingEntity)) {
                        continue
                    }

                    val t0 = System.nanoTime()
                    val candidateTx = matchTemplate(template, event.text, event.packageName, event.postTime)
                    val durationNanos = System.nanoTime() - t0

                    effectBuffer.reset()
                    if (candidateTx != null) {
                        effectBuffer.begin(EffectKindId.SET_CATEGORY)
                        effectBuffer.putLong(Category.FINANCE.ordinal.toLong())
                        effectBuffer.putLong(1.0.toRawBits())
                        effectBuffer.end()

                        effectBuffer.begin(EffectKindId.CREATE_FINANCIAL_TRANSACTION)
                        effectBuffer.putRef(candidateTx)
                        effectBuffer.end()
                    }

                    val outcome = effectEvaluator.evaluate(
                        signal = if (candidateTx != null) Signal.PASS else Signal.DROP,
                        effectBuffer = effectBuffer,
                        durationNanos = durationNanos
                    )

                    val matchedTransaction = outcome.transaction
                    if (matchedTransaction != null) {
                        upsertTransaction(
                            eventId = event.eventId,
                            existingTx = existingEntity,
                            matchedTx = matchedTransaction,
                            templateId = template.id,
                            priority = template.priority,
                            isRefund = template.constants["isRefund"] == "true"
                        )
                        extractedTransactions++
                    }

                    if (analyzedEvents >= targetLimit) break
                }

                emit(
                    BackfillProgress.InProgress(
                        analyzedCount = analyzedEvents,
                        totalCount = targetLimit,
                        extractedCount = extractedTransactions,
                        skippedCount = skippedManualEdits,
                        currentEventId = lastSeenId
                    )
                )
            }

            emit(
                BackfillProgress.Completed(
                    BackfillReport(
                        templateId = template.id,
                        totalAnalyzedEvents = analyzedEvents,
                        extractedTransactionsCount = extractedTransactions,
                        skippedManualEditsCount = skippedManualEdits,
                        executionDurationMs = System.currentTimeMillis() - startTimeMs
                    )
                )
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            emit(BackfillProgress.Failed(t, analyzedEvents))
        }
    }.flowOn(dispatcher)

    private suspend fun fetchHistoricalBatch(
        sourcePackage: String,
        afterId: Long,
        startTime: Long,
        endTime: Long,
        limit: Int
    ): List<ReplayHistoricalEvent> {
        return if (eventSourceDao != null) {
            try {
                eventSourceDao.getEventsByPackageAfterId(sourcePackage, afterId, startTime, endTime, limit)
            } catch (_: NoSuchMethodError) {
                eventSourceDao.getEventsAfterId(afterId, startTime, endTime, limit)
                    .filter { it.packageName == sourcePackage }
            } catch (_: AbstractMethodError) {
                eventSourceDao.getEventsAfterId(afterId, startTime, endTime, limit)
                    .filter { it.packageName == sourcePackage }
            }
        } else {
            emptyList()
        }
    }

    private suspend fun countHistoricalEvents(sourcePackage: String, startTime: Long, endTime: Long): Int {
        return if (eventSourceDao != null) {
            try {
                eventSourceDao.countEventsByPackage(sourcePackage, startTime, endTime)
            } catch (_: Throwable) {
                eventSourceDao.countEvents(startTime, endTime)
            }
        } else {
            0
        }
    }

    private suspend fun findExistingTransaction(eventId: Long): FinancialTransactionEntity? {
        if (transactionDao != null) {
            return transactionDao.getByEventId(eventId)
        }
        if (storageGateway != null) {
            val domainTx = storageGateway.getTransactionByEventId(eventId)
            if (domainTx != null) {
                return FinancialTransactionEntity(
                    id = domainTx.id,
                    eventId = domainTx.eventId,
                    bank = domainTx.bank,
                    direction = domainTx.type.name,
                    amountMinor = domainTx.amount.minor,
                    currency = domainTx.amount.currency.value,
                    balanceMinor = domainTx.balance?.minor,
                    balanceCurrency = domainTx.balance?.currency?.value,
                    merchant = domainTx.merchant,
                    accountMask = domainTx.accountMask,
                    occurredAt = domainTx.occurredAt.toEpochMilli(),
                    extractorId = domainTx.extractorId,
                    extractorVersion = domainTx.extractorVersion,
                    createdAt = domainTx.createdAt.toEpochMilli(),
                    extractorKind = domainTx.extractorKind.name,
                    templateId = domainTx.templateId
                )
            }
        }
        return null
    }

    /**
     * Проверка защиты пользовательских данных (DoD / Архитектурный инвариант 3.4).
     */
    private suspend fun isUserProtected(event: ReplayHistoricalEvent, existing: FinancialTransactionEntity?): Boolean {
        // 1. Проверка по исторической категории события
        val cat = event.historicalCategory.uppercase()
        if (cat == "USER_EDITED" || cat == "USER_CONFIRMED") {
            return true
        }

        // 2. Проверка через EventDao (isUserCorrected, engineUsed)
        val eventEntity = eventDao?.getById(event.eventId)
        if (eventEntity != null && (eventEntity.isUserCorrected || eventEntity.engineUsed == "USER")) {
            return true
        }

        // 3. Проверка по существующей транзакции
        if (existing != null) {
            val kind = existing.extractorKind.uppercase()
            if (kind == "MANUAL" || kind == "USER_EDITED" || kind == "USER_CONFIRMED") {
                return true
            }
            val extractorId = existing.extractorId.lowercase()
            if (extractorId.contains("user") || extractorId.contains("manual")) {
                return true
            }
        }

        return false
    }

    /**
     * Проверка применимости: транзакция отсутствует ЛИБО находится в статусе SUGGESTED.
     */
    private fun isEligibleForBackfill(event: ReplayHistoricalEvent, existing: FinancialTransactionEntity?): Boolean {
        val json = event.historicalTransactionJson
        if (existing == null && (json == null || json.isBlank())) {
            return true
        }

        if (existing != null) {
            val cat = event.historicalCategory.uppercase()
            if (cat == "SUGGESTED") return true

            val kind = existing.extractorKind.uppercase()
            if (kind == "UNIVERSAL" || kind == "SUGGESTED") return true

            val extractorId = existing.extractorId.lowercase()
            if (extractorId.contains("suggest") || extractorId.contains("universal")) return true
        }

        return false
    }

    private suspend fun upsertTransaction(
        eventId: Long,
        existingTx: FinancialTransactionEntity?,
        matchedTx: FinancialTransaction,
        templateId: String,
        priority: Int,
        isRefund: Boolean
    ) {
        val targetId = existingTx?.id ?: 0L
        val occurredAtMs = matchedTx.occurredAt.toEpochMilli()
        val createdAtMs = existingTx?.createdAt ?: System.currentTimeMillis()

        val entity = FinancialTransactionEntity(
            id = targetId,
            eventId = eventId,
            bank = matchedTx.bank,
            direction = matchedTx.type.name,
            amountMinor = matchedTx.amount.minor,
            currency = matchedTx.amount.currency.value,
            balanceMinor = matchedTx.balance?.minor,
            balanceCurrency = matchedTx.balance?.currency?.value,
            merchant = matchedTx.merchant,
            accountMask = matchedTx.accountMask,
            occurredAt = occurredAtMs,
            extractorId = "template:$templateId",
            extractorVersion = 1,
            createdAt = createdAtMs,
            extractorKind = ExtractorKind.TEMPLATE.name,
            templateId = templateId,
            bankVersion = priority.toLong(),
            isRefund = isRefund
        )

        if (transactionDao != null) {
            transactionDao.insert(entity)
        } else if (storageGateway != null) {
            val domainTx = matchedTx.copy(
                id = targetId,
                eventId = eventId,
                extractorKind = ExtractorKind.TEMPLATE,
                templateId = templateId,
                txStatus = TxStatus.CONFIRMED_AUTO
            )
            storageGateway.insertTransaction(domainTx)
        }

        eventDao?.updateClassification(
            id = eventId,
            category = Category.FINANCE.name,
            confidence = 1.0,
            engineUsed = "TEMPLATE",
            fingerprint = null
        )
    }

    private fun matchTemplate(
        template: CompiledTemplate,
        text: String,
        sourcePackage: String,
        postTimeMs: Long
    ): FinancialTransaction? {
        // Быстрый префильтр по литералам
        for (lit in template.requiredLiterals) {
            if (!text.contains(lit, ignoreCase = true)) {
                return null
            }
        }

        // 1. Конвейер слотовых регулярок (Slot-Decomposed Regex Pipeline, OPT-PIPE-001)
        val pipeline = template.resolvedPipeline
        if (pipeline != null) {
            if (!pipeline.matchesAnchor(text)) {
                return null
            }

            val extracted = pipeline.extract(text) ?: return null
            val currency = resolveCurrency(
                extracted.currency ?: template.constants["currency"] ?: template.constants["defaultCurrency"],
                sourcePackage
            )
            val minorUnits = parseAmountToMinor(extracted.amount, currency)
            val balanceMinor = extracted.balance?.let { parseAmountToMinor(it, currency) }
            val txType = when (template.constants["opType"] ?: template.constants["transactionType"]) {
                "CREDIT", "INCOME" -> TransactionType.CREDIT
                "TRANSFER" -> TransactionType.TRANSFER
                else -> TransactionType.DEBIT
            }

            return FinancialTransaction(
                id = 0L,
                eventId = null,
                bank = sourcePackage,
                type = txType,
                amount = Money(minorUnits, currency),
                balance = balanceMinor?.let { Money(it, currency) },
                merchant = extracted.merchant,
                accountMask = extracted.cardMask,
                status = TransactionStatus.SUCCESS,
                occurredAt = Instant.ofEpochMilli(postTimeMs),
                extractorId = "template:${template.id}",
                extractorVersion = 1,
                rawText = text,
                extractorKind = ExtractorKind.TEMPLATE,
                templateId = template.id,
                txStatus = TxStatus.CONFIRMED_AUTO
            )
        }

        // 2. Монолитное RE2/J сопоставление
        val matcher = template.pattern.matcher(text)
        if (matcher.find()) {
            val amountStr = extractGroupSafely(matcher, "amount") 
                ?: extractGroupSafely(matcher, "TX_AMOUNT") 
                ?: extractGroupSafely(matcher, "tx_amount") 
                ?: return null
            val currStr = extractGroupSafely(matcher, "curr") ?: extractGroupSafely(matcher, "currency")
            val cardStr = extractGroupSafely(matcher, "card") 
                ?: extractGroupSafely(matcher, "mask") 
                ?: extractGroupSafely(matcher, "CARD_MASK") 
                ?: extractGroupSafely(matcher, "card_mask")
            val balStr = extractGroupSafely(matcher, "bal") 
                ?: extractGroupSafely(matcher, "balance") 
                ?: extractGroupSafely(matcher, "BALANCE")
            val merchantStr = extractGroupSafely(matcher, "merchant") ?: extractGroupSafely(matcher, "MERCHANT")

            val currency = resolveCurrency(
                currStr ?: template.constants["currency"] ?: template.constants["defaultCurrency"],
                sourcePackage
            )
            val minorUnits = parseAmountToMinor(amountStr, currency)
            val balanceMinor = balStr?.let { parseAmountToMinor(it, currency) }
            val txType = when (template.constants["opType"] ?: template.constants["transactionType"]) {
                "CREDIT", "INCOME" -> TransactionType.CREDIT
                "TRANSFER" -> TransactionType.TRANSFER
                else -> TransactionType.DEBIT
            }

            return FinancialTransaction(
                id = 0L,
                eventId = null,
                bank = sourcePackage,
                type = txType,
                amount = Money(minorUnits, currency),
                balance = balanceMinor?.let { Money(it, currency) },
                merchant = merchantStr,
                accountMask = cardStr,
                status = TransactionStatus.SUCCESS,
                occurredAt = Instant.ofEpochMilli(postTimeMs),
                extractorId = "template:${template.id}",
                extractorVersion = 1,
                rawText = text,
                extractorKind = ExtractorKind.TEMPLATE,
                templateId = template.id,
                txStatus = TxStatus.CONFIRMED_AUTO
            )
        }

        return null
    }

    private fun extractGroupSafely(matcher: Matcher, name: String): String? {
        return try {
            matcher.group(name)
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveCurrency(currStr: String?, sourcePackage: String): CurrencyCode {
        val lower = currStr?.lowercase() ?: ""
        return when {
            lower.contains("mdl") || lower.contains("лей") -> CurrencyCode.MDL
            lower.contains("rup") -> CurrencyCode.RUP
            lower.contains("rub") -> CurrencyCode.RUB
            lower.contains("usd") || lower.contains("$") -> CurrencyCode.USD
            lower.contains("eur") || lower.contains("€") -> CurrencyCode.EUR
            lower.contains("руб") || lower.contains("р.") -> {
                if (sourcePackage.contains("apb") || sourcePackage.contains("prisbank")) CurrencyCode.RUP else CurrencyCode.RUB
            }
            else -> CurrencyCode.MDL
        }
    }

    private fun parseAmountToMinor(amountStr: String, currency: CurrencyCode): Long {
        val clean = amountStr.replace(" ", "").replace("\u00A0", "").replace("\u202F", "")
        return if (clean.contains(',')) {
            val parts = clean.split(',')
            val intPart = parts[0].toLongOrNull() ?: 0L
            val fracPart = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L else 0L
            intPart * 100L + fracPart
        } else if (clean.contains('.')) {
            val parts = clean.split('.')
            val intPart = parts[0].toLongOrNull() ?: 0L
            val fracPart = if (parts.size > 1) parts[1].padEnd(2, '0').take(2).toLongOrNull() ?: 0L else 0L
            intPart * 100L + fracPart
        } else {
            (clean.toLongOrNull() ?: 0L) * 100L
        }
    }

    companion object {
        fun toCompiledTemplate(target: BackfillTargetTemplate): CompiledTemplate {
            return CompiledTemplate(
                id = target.id,
                sourceKey = target.sourcePackage,
                priority = target.priority,
                pattern = Pattern.compile(target.pattern),
                requiredLiterals = target.requiredLiterals,
                constants = target.constants
            )
        }

        fun toCompiledTemplate(entity: DynamicTemplateEntity): CompiledTemplate {
            return CompiledTemplate(
                id = entity.id,
                sourceKey = entity.sourceKey,
                priority = entity.priority,
                pattern = Pattern.compile(entity.pattern),
                constants = parseSimpleJsonMap(entity.constantsJson)
            )
        }

        private fun parseSimpleJsonMap(json: String): Map<String, String> {
            if (json.isBlank() || json == "{}") return emptyMap()
            val result = mutableMapOf<String, String>()
            val trimmed = json.trim().removePrefix("{").removeSuffix("}")
            val pairs = trimmed.split(",")
            for (pair in pairs) {
                val kv = pair.split(":")
                if (kv.size == 2) {
                    val key = kv[0].trim().removeSurrounding("\"")
                    val value = kv[1].trim().removeSurrounding("\"")
                    result[key] = value
                }
            }
            return result
        }
    }
}
