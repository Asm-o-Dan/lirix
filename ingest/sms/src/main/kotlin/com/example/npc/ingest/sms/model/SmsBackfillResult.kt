package com.example.npc.ingest.sms.model

data class SmsBackfillResult(
    val importedCount: Int,
    val skippedDuplicatesCount: Int,
    val isSuccess: Boolean,
    val failureReason: String? = null
) {
    init {
        require(importedCount >= 0) { "importedCount must be >= 0" }
        require(skippedDuplicatesCount >= 0) { "skippedDuplicatesCount must be >= 0" }
        if (isSuccess) {
            require(failureReason == null) { "failureReason must be null on success" }
        } else {
            require(failureReason != null) { "failureReason must not be null on failure" }
        }
    }
}
