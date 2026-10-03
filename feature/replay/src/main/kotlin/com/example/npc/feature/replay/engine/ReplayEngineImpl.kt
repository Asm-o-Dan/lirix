package com.example.npc.feature.replay.engine

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.finance.CurrencyCode
import com.example.npc.core.model.finance.Direction
import com.example.npc.core.model.finance.FinancialTransaction
import com.example.npc.core.model.finance.Money
import com.example.npc.core.model.finance.TransactionStatus
import com.example.npc.core.model.finance.TransactionType
import com.example.npc.core.storage.dao.ReplayEventSourceDao
import com.example.npc.core.storage.dao.ReplayHistoricalEvent
import com.example.npc.feature.replay.report.ConfidenceShift
import com.example.npc.feature.replay.report.DiffMetrics
import com.example.npc.feature.replay.report.DiscrepancyType
import com.example.npc.feature.replay.report.EventDiscrepancy
import com.example.npc.feature.replay.report.ReplayDiffReport
import com.example.npc.pipeline.compiler.CompiledPipeline
import com.example.npc.pipeline.compiler.Signal
import com.example.npc.pipeline.nodes.api.frame.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import java.time.Instant

interface ReplayEngine {

    /**
     * Запускает симуляцию черновика конвейера на исторических данных.
     *
     * @param draftPipeline Скомпилированный конвейер-черновик.
     * @param activePipeline Текущий активный конвейер (для парного сравнения) либо null (сравнение с историей в БД).
     * @param criteria Критерии выборки событий (временной диапазон, фильтр пакетов, лимит).
     * @return Холодный [Flow], транслирующий статус выполнения и итоговый [ReplayDiffReport].
     */
    fun runSimulation(
        draftPipeline: CompiledPipeline,
        activePipeline: CompiledPipeline? = null,
        criteria: ReplayCriteria = ReplayCriteria.Default
    ): Flow<ReplayStatus>
}

