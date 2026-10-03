package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "template_bank_version"
)
data class TemplateBankVersionEntity(
    @PrimaryKey
    @ColumnInfo(name = "version")
    val version: Long,

    @ColumnInfo(name = "parentVersion", defaultValue = "NULL")
    val parentVersion: Long? = null,

    @ColumnInfo(name = "membershipHash")
    val membershipHash: String,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long,

    @ColumnInfo(name = "cause")
    val cause: String
)
