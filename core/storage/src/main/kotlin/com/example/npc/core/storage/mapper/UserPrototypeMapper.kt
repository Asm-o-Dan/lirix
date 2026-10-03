package com.example.npc.core.storage.mapper

import com.example.npc.core.model.classify.Category
import com.example.npc.core.model.classify.UserPrototype
import com.example.npc.core.storage.entity.UserPrototypeEntity
import java.time.Instant

object UserPrototypeMapper {

    fun toEntity(domain: UserPrototype): UserPrototypeEntity = UserPrototypeEntity(
        id = domain.id,
        packageName = domain.packageName,
        fingerprint = domain.fingerprint,
        category = domain.category.name,
        supportCount = domain.supportCount,
        createdAt = domain.createdAt.toEpochMilli(),
        lastSeenAt = domain.lastSeenAt.toEpochMilli()
    )

    fun toDomain(entity: UserPrototypeEntity): UserPrototype = UserPrototype(
        id = entity.id,
        packageName = entity.packageName,
        fingerprint = entity.fingerprint,
        category = Category.fromStringOrUnclassified(entity.category),
        supportCount = entity.supportCount,
        createdAt = Instant.ofEpochMilli(entity.createdAt),
        lastSeenAt = Instant.ofEpochMilli(entity.lastSeenAt)
    )
}
