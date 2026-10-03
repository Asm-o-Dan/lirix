package com.example.npc.app.worker

data class AbsenceAlertConfig(
    val thresholdHours: Long = 6L,
    val checkIntervalMinutes: Long = 30L,
    val flexIntervalMinutes: Long = 10L,
    val suppressAlertWindowHours: Long = 4L
) {
    init {
        require(thresholdHours >= 1L) { "Absence threshold must be at least 1 hour" }
        require(checkIntervalMinutes >= 15L) { "WorkManager periodic interval must be at least 15 minutes" }
        require(flexIntervalMinutes in 5L..checkIntervalMinutes) { "Flex interval must be between 5 min and checkInterval" }
        require(suppressAlertWindowHours >= 1L) { "Alert suppression window must be at least 1 hour" }
    }
}
