package com.example.npc.extract.universal

/**
 * Конфигурация скоринга и порогов вероятностной модели (RoleAssignmentSolver и SafetyGate).
 */
data class ScoringConfig(
    val acceptThreshold: Float = 0.80f,
    val suggestThreshold: Float = 0.50f,
    val weightOpType: Double = 0.35,
    val weightSolver: Double = 0.45,
    val bonusCardMask: Float = 0.10f,
    val bonusBalance: Float = 0.10f,
    val priorTxAmountNoAnchor: Double = 0.85,
    val priorTxAmountWithSign: Double = 0.95,
    val priorBalanceWithAnchor: Double = 0.95,
    val priorBalanceNoAnchor: Double = 0.001,
    val priorFeeWithAnchor: Double = 0.90,
    val priorFeeNoAnchor: Double = 0.01,
    val priorOther: Double = 0.20
) {
    init {
        require(acceptThreshold in 0.0f..1.0f) { "acceptThreshold must be in [0, 1]" }
        require(suggestThreshold in 0.0f..acceptThreshold) { "suggestThreshold must be in [0, acceptThreshold]" }
    }

    companion object {
        val DEFAULT = ScoringConfig()
    }
}
