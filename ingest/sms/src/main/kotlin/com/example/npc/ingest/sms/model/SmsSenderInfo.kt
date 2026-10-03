package com.example.npc.ingest.sms.model

data class SmsSenderInfo(
    val address: String,
    val displayName: String?,
    val isAlphanumeric: Boolean
)
