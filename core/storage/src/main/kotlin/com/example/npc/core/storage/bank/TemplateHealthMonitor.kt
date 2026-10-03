package com.example.npc.core.storage.bank

import com.example.npc.core.storage.dao.DynamicTemplateDao
import com.example.npc.core.storage.entity.TemplateStatsEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class ExecutionFeedback(
    val templateId: String,
    val isSuccess: Boolean,
    val executionDurationNanos: Long = 0L,
    val isUserCorrection: Boolean = false,
    val isShadowAgreement: Boolean = false
)

data class TemplateHealthStatus(
    val templateId: String,
    val hits: Long,
    val failureRate: Float,
    val isPromotionReady: Boolean,
    val isQuarantineRecommended: Boolean
)

class TemplateHealthMonitor(
    private val templateDao: DynamicTemplateDao,
    private val bankManager: TemplateBankManager,
    private val timeoutThresholdNanos: Long = 5_000_000L, // 5 ms threshold (ADR-304/Spec)
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val consecutiveFailures = ConcurrentHashMap<String, AtomicInteger>()

    suspend fun recordFeedback(feedback: ExecutionFeedback) = withContext(dispatcher) {
        val templateId = feedback.templateId
        if (templateId.isBlank()) return@withContext

        val effectiveSuccess = feedback.isSuccess && (feedback.executionDurationNanos < timeoutThresholdNanos)

        val existingStats = templateDao.getStats(templateId)
            ?: TemplateStatsEntity(templateId = templateId).also {
                templateDao.insertStats(it)
            }

        val now = System.currentTimeMillis()
        val newHits = existingStats.hits + 1L
        val newParseFailures = if (!effectiveSuccess) existingStats.parseFailures + 1L else existingStats.parseFailures
        val newUserCorrections = if (feedback.isUserCorrection) existingStats.userCorrections + 1L else existingStats.userCorrections
        val newShadowAgreements = if (feedback.isShadowAgreement) existingStats.shadowAgreements + 1L else existingStats.shadowAgreements
        val newShadowDisagreements = if (!feedback.isShadowAgreement && !effectiveSuccess) {
            existingStats.shadowDisagreements + 1L
        } else {
            existingStats.shadowDisagreements
        }
        val newLastHitAt = if (effectiveSuccess) now else existingStats.lastHitAt

        val updatedStats = existingStats.copy(
            hits = newHits,
            parseFailures = newParseFailures,
            userCorrections = newUserCorrections,
            shadowAgreements = newShadowAgreements,
            shadowDisagreements = newShadowDisagreements,
            lastHitAt = newLastHitAt
        )
        templateDao.updateStats(updatedStats)

        val consecutiveCounter = consecutiveFailures.computeIfAbsent(templateId) { AtomicInteger(0) }
        val currentConsecutive = if (effectiveSuccess) {
            consecutiveCounter.set(0)
            0
        } else {
            consecutiveCounter.incrementAndGet()
        }

        val template = templateDao.getById(templateId) ?: return@withContext
        val state = try {
            TemplateState.valueOf(template.state)
        } catch (_: Exception) {
            return@withContext
        }

        if (state == TemplateState.QUARANTINED || state == TemplateState.DISABLED || state == TemplateState.SUPERSEDED) {
            return@withContext
        }

        val failureRate = if (newHits > 0) newParseFailures.toFloat() / newHits.toFloat() else 0.0f

        // Quarantine check: 2 consecutive failures OR failureRate > 0.05 when hits >= 20
        if (currentConsecutive >= 2) {
            bankManager.quarantineTemplate(templateId, "Consecutive parse failures ($currentConsecutive)")
            return@withContext
        } else if (newHits >= 20 && failureRate > 0.05f) {
            bankManager.quarantineTemplate(templateId, "Failure rate exceeded 5% (${(failureRate * 100).toInt()}%)")
            return@withContext
        }

        // Auto-promotion: SHADOW -> ACTIVE when shadowAgreements >= 5 and 0 failures
        if (state == TemplateState.SHADOW) {
            if (newShadowAgreements >= 5 && newParseFailures == 0L) {
                bankManager.activateTemplate(templateId)
            }
        }
    }

    suspend fun getHealthStatus(templateId: String): TemplateHealthStatus? = withContext(dispatcher) {
        val stats = templateDao.getStats(templateId) ?: return@withContext null
        val failureRate = if (stats.hits > 0) stats.parseFailures.toFloat() / stats.hits.toFloat() else 0.0f
        val template = templateDao.getById(templateId)
        val isShadow = template?.state == TemplateState.SHADOW.name
        val consecutive = consecutiveFailures[templateId]?.get() ?: 0

        val isPromotionReady = isShadow && stats.shadowAgreements >= 5 && stats.parseFailures == 0L
        val isQuarantineRecommended = consecutive >= 2 || (stats.hits >= 20 && failureRate > 0.05f)

        TemplateHealthStatus(
            templateId = templateId,
            hits = stats.hits,
            failureRate = failureRate,
            isPromotionReady = isPromotionReady,
            isQuarantineRecommended = isQuarantineRecommended
        )
    }

    fun getConsecutiveFailures(templateId: String): Int {
        return consecutiveFailures[templateId]?.get() ?: 0
    }

    fun resetConsecutiveFailures(templateId: String) {
        consecutiveFailures[templateId]?.set(0)
    }
}
