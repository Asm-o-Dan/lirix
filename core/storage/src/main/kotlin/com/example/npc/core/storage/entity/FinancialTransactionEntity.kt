package com.example.npc.core.storage.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "financial_transaction",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["event_id"], unique = true),
        Index(value = ["occurred_at"], unique = false),
        Index(value = ["bank"], unique = false)
    ]
)
data class FinancialTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "event_id")
    val eventId: Long?,

    @ColumnInfo(name = "bank")
    val bank: String,

    @ColumnInfo(name = "direction")
    val direction: String,

    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,

    @ColumnInfo(name = "currency")
    val currency: String,

    @ColumnInfo(name = "balance_minor")
    val balanceMinor: Long?,

    @ColumnInfo(name = "balance_currency")
    val balanceCurrency: String?,

    @ColumnInfo(name = "merchant")
    val merchant: String?,

    @ColumnInfo(name = "account_mask")
    val accountMask: String?,

    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    @ColumnInfo(name = "extractor_id")
    val extractorId: String,

    @ColumnInfo(name = "extractor_version")
    val extractorVersion: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "extractorKind", defaultValue = "'STATIC'")
    val extractorKind: String = "STATIC",

    @ColumnInfo(name = "templateId", defaultValue = "NULL")
    val templateId: String? = null,

    @ColumnInfo(name = "bankVersion", defaultValue = "0")
    val bankVersion: Long = 0L,

    @ColumnInfo(name = "isRefund", defaultValue = "0")
    val isRefund: Boolean = false
)
