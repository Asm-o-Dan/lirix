package com.example.npc.core.storage.mapper

import com.example.npc.core.model.SourceHealth
import com.example.npc.core.model.SourceId
import com.example.npc.core.storage.entity.SourceHealthEntity
import java.time.Instant

object SourceHealthMapper {
    fun toDomain(entity: SourceHealthEntity): SourceHealth = SourceHealth(
        source = SourceId(entity.source),
        lastEventAt = entity.lastEventAt?.let { Instant.ofEpochMilli(it) },
        events24h = entity.events24h,
        lastError = entity.lastError,
        queueDepth = entity.queueDepth
    )

    fun toEntity(domain: SourceHealth): SourceHealthEntity = SourceHealthEntity(
        source = domain.source.value,
        lastEventAt = domain.lastEventAt?.toEpochMilli(),
        events24h = domain.events24h,
        lastError = domain.lastError,
        queueDepth = domain.queueDepth
    )
}
