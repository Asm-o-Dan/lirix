package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dynamic_template",
    indices = [
        Index(name = "idx_dyn_tmpl_src_state", value = ["sourceKey", "state"], unique = false),
        Index(name = "idx_dyn_tmpl_hash", value = ["canonicalHash"], unique = true)
    ]
)
data class DynamicTemplateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "sourceKey")
    val sourceKey: String,

    @ColumnInfo(name = "tier")
    val tier: String,

    @ColumnInfo(name = "origin")
    val origin: String,

    @ColumnInfo(name = "state")
    val state: String,

    @ColumnInfo(name = "priority")
    val priority: Int,

    @ColumnInfo(name = "pattern")
    val pattern: String,

    @ColumnInfo(name = "bindingsJson")
    val bindingsJson: String,

    @ColumnInfo(name = "constantsJson")
    val constantsJson: String,

    @ColumnInfo(name = "amountFormatJson")
    val amountFormatJson: String,

    @ColumnInfo(name = "specVersion")
    val specVersion: Int,

    @ColumnInfo(name = "compilerVersion")
    val compilerVersion: Int,

    @ColumnInfo(name = "canonicalHash")
    val canonicalHash: String,

    @ColumnInfo(name = "specificity")
    val specificity: Double,

    @ColumnInfo(name = "parentTemplateId")
    val parentTemplateId: String? = null,

    @ColumnInfo(name = "sampleEventId")
    val sampleEventId: String? = null,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long,

    @ColumnInfo(name = "stateReason")
    val stateReason: String? = null
)
