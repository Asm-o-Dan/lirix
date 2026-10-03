package com.example.npc.ingest.sms

data class SmsContract(
    val originAddress: String,
    val body: String,
    val timestampMillis: Long
) {
    init {
        require(originAddress.isNotBlank()) { "originAddress must not be blank" }
        require(timestampMillis >= 0L) { "timestampMillis must be >= 0" }
    }
}
