package com.example.npc.ui.timeline.model

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine

@Immutable
data class EventUiModel(
    val id: Long,
    val rawId: Long,
    val formattedTime: String,
    val title: String,
    val text: String,
    val sourceName: String,
    @DrawableRes val sourceIconRes: Int,
    val category: Category = Category.UNCLASSIFIED,
    val confidence: Float = 0.0f,
    val engineUsed: Engine = Engine.NONE,
    val isUserCorrected: Boolean = false,
    val isPrototypeDerived: Boolean = engineUsed == Engine.PROTOTYPE,
    val transaction: FinancialTransactionUiModel? = null,
    val financialData: FinancialTransactionUiModel? = transaction,
    val hasFinancialData: Boolean = transaction != null,
    val normalizedText: String = text.lowercase(),
    val langLabel: String = "RU",
    val isUpdate: Boolean = false,
    val threadKey: String? = null,
    val packageName: String? = null,
    val isUpdateOf: Long? = null,
    val payloadJson: String? = null,
    val contentFingerprint: String? = null,
    val pipelineRevisionId: Long? = null
) {
    init {
        require(id >= 0L) { "id must be >= 0" }
        require(rawId >= 0L) { "rawId must be >= 0" }
        require(title.isNotEmpty()) { "displayTitle must not be empty" }
        require(formattedTime.isNotEmpty()) { "timeLabel must not be empty" }
        require(confidence in 0.0f..1.0f) { "confidence must be in 0.0..1.0, got $confidence" }
    }

    val displayTitle: String get() = title
    val displayText: String get() = text
    val timeLabel: String get() = formattedTime
    val source: String get() = sourceName
    val engine: Engine get() = engineUsed

    constructor(
        id: Long,
        rawId: Long,
        displayTitle: String,
        displayText: String,
        normalizedText: String,
        timeLabel: String,
        sourceName: String,
        sourceIconRes: Int,
        langLabel: String,
        isUpdate: Boolean,
        threadKey: String?,
        packageName: String? = null,
        isUpdateOf: Long? = null,
        payloadJson: String? = null,
        category: Category = Category.UNCLASSIFIED,
        confidence: Float = 0.0f,
        engineUsed: Engine = Engine.NONE,
        isUserCorrected: Boolean = false,
        contentFingerprint: String? = null,
        financialData: FinancialTransactionUiModel? = null,
        pipelineRevisionId: Long? = null
    ) : this(
        id = id,
        rawId = rawId,
        formattedTime = timeLabel,
        title = displayTitle,
        text = displayText,
        sourceName = sourceName,
        sourceIconRes = sourceIconRes,
        category = category,
        confidence = confidence,
        engineUsed = engineUsed,
        isUserCorrected = isUserCorrected,
        isPrototypeDerived = engineUsed == Engine.PROTOTYPE,
        transaction = financialData,
        financialData = financialData,
        hasFinancialData = financialData != null,
        normalizedText = normalizedText,
        langLabel = langLabel,
        isUpdate = isUpdate,
        threadKey = threadKey,
        packageName = packageName,
        isUpdateOf = isUpdateOf,
        payloadJson = payloadJson,
        contentFingerprint = contentFingerprint,
        pipelineRevisionId = pipelineRevisionId
    )
}
