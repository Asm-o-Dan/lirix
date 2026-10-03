package com.example.npc.core.storage.bank

import com.example.npc.core.storage.entity.DynamicTemplateEntity
import kotlinx.coroutines.flow.StateFlow

enum class TemplateState {
    DRAFT,
    VALIDATED,
    ACTIVE,
    SHADOW,
    QUARANTINED,
    DISABLED,
    SUPERSEDED
}

data class DynamicTemplateDraft(
    val sourceKey: String,
    val tier: String,
    val origin: String,
    val pattern: String,
    val bindingsJson: String,
    val constantsJson: String,
    val amountFormatJson: String,
    val specificity: Float,
    val sampleEventId: String?,
    val priority: Int = 100
)

data class BankTransitionResult(
    val templateId: String,
    val previousState: TemplateState,
    val newState: TemplateState,
    val newBankVersion: Long
)

data class TemplateBankInfo(
    val version: Long,
    val activeCount: Int,
    val shadowCount: Int,
    val quarantinedCount: Int,
    val lastUpdatedAt: Long
)

interface TemplateBankManager {
    val currentBankFlow: StateFlow<TemplateBankInfo>

    suspend fun registerDraft(draft: DynamicTemplateDraft, initialState: TemplateState? = null): String

    suspend fun validateTemplate(templateId: String): BankTransitionResult

    suspend fun activateTemplate(templateId: String): BankTransitionResult

    suspend fun transitionToShadow(templateId: String): BankTransitionResult

    suspend fun quarantineTemplate(templateId: String, reason: String): BankTransitionResult

    suspend fun disableTemplate(templateId: String): BankTransitionResult

    suspend fun promoteShadowTemplate(templateId: String): BankTransitionResult

    suspend fun getTemplate(templateId: String): DynamicTemplateEntity?

    suspend fun getBankInfo(): TemplateBankInfo

    suspend fun getLatestBankVersion(): Long
}
