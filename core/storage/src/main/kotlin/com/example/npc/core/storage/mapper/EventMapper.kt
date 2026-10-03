package com.example.npc.core.storage.mapper

import com.example.npc.core.model.Event
import com.example.npc.core.model.Lang
import com.example.npc.core.model.ThreadKey
import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.Engine
import com.example.npc.core.storage.entity.EventEntity
import java.time.Instant

object EventMapper {
    fun toDomain(entity: EventEntity): Event = Event(
        id = entity.id,
        rawId = entity.rawId,
        ts = Instant.ofEpochMilli(entity.ts),
        title = entity.title,
        text = entity.text,
        normalizedText = entity.normalizedText,
        lang = runCatching { Lang.valueOf(entity.lang) }.getOrDefault(Lang.UNK),
        threadKey = entity.threadKey?.let { ThreadKey(it) },
        isUpdateOf = entity.isUpdateOf,
        category = runCatching { Category.valueOf(entity.category) }.getOrDefault(Category.UNCLASSIFIED),
        confidence = entity.confidence.toFloat(),
        engineUsed = runCatching { Engine.valueOf(entity.engineUsed) }.getOrDefault(Engine.NONE),
        isUserCorrected = entity.isUserCorrected,
        contentFingerprint = entity.contentFingerprint,
        pipelineRevisionId = entity.pipelineRevisionId
    )

    fun toEntity(
        domain: Event,
        category: String = domain.category.name,
        confidence: Double = domain.confidence.toDouble(),
        engineUsed: String = domain.engineUsed.name,
        isUserCorrected: Boolean = domain.isUserCorrected,
        contentFingerprint: String? = domain.contentFingerprint,
        pipelineRevisionId: Long? = domain.pipelineRevisionId
    ): EventEntity = EventEntity(
        id = domain.id,
        rawId = domain.rawId,
        ts = domain.ts.toEpochMilli(),
        title = domain.title,
        text = domain.text,
        normalizedText = domain.normalizedText,
        lang = domain.lang.name,
        threadKey = domain.threadKey?.value,
        isUpdateOf = domain.isUpdateOf,
        category = category,
        confidence = confidence,
        engineUsed = engineUsed,
        isUserCorrected = isUserCorrected,
        contentFingerprint = contentFingerprint,
        pipelineRevisionId = pipelineRevisionId
    )
}
