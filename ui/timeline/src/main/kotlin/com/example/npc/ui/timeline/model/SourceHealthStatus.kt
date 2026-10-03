package com.example.npc.ui.timeline.model

enum class SourceHealthStatus {
    GREEN,
    YELLOW,
    RED;

    val isHealthy: Boolean get() = this == GREEN
    val isWarning: Boolean get() = this == YELLOW
    val isCritical: Boolean get() = this == RED

    companion object {
        val HEALTHY = GREEN
        val WARNING = YELLOW
        val CRITICAL = RED
        val UNKNOWN = YELLOW
    }
}
