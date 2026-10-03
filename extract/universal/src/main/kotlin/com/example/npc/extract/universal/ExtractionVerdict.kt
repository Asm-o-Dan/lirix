package com.example.npc.extract.universal

/**
 * Вердикт защитного шлюза (Gate) универсального экстрактора.
 */
enum class ExtractionVerdict {
    /**
     * Уверенность >= 0.80, авто-подтверждение.
     */
    ACCEPT,

    /**
     * Уверенность 0.50..0.79, требует подтверждения человеком (HITL).
     */
    SUGGEST,

    /**
     * Уверенность < 0.50 или сработал Veto.
     */
    REJECT
}

data class SafetyCheckResult(
    val verdict: ExtractionVerdict,
    val finalScore: Float,
    val isOtpVeto: Boolean = false,
    val isPromoVeto: Boolean = false,
    val reason: String? = null
)