class ReplayEngineImpl(
    private val eventSourceDao: ReplayEventSourceDao,
    private val effectEvaluator: VirtualEffectEvaluator = VirtualEffectEvaluator()
) : ReplayEngine {

    override fun runSimulation(
        draftPipeline: CompiledPipeline,
        activePipeline: CompiledPipeline?,
        criteria: ReplayCriteria
    ): Flow<ReplayStatus> = flow {
        val (startTime, endTime) = when (val tr = criteria.timeRange) {
            is TimeRange.Last24Hours -> (System.currentTimeMillis() - 24 * 60 * 60 * 1000L) to System.currentTimeMillis()
            is TimeRange.Last7Days -> (System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L) to System.currentTimeMillis()
            is TimeRange.AllTime -> 0L to Long.MAX_VALUE
            is TimeRange.Custom -> tr.start.toEpochMilli() to tr.end.toEpochMilli()
        }

        val totalInDb = try {
            eventSourceDao.countEvents(startTime, endTime)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            emit(ReplayStatus.Failed(t, 0))
            return@flow
        }

        val totalCount = minOf(totalInDb, criteria.maxEventsLimit)

        if (totalCount == 0) {
            emit(
                ReplayStatus.Completed(
                    ReplayDiffReport(
                        metrics = DiffMetrics(
                            totalProcessedEvents = 0,
                            identicalOutcomesCount = 0,
                            discrepancyCount = 0,
                            matchRatePercent = 100.0f,
                            avgDraftLatencyUs = 0L,
                            avgActiveLatencyUs = 0L,
                            p95DraftLatencyUs = 0L,
                            p95ActiveLatencyUs = 0L,
                            confidenceShift = ConfidenceShift(0.0, 0.0, 0.0)
                        ),
                        discrepancies = emptyList(),
                        executionDurationMs = 0L
                    )
                )
            )
            return@flow
        }

        val draftFrame = Frame(draftPipeline.layout)
        val activeFrame = activePipeline?.let { Frame(it.layout) }

        var lastSeenId = 0L
        var processedCount = 0
        var identicalCount = 0
        val discrepancies = mutableListOf<EventDiscrepancy>()
        val draftLatenciesUs = mutableListOf<Long>()
        val activeLatenciesUs = mutableListOf<Long>()
        var draftConfidenceSum = 0.0
        var activeConfidenceSum = 0.0
        val startTimeMs = System.currentTimeMillis()

        try {
            while (processedCount < totalCount) {
                currentCoroutineContext().ensureActive()
                val limit = minOf(criteria.chunkSize, totalCount - processedCount)
                val batch = eventSourceDao.getEventsAfterId(lastSeenId, startTime, endTime, limit)
                if (batch.isEmpty()) break

                for (event in batch) {
                    currentCoroutineContext().ensureActive()
                    lastSeenId = event.eventId

                    if (criteria.packageFilter != null && criteria.packageFilter.isNotEmpty() && event.packageName !in criteria.packageFilter) {
                        continue
                    }

                    // 1. Execute draft pipeline
                    draftFrame.resetRefs()
                    if (draftFrame.texts.isNotEmpty()) draftFrame.texts[0].set(event.text)
                    if (draftFrame.texts.size > 1) draftFrame.texts[1].set(event.packageName)
                    if (draftFrame.longs.isNotEmpty()) draftFrame.longs[0] = event.postTime

                    val t0Draft = System.nanoTime()
                    val draftSignal = try {
                        draftPipeline.execute(draftFrame)
                    } catch (c: CancellationException) {
                        throw c
                    } catch (t: Throwable) {
                        Signal.FAULT
                    }
                    val t1Draft = System.nanoTime()
                    val draftDurationNanos = t1Draft - t0Draft
                    val draftDurationUs = draftDurationNanos / 1000
                    draftLatenciesUs.add(draftDurationUs)

                    val draftOutcome = effectEvaluator.evaluate(draftSignal, draftFrame.effects, draftDurationNanos)
                    draftConfidenceSum += draftOutcome.confidence

                    // 2. Baseline outcome
                    val (baselineOutcome, baselineDurationUs) = if (activePipeline != null && activeFrame != null) {
                        activeFrame.resetRefs()
                        if (activeFrame.texts.isNotEmpty()) activeFrame.texts[0].set(event.text)
                        if (activeFrame.texts.size > 1) activeFrame.texts[1].set(event.packageName)
                        if (activeFrame.longs.isNotEmpty()) activeFrame.longs[0] = event.postTime

                        val t0Active = System.nanoTime()
                        val activeSignal = try {
                            activePipeline.execute(activeFrame)
                        } catch (c: CancellationException) {
                            throw c
                        } catch (t: Throwable) {
                            Signal.FAULT
                        }
                        val t1Active = System.nanoTime()
                        val actDurationNanos = t1Active - t0Active
                        val actDurationUs = actDurationNanos / 1000
                        activeLatenciesUs.add(actDurationUs)

                        effectEvaluator.evaluate(activeSignal, activeFrame.effects, actDurationNanos) to actDurationUs
                    } else {
                        val baseCat = runCatching { Category.valueOf(event.historicalCategory) }.getOrDefault(Category.UNCLASSIFIED)
                        val baseTx = parseHistoricalTransaction(event.historicalTransactionJson)
                        EvaluationOutcome(
                            signal = Signal.PASS,
                            category = baseCat,
                            confidence = event.historicalConfidence,
                            transaction = baseTx,
                            executionDurationNanos = 0L
                        ) to 0L
                    }
                    activeConfidenceSum += baselineOutcome.confidence

                    // 3. Diff analysis
                    val discrepancy = compareOutcomes(
                        event = event,
                        draft = draftOutcome,
                        baseline = baselineOutcome,
                        draftLatencyUs = draftDurationUs,
                        baselineLatencyUs = baselineDurationUs
                    )

                    if (discrepancy == null) {
                        identicalCount++
                    } else {
                        discrepancies.add(discrepancy)
                    }

                    processedCount++
                    if (processedCount >= totalCount) break
                }

                val progress = if (totalCount > 0) processedCount.toFloat() / totalCount.toFloat() else 0f
                emit(
                    ReplayStatus.Running(
                        processedCount = processedCount,
                        totalCount = totalCount,
                        percentProgress = progress.coerceIn(0f, 1f),
                        currentEventId = lastSeenId
                    )
                )
                yield()
            }

            val execDurationMs = System.currentTimeMillis() - startTimeMs
            val matchRate = if (processedCount > 0) (identicalCount.toFloat() / processedCount.toFloat()) * 100.0f else 100.0f
            val avgDraftLatency = if (draftLatenciesUs.isNotEmpty()) draftLatenciesUs.average().toLong() else 0L
            val avgActiveLatency = if (activeLatenciesUs.isNotEmpty()) activeLatenciesUs.average().toLong() else 0L
            val p95DraftLatency = percentile(draftLatenciesUs, 95)
            val p95ActiveLatency = percentile(activeLatenciesUs, 95)

            val avgDraftConf = if (processedCount > 0) draftConfidenceSum / processedCount else 0.0
            val avgActiveConf = if (processedCount > 0) activeConfidenceSum / processedCount else 0.0
            val confShift = ConfidenceShift(
                averageDraft = avgDraftConf,
                averageActive = avgActiveConf,
                delta = avgDraftConf - avgActiveConf
            )

            val report = ReplayDiffReport(
                metrics = DiffMetrics(
                    totalProcessedEvents = processedCount,
                    identicalOutcomesCount = identicalCount,
                    discrepancyCount = discrepancies.size,
                    matchRatePercent = matchRate.coerceIn(0f, 100f),
                    avgDraftLatencyUs = avgDraftLatency,
                    avgActiveLatencyUs = avgActiveLatency,
                    p95DraftLatencyUs = p95DraftLatency,
                    p95ActiveLatencyUs = p95ActiveLatency,
                    confidenceShift = confShift
                ),
                discrepancies = discrepancies,
                executionDurationMs = execDurationMs
            )
            emit(ReplayStatus.Completed(report))

        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            emit(ReplayStatus.Failed(t, processedCount))
        }
    }.flowOn(Dispatchers.Default)

    private fun compareOutcomes(
        event: ReplayHistoricalEvent,
        draft: EvaluationOutcome,
        baseline: EvaluationOutcome,
        draftLatencyUs: Long,
        baselineLatencyUs: Long
    ): EventDiscrepancy? {
        val discrepancyType: DiscrepancyType? = when {
            draft.isFault -> DiscrepancyType.EXECUTION_FAILED
            draft.isDropped != baseline.isDropped -> DiscrepancyType.DROPPED_VS_PASSED
            draft.category != baseline.category -> DiscrepancyType.CATEGORY_MISMATCH
            baseline.confidence - draft.confidence > 0.25 -> DiscrepancyType.CONFIDENCE_DROP
            draft.transaction != null && baseline.transaction == null -> DiscrepancyType.TRANSACTION_EXTRACTED_VS_NONE
            draft.transaction == null && baseline.transaction != null -> DiscrepancyType.TRANSACTION_MISSED
            draft.transaction != null && baseline.transaction != null -> {
                when {
                    draft.transaction.amount.minor != baseline.transaction.amount.minor -> DiscrepancyType.TRANSACTION_AMOUNT_DIFF
                    draft.transaction.amount.currency != baseline.transaction.amount.currency -> DiscrepancyType.TRANSACTION_CURRENCY_DIFF
                    else -> null
                }
            }
            else -> null
        }

        return discrepancyType?.let { type ->
            EventDiscrepancy(
                eventId = event.eventId,
                packageName = event.packageName,
                notificationTitle = event.title,
                notificationText = event.text,
                discrepancyType = type,
                baselineCategory = baseline.category,
                draftCategory = draft.category,
                baselineTransaction = baseline.transaction,
                draftTransaction = draft.transaction,
                baselineLatencyUs = baselineLatencyUs,
                draftLatencyUs = draftLatencyUs
            )
        }
    }

    private fun parseHistoricalTransaction(str: String?): FinancialTransaction? {
        if (str == null || str.isBlank()) return null
        val amount = str.toLongOrNull() ?: return null
        return FinancialTransaction(
            id = 0L,
            eventId = null,
            bank = "UNKNOWN",
            type = TransactionType.DEBIT,
            amount = Money(amount, CurrencyCode.MDL),
            balance = null,
            merchant = null,
            accountMask = null,
            status = TransactionStatus.COMPLETED,
            occurredAt = Instant.now(),
            extractorId = "legacy",
            extractorVersion = 1,
            rawText = "historical transaction"
        )
    }

    private fun percentile(latencies: List<Long>, percentile: Int): Long {
        if (latencies.isEmpty()) return 0L
        val sorted = latencies.sorted()
        val index = (percentile / 100.0 * (sorted.size - 1)).toInt()
        return sorted[index.coerceIn(0, sorted.size - 1)]
    }
}
