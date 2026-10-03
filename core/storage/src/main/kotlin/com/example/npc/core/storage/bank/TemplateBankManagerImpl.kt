package com.example.npc.core.storage.bank

import androidx.room.withTransaction
import com.example.npc.core.storage.AppDatabase
import com.example.npc.core.storage.dao.DynamicTemplateDao
import com.example.npc.core.storage.entity.DynamicTemplateEntity
import com.example.npc.core.storage.entity.TemplateBankMembershipEntity
import com.example.npc.core.storage.entity.TemplateBankVersionEntity
import com.example.npc.core.storage.entity.TemplateStatsEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.UUID

class TemplateBankManagerImpl(
    private val database: AppDatabase,
    private val templateDao: DynamicTemplateDao = database.dynamicTemplateDao(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val transactionRunner: (suspend (suspend () -> Any?) -> Any?)? = null
) : TemplateBankManager {

    private val mutex = Mutex()

    private val _currentBankFlow = MutableStateFlow(
        TemplateBankInfo(
            version = 0L,
            activeCount = 0,
            shadowCount = 0,
            quarantinedCount = 0,
            lastUpdatedAt = 0L
        )
    )
    override val currentBankFlow: StateFlow<TemplateBankInfo> = _currentBankFlow.asStateFlow()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (transactionRunner != null) {
            transactionRunner.invoke(block as suspend () -> Any?) as T
        } else {
            database.withTransaction(block)
        }
    }

    override suspend fun registerDraft(
        draft: DynamicTemplateDraft,
        initialState: TemplateState?
    ): String = withContext(dispatcher) {
        validatePattern(draft.pattern)

        mutex.withLock {
            val now = System.currentTimeMillis()
            val canonicalHash = calculateCanonicalHash(draft.sourceKey, draft.pattern)

            val targetState = initialState ?: when (draft.origin) {
                "USER" -> TemplateState.ACTIVE
                "AUTO" -> TemplateState.SHADOW
                else -> TemplateState.DRAFT
            }

            val existing = templateDao.getByCanonicalHash(canonicalHash)
            if (existing != null) {
                val updatedEntity = existing.copy(
                    bindingsJson = draft.bindingsJson,
                    constantsJson = draft.constantsJson,
                    amountFormatJson = draft.amountFormatJson,
                    priority = draft.priority,
                    specificity = draft.specificity.toDouble(),
                    updatedAt = now,
                    state = targetState.name
                )
                runInTransaction {
                    templateDao.update(updatedEntity)
                    if (targetState == TemplateState.ACTIVE && existing.state != TemplateState.ACTIVE.name) {
                        commitNewBankVersionLocked("UPDATE_ACTIVE_USER", now)
                    }
                }
                updateBankInfoLocked(now)
                return@withLock existing.id
            }

            val templateId = UUID.randomUUID().toString()
            val entity = DynamicTemplateEntity(
                id = templateId,
                sourceKey = draft.sourceKey,
                tier = draft.tier,
                origin = draft.origin,
                state = targetState.name,
                priority = draft.priority,
                pattern = draft.pattern,
                bindingsJson = draft.bindingsJson,
                constantsJson = draft.constantsJson,
                amountFormatJson = draft.amountFormatJson,
                specVersion = 1,
                compilerVersion = 1,
                canonicalHash = canonicalHash,
                specificity = draft.specificity.toDouble(),
                parentTemplateId = null,
                sampleEventId = draft.sampleEventId,
                createdAt = now,
                updatedAt = now,
                stateReason = "Registered as ${targetState.name}"
            )

            runInTransaction {
                templateDao.insert(entity)
                templateDao.insertStats(TemplateStatsEntity(templateId = templateId))

                if (targetState == TemplateState.ACTIVE) {
                    commitNewBankVersionLocked("REGISTER_ACTIVE_USER", now)
                }
            }

            updateBankInfoLocked(now)
            templateId
        }
    }

    override suspend fun validateTemplate(templateId: String): BankTransitionResult = withContext(dispatcher) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            runInTransaction {
                val template = getTemplateOrThrow(templateId)
                val currentState = TemplateState.valueOf(template.state)
                validateTransition(currentState, TemplateState.VALIDATED, templateId)

                templateDao.updateState(templateId, TemplateState.VALIDATED.name, "Validated", now)
                val currentVersion = templateDao.getLatestBankVersion()?.version ?: 0L
                updateBankInfoLocked(now)
                BankTransitionResult(templateId, currentState, TemplateState.VALIDATED, currentVersion)
            }
        }
    }

    override suspend fun activateTemplate(templateId: String): BankTransitionResult = withContext(dispatcher) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            runInTransaction {
                val template = getTemplateOrThrow(templateId)
                val currentState = TemplateState.valueOf(template.state)
                validateTransition(currentState, TemplateState.ACTIVE, templateId)

                templateDao.updateState(templateId, TemplateState.ACTIVE.name, "Activated", now)
                val newVersion = commitNewBankVersionLocked("ACTIVATE", now)
                updateBankInfoLocked(now)
                BankTransitionResult(templateId, currentState, TemplateState.ACTIVE, newVersion)
            }
        }
    }

    override suspend fun transitionToShadow(templateId: String): BankTransitionResult = withContext(dispatcher) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            runInTransaction {
                val template = getTemplateOrThrow(templateId)
                val currentState = TemplateState.valueOf(template.state)
                validateTransition(currentState, TemplateState.SHADOW, templateId)

                templateDao.updateState(templateId, TemplateState.SHADOW.name, "Moved to shadow", now)
                val newVersion = if (currentState == TemplateState.ACTIVE) {
                    commitNewBankVersionLocked("SHADOW_FROM_ACTIVE", now)
                } else {
                    templateDao.getLatestBankVersion()?.version ?: 0L
                }
                updateBankInfoLocked(now)
                BankTransitionResult(templateId, currentState, TemplateState.SHADOW, newVersion)
            }
        }
    }

    override suspend fun quarantineTemplate(
        templateId: String,
        reason: String
    ): BankTransitionResult = withContext(dispatcher) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            runInTransaction {
                val template = getTemplateOrThrow(templateId)
                val currentState = TemplateState.valueOf(template.state)
                validateTransition(currentState, TemplateState.QUARANTINED, templateId)

                templateDao.updateState(templateId, TemplateState.QUARANTINED.name, reason, now)
                val newVersion = commitNewBankVersionLocked("QUARANTINE: $reason", now)
                updateBankInfoLocked(now)
                BankTransitionResult(templateId, currentState, TemplateState.QUARANTINED, newVersion)
            }
        }
    }

    override suspend fun disableTemplate(templateId: String): BankTransitionResult = withContext(dispatcher) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            runInTransaction {
                val template = getTemplateOrThrow(templateId)
                val currentState = TemplateState.valueOf(template.state)
                validateTransition(currentState, TemplateState.DISABLED, templateId)

                templateDao.updateState(templateId, TemplateState.DISABLED.name, "Disabled", now)
                val newVersion = if (currentState == TemplateState.ACTIVE) {
                    commitNewBankVersionLocked("DISABLE_ACTIVE", now)
                } else {
                    templateDao.getLatestBankVersion()?.version ?: 0L
                }
                updateBankInfoLocked(now)
                BankTransitionResult(templateId, currentState, TemplateState.DISABLED, newVersion)
            }
        }
    }

    override suspend fun promoteShadowTemplate(templateId: String): BankTransitionResult {
        return activateTemplate(templateId)
    }

    override suspend fun getTemplate(templateId: String): DynamicTemplateEntity? = withContext(dispatcher) {
        templateDao.getById(templateId)
    }

    override suspend fun getBankInfo(): TemplateBankInfo = withContext(dispatcher) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val latest = templateDao.getLatestBankVersion()?.version ?: 0L
            val active = templateDao.countByState(TemplateState.ACTIVE.name)
            val shadow = templateDao.countByState(TemplateState.SHADOW.name)
            val quarantined = templateDao.countByState(TemplateState.QUARANTINED.name)
            val info = TemplateBankInfo(
                version = latest,
                activeCount = active,
                shadowCount = shadow,
                quarantinedCount = quarantined,
                lastUpdatedAt = now
            )
            _currentBankFlow.value = info
            info
        }
    }

    override suspend fun getLatestBankVersion(): Long = withContext(dispatcher) {
        templateDao.getLatestBankVersion()?.version ?: 0L
    }

    private suspend fun getTemplateOrThrow(templateId: String): DynamicTemplateEntity {
        return templateDao.getById(templateId)
            ?: throw NoSuchElementException("Template with id $templateId not found")
    }

    private fun validateTransition(from: TemplateState, to: TemplateState, templateId: String) {
        if (from == to) return

        val isValid = when (from) {
            TemplateState.DRAFT -> to in setOf(TemplateState.VALIDATED, TemplateState.ACTIVE, TemplateState.SHADOW, TemplateState.DISABLED, TemplateState.SUPERSEDED)
            TemplateState.VALIDATED -> to in setOf(TemplateState.ACTIVE, TemplateState.SHADOW, TemplateState.DISABLED, TemplateState.SUPERSEDED)
            TemplateState.ACTIVE -> to in setOf(TemplateState.QUARANTINED, TemplateState.SHADOW, TemplateState.DISABLED, TemplateState.SUPERSEDED)
            TemplateState.SHADOW -> to in setOf(TemplateState.ACTIVE, TemplateState.QUARANTINED, TemplateState.DISABLED, TemplateState.SUPERSEDED)
            TemplateState.QUARANTINED -> to in setOf(TemplateState.DRAFT, TemplateState.VALIDATED, TemplateState.ACTIVE, TemplateState.DISABLED, TemplateState.SUPERSEDED)
            TemplateState.DISABLED -> to in setOf(TemplateState.DRAFT, TemplateState.VALIDATED, TemplateState.SUPERSEDED)
            TemplateState.SUPERSEDED -> false
        }

        if (!isValid) {
            throw IllegalStateException("Invalid state transition for template $templateId: $from -> $to")
        }
    }

    private suspend fun commitNewBankVersionLocked(cause: String, now: Long): Long {
        val currentVersionEntity = templateDao.getLatestBankVersion()
        val currentVersion = currentVersionEntity?.version ?: 0L
        val nextVersion = currentVersion + 1L

        val activeTemplates = templateDao.getByState(TemplateState.ACTIVE.name)
        val sortedActiveIds = activeTemplates.map { it.id }.sorted()
        val membershipHash = calculateMembershipHash(sortedActiveIds)

        val versionEntity = TemplateBankVersionEntity(
            version = nextVersion,
            parentVersion = if (currentVersion > 0L) currentVersion else null,
            membershipHash = membershipHash,
            createdAt = now,
            cause = cause
        )
        templateDao.insertBankVersion(versionEntity)

        if (sortedActiveIds.isNotEmpty()) {
            val memberships = sortedActiveIds.map { id ->
                TemplateBankMembershipEntity(version = nextVersion, templateId = id)
            }
            templateDao.insertMemberships(memberships)
        }

        return nextVersion
    }

    private suspend fun updateBankInfoLocked(now: Long) {
        val latest = templateDao.getLatestBankVersion()?.version ?: 0L
        val active = templateDao.countByState(TemplateState.ACTIVE.name)
        val shadow = templateDao.countByState(TemplateState.SHADOW.name)
        val quarantined = templateDao.countByState(TemplateState.QUARANTINED.name)
        _currentBankFlow.value = TemplateBankInfo(
            version = latest,
            activeCount = active,
            shadowCount = shadow,
            quarantinedCount = quarantined,
            lastUpdatedAt = now
        )
    }

    private fun validatePattern(pattern: String) {
        try {
            com.google.re2j.Pattern.compile(pattern)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid regex pattern: $pattern", e)
        }
    }

    companion object {
        fun calculateCanonicalHash(sourceKey: String, pattern: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest("$sourceKey:$pattern".toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun calculateMembershipHash(sortedActiveIds: List<String>): String {
            val md = MessageDigest.getInstance("SHA-256")
            val content = if (sortedActiveIds.isEmpty()) "" else sortedActiveIds.sorted().joinToString(",")
            val bytes = md.digest(content.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
