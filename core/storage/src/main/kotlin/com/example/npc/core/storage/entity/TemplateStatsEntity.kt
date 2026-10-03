package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "template_stats",
    foreignKeys = [
        ForeignKey(
            entity = DynamicTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TemplateStatsEntity(
    @PrimaryKey
    @ColumnInfo(name = "templateId")
    val templateId: String,

    @ColumnInfo(name = "hits", defaultValue = "0")
    val hits: Long = 0L,

    @ColumnInfo(name = "parseFailures", defaultValue = "0")
    val parseFailures: Long = 0L,

    @ColumnInfo(name = "userCorrections", defaultValue = "0")
    val userCorrections: Long = 0L,

    @ColumnInfo(name = "shadowAgreements", defaultValue = "0")
    val shadowAgreements: Long = 0L,

    @ColumnInfo(name = "shadowDisagreements", defaultValue = "0")
    val shadowDisagreements: Long = 0L,

    @ColumnInfo(name = "lastHitAt", defaultValue = "NULL")
    val lastHitAt: Long? = null
)
