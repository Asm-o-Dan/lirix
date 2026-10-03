package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "template_bank_membership",
    primaryKeys = ["version", "templateId"]
)
data class TemplateBankMembershipEntity(
    @ColumnInfo(name = "version")
    val version: Long,

    @ColumnInfo(name = "templateId")
    val templateId: String
)
