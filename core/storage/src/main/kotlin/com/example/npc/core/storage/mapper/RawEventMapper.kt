package com.example.npc.core.storage.mapper

import com.example.npc.core.model.DeduplicationKey
import com.example.npc.core.model.RawEvent
import com.example.npc.core.model.SourceId
import com.example.npc.core.storage.entity.RawEventEntity
import java.time.Instant

object RawEventMapper {
    fun toDomain(entity: RawEventEntity): RawEvent = RawEvent(
        id = entity.id,
        seq = entity.seq,
        source = SourceId(entity.source),
        packageName = entity.packageName,
        receivedAt = Instant.ofEpochMilli(entity.receivedAt),
        payloadJson = entity.payloadJson,
        hash = DeduplicationKey(entity.hash)
    )

    fun toEntity(domain: RawEvent): RawEventEntity = RawEventEntity(
        id = domain.id,
        seq = domain.seq,
        source = domain.source.value,
        packageName = domain.packageName,
        receivedAt = domain.receivedAt.toEpochMilli(),
        payloadJson = domain.payloadJson,
        hash = domain.hash.value
    )
}
